//! Multi-account management for ctdozlite.
//!
//! Stores isolated account sessions, thread caches, and history databases in `accounts/<uin>.*`.

use anyhow::{ensure, Context, Result};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct AccountProfile {
    pub uin: String,
    pub display_name: String,
    pub avatar: Option<String>,
    pub last_active_ts: i64,
    pub is_current: bool,
}

pub fn accounts_dir() -> PathBuf {
    PathBuf::from("accounts")
}

pub(crate) fn validate_account_id(uin: &str) -> Result<()> {
    ensure!(
        !uin.is_empty()
            && uin.len() <= 64
            && uin
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_')),
        "Mã tài khoản không hợp lệ"
    );
    Ok(())
}

fn require_regular_file(path: &Path) -> Result<()> {
    let metadata = std::fs::symlink_metadata(path)?;
    ensure!(
        metadata.is_file() && !metadata.file_type().is_symlink(),
        "Tệp dữ liệu tài khoản không hợp lệ"
    );
    Ok(())
}

fn validate_saved_session_account(path: &Path, uin: &str) -> Result<()> {
    require_regular_file(path)?;
    ensure!(
        std::fs::metadata(path)?.len() <= 8 * 1024 * 1024,
        "Tệp phiên tài khoản quá lớn"
    );
    let saved: serde_json::Value = serde_json::from_reader(std::fs::File::open(path)?)
        .context("Tệp phiên tài khoản bị lỗi")?;
    ensure!(
        saved.pointer("/login/uin").and_then(|value| value.as_str()) == Some(uin),
        "Tệp phiên không thuộc tài khoản đã chọn"
    );
    Ok(())
}

/// List all saved accounts in the `accounts/` directory.
pub fn list_saved_accounts(current_uin: &str) -> Vec<AccountProfile> {
    let dir = accounts_dir();
    let mut out = Vec::new();
    let Ok(entries) = std::fs::read_dir(&dir) else {
        return out;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.extension().and_then(|s| s.to_str()) == Some("json") {
            let file_stem = path
                .file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or_default();
            if file_stem.ends_with("_threads") {
                continue;
            }
            if let Ok(text) = std::fs::read_to_string(&path) {
                if let Ok(val) = serde_json::from_str::<serde_json::Value>(&text) {
                    if let Some(login) = val.get("login") {
                        let uin = login
                            .get("uin")
                            .and_then(|v| v.as_str())
                            .unwrap_or_default()
                            .to_string();
                        if !uin.is_empty() {
                            let display_name = login
                                .get("display_name")
                                .and_then(|v| v.as_str())
                                .unwrap_or("Người dùng Zalo")
                                .to_string();
                            let avatar = login
                                .get("avatar")
                                .and_then(|v| v.as_str())
                                .map(str::to_string);
                            let is_current = uin == current_uin;
                            let last_active_ts = entry
                                .metadata()
                                .and_then(|m| m.modified())
                                .ok()
                                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                                .map(|d| d.as_secs() as i64)
                                .unwrap_or(0);
                            out.push(AccountProfile {
                                uin,
                                display_name,
                                avatar,
                                last_active_ts,
                                is_current,
                            });
                        }
                    }
                }
            }
        }
    }
    out.sort_by(|a, b| b.last_active_ts.cmp(&a.last_active_ts));
    out
}

/// Save the current session and caches into `accounts/<uin>.*`
pub fn save_current_account_snapshot(uin: &str) -> Result<()> {
    save_current_account_snapshot_in(Path::new("."), uin)
}

