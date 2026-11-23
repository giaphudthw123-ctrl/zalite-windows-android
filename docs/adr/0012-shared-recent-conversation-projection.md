# ADR 0012: Share preview transport and bound Android conversation projection

- **Status:** accepted for prototype testing
- **Date:** 2026-11-23

## Decision

Compile the existing `get_last_msgs` request in the repository-internal auth crate and delegate the desktop entry point to it. Preserve the encrypted request body, common parameters, domain selection, Session client, cookie allowlist and decryption path. Remove the plaintext fallback when encryption material is missing: the encrypted request shape is documented in `re-notes/02_chat_data.md`, section 2. An offline regression proves missing identity/key fails before an invalid destination could be used. No fresh authenticated capture has been made.

Project a limited set of preview envelopes into mobile DTOs. Treat these shapes as fixture-tested compatibility candidates, not a proven exhaustive server schema. Reject unknown/error envelopes. Bound entry/message/text budgets and retained threads/messages; identify group and direct threads separately. Render attachment placeholders without fetching remote content. All reads are explicit user actions, off the Android UI thread, with a 30-second deadline and native Session generation checks. Cached results and draft text are cleared on account changes. Cancelled native owners cannot provide a request snapshot.

Provide a debug-only synthetic conversation preview with a persistent data-test label. Do not manufacture account names, online presence, delivery state, unread counts, a successful send, or a complete history. Mobile chat composition is a memory-only draft at this checkpoint. Search only the locally loaded names and last messages; Vietnamese accents are optional. Do not expose unsupported call/send controls as working buttons.

## Limits

Actual authenticated preview interoperability, full history, contacts/name resolution, socket updates, sending and media remain open. The shared Session HTTP response is read before the projection limits apply; these are not a network-byte cap. Java Activity lifecycle cancellation, restore ordering and stale open-chat refresh need broader coverage. No encrypted persistent chat history is added. Independent human/code review and real-device evidence remain separate release gates.
