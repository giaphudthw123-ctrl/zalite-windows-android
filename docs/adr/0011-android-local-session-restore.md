# ADR 0011: Distinguish local Session restoration from server authentication

- **Status:** accepted for prototype testing
- **Date:** 2026-11-23
- **Deciders:** Windows/Android implementation

## Decision

Add an in-memory restore API to the existing Session implementation. Android decrypts its vault on a background task and submits bounded bytes through JNI. Native code constructs and validates a fresh candidate before replacing the current owner; malformed, explicitly expired or incomplete candidates fail without altering it. Display a local-restored/unverified label. Do not automatically equate readable ciphertext with authenticated server access.

Read the vault on Activity start/resume so the background cancellation boundary does not permanently discard an already saved Session. Keep files after decryption/validation failure; do not turn a recoverable vault error into destructive logout. Retain compatibility with the Windows file reader by having it delegate to the same restoration method.

## Consequences

Synthetic local restore and unscanned QR tests can proceed without a real account. Server validation, refresh and a real protected-vault restart remain separate gates. A cached Session is not yet chat connectivity. Cross-lifecycle ordering and key invalidation need further tests before release. See the 2026-11-23 verification record.
