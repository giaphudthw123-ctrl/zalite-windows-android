# ADR 0014: Share text transport with explicit mobile send intents

- **Status:** accepted for prototype testing
- **Date:** 2026-11-23

## Evidence and decision

The desktop already sends text to `/api/message/sms` and group text to `/api/group/sendmsg`, encrypting the form with Session and preserving a client ID. Move this exact transport and its ID allocator to `src/text_transport.rs`, compile it in the internal auth crate, and delegate desktop wrappers. Desktop rich-text preprocessing stays in `chat.rs`; mobile sends literal plain text. Preserve fields, group normalization, URL selection, encryption and error handling. Move the existing fallback host literal into endpoints. This is extraction of existing code, not proof of Android server compatibility.

Use a memory-only mobile ledger with at most 32 intents, one in-flight POST, two-minute preview expiry and a stable client ID allocated at prepare time. Bind each intent to the native Session generation, account and recipient. Validate numeric identifiers and limit text to 4,000 UTF-16 units with no forbidden control characters. These are local resource constraints, not Zalo account limits.

Only an explicit confirmation starts a POST. Cancelling an unsubmitted preview removes it. Confirmation is single use within the process. A timeout, lost response or malformed acknowledgement is uncertain, not "not sent"; never automatically retry. Block an identical uncertain intent within its current Session generation. An explicit numeric `error_code:0` is a transport acknowledgement only; do not fabricate delivered/read state. Keep uncertain records until the process/account changes; do not silently evict them to enable retries. A bounded POST may finish after Activity stop; retain its receipt in the process ledger, discard stale UI callbacks, and expose a status button when returning.

Cancel confirmation dialogs on Activity stop. The dialog shows the account, target name/ID and exact content. UI composition can change while work runs; clear only an unchanged draft, and never jump back into a previous conversation when its result arrives.

The debug sample send is a separate local path and visibly labelled. It appends a sample bubble without invoking any JNI send operation. Bound rendered preview messages to 100. It is UI evidence, not proof of authenticated messaging.

## Validation and limits

A synthetic localhost contract checks decrypted direct/group payloads, literal Unicode text, rich-text properties passthrough, client IDs, server rejection and a missing acknowledgement. The initial integration-test attempt was rejected by the production host guard. Run the fixture as a unit-test module using the pre-existing cfg(test) loopback exception; do not weaken production trust rules.

Actual login, recipients, accepted server-side send, echo reconciliation, full history, attachments/GIF and persistent encrypted outbox remain unverified or incomplete. The ledger does not survive process death and does not promise exactly-once delivery or immunity to account restrictions. No batch dispatch, automatic reply or server entitlement is added. Real-account testing and release require separate gates.

Acknowledgement refinement from a failing regression: Session converts an empty HTTP response into a zero-code keepalive envelope. Mobile therefore additionally requires a nonempty, nonzero server-message identity in a recognized root/data field. A zero without that identity remains Unknown. This stricter classifier is mobile-specific; desktop wrappers retain their baseline response behavior and need a separate delivery-state audit.
