# ADR 0010: Android companion QR Session owner and protected output

- **Status:** accepted for prototype testing only
- **Date:** 2026-11-22
- **Deciders:** Windows/Android implementation

## Decision

Android JNI owns one cancellable companion login attempt with generation-scoped progress. Request, waiting-scan, waiting-confirm, authenticated, error and cancelled phases are reported as JSON without credential fields. A bounded QR matrix renders locally. Dropping the login future on cancellation stops pending requests; a four-minute outer timeout bounds the attempt. Stale events are ignored. The completed in-memory Session is retained by native state and serialized credentials are handed to Java once through a separate byte-array method, never written by Rust's non-Windows plaintext storage path.

Java stores those bytes in an AtomicFile under noBackupFilesDir using AES-256-GCM and an AndroidKeyStore key. A fresh provider-generated IV is used for each save. The envelope contains a version marker and IV; corruption or decryption failure returns an error. No plaintext fallback exists. A dedicated self-test uses a different key alias/file and synthetic bytes, then deletes its own artifacts. Backgrounding the Activity cancels the pending attempt.

## References

- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [KeyGenParameterSpec AES/GCM example](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)
- [AtomicFile contract](https://developer.android.com/reference/android/util/AtomicFile)

## Limits

Native flow is wired but live server interoperability is not verified. The inherited companion identity/protocol is not primary mobile-account login. A restored-session path and chat sync/send are not yet connected. The key does not require biometric confirmation; same-app compromise and live process memory remain risks. Hardware backing is device-dependent and not claimed. Lifecycle races, actual cancellation of remote polling, save failure after server confirmation and key invalidation need further testing. Do not release or use a real account based solely on successful build/self-test.
