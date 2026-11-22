//! Versioned Windows user-bound session protection; legacy JSON remains readable.
use anyhow::{ensure, Context, Result};
use std::{io::Read, path::Path};

const MAGIC: &[u8] = b"ZALITE-DPAPI-1\0";
const MAX_BYTES: u64 = 8 * 1024 * 1024;

/// Read a bounded session, decrypting the Windows envelope when present.
/// Returns an error for corrupt data or an unavailable Windows protection context.
pub fn read(path: &Path) -> Result<Vec<u8>> {
    let mut bytes = Vec::new();
    std::fs::File::open(path)?
        .take(MAX_BYTES + 1)
        .read_to_end(&mut bytes)?;
    ensure!(bytes.len() as u64 <= MAX_BYTES, "Tệp phiên quá lớn");
    if let Some(payload) = bytes.strip_prefix(MAGIC) {
        #[cfg(target_os = "windows")]
        {
            return windows::transform(payload, false);
        }
        #[cfg(not(target_os = "windows"))]
        {
            let _ = payload;
            anyhow::bail!("Phiên này chỉ mở được bằng tài khoản Windows đã lưu");
        }
    }
    ensure!(
        !bytes.starts_with(b"ZALITE-DPAPI-"),
        "Định dạng phiên không được hỗ trợ"
    );
    Ok(bytes)
}

/// Atomically store a session with user-bound DPAPI on Windows.
/// Returns an error without a plaintext fallback if protection or writing fails.
pub fn write(path: &Path, plaintext: &[u8]) -> Result<()> {
    ensure!(
        plaintext.len() as u64 <= MAX_BYTES - 4096,
        "Tệp phiên quá lớn"
    );
    #[cfg(target_os = "windows")]
    let bytes = {
        let protected = windows::transform(plaintext, true)?;
        let mut bytes = MAGIC.to_vec();
        bytes.extend_from_slice(&protected);
        bytes
    };
    #[cfg(not(target_os = "windows"))]
    let bytes = plaintext.to_vec();
    crate::data_dir::write_private_atomic(path, &bytes).context("Không lưu được phiên đăng nhập")
}

#[cfg(target_os = "windows")]
mod windows {
    use super::*;
    use std::ffi::c_void;
    #[repr(C)]
    struct Blob {
        len: u32,
        data: *mut u8,
    }
    #[link(name = "crypt32")]
    extern "system" {
        fn CryptProtectData(
            input: *const Blob,
            description: *const u16,
            entropy: *const Blob,
            reserved: *mut c_void,
            prompt: *mut c_void,
            flags: u32,
            output: *mut Blob,
        ) -> i32;
        fn CryptUnprotectData(
            input: *const Blob,
            description: *mut *mut u16,
            entropy: *const Blob,
            reserved: *mut c_void,
            prompt: *mut c_void,
            flags: u32,
            output: *mut Blob,
        ) -> i32;
    }
    #[link(name = "kernel32")]
    extern "system" {
        fn LocalFree(memory: *mut c_void) -> *mut c_void;
    }

    pub(super) fn transform(bytes: &[u8], protect: bool) -> Result<Vec<u8>> {
        ensure!(
            !bytes.is_empty() && bytes.len() as u64 <= MAX_BYTES,
            "Dữ liệu phiên không hợp lệ"
        );
        let input = Blob {
            len: bytes.len() as u32,
            data: bytes.as_ptr() as *mut u8,
        };
        let mut output = Blob {
            len: 0,
            data: std::ptr::null_mut(),
        };
        // SAFETY: blobs remain valid for the synchronous call. Optional pointers are null;
        // UI_FORBIDDEN (1) uses current-user scope without LOCAL_MACHINE or a prompt.
        let success = unsafe {
            if protect {
                CryptProtectData(
                    &input,
                    std::ptr::null(),
                    std::ptr::null(),
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                    1,
                    &mut output,
                )
            } else {
                CryptUnprotectData(
                    &input,
                    std::ptr::null_mut(),
                    std::ptr::null(),
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                    1,
                    &mut output,
                )
            }
        };
        ensure!(
            success != 0,
            "Windows không bảo vệ hoặc mở được phiên đăng nhập"
        );
        let result = if output.data.is_null()
            || output.len == 0
            || u64::from(output.len) > MAX_BYTES
        {
            Err(anyhow::anyhow!("Kích thước phiên Windows không hợp lệ"))
        } else {
            // SAFETY: a successful DPAPI call owns output.len bytes until LocalFree below.
            Ok(unsafe { std::slice::from_raw_parts(output.data, output.len as usize) }.to_vec())
        };
        // SAFETY: DPAPI allocates this buffer with LocalAlloc; wipe plaintext before freeing.
        unsafe {
            if !output.data.is_null() {
                if !protect {
                    for index in 0..output.len as usize {
                        std::ptr::write_volatile(output.data.add(index), 0);
                    }
                }
                LocalFree(output.data.cast());
            }
        }
        result
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn legacy_and_protected_session_roundtrip() {
        let root = crate::data_dir::PrivateTempDir::new("session-protection-fixture").unwrap();
        let path = root.path().join("session.json");
        let fixture = br#"{"login":{"uin":"fixture"},"token":"synthetic-secret"}"#;
        std::fs::write(&path, fixture).unwrap();
        assert_eq!(read(&path).unwrap(), fixture);
        write(&path, fixture).unwrap();
        assert_eq!(read(&path).unwrap(), fixture);
        #[cfg(target_os = "windows")]
        {
            let first = std::fs::read(&path).unwrap();
            assert!(first.starts_with(MAGIC));
            assert!(!first
                .windows(b"synthetic-secret".len())
                .any(|part| part == b"synthetic-secret"));
            write(&path, fixture).unwrap();
            assert_ne!(std::fs::read(&path).unwrap(), first);
            let mut damaged = first;
            let last = damaged.len() - 1;
            damaged[last] ^= 1;
            std::fs::write(&path, damaged).unwrap();
            assert!(read(&path).is_err());
        }
    }
}
