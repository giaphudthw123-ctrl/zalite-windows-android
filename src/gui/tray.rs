//! System tray / menu-bar icon so the app keeps running after its window is closed.
//!
//! Windows and macOS only (Linux needs GTK + appindicator system libraries; it falls back to
//! minimising). Tray events arrive on OS threads, so they only set atomic flags and request a
//! repaint; the UI loop in `update` consumes the flags.

#[cfg(target_os = "windows")]
use std::ffi::c_void;
#[cfg(target_os = "windows")]
use std::sync::atomic::AtomicIsize;
use std::sync::atomic::{AtomicBool, Ordering};

static TRAY_ACTIVE: AtomicBool = AtomicBool::new(false);
static SHOW_REQUESTED: AtomicBool = AtomicBool::new(false);
static QUIT_REQUESTED: AtomicBool = AtomicBool::new(false);
#[cfg(target_os = "windows")]
static RESTORE_CONTEXT: std::sync::OnceLock<eframe::egui::Context> = std::sync::OnceLock::new();
#[cfg(target_os = "windows")]
static RESTORE_WINDOW: AtomicIsize = AtomicIsize::new(0);

#[cfg(target_os = "windows")]
#[link(name = "user32")]
extern "system" {
    fn GetActiveWindow() -> *mut c_void;
    fn GetWindowThreadProcessId(window: *mut c_void, process_id: *mut u32) -> u32;
    fn IsWindow(window: *mut c_void) -> i32;
    fn ShowWindowAsync(window: *mut c_void, command: i32) -> i32;
    fn SetForegroundWindow(window: *mut c_void) -> i32;
}
#[cfg(target_os = "windows")]
#[link(name = "kernel32")]
extern "system" {
    fn GetCurrentProcessId() -> u32;
}

#[cfg(target_os = "windows")]
pub fn register_restore_context(ctx: &eframe::egui::Context) {
    let _ = RESTORE_CONTEXT.set(ctx.clone());
}

/// Capture the main HWND on the GUI thread before the user hides it.
#[cfg(target_os = "windows")]
pub fn register_restore_window() {
    if RESTORE_WINDOW.load(Ordering::Relaxed) != 0 {
        return;
    }
    let window = unsafe { GetActiveWindow() };
    if window.is_null() {
        return;
    }
    let mut owner = 0;
    if unsafe { GetWindowThreadProcessId(window, &mut owner) } != 0
        && owner == unsafe { GetCurrentProcessId() }
    {
        RESTORE_WINDOW.store(window as isize, Ordering::Release);
    }
}

#[cfg(target_os = "windows")]
fn reveal_native_window() {
    let window = RESTORE_WINDOW.load(Ordering::Acquire) as *mut c_void;
    if window.is_null() || unsafe { IsWindow(window) } == 0 {
        return;
    }
    // A hidden winit window may not repaint, so make it visible from the event thread first.
    unsafe {
        ShowWindowAsync(window, 9); // SW_RESTORE
        ShowWindowAsync(window, 5); // SW_SHOW
        SetForegroundWindow(window);
    }
}

fn request_show(ctx: &eframe::egui::Context) {
    SHOW_REQUESTED.store(true, Ordering::Release);
    #[cfg(target_os = "windows")]
    reveal_native_window();
    ctx.request_repaint();
}

#[cfg(target_os = "windows")]
pub fn request_show_from_second_instance() {
    if let Some(ctx) = RESTORE_CONTEXT.get() {
        request_show(ctx);
    } else {
        SHOW_REQUESTED.store(true, Ordering::Release);
    }
}

/// True when a tray icon exists, so hiding the window is recoverable.
pub fn is_active() -> bool {
    TRAY_ACTIVE.load(Ordering::Relaxed)
}

pub fn take_show_request() -> bool {
    SHOW_REQUESTED.swap(false, Ordering::Relaxed)
}

pub fn take_quit_request() -> bool {
    QUIT_REQUESTED.swap(false, Ordering::Relaxed)
}

/// Owns the tray icon; dropping it removes the icon.
pub struct Tray {
    #[cfg(any(target_os = "windows", target_os = "macos"))]
    _icon: tray_icon::TrayIcon,
}

impl Tray {
    #[cfg(any(target_os = "windows", target_os = "macos"))]
    pub fn new(ctx: &eframe::egui::Context) -> Option<Self> {
        use tray_icon::menu::{Menu, MenuEvent, MenuItem};
        use tray_icon::{Icon, MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};

        let icon_data = super::theme::load_app_icon()?;
        let icon = Icon::from_rgba(icon_data.rgba, icon_data.width, icon_data.height).ok()?;

        let show_item = MenuItem::with_id("show", "Mở Zalo", true, None);
        let quit_item = MenuItem::with_id("quit", "Thoát", true, None);
        let menu = Menu::new();
        menu.append_items(&[&show_item, &quit_item]).ok()?;

        let repaint_ctx = ctx.clone();
        MenuEvent::set_event_handler(Some(move |event: MenuEvent| {
            match event.id.0.as_str() {
                "show" => request_show(&repaint_ctx),
                "quit" => {
                    QUIT_REQUESTED.store(true, Ordering::Release);
                    #[cfg(target_os = "windows")]
                    reveal_native_window();
                }
                _ => {}
            }
            repaint_ctx.request_repaint();
        }));
        let repaint_ctx = ctx.clone();
        TrayIconEvent::set_event_handler(Some(move |event: TrayIconEvent| {
            // Left click / double click restores the window (Windows convention).
            let restore = matches!(
                event,
                TrayIconEvent::Click {
                    button: MouseButton::Left,
                    button_state: MouseButtonState::Up,
                    ..
                } | TrayIconEvent::DoubleClick {
                    button: MouseButton::Left,
                    ..
                }
            );
            if restore {
                request_show(&repaint_ctx);
            }
        }));

        let tray = TrayIconBuilder::new()
            .with_menu(Box::new(menu))
            .with_menu_on_left_click(false)
            .with_tooltip(crate::APP_DISPLAY_NAME)
            .with_icon(icon)
            .build()
            .ok()?;
        TRAY_ACTIVE.store(true, Ordering::Relaxed);
        Some(Self { _icon: tray })
    }

    #[cfg(not(any(target_os = "windows", target_os = "macos")))]
    pub fn new(_ctx: &eframe::egui::Context) -> Option<Self> {
        None
    }
}
