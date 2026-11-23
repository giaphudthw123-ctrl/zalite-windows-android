# ADR 0015: Insert templates into the active composer and protect the local library

- **Status:** accepted for prototype testing
- **Date:** 2026-11-23

## Evidence

The chat toolbar opened the separate tools page, whose template insertion targeted a different draft. Android also loaded/saved template strings synchronously through plaintext SharedPreferences. The mobile request therefore did not yet have an active-chat template path or protected template storage.

## Decision

Open a searchable template picker directly from the chat toolbar. Create/edit templates there, expand `{ten}`, `{gio}` and `{ngay}` through the existing shared Rust function, and show a preview before insertion. Insertion is separate from sending. Bind the callback to the current composer generation, account, conversation and unchanged draft. Replace the selected range; reject invalid UTF-16 boundaries or an over-limit result rather than truncating. Dismiss template dialogs on Activity stop.

Store versioned/revisioned template snapshots in a separate Keystore AES-GCM/AtomicFile vault and key alias. Reuse the existing cryptographic envelope and randomized IV policy; do not invent a new cipher. Serialize repository operations on a background executor and worker-side store lock. Compare the expected revision before writing. Publish the new cached list only after saving and decrypting/verifying the snapshot. A storage-opening exception must end loading with an error, not leave an indefinite loading flag.

For migration, validate the entire legacy list, encrypt it, read it back, and only then remove the active legacy preference key. Failed validation preserves the source. If an existing encrypted library and legacy list disagree, preserve both and stop automatic migration. Corruption does not fall back to an empty writable library or overwrite the error file.

## Limits

The current inherited format still caps the library at 100 templates of 120 codepoints; scalable/categorized libraries, attachments, import/export and portable encrypted backups remain outstanding requirements. The library is device/app-local and shared across local Zalo identities, while insertion remains bound to a particular composer. Drafts are memory-only in this chat path. Old draft copies, exported files and filesystem recovery of removed preferences are not securely erased by this migration. Key loss, uninstall recovery, disk/power failure, actual-account sending and physical-device testing remain separate gates. Windows and the shared Rust code are unchanged in this checkpoint.
