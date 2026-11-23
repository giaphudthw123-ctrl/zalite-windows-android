//! Shared request for recent-conversation previews; no UI or disk access.
use crate::session::{obj, Session};
use anyhow::Result;
use serde_json::{json, Value};

/// Fetch existing companion preview data through the standard Session request path.
pub async fn get_last_msgs(s: &Session, thread_ids: &[String]) -> Result<Value> {
    if s.login.is_none() {
        anyhow::bail!("chưa đăng nhập");
    }
    let mut map = serde_json::Map::new();
    // [O] `getLastMessagesForPreview(e)` in `compact-app-pc.js:14385933` passes `{}` on preload
    // to fetch all recent conversation previews without overflowing URL length.
    if thread_ids.len() <= 25 {
        for id in thread_ids {
            let clean = id.trim_start_matches("group_").trim_start_matches('g');
            let key = if id.starts_with("group_") || id.starts_with('g') {
                format!("{clean}_1")
            } else {
                format!("{clean}_0")
            };
            map.insert(key, json!("1"));
        }
    }
    let map_str = serde_json::to_string(&map).unwrap_or_else(|_| "{}".into());
    let body = obj(&[
        ("threadIdLocalMsgId", json!(map_str)),
        ("imei", json!(s.imei)),
    ]);
    // Missing encryption material must not silently send an unencoded request.
    let params = format!("params={}", s.encoded_params(&body)?);

    let url = format!(
        "{}/api/preloadconvers/get-last-msgs?{}&{params}",
        s.domains.convers_api,
        s.common_params()
    );
    let resp = s.get(&url).await?;
    let resp = s.maybe_decrypt(resp, s.session_key_bytes()).await;
    Ok(resp)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::session::LoginInfo;

    #[tokio::test]
    async fn missing_login_and_missing_cipher_key_fail_before_network() {
        let mut session = Session::new(false, false, false).unwrap();
        // Invalid destination would fail differently if a request escaped the guards.
        session.domains.convers_api = "not-a-url".into();
        assert!(get_last_msgs(&session, &[])
            .await
            .unwrap_err()
            .to_string()
            .contains("chưa đăng nhập"));
        session.login = Some(LoginInfo {
            uin: "synthetic-user".into(),
            ..Default::default()
        });
        assert!(get_last_msgs(&session, &[])
            .await
            .unwrap_err()
            .to_string()
            .contains("no session key"));
    }
}
