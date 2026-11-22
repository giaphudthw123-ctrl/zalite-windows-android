# Adversarial security matrix — 2026-11-22

Requested scope: at least 1,000 meaningful cases. `tests/security_matrix.rs` adds **1,024 deterministic input scenarios in four test functions**, all passing on Windows. This is not 1,024 individually discovered Rust test functions. The existing library suite separately passed 380 active test functions (2 ignored) after the runtime account rollback change.

| Matrix | Scenarios | Assertions |
| --- | ---: | --- |
| Credential destinations | 256 | Four trusted base domains × 16 distinct subdomains/attacker names × suffix impersonation, userinfo redirection, insecure HTTP and nonstandard port. All rejected before credential attachment. This tests the URL gate, not every HTTP caller. |
| Managed image paths | 256 | 32 account/path variants × traversal, invalid root, extra path segments, executable suffix, alternate-stream syntax and malformed hash. All rejected. |
| Product control characters | 320 | 64 control codepoints × five fields. Only description LF is allowed. Row count proves rejected input has no partial write. |
| Unicode boundaries | 192 | Four fields × 48 lengths around their limits. Accepted multibyte Vietnamese text is checked after SQLite retrieval; rejected values leave no row. |
| **Total new input scenarios** | **1,024** | Four Rust test functions passed, 0 failed. |

Run: `cargo test --locked --offline --test security_matrix -- --nocapture`. The final output reports each scenario count and the runner's four test functions separately. These fixtures do not access the network or a real account.

Limits: no fuzzing coverage claim, no GUI automation, no authenticated sends, no Android-device verification and no guarantee of account safety from a test count. More protocol replay, malformed media, filesystem fault and Android cases remain necessary.
