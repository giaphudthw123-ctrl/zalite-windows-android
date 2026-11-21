# Local product images — 2026-11-21

The Windows catalog editor now imports a PNG, JPEG or GIF from a local file, shows a bounded preview and can attach the managed copy alongside the product's text card in the current composer. The user must inspect the recipient and send manually. The app does not create a Zalo Catalog item.

The import runs off the UI thread. It rejects symlinks, files above 10 MiB, unsupported or undecodable formats, and images above 16 million pixels. The image is copied into a private hash-named file, deduplicated, and the managed directory has a 200 MiB cap. Preview checks the stored content hash and decodes at no more than 160 by 160 pixels. Orphaned managed images are removed on a later import.

| Check | Result | Limit |
| --- | --- | --- |
| Targeted catalog tests | 8 passed, including version-3 migration, deduplication, size/path validation, preview and modified-file rejection | Synthetic images and local databases only. |
| Full Rust library suite | 372 passed, 2 ignored, 0 failed with the synthetic loopback fixture allowed | No Zalo account or network request. |
| Windows compiler | `cargo check --locked --offline --bin ctdozlite` passed | Does not prove the editor click path. |
| Windows release build | `cargo build --release --locked --offline --bin ctdozlite` passed; EXE 79.11 MiB, SHA-256 `7C93C7A8FEF1FC7CE4B57C4CCC71350B8496DE3D7A9EE1B98955C23E402AD60C` | Unsigned local binary; no installer release. |
| Manual Windows UI | Not run on the authenticated catalog screen | User stopped the previous Computer Use session. No QR scan or real message was used. |

Thirty-second no-login sample of this release build with a fresh isolated profile, excluding the first three startup seconds: peak working set **114.02 MiB**, peak private memory **51.73 MiB**, mean CPU **2.29% of one core**, profile files **76 KiB**, executable **79.11 MiB**. This is a steady empty-profile sample; it does not measure the catalog or chat after login. Per-process network bytes were not measured, and no performance improvement is claimed from this sample.

Open: authenticated add/edit/preview/attach click-through, real Zalo image send, account-switch race review, encryption of the product table and image files, encrypted backup of these images, Android catalog UI and performance/network sampling for this new build. These gates prevent a release claim.
