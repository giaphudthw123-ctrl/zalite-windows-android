# Account panel worker boundary — 2026-11-22

The settings account panel previously called `list_saved_accounts` every frame, including filesystem reads, JSON parsing and now DPAPI decryption. It also deleted saved account files on the UI thread.

The panel now requests one background refresh when its account changes or the user presses refresh. It renders the cached profiles and loading state. Saved-account deletion also runs through the worker; deletion errors are surfaced and the current account cannot be deleted through this command. Results are scoped to the requested account and ignored after a switch. Account-switch completion invalidates the cached list.

Static review confirms the settings render no longer calls either account-manager filesystem function. The worker runs the functions in `spawn_blocking` and returns a terminal event. The library suite passed 381 active tests, 2 ignored; this suite does not automate the settings-panel interactions. Compiler check passed before the final deletion error handling; the final Windows release build result is recorded below.

Open: authenticated refresh/delete/switch click-through, worker failure UI, directory-read failure reporting and explicit deletion confirmation. The existing 1,024-case matrix does not cover these UI flows. No live Session was accessed; no fresh performance/network sample is claimed.

Final release build passed: 83,101,509-byte EXE, SHA-256 `B33E3F09DED3A02D247B8B40FB36C75DB3D289CBC71FADA59AB8FE9AC29E30EE`. Formatting/diff checks passed. Existing unused-mut warning remains.
