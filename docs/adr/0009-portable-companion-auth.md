# ADR 0009: Compile existing authentication without desktop dependencies

- **Status:** accepted for the prototype
- **Date:** 2026-11-22
- **Deciders:** Windows/Android implementation

## Context

The Android shell has no authentication transport. Depending on the root library would pull desktop UI, audio, WASM and storage packages into the Android build. Session also called the large call module solely to generate the companion device identity.

## Decision

Create `crates/ctdozlite-auth` whose module paths compile the exact existing source files for Session, login, QR, endpoints, crypto, ParamCipher and their logger/storage helpers. Move the unchanged identity generator into `protocol_identity`; desktop calls delegate to it. No protocol fields, signing flow, URLs or device-format behavior change. Lock dependencies for reproducible standalone builds. This crate is repository-internal and requires the root source files; it is not a self-contained crates.io package.

## Limits and follow-ups

This is the inherited companion/QR protocol, not proof that primary mobile account login works. The Windows-oriented device format remains inherited protocol behavior. No new network request or live login is executed by the build/tests. Android storage must use a Keystore-backed adapter before any live Session persistence: the current shared storage fallback is plaintext on non-Windows systems. Do not call that fallback from a released Android account flow. Android logging must remain disabled and Session secret handling needs a separate review before JNI exposure.

Next: compile on both mobile ABIs, connect a cancellable JNI Session owner with typed progress/errors, implement the QR screen, then check the no-account error/cancel paths and only request account testing after security gates. Do not confuse transport compilation with working app authentication.
