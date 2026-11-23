//! Shared photo upload/dispatch transport, independent of desktop messages, caches and decoders.
use crate::session::{Session, SessionError};
use anyhow::{bail, ensure, Result};
use serde_json::{json, Map, Value};

const CHUNK_BYTES: usize = 1_048_576;

/// Desktop keeps its documented single-shot compatibility path; companions require explicit metadata/ack.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PhotoPolicy {
    DesktopCompatibility,
    Companion,
}

/// Immutable caller-validated image snapshot. No file I/O or decoder belongs in this transport.
pub struct PhotoInput<'a> {
    pub bytes: &'a [u8],
    pub file_name: &'a str,
    pub mime: &'a str,
    pub caption: &'a str,
    pub width: u32,
    pub height: u32,
    pub original: bool,
}

/// Uploaded locations and the dispatch response; this does not prove recipient delivery or animation.
pub struct PhotoReceipt {
    pub response: Value,
    pub normal_url: String,
    pub hd_url: String,
    pub original_url: String,
    pub thumbnail_url: String,
}

fn pick(value: &Value, keys: &[&str]) -> Option<String> {
    keys.iter().find_map(|key| match value.get(*key) {
        Some(Value::String(text)) if !text.is_empty() => Some(text.clone()),
        Some(Value::Number(number)) => Some(number.to_string()),
        _ => None,
    })
}

fn require_success(response: &Value) -> Result<()> {
    match response.get("error_code").and_then(Value::as_i64) {
        Some(0) => Ok(()),
        Some(code) => Err(SessionError::new(code, "Máy chủ từ chối thao tác ảnh").into()),
        None => bail!("Phản hồi ảnh thiếu mã kết quả hợp lệ; không tự gửi lại"),
    }
}

fn has_message_identity(response: &Value) -> bool {
    [Some(response), response.get("data")]
        .into_iter()
        .flatten()
        .any(|node| {
            ["msgId", "globalMsgId", "messageId", "msg_id"]
                .iter()
                .any(|key| match node.get(*key) {
                    Some(Value::String(text)) => !text.trim().is_empty() && text.trim() != "0",
                    Some(Value::Number(number)) => number.as_u64().is_some_and(|id| id > 0),
                    _ => false,
                })
        })
}

fn target(raw: &str, group: bool) -> Result<&str> {
    let value = if group {
        raw.trim_start_matches("group_")
            .trim_start_matches('g')
            .trim_start_matches('G')
    } else {
        raw
    };
    ensure!(
        !value.is_empty() && value.len() <= 80 && value.bytes().all(|byte| byte.is_ascii_digit()),
        "Người nhận ảnh không hợp lệ"
    );
    Ok(value)
}

fn validate(input: &PhotoInput<'_>, policy: PhotoPolicy) -> Result<()> {
    let limit = if policy == PhotoPolicy::Companion {
        8 * 1024 * 1024
    } else {
        64 * 1024 * 1024
    };
    ensure!(
        !input.bytes.is_empty() && input.bytes.len() <= limit,
        "Dung lượng ảnh không hợp lệ"
    );
    ensure!(
        input.width > 0 && input.height > 0,
        "Kích thước ảnh không hợp lệ"
    );
    ensure!(
        !input.file_name.is_empty()
            && input.file_name.len() <= 1024
            && input.caption.len() <= 24000,
        "Thông tin ảnh quá dài"
    );
    ensure!(
        matches!(
            input.mime,
            "image/png" | "image/jpeg" | "image/webp" | "image/gif" | "image/bmp"
        ),
        "Định dạng ảnh không hỗ trợ"
    );
    Ok(())
}

fn recipient(body: &mut Map<String, Value>, to: &str, group: bool, visibility: bool) {
    body.insert(if group { "grid" } else { "toid" }.into(), json!(to));
    if group && visibility {
        body.insert("visibility".into(), json!(0));
    }
}

/// Upload all chunks, then dispatch once. Upload rejection/malformed replies never trigger fallback.
///
/// # Errors
/// Returns validation, Session/authentication, network, upload or dispatch errors. Companion mode
/// also rejects unsafe returned URLs and acknowledgements without a nonzero message identity.
/// A failed/uncertain dispatch must not be retried automatically; callers own an overall deadline.
pub async fn send_photo(
    s: &Session,
    to: &str,
    group: bool,
    input: &PhotoInput<'_>,
    client_id: u64,
    policy: PhotoPolicy,
) -> Result<PhotoReceipt> {
    send_photo_if_current(s, to, group, input, client_id, policy, || true).await
}

