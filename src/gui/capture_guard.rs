//! Best-effort Windows capture exclusion for this process's active top-level window.

#![cfg(target_os = "windows")]

use std::ffi::c_void;

const WDA_NONE: u32 = 0;
const WDA_EXCLUDEFROMCAPTURE: u32 = 0x11;

#[link(name = "user32")]
extern "system" {
    fn GetActiveWindow() -> *mut c_void;
    fn GetWindowThreadProcessId(window: *mut c_void, process_id: *mut u32) -> u32;
    fn SetWindowDisplayAffinity(window: *mut c_void, affinity: u32) -> i32;
}
#[link(name = "kernel32")]
extern "system" {
    fn GetCurrentProcessId() -> u32;
}

/// `Ok(false)` means no GUI window is active yet; the caller should retry later.
pub fn apply(enabled: bool) -> std::io::Result<bool> {
    let window = unsafe { GetActiveWindow() };
    if window.is_null() {
        return Ok(false);
    }
    let mut owner = 0;
    if unsafe { GetWindowThreadProcessId(window, &mut owner) } == 0
        || owner != unsafe { GetCurrentProcessId() }
    {
        return Ok(false);
    }
    let affinity = if enabled {
        WDA_EXCLUDEFROMCAPTURE
    } else {
        WDA_NONE
    };
    if unsafe { SetWindowDisplayAffinity(window, affinity) } == 0 {
        return Err(std::io::Error::last_os_error());
    }
    Ok(true)
}
