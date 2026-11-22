# ADR 0006: Consistent account snapshots

- **Status:** accepted for the Windows prototype
- **Date:** 2026-11-21
- **Deciders:** Windows client implementation

## Context

Account switching copied the live SQLite file as raw bytes and removed the live Session and Store before checking whether the target Session existed. A raw file copy may miss committed data still in WAL; a missing target could leave the live account unusable.

## Decision

Validate account IDs, the target Session JSON and its account ID before changing live files. Propagate snapshot errors rather than continuing. Copy the Session and thread cache through temporary files in the data directory and replace their parked copies only after flushing. Generate the parked Store through SQLite `VACUUM INTO` into a temporary file, then replace the parked database. [SQLite documents](https://www.sqlite.org/lang_vacuum.html) the output as a consistent snapshot of the original database, including when the original is live. Temporary files use owner-only modes on Unix; Windows still relies on the selected data directory's ACL.

## Consequences

- Positive: a missing target cannot remove the current account; a failed Store snapshot leaves the previous parked copy intact; committed WAL data survives a snapshot.
- Negative: `VACUUM INTO` can cost disk space and CPU during a switch. A mid-switch file replacement failure can still leave live files partially changed, although the parked current-account snapshot remains. Windows ACL hardening and encrypted backup remain open.
- Follow-ups: stage target files and add rollback for partial replacement; validate the complete Windows multi-account UI with synthetic or explicitly approved test accounts.

## Alternatives Considered

| Option | Why rejected |
| :--- | :--- |
| Keep copying raw SQLite bytes | Can omit uncheckpointed WAL content or capture a mixed write. |
| Use an in-memory dump | Loads the whole history into RAM, contrary to the light-client goal. |
