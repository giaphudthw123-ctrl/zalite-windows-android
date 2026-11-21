# ADR 0004: Account-local product catalog

- **Status:** accepted for the Windows prototype
- **Date:** 2026-11-21
- **Deciders:** project owner and Windows client implementation

## Decision

Store personal products in the existing account-local SQLite history database. Migration 3 adds a `products` table with a random local ID, optional unique SKU, name, description, VND price, variants, private stock note and update time. Migration 4 adds a managed image path. Product actions use the existing ordered UI-to-worker channel; the UI never opens SQLite. Reads return at most 200 rows, while storage has no 200-product cap. Search uses bound SQL parameters and escapes wildcard characters.

The user can insert a plain-text product card and its optional managed image into the current composer, review the recipient and send manually through the existing chat flow. The private stock note is excluded from that card. This does not create a Zalo Catalog entry or grant any zBusiness right.

## Rationale and limits

The account database already owns drafts, saved items and reminders; adding one local table preserves the worker ownership boundary and keeps product data with the corresponding account. Migrations 3 and 4 are atomic and forward only. Image import runs on a background worker, accepts PNG/JPEG/GIF after decoding, rejects links and oversized files, stores a hash-named private copy, and caps the managed image directory at 200 MiB. The preview is bounded and checks the content hash. The product table and images are presently unencrypted, like existing local chat history. Encrypted backup, a full authenticated UI check and server-side Catalog integration remain separate work. No installer or APK release should claim those capabilities.

## Verification and rollback

Fresh, version-2 and version-3 databases are covered by migration tests; a temporary on-disk fixture checks reopen and more than 200 stored products. Image tests cover deduplication, path validation, size limits, preview and tamper detection. This build rejects database versions newer than 4 before writing tables. Reverting this feature must leave migrations 3 and 4 in place; a previously shipped binary cannot be made safe to downgrade retroactively.

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| Add products to the shared settings JSON | Would mix account-specific business data with global appearance settings and require UI-thread file handling. |
| Claim the official zBusiness Catalog | Requires server-side entitlement and an approved Zalo API; the client cannot grant it. |
