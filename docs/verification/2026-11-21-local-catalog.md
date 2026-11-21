# Local product catalog checkpoint — 2026-11-21

The Windows source now has an account-local product catalog in the personal tools menu. It supports add, edit, SKU/name/description search, confirmed delete, VND price, variants and a private stock note. “Chèn vào ô soạn” inserts a text card in the selected conversation draft; it does not send a message. It is not the official zBusiness Catalog.

| Check | Result | Limit |
| --- | --- | --- |
| Targeted catalog tests | 6 passed after the migration guard and on-disk reopen tests were added | No authenticated UI in fixture. |
| Full Rust library suite | 370 passed, 2 ignored, 0 failed when the synthetic loopback fixture was allowed to bind 127.0.0.1 | Initial restricted run had one existing loopback test failure; rerun passed. |
| Windows release build | Passed after the final database guard, `ctdozlite.exe` 79.10 MiB | Unsigned local build, not an installer. |
| Manual Windows startup | The preceding catalog build opened the login screen using an empty isolated profile | The final guard build was not visually checked: the user stopped Computer Use with Esc. Catalog screen cannot be reached without an account. No QR scan or real message was used. |
| Formatting and diff | `cargo fmt --all -- --check` and `git diff --check` passed after the final code change | No formatting differences. |

Thirty-second no-login sample after the preceding catalog build, excluding the first three startup seconds: peak working set **116.36 MiB**, peak private memory **52.06 MiB**, mean CPU **2.58% of one core**, profile files **76 KiB**, executable **79.11 MiB**. The final database-guard build is **79.10 MiB** but was not benchmarked separately. This is a steady no-login sample, not directly comparable with earlier startup samples. Network bytes were not measured. No RAM/CPU/network improvement is claimed.

Open: product image import and preview, encrypted local data/backup, authenticated click-through of add/edit/search/delete/insert, real Zalo send verification, and Android catalog UI. These gates prevent a release claim.
