//! Keep one Windows GUI process per data directory and restore it on a second launch.

#![cfg(target_os = "windows")]

use anyhow::{bail, Result};
use sha2::{Digest, Sha256};
use std::ffi::c_void;
use std::os::windows::ffi::OsStrExt;
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

const ERROR_ALREADY_EXISTS: u32 = 183;
const INFINITE: u32 = 0xFFFF_FFFF;
const WAIT_OBJECT_0: u32 = 0;

#[link(name = "kernel32")]
extern "system" {
    fn CreateMutexW(attributes: *const c_void, initial_owner: i32, name: *const u16)
        -> *mut c_void;
    fn CreateEventW(
        attributes: *const c_void,
        manual_reset: i32,
        initial_state: i32,
        name: *const u16,
    ) -> *mut c_void;
    fn SetEvent(event: *mut c_void) -> i32;
    fn WaitForSingleObject(handle: *mut c_void, milliseconds: u32) -> u32;
    fn CloseHandle(handle: *mut c_void) -> i32;
    fn GetLastError() -> u32;
}

/// Owns the process-wide mutex and event until the GUI exits.
pub struct SingleInstance {
    mutex: *mut c_void,
    event: *mut c_void,
    stop: Arc<AtomicBool>,
    watcher: Option<std::thread::JoinHandle<()>>,
}

impl SingleInstance {
    /// Return `None` after signaling an already-running GUI to show itself.
    pub fn acquire(data_dir: &Path) -> Result<Option<Self>> {
        let canonical = data_dir.canonicalize()?;
        let key = canonical.to_string_lossy().to_lowercase();
        let digest = Sha256::digest(key.as_bytes());
        let suffix: String = digest[..16]
            .iter()
            .map(|byte| format!("{byte:02x}"))
            .collect();
        let event_name = wide(&format!("Local\\ctdozlite-show-{suffix}"));
        let mutex_name = wide(&format!("Local\\ctdozlite-one-{suffix}"));

        // Create the event first so a concurrent second launch can always signal it.
        let event = unsafe { CreateEventW(std::ptr::null(), 0, 0, event_name.as_ptr()) };
        if event.is_null() {
            bail!("Không tạo được tín hiệu mở cửa sổ Zalo");
        }
        let mutex = unsafe { CreateMutexW(std::ptr::null(), 0, mutex_name.as_ptr()) };
        if mutex.is_null() {
            unsafe { CloseHandle(event) };
            bail!("Không khóa được phiên ứng dụng Zalo");
        }
        if unsafe { GetLastError() } == ERROR_ALREADY_EXISTS {
            let signaled = unsafe { SetEvent(event) } != 0;
            unsafe {
                CloseHandle(mutex);
                CloseHandle(event);
            }
            if !signaled {
                bail!("Zalo đã chạy nhưng không thể gọi lại cửa sổ cũ");
            }
            return Ok(None);
        }

        let event_value = event as usize;
        let stop = Arc::new(AtomicBool::new(false));
        let watcher_stop = Arc::clone(&stop);
        let watcher = std::thread::spawn(move || {
            let event = event_value as *mut c_void;
            while unsafe { WaitForSingleObject(event, INFINITE) } == WAIT_OBJECT_0 {
                if watcher_stop.load(Ordering::Acquire) {
                    break;
                }
                crate::gui::request_restore_from_second_instance();
            }
        });
        Ok(Some(Self {
            mutex,
            event,
            stop,
            watcher: Some(watcher),
        }))
    }
}

impl Drop for SingleInstance {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Release);
        unsafe { SetEvent(self.event) };
        if let Some(watcher) = self.watcher.take() {
            let _ = watcher.join();
        }
        unsafe {
            CloseHandle(self.mutex);
            CloseHandle(self.event);
        }
    }
}

fn wide(value: &str) -> Vec<u16> {
    std::ffi::OsStr::new(value)
        .encode_wide()
        .chain(std::iter::once(0))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::SingleInstance;

    #[test]
    fn second_launch_signals_existing_instance_for_same_data_directory() {
        let data_dir = std::env::temp_dir().join(format!(
            "ctdozlite-single-instance-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|duration| duration.as_nanos())
                .unwrap_or_default()
        ));
        std::fs::create_dir(&data_dir).unwrap();

        let first = SingleInstance::acquire(&data_dir).unwrap();
        assert!(first.is_some());
        assert!(SingleInstance::acquire(&data_dir).unwrap().is_none());
        drop(first);
        assert!(SingleInstance::acquire(&data_dir).unwrap().is_some());
    }
}
