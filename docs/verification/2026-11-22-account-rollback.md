# Account switch rollback — 2026-11-22

The switch prepares target files and rollback copies before replacing any live file. If a replacement fails, it restores all files already attempted. The account marker is replaced last. If rollback fails too, `.account-rollback` copies remain in the data directory and the error tells the user that recovery is incomplete. This is runtime I/O failure recovery, not power-loss or process-crash recovery.

- Nine account-manager tests passed, including injected failures before each of the four replacements and byte-for-byte restoration of all original live files.
- Full library suite: 380 passed, 2 ignored, 0 failed with the synthetic loopback fixture allowed.
- Release build passed: EXE 83,087,524 bytes (79.24 MiB), SHA-256 `9FEEF4F38B0A7E7012681216FB1A42494AA38A967CB03EFDE05DC779A147E739`.
- Authenticated Windows multi-account UI, Windows sharing violations during real database writes, process-crash recovery and per-process network measurement remain open. No real Session was used.

The rollback fixtures prove controlled failure handling. They do not prove recovery if the filesystem also refuses rollback writes; that case preserves backup copies for manual recovery.

The preceding build's RAM/CPU sample is not reused as a measurement of this binary. This checkpoint has no new performance or network measurement.
