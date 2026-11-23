//! Shared text-send transport. Callers allocate one client ID per explicit user action.
use crate::session::{obj, Session, SessionError};
use anyhow::Result;
use serde_json::{json, Value};

pub fn next_client_message_id() -> u64 {
    use std::sync::atomic::{AtomicU64, Ordering};
    static LAST_ID: AtomicU64 = AtomicU64::new(0);
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis() as u64)
        .unwrap_or(0);
    let mut previous = LAST_ID.load(Ordering::Relaxed);
    loop {
        let next = now.max(previous.saturating_add(1));
        match LAST_ID.compare_exchange_weak(previous, next, Ordering::Relaxed, Ordering::Relaxed) {
            Ok(_) => return next,
            Err(actual) => previous = actual,
        }
    }
}

/// Preserve the desktop wire shape; rich-text preprocessing remains with its caller.
pub async fn send_text(
    s: &Session,
    target: &str,
    group: bool,
    text: &str,
    properties: Option<&str>,
    client_id: u64,
) -> Result<Value> {
    if s.login.is_none() {
        anyhow::bail!("chưa đăng nhập");
    }
    let clean_group = target
        .trim_start_matches("group_")
        .trim_start_matches('g')
        .trim_start_matches('G');
    let mut body = obj(&[
        (
            if group { "grid" } else { "toid" },
            json!(if group { clean_group } else { target }),
        ),
        ("message", json!(text)),
        ("clientId", json!(client_id)),
        ("imei", json!(s.imei)),
    ]);
    if let Some(map) = body.as_object_mut() {
        if group {
            map.insert("visibility".into(), json!(0));
        }
        if let Some(properties) = properties {
            map.insert("textProperties".into(), json!(properties));
        }
    }
    let params = s.encoded_params(&body)?;
    let host = if group {
        s.domains.group.as_str()
    } else if s.domains.chat.contains("-msg-chat.")
        || s.domains.chat.contains("tt-chat2-wpa")
        || s.domains.chat.is_empty()
    {
        crate::endpoints::TEXT_SEND_FALLBACK
    } else {
        s.domains.chat.as_str()
    };
    let path = if group {
        "/api/group/sendmsg"
    } else {
        "/api/message/sms"
    };
    let url = format!("{host}{path}?{}", s.common_params());
    let response = s.post_form(&url, format!("params={params}")).await?;
    let response = s.maybe_decrypt(response, s.session_key_bytes()).await;
    if let Some(code) = response.get("error_code").and_then(Value::as_i64) {
        if code != 0 {
            return Err(SessionError::new(
                code,
                response
                    .get("error_message")
                    .and_then(Value::as_str)
                    .unwrap_or("unknown"),
            )
            .into());
        }
    }
    Ok(response)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn missing_identity_or_key_never_sends_plaintext() {
        let mut session = Session::new(false, false, false).unwrap();
        session.domains.chat = "not-a-url".into();
        session.domains.group = "not-a-url".into();
        for group in [false, true] {
            assert!(send_text(&session, "1", group, "text", None, 1)
                .await
                .unwrap_err()
                .to_string()
                .contains("chưa đăng nhập"));
        }
        session.login = Some(crate::session::LoginInfo {
            uin: "fixture".into(),
            ..Default::default()
        });
        for group in [false, true] {
            assert!(send_text(&session, "1", group, "text", None, 1)
                .await
                .unwrap_err()
                .to_string()
                .contains("no session key"));
        }
    }
}
