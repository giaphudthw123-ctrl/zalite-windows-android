# Architecture Decision Records

One file per decision: `NNNN-<kebab-name>.md`, numbered from `0001`, never renumbered.

## Template

```markdown
# ADR-0001: <Decision title>

- **Status:** proposed | accepted | superseded by ADR-NNNN | deprecated
- **Date:** YYYY-MM-DD
- **Deciders:** <names/roles>

## Context

Forces at play, constraints, evidence (capture note, upstream behaviour).

## Decision

What we will do, in one paragraph, present tense.

## Consequences

- Positive:
- Negative:
- Follow-ups:

## Alternatives Considered

| Option | Why rejected |
| :--- | :--- |
```

## Index

| ADR | Title | Status |
| :--- | :--- | :--- |
| 0001 | History sync | Existing |
| 0002 | Self updater | Accepted |
| 0003 | Changelog policy | Existing |
| [0004](0004-local-product-catalog.md) | Account-local product catalog | Accepted for Windows prototype |
| [0005](0005-account-scoped-product-images.md) | Account-scoped product images | Accepted for Windows prototype |
| [0006](0006-consistent-account-snapshots.md) | Consistent account snapshots | Accepted for Windows prototype |
| [0007](0007-account-switch-rollback.md) | Runtime account switch rollback | Accepted for Windows prototype |
| [0008](0008-windows-session-protection.md) | Windows user-bound Session protection | Accepted for Windows prototype |
| [0009](0009-portable-companion-auth.md) | Portable companion authentication sources | Accepted for prototype |
| [0010](0010-android-qr-session-owner.md) | Android QR Session owner and Keystore output | Prototype testing only |
| [0011](0011-android-local-session-restore.md) | Android local Session restoration | Prototype testing |
| [0012](0012-shared-recent-conversation-projection.md) | Shared preview transport and bounded mobile projection | Prototype testing |

Related domain language lives in [`/GLOSSARY.md`](../../GLOSSARY.md).
