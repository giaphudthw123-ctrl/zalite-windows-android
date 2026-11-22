# Windows Session protection — 2026-11-22

New Session writes and account snapshots use current-user DPAPI on Windows, including an explicit version marker. The reader accepts legacy JSON, bounds input at 8 MiB and rejects unsupported protected versions or failed decryption. An encryption failure aborts the save. No user Session file was opened or migrated during this work.

| Check | Result |
| --- | --- |
| Native Windows roundtrip | Passed with synthetic JSON; ciphertext does not contain the synthetic token; repeated save differs; modified ciphertext is rejected. |
| Full library tests | 381 passed, 2 ignored, 0 failed, including Session save/load and account switching. |
| Security matrix regression | Four integration test functions passed all 1,024 scenarios on this source. |
| Release Windows build | Passed; EXE 83,067,017 bytes, SHA-256 `2A38917572659B62B7D179D88212D1C8EB6B1D8991958C45EB6A70B37C7F2F57`. |
| Authenticated session restart / switching | Unverified. |
| Different Windows user / profile transfer | Unverified; DPAPI data is intentionally user-bound. |

This protects newly written Session files only. Previously saved JSON remains readable and may remain plaintext until saved again. SQLite chat/product data and image files are still unencrypted. Same-user malware, process memory and encrypted portable backup are outside this implementation. No new RAM/CPU/network measurement is claimed.
