# Shared recent preview request — 2026-11-23

Source evidence: `02_chat_data.md`, section 2, records the upstream encrypted `getLastMessagesForPreview` shape. The existing Rust request is moved to `src/conversation_transport.rs`; `chat::get_last_msgs` delegates. The only deliberate wire-policy change is failing locally when `encoded_params` fails rather than falling back to plaintext. A valid encrypted request is constructed as before. Missing key/identity is covered without network access.

The Android response projection is exercised with synthetic JSON for message buckets, thread message arrays, last-message wrappers and nested data strings. These examples are not authenticated captures. The prior unscanned QR test establishes only QR-ticket interoperability. Do not infer login/read/send success from it. Authenticated preview parsing and request acceptance remain unverified.
