# ADR 0018: Shared photo upload/dispatch with explicit compatibility policy

- **Status:** Accepted for prototype testing
- **Date:** 2026-11-23

## Evidence

Android has local GIF selection but no photo transport. Desktop `send_attachment_ext` combines uploading/dispatching with desktop message formatting and thumbnail caches. Its original code did not inspect each upload's error envelope before choosing the single-shot fallback. A synthetic localhost regression observed two POST requests after an upload returned error_code 114, although the required behavior was to stop after one.

## Decision

Extract the actual desktop wire flow into `src/photo_transport.rs`, exported by the desktop library and the portable auth crate through the same source path. Keep decoders, file reads, thumbnails, UI models and formatting in their caller. Transport receives an immutable image snapshot, checked dimensions and one caller-allocated client ID. Reuse Session encoded_params, maybe_decrypt, post_multipart and post_form; no independent HTTP client, cookie destination or cipher is introduced. Centralize the pre-existing file-service fallback in endpoints.rs.

Validate identity, target, payload bounds and MIME before network work. Upload 1 MiB chunks with the existing direct/group fields and stop immediately unless every reply explicitly reports numeric error_code 0. A malformed final upload envelope is not permission to dispatch. Preserve the original desktop single-shot compatibility path only after a positive upload reply with an object that lacks usable photo metadata. Keep desktop dispatch-response interpretation otherwise unchanged; its broader delivery-state audit is still open.

Companion policy additionally limits the image to 8 MiB, requires upload identity and trusted HTTPS returned image URLs, forbids legacy fallback, and requires numeric zero plus a nonzero message identity in the dispatch response. Do not retry ambiguous outcomes. The scoped variant rechecks its caller's Session ownership predicate before every new chunk and before dispatch. It cannot recall a POST already in flight; callers still own an overall deadline and the send ledger.

## Verification boundary

Synthetic localhost contracts decrypt request params and compare full direct/group upload/send objects, actual multipart bytes, client ID, target, original/HD flags and encrypted reply handling. Reject unsafe URLs, malformed/negative upload replies and ambiguous acknowledgements without extra POSTs. Test changed scope before initial upload, between chunks and before dispatch. Existing localhost test support for ordinary requests is extended to multipart only under cfg(test); production domain validation is unchanged. Fixture accepted sockets explicitly become blocking on Windows to avoid inherited WouldBlock races under concurrent test execution.

Android compilation is not an active photo JNI path. Port the immutable GIF snapshot into the bounded native intent ledger, add recipient/account/content confirmation and receipt/error/lifecycle handling before removing the composer guard. Actual Zalo upload schema, successful recipient animation/delivery, independent review and release signing remain unverified gates.