fn save_current_account_snapshot_in(base: &Path, uin: &str) -> Result<()> {
    if uin.is_empty() {
        return Ok(());
    }
    validate_account_id(uin)?;
    let dir = base.join(accounts_dir());
    crate::data_dir::ensure_private_dir(&dir)?;
    let session = base.join("session.json");
    let threads = base.join("threads_cache.json");
    let history = base.join("zalo_history.db");
    if session.exists() {
        require_regular_file(&session)?;
        crate::data_dir::ensure_private_file(&session)?;
        crate::data_dir::copy_private_atomic(&session, &dir.join(format!("{uin}.json")))?;
    }
    if threads.exists() {
        require_regular_file(&threads)?;
        crate::data_dir::copy_private_atomic(&threads, &dir.join(format!("{uin}_threads.json")))?;
    }
    if history.exists() {
        require_regular_file(&history)?;
        let marker = base.join(ACTIVE_ACCOUNT_MARKER);
        if marker.exists() {
            let owner = std::fs::read_to_string(&marker)?;
            ensure!(owner.trim() == uin, "Lịch sử đang thuộc tài khoản khác");
        }
        snapshot_history(&history, &dir.join(format!("{uin}_history.db")))?;
    }
    Ok(())
}

/// SQLite writes a consistent snapshot even when the live source uses WAL.
fn snapshot_history(source: &Path, destination: &Path) -> Result<()> {
    require_regular_file(source)?;
    let parent = destination
        .parent()
        .context("Thiếu thư mục sao lưu lịch sử")?;
    crate::data_dir::ensure_private_dir(parent)?;
    let (temporary, file) = crate::data_dir::create_private_file(parent, ".history-tmp")?;
    drop(file);
    let result = (|| -> Result<()> {
        let source_path = source.to_str().context("Đường dẫn lịch sử không hợp lệ")?;
        let temporary_path = temporary
            .to_str()
            .context("Đường dẫn sao lưu không hợp lệ")?;
        let conn = rusqlite::Connection::open_with_flags(
            source_path,
            rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY,
        )?;
        conn.execute("VACUUM INTO ?1", [temporary_path])?;
        drop(conn);
        crate::data_dir::ensure_private_file(&temporary)?;
        std::fs::rename(&temporary, destination)?;
        crate::data_dir::ensure_private_file(destination)?;
        Ok(())
    })();
    if result.is_err() {
        let _ = std::fs::remove_file(&temporary);
    }
    result
}

/// Switch to a target account:
/// 1. Saves current account snapshot to accounts/<current_uin>.*
/// 2. Restores accounts/<target_uin>.* into session.json, threads_cache.json, zalo_history.db
pub fn switch_to_account(current_uin: &str, target_uin: &str) -> Result<()> {
    switch_to_account_in(Path::new("."), current_uin, target_uin)
}

fn switch_to_account_in(base: &Path, current_uin: &str, target_uin: &str) -> Result<()> {
    validate_account_id(target_uin)?;
    if !current_uin.is_empty() {
        validate_account_id(current_uin)?;
    }
    if current_uin == target_uin {
        return Ok(());
    }
    let dir = base.join(accounts_dir());
    let target_session = dir.join(format!("{target_uin}.json"));
    ensure!(
        target_session.exists(),
        "Không tìm thấy dữ liệu phiên của tài khoản {target_uin}"
    );
    validate_saved_session_account(&target_session, target_uin)?;
    crate::data_dir::ensure_private_file(&target_session)?;
    let target_threads = dir.join(format!("{target_uin}_threads.json"));
    let target_db = dir.join(format!("{target_uin}_history.db"));
    for optional in [&target_threads, &target_db] {
        if optional.exists() {
            require_regular_file(optional)?;
            crate::data_dir::ensure_private_file(optional)?;
        }
    }
    if !current_uin.is_empty() {
        save_current_account_snapshot_in(base, current_uin)?;
    }
    let live_session = base.join("session.json");
    let live_threads = base.join("threads_cache.json");
    let live_db = base.join("zalo_history.db");
    if live_session.exists() {
        std::fs::remove_file(&live_session)?;
    }
    if live_threads.exists() {
        std::fs::remove_file(&live_threads)?;
    }
    if live_db.exists() {
        std::fs::remove_file(&live_db)?;
    }
    crate::data_dir::copy_private(&target_session, &live_session)?;

    if target_threads.exists() {
        crate::data_dir::copy_private(&target_threads, &live_threads)?;
    }
    if target_db.exists() {
        crate::data_dir::copy_private(&target_db, &live_db)?;
    }
    crate::data_dir::write_private_file(&base.join(ACTIVE_ACCOUNT_MARKER), target_uin.as_bytes())?;
    Ok(())
}

