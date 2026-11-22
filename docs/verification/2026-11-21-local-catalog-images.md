# Local product images — 2026-11-21

Later account-scoping changes are recorded in [the account storage checkpoint](2026-11-21-account-storage.md). Its current import path no longer deletes orphaned images automatically.

The Windows catalog editor now imports a PNG, JPEG or GIF from a local file, shows a bounded preview and can attach the managed copy alongside the product's text card in the current composer. The user must inspect the recipient and send manually. The app does not create a Zalo Catalog item.

The import runs off the UI thread. It rejects symlinks, files above 10 MiB, unsupported or undecodable formats, and images above 16 million pixels. The image is copied into a private hash-named file, deduplicated, and the managed directory has a 200 MiB cap. Preview checks the stored content hash and decodes at no more than 160 by 160 pixels. Orphaned managed images are removed on a later import.

| Check | Result | Limit |
| --- | --- | --- |
| Targeted catalog tests | 8 passed, including version-3 migration, deduplication, size/path validation, preview and modified-file rejection | Synthetic images and local databases only. |
| Full Rust library suite | 372 passed, 2 ignored, 0 failed with the synthetic loopback fixture allowed | No Zalo account or network request. |
| Windows compiler | `cargo check --locked --offline --bin ctdozlite` passed | Does not prove the editor click path. |
| Windows release build | Final `cargo build --release --locked --offline --bin ctdozlite` passed after the account-switch fix; EXE 79.13 MiB, SHA-256 `B4B55077F041640285A43E6709FE35388F09030D0AA031F0F71EACC6571BFD8B` | Unsigned local binary; no installer release. |
| Manual Windows UI | Not run on the authenticated catalog screen | User stopped the previous Computer Use session. No QR scan or real message was used. |

Thirty-second no-login sample of the final release build with a fresh isolated profile, excluding the first three startup seconds: peak working set **114.41 MiB**, peak private memory **52.02 MiB**, mean CPU **0.36% of one core**, profile files **76 KiB**, executable **79.13 MiB**. This is a steady empty-profile sample; it does not measure the catalog or chat after login. Per-process network bytes were not measured, and no performance improvement is claimed from this sample.

After the initial checkpoint, the picker was changed to return its selected path to the serialized worker. The worker checks the active account again and holds the session guard until the import completes, so a queued account switch cannot redirect the import into the next account's live database. The UI also clears the prior account's product editor, message draft and pending attachments when an account switch completes. This path passed compilation and the library suite; a real multi-account UI test is still needed.

Open: authenticated add/edit/preview/attach click-through, real Zalo image send, multi-account UI regression, encryption of the product table and image files, encrypted backup of these images, Android catalog UI and per-process network sampling. These gates prevent a release claim.
