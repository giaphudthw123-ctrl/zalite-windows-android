# Windows UI and background restore follow-up — 2026-11-15

This is a source and no-account test record for the Windows-first branch. The user's Zalo PC screenshots were used as a layout reference; they are not copied into the repository because they contain private conversations. No real Zalo account was used or inspected.

| Area | Current evidence | Limit |
| --- | --- | --- |
| Composer | Toolbar moved above a three-line minimum editor; GIF, quick-message and reminder entry points added. Windows release builds. | Not visually exercised inside an authenticated conversation. |
| Left rail and menus | Local-tools popover, unread tab and bottom-anchored account/settings menu compile. Existing quick-message, personal GIF, starred-message and reminder flows are connected. | Catalog and automatic reply are not implemented by this menu; no claim of zBusiness entitlement. |
| Historical photo without URL | Renders a missing-preview card instead of the internal `[Hình ảnh]` marker. | Cannot recover an image whose CDN URL is absent from local history. |
| One GUI process | Windows named mutex test passes; a same-shell no-account launch left the first process running and the second exited with code 0. | Separately sandboxed launches may have distinct named-object namespaces. Existing older binaries do not use this mutex. |
| X then reopen | The first manual no-account test proved X hid the window and a second launch did **not** reveal it. The source was then changed to reveal the native HWND with `ShowWindowAsync` before asking egui to repaint. On 2026-11-21, a fresh empty profile was opened, X hid the window, and a second launch exited with code 0 while the original window visibly reappeared. | Tested without a Zalo account. Session persistence after a real login remains unverified. |
| Folder selection | Settings now calls a native folder picker on a blocking worker and accepts an existing directory. | Not clicked in an authenticated GUI. |
| Session persistence | QR success now requires a nonempty account ID and session cookie, and reports a failed `session.json` write instead of silently succeeding. | No QR login or close/reopen of a real session was performed. Server expiry remains a possible cause of QR prompts. |
| Capture exclusion | An isolated no-account profile with capture protection enabled returned Windows display affinity `0x11` from `GetWindowDisplayAffinity`. | This does not prove every capture tool is blocked; no OBS/Discord/Snipping Tool comparison was performed. |

## Build and tests

- `cargo check --locked --offline --bin ctdozlite --message-format=short`: passed after the final native-window restore change. One pre-existing `unused_mut` warning in `src/data_dir.rs` remains.
- `cargo build --release --locked --offline --bin ctdozlite`: passed after the final native-window restore change. Build path: `D:\user\TIN\zalo client\ctdozlite-ui-target\release\ctdozlite.exe`.
- `cargo test --locked --offline --lib`: 364 passed, 2 ignored, 0 failed on the final source when the loopback-only fixture was allowed to bind `127.0.0.1`.
- `cargo fmt --all -- --check` and `git diff --check`: passed on the final source.

## Resource measurements

Thirty seconds with a separate empty data directory, before the final native-window restore change:

| Measure | Earlier Windows no-login baseline | This run |
| --- | ---: | ---: |
| Executable | 78.82 MiB | 78.91 MiB |
| Peak working set | 122.07 MiB | 113.41 MiB |
| Peak private memory | 45.32 MiB | 52.46 MiB |
| Mean CPU, one-core basis | 0.27% | 1.04% |
| Test-profile files | not directly comparable | 60 KiB |

These are separate no-login runs, not a controlled performance comparison. The increase in private memory and CPU is real in these samples but may include startup/QR activity. Network bytes were not measured per process, so no network-improvement claim is made.

## Open gates

- Test tray “Mở/Thoát” on the final release build. X → launch again passed on 2026-11-21 with an empty profile.
- Inspect the composer, menu placement, special-character font, and folder picker in an actual conversation without sending any message.
- Verify QR session persistence only with explicit permission to use a real test account; do not use the owner's account implicitly.
- Do not publish an installer or APK release from this UI revision until the remaining security and device gates are complete.