/// Delete a saved account from `accounts/`
pub fn delete_saved_account(uin: &str) -> Result<()> {
    if uin.is_empty() {
        return Ok(());
    }
    validate_account_id(uin)?;
    let dir = accounts_dir();
    let _ = std::fs::remove_file(dir.join(format!("{uin}.json")));
    let _ = std::fs::remove_file(dir.join(format!("{uin}_threads.json")));
    let _ = std::fs::remove_file(dir.join(format!("{uin}_history.db")));
    Ok(())
}

/// Records which account owns the live `zalo_history.db` / `threads_cache.json`.
const ACTIVE_ACCOUNT_MARKER: &str = "active_account";
const LIVE_DB: &str = "zalo_history.db";
const LIVE_THREADS: &str = "threads_cache.json";

/// What `activate_account_data` did, for logging.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ActivationOutcome {
    /// The live files already belonged to this account; nothing changed.
    AlreadyActive,
    /// The parked `accounts/<uin>_*` copy was restored as the live files.
    Restored,
    /// Legacy layout (no owner marker): the parked database was merged into the live one.
    Merged { messages: usize },
    /// Neither live nor parked history exists yet.
    Fresh,
}

/// Make the live history files belong to `uin` after login or at startup.
///
/// The active account always reads and writes `zalo_history.db`; `accounts/<uin>_*` only parks
/// accounts that are not active. Before this existed, logout parked and deleted the live DB but
/// login never restored it, so a re-login started from an empty history (and the next snapshot
/// overwrote the good parked copy).
pub fn activate_account_data(uin: &str) -> Result<ActivationOutcome> {
    activate_account_data_in(Path::new("."), uin)
}

