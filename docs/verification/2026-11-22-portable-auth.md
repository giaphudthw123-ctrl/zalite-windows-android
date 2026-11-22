# Portable authentication compilation checkpoint — 2026-11-22

`crates/ctdozlite-auth` compiles the existing companion authentication sources directly through module paths. It includes Session, login, QR, endpoints, crypto and ParamCipher plus their existing helpers. The previously call-module-owned device generator now lives in `src/protocol_identity.rs`; the desktop call API delegates to it. No server protocol or identity bytes were intentionally changed.

| Gate | Result |
| --- | --- |
| Host crate check | Passed. |
| Authentication source tests | 57 passed, 0 failed, using synthetic fixtures; no live login. |
| Root desktop library | 381 passed, 2 ignored, 0 failed after identity extraction. |
| Android ARM64 release library | Passed with NDK API-28 linker, final lockfile. |
| Android x86_64 release library | Passed with NDK API-28 linker, final lockfile. |
| Dependency boundary | Cargo tree has no eframe/egui/cpal/wasmtime/rusqlite. |
| Windows release | Passed; EXE 83,068,404 bytes, SHA-256 `15213207847FC80D50916D525E88E647D86940A379EC922D88E12A28C0F26737`. |

Initial standalone offline dependency selection failed because the cached Tokio version was yanked. Resolution was seeded from the already-used root lockfile, then reduced to this crate's actual dependencies; locked offline builds subsequently passed. No replacement crate was downloaded. One unused aes-gcm dependency was removed after the first ARM64 build; final ARM64 was rebuilt successfully.

These are Rust library artifacts, not a new APK with authentication. The Android JNI library does not yet link this crate or expose a Session owner. Required next gates: lifecycle/cancellation, Keystore session persistence, typed error/status mapping, QR display, no-account error tests and explicit authorization before any real-account test. The shared non-Windows session-storage fallback is plaintext and must not be used for Android live credentials.

No live account, QR request, authenticated message, Android-network benchmark or updated APK was used/claimed in this checkpoint. This step does not fulfill Android login/chat requirements. See ADR 0009.
