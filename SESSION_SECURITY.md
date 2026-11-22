# Session Security Notice

Windows prototype update (2026-11-22): new Session writes and account snapshots use user-bound DPAPI. Existing JSON remains readable and may remain plaintext until saved again. Protected files are not portable backups; software running as the same Windows user can still decrypt them. See `docs/adr/0008-windows-session-protection.md`. Chat databases and media are not covered.

`session.json` contains live authentication credentials, session tokens (`zpw_sek`, `zpw_enk`), encryption keys, and hardware identifiers (IMEI) for your Zalo account.

**Security Requirements:**
- **Never commit** `session.json` to version control (ensure it remains in `.gitignore`).
- **Never share** the file or its contents with anyone or paste it into issue trackers or public chats.
- Anyone with access to this file can access your active Zalo chat session without re-authenticating.
- If this file is accidentally exposed, log out of Zalo immediately from your mobile app (Settings > Account & Security > Login History / Active Sessions) to invalidate all session keys and cookies.
