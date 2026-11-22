# ADR 0005: Account-scoped product images

- **Status:** accepted for the Windows prototype
- **Date:** 2026-11-21
- **Deciders:** Windows client implementation

## Context

ADR 0004 introduced hash-named product images under one shared `product-images` directory. Orphan cleanup queried only the live account's Store, so importing an image for account A could remove a file referenced by account B's parked Store.

## Decision

New imports use `product-images/<account-id>/<sha256>.<extension>` and a 200 MiB cap per account. Import never deletes existing images; account IDs are validated as path components. Existing flat `product-images/<sha256>.<extension>` paths remain readable. Preview rejects symlinks in the image path and checks the stored hash.

## Consequences

- Positive: importing for one account cannot sweep another account's managed images, including during a concurrent account change; existing product cards keep their old images.
- Negative: old images remain until a user-controlled cleanup is implemented, so the 200 MiB per-account cap can be reached. Legacy flat images also remain in place. Image files and the Store remain unencrypted.
- Follow-ups: exercise account switching with authenticated test accounts, add encrypted backup that includes image files, and provide a bounded user-controlled cleanup flow.

## Alternatives Considered

| Option | Why rejected |
| :--- | :--- |
| Scan every parked database when cleaning one shared directory | Expensive, racy while account snapshots change, and hard to bound. |
| Delete all legacy images during migration | Would lose images already referenced by existing product rows. |