fn activate_account_data_in(base: &Path, uin: &str) -> Result<ActivationOutcome> {
    if uin.is_empty() {
        return Ok(ActivationOutcome::Fresh);
    }
    validate_account_id(uin)?;
    let live_db = base.join(LIVE_DB);
    let live_threads = base.join(LIVE_THREADS);
    let marker = base.join(ACTIVE_ACCOUNT_MARKER);
    let dir = base.join(accounts_dir());
    let parked_db = dir.join(format!("{uin}_history.db"));
    let parked_threads = dir.join(format!("{uin}_threads.json"));
    let owner = std::fs::read_to_string(&marker)
        .map(|s| s.trim().to_string())
        .unwrap_or_default();

    if live_db.exists() && owner == uin {
        return Ok(ActivationOutcome::AlreadyActive);
    }

    // Live files of another account: park them (history only; that account's session file
    // was saved when it was active) before restoring ours.
    if live_db.exists() && !owner.is_empty() && owner != uin {
        crate::data_dir::ensure_private_dir(&dir)?;
        crate::data_dir::copy_private(&live_db, &dir.join(format!("{owner}_history.db")))?;
        if live_threads.exists() {
            crate::data_dir::copy_private(
                &live_threads,
                &dir.join(format!("{owner}_threads.json")),
            )?;
        }
        std::fs::remove_file(&live_db)?;
        let _ = std::fs::remove_file(&live_threads);
    }

    let outcome = if !live_db.exists() {
        if parked_db.exists() {
            crate::data_dir::copy_private(&parked_db, &live_db)?;
            if parked_threads.exists() && !live_threads.exists() {
                crate::data_dir::copy_private(&parked_threads, &live_threads)?;
            }
            ActivationOutcome::Restored
        } else {
            ActivationOutcome::Fresh
        }
    } else if parked_db.exists() {
        // No owner marker: data written by builds that split reads (parked DB) from writes
        // (live DB). Merge so neither side's messages are lost; inserts are idempotent.
        let report =
            crate::store::import_history_from_path(&parked_db, live_db.to_string_lossy().as_ref())?;
        ActivationOutcome::Merged {
            messages: report.messages_imported,
        }
    } else {
        ActivationOutcome::AlreadyActive
    };
    crate::data_dir::write_private_file(&marker, uin.as_bytes())?;
    Ok(outcome)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_base(tag: &str) -> PathBuf {
        let p = std::env::temp_dir().join(format!(
            "ctdozlite-acct-{tag}-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_nanos())
                .unwrap_or(0)
        ));
        std::fs::create_dir_all(p.join("accounts")).unwrap();
        p
    }

    fn write_history(path: &Path, value: &str) {
        let conn = rusqlite::Connection::open(path).unwrap();
        conn.execute_batch("CREATE TABLE synthetic_note(value TEXT NOT NULL);")
            .unwrap();
        conn.execute("INSERT INTO synthetic_note(value) VALUES(?1)", [value])
            .unwrap();
    }

    fn read_history(path: &Path) -> String {
        rusqlite::Connection::open(path)
            .unwrap()
            .query_row("SELECT value FROM synthetic_note", [], |row| row.get(0))
            .unwrap()
    }

    fn fake_session(uin: &str) -> Vec<u8> {
        serde_json::json!({"login": {"uin": uin}})
            .to_string()
            .into_bytes()
    }

    #[test]
    fn relogin_restores_parked_history_instead_of_starting_empty() {
        let base = temp_base("restore");
        std::fs::write(base.join("accounts/111_history.db"), b"parked").unwrap();
        std::fs::write(base.join("accounts/111_threads.json"), b"[]").unwrap();
        // Logged out: no live files.
        assert_eq!(
            activate_account_data_in(&base, "111").unwrap(),
            ActivationOutcome::Restored
        );
        assert_eq!(std::fs::read(base.join(LIVE_DB)).unwrap(), b"parked");
        assert_eq!(
            activate_account_data_in(&base, "111").unwrap(),
            ActivationOutcome::AlreadyActive
        );
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn other_accounts_live_history_is_parked_before_switching() {
        let base = temp_base("park");
        std::fs::write(base.join(LIVE_DB), b"alice-live").unwrap();
        std::fs::write(base.join(ACTIVE_ACCOUNT_MARKER), b"aaa").unwrap();
        std::fs::write(base.join("accounts/bbb_history.db"), b"bob-parked").unwrap();
        assert_eq!(
            activate_account_data_in(&base, "bbb").unwrap(),
            ActivationOutcome::Restored
        );
        assert_eq!(
            std::fs::read(base.join("accounts/aaa_history.db")).unwrap(),
            b"alice-live"
        );
        assert_eq!(std::fs::read(base.join(LIVE_DB)).unwrap(), b"bob-parked");
        assert_eq!(
            std::fs::read_to_string(base.join(ACTIVE_ACCOUNT_MARKER)).unwrap(),
            "bbb"
        );
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn legacy_split_databases_are_merged_without_loss() {
        let base = temp_base("merge");
        let live = base.join(LIVE_DB);
        let parked = base.join("accounts/111_history.db");
        let msg = |id: &str| crate::store::StoredMessage {
            msg_id: id.into(),
            thread_id: "t1".into(),
            sender_id: "1".into(),
            sender_name: "A".into(),
            text: id.into(),
            msg_type: "webchat".into(),
            ts: 1,
            is_outgoing: false,
            raw_json: String::new(),
        };
        {
            let mut s = crate::store::MessageStore::open(live.to_str().unwrap()).unwrap();
            s.upsert_message(&msg("live-only")).unwrap();
            s.upsert_message(&msg("both")).unwrap();
        }
        {
            let mut s = crate::store::MessageStore::open(parked.to_str().unwrap()).unwrap();
            s.upsert_message(&msg("parked-only")).unwrap();
            s.upsert_message(&msg("both")).unwrap();
        }
        assert_eq!(
            activate_account_data_in(&base, "111").unwrap(),
            ActivationOutcome::Merged { messages: 1 }
        );
        let s = crate::store::MessageStore::open(live.to_str().unwrap()).unwrap();
        let n: i64 = s
            .conn
            .query_row("SELECT COUNT(*) FROM messages", [], |r| r.get(0))
            .unwrap();
        assert_eq!(n, 3);
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn missing_or_invalid_target_keeps_live_account_files() {
        let base = temp_base("missing-target");
        std::fs::write(base.join("session.json"), b"alice-session").unwrap();
        std::fs::write(base.join(LIVE_DB), b"alice-history").unwrap();
        std::fs::write(base.join(ACTIVE_ACCOUNT_MARKER), b"alice").unwrap();

        assert!(switch_to_account_in(&base, "alice", "missing").is_err());
        assert!(switch_to_account_in(&base, "alice", "../outside").is_err());
        std::fs::write(base.join("accounts/missing.json"), b"malformed").unwrap();
        assert!(switch_to_account_in(&base, "alice", "missing").is_err());
        std::fs::write(base.join("accounts/missing.json"), fake_session("other")).unwrap();
        assert!(switch_to_account_in(&base, "alice", "missing").is_err());
        assert_eq!(
            std::fs::read(base.join("session.json")).unwrap(),
            b"alice-session"
        );
        assert_eq!(std::fs::read(base.join(LIVE_DB)).unwrap(), b"alice-history");
        assert_eq!(
            std::fs::read(base.join(ACTIVE_ACCOUNT_MARKER)).unwrap(),
            b"alice"
        );
        assert!(delete_saved_account("../outside").is_err());
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn switching_accounts_preserves_each_accounts_session_and_history() {
        let base = temp_base("roundtrip");
        std::fs::write(base.join("session.json"), fake_session("alice")).unwrap();
        write_history(&base.join(LIVE_DB), "alice-history");
        std::fs::write(base.join("accounts/bob.json"), fake_session("bob")).unwrap();
        write_history(&base.join("accounts/bob_history.db"), "bob-history");
        switch_to_account_in(&base, "alice", "bob").unwrap();
        assert_eq!(
            std::fs::read(base.join("session.json")).unwrap(),
            fake_session("bob")
        );
        assert_eq!(read_history(&base.join(LIVE_DB)), "bob-history");
        assert_eq!(
            read_history(&base.join("accounts/alice_history.db")),
            "alice-history"
        );
        switch_to_account_in(&base, "bob", "alice").unwrap();
        assert_eq!(
            std::fs::read(base.join("session.json")).unwrap(),
            fake_session("alice")
        );
        assert_eq!(read_history(&base.join(LIVE_DB)), "alice-history");
        assert_eq!(
            std::fs::read(base.join(ACTIVE_ACCOUNT_MARKER)).unwrap(),
            b"alice"
        );
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn snapshot_includes_committed_wal_data_while_database_is_open() {
        let base = temp_base("wal-snapshot");
        let live = base.join(LIVE_DB);
        let conn = rusqlite::Connection::open(&live).unwrap();
        conn.execute_batch("PRAGMA journal_mode=WAL; CREATE TABLE synthetic_note(value TEXT NOT NULL); INSERT INTO synthetic_note(value) VALUES('wal-row');").unwrap();
        let parked = base.join("accounts/alice_history.db");
        snapshot_history(&live, &parked).unwrap();
        assert_eq!(read_history(&parked), "wal-row");
        drop(conn);
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn failed_snapshot_preserves_previous_parked_database() {
        let base = temp_base("bad-snapshot");
        let live = base.join(LIVE_DB);
        let parked = base.join("accounts/alice_history.db");
        std::fs::write(&live, b"not sqlite").unwrap();
        write_history(&parked, "previous-good-copy");
        assert!(snapshot_history(&live, &parked).is_err());
        assert_eq!(read_history(&parked), "previous-good-copy");
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn snapshot_rejects_history_owned_by_another_account() {
        let base = temp_base("wrong-owner");
        write_history(&base.join(LIVE_DB), "bob-history");
        std::fs::write(base.join(ACTIVE_ACCOUNT_MARKER), b"bob").unwrap();
        assert!(save_current_account_snapshot_in(&base, "alice").is_err());
        assert!(!base.join("accounts/alice_history.db").exists());
        assert_eq!(read_history(&base.join(LIVE_DB)), "bob-history");
        let _ = std::fs::remove_dir_all(&base);
    }
}
