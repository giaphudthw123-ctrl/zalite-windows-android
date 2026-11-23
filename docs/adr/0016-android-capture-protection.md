# ADR 0016: Android window capture protection

- **Status:** Accepted for prototype testing
- **Date:** 2026-11-23

## Evidence

Android Personal/Discover tabs were mostly empty pages, and the application had no Android window-capture setting. FLAG_SECURE is a window flag: protecting only the Activity does not automatically protect separate AlertDialog windows. See [Android documentation](https://developer.android.com/security/fraud-prevention/activities).

## Decision

Provide explicit Personal/Discover utility rows and direct destinations for existing GIF/template/update tools and local privacy settings. Do not represent this as an official Zalo business profile or subscription.

Set FLAG_SECURE before rendering startup content. A single worker executor loads a versioned four-byte, non-secret preference from app no-backup storage; a missing preference defaults to off after loading. Invalid existing data keeps protection on and disables editing until a successful explicit reload. No synchronous file read/write occurs in the window callbacks. AtomicFile save plus readback must succeed before disabling protection. Enabling applies immediately, and save failure leaves protection enabled with an explicit error.

Route app AlertDialogs through a Builder that applies the owner's current window policy before showing. Track dialogs weakly under weak Activity keys so policy changes also update previously created dialog windows without retaining Activity instances. This registry is UI-thread-only. Existing dialog confirmation/cancellation and Session intent rules remain unchanged.

## Limits

This is Android system capture protection, separate from Windows display affinity. It does not prevent external cameras, privileged/rooted capture, third-party picker/keyboard output or recipient-side copying. Recording/casting and physical-device coverage require separate evidence. It does not implement PIN locking, full chat-at-rest encryption, secure backups or official Zalo protection rights. Manual deletion of the preference resets the opt-in on a later start; it is not a tamper-resistant security policy.
