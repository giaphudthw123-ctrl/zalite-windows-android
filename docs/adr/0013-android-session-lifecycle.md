# ADR 0013: Preserve completed Sessions across Android screen lifecycle

- **Status:** accepted for prototype testing
- **Date:** 2026-11-23

## Evidence

The QR success branch kept `authGeneration` live while its back button and `onStop` called `cancelAuth`, which deletes the native Session. Returning to Messages after a successful save could therefore lose the current in-memory identity. A synthetic regression also demonstrated that a late restore of account B replaced already installed account A. The test failed before the change with the expected `already_present` assertion.

## Decision

Treat completed QR authentication separately from an active QR request. Consume its credential export once, detach the active QR cancellation handle, and make the return button navigation-only. Retain the native Session on Activity stop after completion. A failed protected save is displayed as memory-only; it is not represented as a durable login.

Restore only into an empty, inactive native owner whose revision matches the value captured before vault I/O. Reject stale candidates and do not replace an existing Session. Validate identity, encryption key, credentials and explicit expiry. Native state description exposes only a bounded display name, never tokens.

Give recent-message reads a separate revision invalidated on Activity stop or login-screen change. UI callback tickets expire when stopped/resumed or switching screens. Cancelling reads preserves the Session. Clear and redraw cached account views when restoration installs a different identity.

Serialize vault reads/writes across adapter instances on a worker-side lock. Check that a save still belongs to the current Session while holding the vault gate; later saves run after an in-progress older write, and an old queued save cannot overwrite a newer saved identity. Recheck identity before presenting success. No UI thread waits on the vault lock.

## Consequences and limits

These rules revise ADR 0011's unconditional resume restoration: resume inspects an existing owner before reading disk, and installation uses compare-and-set semantics. The envelope/Keystore algorithms are unchanged. Synthetic native tests and a Keystore stale-write self-test cover local behavior; completed real QR login, account switching, restart recovery and background timing under network load still require permitted end-to-end validation. No release or real-account authorization is inferred.

Manual no-account QR testing additionally found that background cancellation removed the polling callback but left the old bitmap and a disabled retry button. Cancelling an active QR now clears its view, reenables retry, marks native state terminal immediately and ignores late progress events. New attempts get a new native generation; an old task cannot republish a QR into the cancelled owner.

Observation clarification: the initial immediate Home/return did not establish an Activity stop and is not evidence of cancellation itself; the missing UI reset was established by code inspection. The final test separately confirmed Launcher foreground before resuming, observed cleared QR/enabled retry, and successfully requested another unscanned QR. The verification record distinguishes these observations.