/// Scope-aware upload/dispatch variant for a Session owner that can change during chunk uploads.
///
/// # Errors
/// In addition to [`send_photo`] errors, stops before a new upload chunk or dispatch when
/// `is_current` returns false. A dispatch already in flight cannot be recalled by this check.
pub async fn send_photo_if_current<F: Fn() -> bool>(
    s: &Session,
    to: &str,
    group: bool,
    input: &PhotoInput<'_>,
    client_id: u64,
    policy: PhotoPolicy,
    is_current: F,
) -> Result<PhotoReceipt> {
    ensure!(s.login.is_some(), "chưa đăng nhập");
    validate(input, policy)?;
    ensure!(client_id > 0, "Định danh gửi ảnh không hợp lệ");
    let to = target(to, group)?;
    let host = if s.domains.file.is_empty() {
        crate::endpoints::PHOTO_FILE_FALLBACK
    } else {
        s.domains.file.trim_end_matches('/')
    };
    let upload_path = if group {
        "/api/group/photo_original/upload"
    } else {
        "/api/message/photo_original/upload"
    };
    let total_chunks = input.bytes.len().div_ceil(CHUNK_BYTES);
    let target_type = if group { 11 } else { 2 };
    let mut uploaded = Value::Null;
    for (index, chunk) in input.bytes.chunks(CHUNK_BYTES).enumerate() {
        ensure!(is_current(), "Phiên đã thay đổi; dừng tải/gửi ảnh");
        let mut body = Map::new();
        body.insert("totalChunk".into(), json!(total_chunks));
        body.insert("fileName".into(), json!(input.file_name));
        body.insert("clientId".into(), json!(client_id));
        body.insert("totalSize".into(), json!(input.bytes.len() as u64));
        body.insert("imei".into(), json!(s.imei));
        body.insert("isE2EE".into(), json!(0));
        body.insert("jxl".into(), json!(0));
        body.insert("chunkId".into(), json!(index + 1));
        recipient(&mut body, to, group, false);
        let params = s.encoded_params(&Value::Object(body))?;
        let url = format!(
            "{host}{upload_path}?{}&params={params}&type={target_type}",
            s.common_params()
        );
        let raw = s
            .post_multipart(&url, "chunkContent", input.file_name, input.mime, chunk)
            .await?;
        uploaded = s.maybe_decrypt(raw, s.session_key_bytes()).await;
        require_success(&uploaded)?;
    }
    let node = uploaded.get("data").unwrap_or(&uploaded);
    ensure!(
        node.is_object(),
        "Phản hồi tải ảnh không hợp lệ; không tự gửi lại"
    );
    let photo_id = pick(node, &["photoId", "clientFileId"]);
    let normal_url = pick(node, &["normalUrl", "hdUrl", "oriUrl", "url"]).unwrap_or_default();
    let hd_url =
        pick(node, &["hdUrl", "normalUrl", "oriUrl"]).unwrap_or_else(|| normal_url.clone());
    let original_url =
        pick(node, &["oriUrl", "hdUrl", "normalUrl"]).unwrap_or_else(|| normal_url.clone());
    let thumbnail_url =
        pick(node, &["thumbUrl", "normalUrl", "hdUrl"]).unwrap_or_else(|| normal_url.clone());
    if policy == PhotoPolicy::Companion {
        ensure!(
            photo_id
                .as_deref()
                .is_some_and(|id| !id.trim().is_empty() && id != "0"),
            "Tải ảnh chưa có định danh hợp lệ; chưa gửi"
        );
        for url in [&normal_url, &hd_url, &original_url, &thumbnail_url] {
            ensure!(
                url.starts_with("https://") && crate::session::is_trusted_zalo_url(url),
                "Đường dẫn ảnh từ máy chủ không hợp lệ; chưa gửi"
            );
        }
    }
    ensure!(
        is_current(),
        "Phiên đã thay đổi; ảnh chưa được gửi vào hội thoại"
    );
    let response = if let Some(id) = photo_id.filter(|_| !normal_url.is_empty()) {
        let id = id
            .parse::<u64>()
            .map(|id| json!(id))
            .unwrap_or_else(|_| json!(id));
        let mut body = Map::new();
        body.insert("photoId".into(), id);
        body.insert("clientId".into(), json!(client_id));
        body.insert("desc".into(), json!(input.caption));
        body.insert("width".into(), json!(input.width));
        body.insert("height".into(), json!(input.height));
        body.insert("rawUrl".into(), json!(normal_url));
        body.insert("hdUrl".into(), json!(hd_url));
        body.insert("thumbUrl".into(), json!(thumbnail_url));
        body.insert(
            "oriUrl".into(),
            json!(if input.original {
                &original_url
            } else {
                &normal_url
            }),
        );
        body.insert("normalUrl".into(), json!(normal_url));
        body.insert(
            "hdSize".into(),
            json!(if input.original {
                input.bytes.len() as u64
            } else {
                0
            }),
        );
        for flag in ["isHD", "hd", "isOriginal"] {
            body.insert(flag.into(), json!(if input.original { 1 } else { 0 }));
        }
        body.insert("zsource".into(), json!(-1));
        body.insert("jcp".into(), json!("{\"convertible\":true}"));
        body.insert("imei".into(), json!(s.imei));
        recipient(&mut body, to, group, true);
        let params = s.encoded_params(&Value::Object(body))?;
        let path = if group {
            "/api/group/photo_original/send"
        } else {
            "/api/message/photo_original/send"
        };
        let url = format!("{host}{path}?{}", s.common_params());
        let raw = s.post_form(&url, format!("params={params}")).await?;
        s.maybe_decrypt(raw, s.session_key_bytes()).await
    } else {
        ensure!(
            policy == PhotoPolicy::DesktopCompatibility,
            "Tải ảnh chưa đủ thông tin; chưa gửi"
        );
        let mut body = Map::new();
        body.insert("clientId".into(), json!(client_id));
        body.insert("desc".into(), json!(input.caption));
        body.insert("width".into(), json!(input.width));
        body.insert("height".into(), json!(input.height));
        body.insert("imei".into(), json!(s.imei));
        recipient(&mut body, to, group, true);
        let params = s.encoded_params(&Value::Object(body))?;
        let path = if group {
            "/api/group/sendpt"
        } else {
            "/api/message/photo"
        };
        let url = format!(
            "{host}{path}?{}&params={params}&type={target_type}",
            s.common_params()
        );
        let raw = s
            .post_multipart(
                &url,
                "fileContent",
                input.file_name,
                input.mime,
                input.bytes,
            )
            .await?;
        s.maybe_decrypt(raw, s.session_key_bytes()).await
    };
    if policy == PhotoPolicy::Companion {
        require_success(&response)?;
        ensure!(
            has_message_identity(&response),
            "Chưa xác minh ảnh đã gửi; không tự gửi lại"
        );
    } else if response
        .get("error_code")
        .and_then(Value::as_i64)
        .is_some_and(|code| code != 0)
    {
        require_success(&response)?;
    }
    Ok(PhotoReceipt {
        response,
        normal_url,
        hd_url,
        original_url,
        thumbnail_url,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn malformed_upload_and_synthetic_keepalive_never_authorize_upload() {
        for response in [
            json!({}),
            json!({"error_code":"0"}),
            json!({"error_code":null}),
            json!({"error_code":114}),
            json!({"error_code":false}),
        ] {
            assert!(require_success(&response).is_err());
        }
        assert!(require_success(&json!({"error_code":0})).is_ok());
        for response in [
            json!({"error_code":0,"data":null}),
            json!({"error_code":0,"data":{"msgId":0}}),
            json!({"data":{"msgId":"  "}}),
        ] {
            assert!(!has_message_identity(&response));
        }
        assert!(has_message_identity(
            &json!({"data":{"msgId":"fixture-id"}})
        ));
    }
    #[test]
    fn recipients_and_payload_bounds_are_checked_before_network_work() {
        assert_eq!(target("group_123", true).unwrap(), "123");
        assert_eq!(target("g123", true).unwrap(), "123");
        for raw in ["", "../123", "1\n2", "g123", "１２３"] {
            assert!(target(raw, false).is_err());
        }
        let mut input = PhotoInput {
            bytes: &[1],
            file_name: "a.gif",
            mime: "image/gif",
            caption: "",
            width: 1,
            height: 1,
            original: true,
        };
        assert!(validate(&input, PhotoPolicy::Companion).is_ok());
        input.mime = "text/plain";
        assert!(validate(&input, PhotoPolicy::Companion).is_err());
        input.mime = "image/gif";
        input.width = 0;
        assert!(validate(&input, PhotoPolicy::Companion).is_err());
    }
}
