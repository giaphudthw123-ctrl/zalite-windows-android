# ADR 0004: Account-local product catalog

- **Status:** accepted for the Windows prototype
- **Date:** 2026-11-21
- **Deciders:** project owner and Windows client implementation

## Decision

Store personal products in the existing account-local SQLite history database. Migration 3 adds a `products` table with a random local ID, optional unique SKU, name, description, VND price, variants, private stock note and update time. Product actions use the existing ordered UI-to-worker channel; the UI never opens SQLite. Reads return at most 200 rows, while storage has no 200-product cap. Search uses bound SQL parameters and escapes wildcard characters.

The user can insert a plain-text product card into the current composer, review the recipient and send manually through the existing chat flow. The private stock note is excluded from that card. This does not create a Zalo Catalog entry or grant any zBusiness right.

## Rationale and limits

The account database already owns drafts, saved items and reminders; adding one local table preserves the worker ownership boundary and keeps product data with the corresponding account. Migration 3 is atomic and forward only. The product table is presently unencrypted, like existing local chat history. Image import, encrypted backup, a full authenticated UI check and server-side Catalog integration remain separate work. No installer or APK release should claim those capabilities.

## Verification and rollback

Fresh and version-2 databases are covered by in-memory migration tests; a temporary on-disk fixture checks reopen and more than 200 stored products. This build rejects database versions newer than 3 before writing tables. Reverting this feature must leave migration 3 and the table in place; a previously shipped binary cannot be made safe to downgrade retroactively.

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| Add products to the shared settings JSON | Would mix account-specific business data with global appearance settings and require UI-thread file handling. |
| Claim the official zBusiness Catalog | Requires server-side entitlement and an approved Zalo API; the client cannot grant it. |
