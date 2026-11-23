# Android local prototype

Version 0.7 adds an explicit background recent-message request using the shared Rust Session transport, bounded conversation/message projection, conversation rows and incoming/outgoing chat bubbles. The debug-only sample conversations are visibly marked as test data. Cached-name/last-message search supports Vietnamese with or without accents. Per-conversation drafts stay in memory and are cleared when the account changes. Sending, full history and authenticated preview compatibility are not yet verified; the composer has no send action. See ADR 0012 and the 2026-11-23 recent-conversations verification record.

Version 0.6 reads the protected vault on Activity start/resume and restores a bounded, validated Session into native memory without a plaintext temporary file. The UI explicitly labels a cached Session as not yet server-verified. Synthetic native restore tests passed. A no-account emulator request reached waiting-for-scan, was cancelled without scanning, and created no Session file. Real-account completion and valid-session app-restart restore are still unverified; see `../docs/verification/2026-11-23-android-restore.md`.

Version 0.5 wires an experimental companion QR flow through JNI and adds Keystore/AES-GCM Session output. The QR screen and synthetic vault self-test were exercised on the internal emulator app, but the create-QR/server/scan/confirm path was deliberately not run. Login interoperability, restoration on restart and chat sync/send remain unverified or incomplete. Both debug APKs are now about 14.1 MB with two ABIs and network/TLS code; no release is published. See `../docs/verification/2026-11-22-android-qr-vault.md`.

Version 0.4 replaces the default tool page with a mobile navigation shell: blue search header, Messages/Contacts/Discover/Timeline/Profile tabs, local draft screen, and tools reached from the shell. This is an initial layout correction, not Zalo login/chat support or a complete visual match. The personal GIF index now supports name/tag search, favorites, 12-row pagination, editing metadata and confirmed deletion. Selection rules have three JVM tests; full UI management flows remain only partially tested. See `../docs/verification/2026-11-22-android-shell.md`.

This is a separate Android UI that links the portable Rust core through JNI. It is a local-only prototype for importing/viewing personal GIFs, receiving GIFs through Android's share sheet, and creating, editing, searching and inserting up to 100 quick message templates. It does not log in to Zalo or send messages, and it is not a replacement for the desktop GUI. The share sheet accepts saved `image/gif` content only; video, WebP and links from other apps are not accepted yet.

Version 0.2 adds recipient-name entry and a preview/confirm step before inserting a template into the local draft. `{ten}`, `{gio}` and `{ngay}` use the same one-pass Rust expansion as Windows, so replacement values are never expanded recursively. Both APK variants were rebuilt and installed on the test emulator; the complete preview/insert flow was exercised on the internal variant. See `../docs/verification/2026-11-22-android-template-preview.md`.

The two debug variants have different package IDs and update sources:

| Variant | Package ID | GitHub update source |
| --- | --- | --- |
| fork | `org.ctdozlite.mobile.fork` | `minhkhang1008/ctdozlite-next` |
| internal | `org.ctdozlite.mobile.internal` | `giaphudthw123-ctrl/zalite-windows-android` via `-PinternalUpdateRepo=owner/repo` at build time |

Run `build-local.ps1 -InternalUpdateRepo owner/repo` on Windows after installing Rust targets `aarch64-linux-android` and `x86_64-linux-android`, Android SDK platform 36, an NDK, Gradle 9.6, and JDK 17 or newer. Set `ANDROID_HOME` and put `cargo` and `gradle.bat` on PATH. The script compiles the shared Rust core for both Android ABIs, runs Android unit tests, and builds both debug APKs. Outputs are under `app/build/outputs/apk/{fork,internal}/debug/`.

Updates require a GitHub release with the exact configured APK asset name and SHA-256 metadata. The incoming APK must have the same package ID and signing certificate and a higher version code. The current debug builds are suitable for emulator testing only: Android's local debug signing key is not a stable release key. The fork's current release has no Android APK, and the internal repository has no APK release yet; both update buttons therefore report an unavailable update. No APK is distributed or connected to a real Zalo account at this stage.
