# ADR 0008: Windows user-bound Session protection

- **Status:** accepted for the Windows prototype
- **Date:** 2026-11-22
- **Deciders:** Windows client implementation

## Decision

On Windows, Session writes use `CryptProtectData` with current-user scope, UI forbidden and no machine-wide flag. A versioned binary prefix identifies the protected file. Reads accept existing JSON and decrypt protected files through `CryptUnprotectData`; protection failures never fall back to plaintext writes. File replacement remains atomic. Account snapshots use the same writer, while account listing, ownership lookup and offline capture decoding use the bounded reader.

## Evidence and limits

[Microsoft documents user-bound DPAPI protection](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata) and [integrity checking on decryption](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptunprotectdata). No prompt structure or LOCAL_MACHINE flag is used. Native allocations are freed with LocalFree and decrypted native output is wiped before freeing.

Synthetic tests check legacy read, encrypted roundtrip, ciphertext variation and rejection after modification. Existing Session and account-switch tests use the reader so they verify actual contents across both formats.

Existing legacy files are not scanned or rewritten automatically; they become protected on a later save/snapshot. Other saved accounts may therefore remain plaintext until used. Windows-profile portability is reduced. Software running as the same Windows user may decrypt DPAPI data. Chat history, product images, memory, other operating systems and backup exports are not encrypted by this change. Cross-user and authenticated UI tests remain open. Account listing retains its pre-existing synchronous file access; moving that UI path fully to the worker remains necessary.
