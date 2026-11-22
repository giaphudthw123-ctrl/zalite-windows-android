# ADR 0007: Runtime account switch rollback

- **Status:** accepted for the Windows prototype
- **Date:** 2026-11-22
- **Deciders:** Windows client implementation

## Context

ADR 0006 left target replacement rollback open. A failure after the first live file was replaced could mix files from two accounts.

## Decision

Stage target files and rollback copies in the same data directory before changing live files. Replace the Session, threads, Store and account marker in order. On error, restore every attempted live file in reverse order; preserve rollback copies if restoration fails. Remove staging files on ordinary completion.

## Consequences

Requires extra disk space during switching. The UI remains off the filesystem path. Controlled runtime failures are covered by injected-failure fixtures; process-crash and power-loss recovery require a durable journal in a later milestone. Sensitive temporary copies inherit the current data directory protection and remain unencrypted.
