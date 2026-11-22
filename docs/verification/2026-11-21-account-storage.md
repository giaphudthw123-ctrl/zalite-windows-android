# Account data and product-image isolation — 2026-11-21

This checkpoint addresses two data-loss paths found during review of the Windows client. New product images are stored under `product-images/<account-id>/`, with a 200 MiB cap per account. Import does not delete existing files or open the live Store. Existing flat image paths still preview. The worker rejects an image selection when its account differs from the active Session or the disk owner marker.

Account switching now validates the target account ID and target Session JSON before changing live files. A missing, malformed or mismatched target leaves the current files in place. Saving the current account uses atomic file replacement for the Session and thread cache and SQLite `VACUUM INTO` for a consistent parked Store. A failed Store snapshot leaves the previous parked copy in place. This is account switching, not an encrypted export or zCloud backup.

| Check | Result | Limit |
| --- | --- | --- |
| Product-image tests | 9 passed in the final library suite, including account isolation and legacy flat-path preview | Synthetic local images only. |
| Account manager tests | 8 passed in the final library suite, including missing/mismatched target, two-account roundtrip, committed WAL data, failed snapshot and wrong owner marker | Synthetic Session and Store fixtures; no Zalo account. |
| Full Rust library suite | 379 passed, 2 ignored, 0 failed with the synthetic loopback fixture allowed to bind localhost | No authenticated UI path. |
| Windows release build | Passed; EXE 79.16 MiB, SHA-256 `81D3CE37098B85DEF658A9E6EE8E07F1CF8C2D6CF5807504E97419FE95CC6523` | No installer or APK release. |
| Manual UI | Not run for two authenticated accounts | User's real account is not used. |

Open: partial failure during target file replacement still needs a staged rollback; Windows data directory ACL hardening and encryption remain open. Orphaned images now remain until a safe, user-controlled cleanup flow exists. Real multi-account UI and actual Zalo image send are unverified. Network bytes are not measured in this checkpoint.

Final build confirmation and empty-profile sample on 2026-11-22: 30 seconds after three startup seconds, peak working set 114.64 MiB, peak private memory 52.50 MiB, mean CPU 1.77% of one core, profile files 76 KiB. This login-screen sample does not measure authenticated chat or account switching and does not prove a performance improvement. Formatting and diff checks passed. One existing `unused_mut` warning in `data_dir.rs` remains.
