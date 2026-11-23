//! Bounded mobile projection of companion preview envelopes; shape compatibility needs live validation.
use anyhow::{ensure, Result};
use serde::Serialize;
use serde_json::Value;
use std::collections::BTreeMap;

#[derive(Debug, Clone, Serialize)]
pub struct Message {
    pub id: String,
    pub text: String,
    pub ts: i64,
    pub outgoing: bool,
}
#[derive(Debug, Clone, Serialize)]
pub struct Conversation {
    pub id: String,
    pub name: String,
    pub group: bool,
    pub messages: Vec<Message>,
    pub ts: i64,
}
#[derive(Debug, Serialize)]
pub struct Snapshot {
    pub conversations: Vec<Conversation>,
    pub limited: bool,
}

fn scalar(value: &Value, names: &[&str]) -> String {
    names
        .iter()
        .find_map(|name| match value.get(*name) {
            Some(Value::String(s)) if !s.is_empty() => Some(s.chars().take(512).collect()),
            Some(Value::Number(n)) => Some(n.to_string()),
            _ => None,
        })
        .unwrap_or_default()
}
fn grouped(v: &Value) -> bool {
    v.get("isGroup").and_then(Value::as_bool).unwrap_or(false)
        || v.get("threadType").and_then(Value::as_i64) == Some(1)
}

/// Project only recognized response shapes; malformed or error envelopes are never empty success.
pub fn parse(response: &Value, current_user: &str) -> Result<Snapshot> {
    ensure!(!current_user.is_empty(), "missing account");
    if let Some(code) = response.get("error_code") {
        ensure!(code.as_i64() == Some(0), "server rejected preview");
    }
    let mut root = response.get("data").unwrap_or(response).clone();
    if let Value::String(text) = &root {
        ensure!(text.len() <= 4 * 1024 * 1024, "preview too large");
        root = serde_json::from_str(text)?;
    }
    if let Some(inner) = root.get("data") {
        root = inner.clone();
    }
    let mut entries = Vec::new();
    if let Some(rows) = root.as_array() {
        ensure!(rows.len() <= 5000, "preview entry budget exceeded");
        for row in rows {
            entries.push((row, false));
        }
    } else if let Some(map) = root.as_object() {
        let mut recognized = map.is_empty();
        for (key, group) in [
            ("msgs", false),
            ("groupMsgs", true),
            ("pageMsgs", false),
            ("conversations", false),
            ("threads", false),
            ("allmsgs", false),
        ] {
            if let Some(value) = map.get(key) {
                recognized = true;
                let rows = value
                    .as_array()
                    .ok_or_else(|| anyhow::anyhow!("invalid preview array"))?;
                ensure!(
                    rows.len().saturating_add(entries.len()) <= 5000,
                    "preview entry budget exceeded"
                );
                for row in rows {
                    entries.push((row, group));
                }
            }
        }
        // clearUnreads is metadata, never render it as a message.
        if map.contains_key("clearUnreads") {
            recognized = true;
        }
        ensure!(recognized, "unsupported preview envelope");
    } else {
        anyhow::bail!("invalid preview envelope");
    }
    ensure!(entries.len() <= 5000, "preview entry budget exceeded");
    let mut conversations: BTreeMap<(String, bool), Conversation> = BTreeMap::new();
    let mut count = 0usize;
    let mut limited = false;
    let mut text_bytes = 0usize;
    for (entry, bucket_group) in entries {
        let mut messages = Vec::new();
        for key in ["messages", "msgs", "groupMsgs"] {
            if let Some(rows) = entry.get(key) {
                let rows = rows
                    .as_array()
                    .ok_or_else(|| anyhow::anyhow!("invalid message array"))?;
                ensure!(
                    rows.len()
                        .saturating_add(messages.len())
                        .saturating_add(count)
                        <= 10000,
                    "preview message budget exceeded"
                );
                for row in rows {
                    messages.push((row, bucket_group || key == "groupMsgs"));
                }
            }
        }
        for key in ["lastMsg", "message"] {
            if let Some(row) = entry.get(key) {
                ensure!(row.is_object(), "invalid message object");
                messages.push((row, bucket_group));
            }
        }
        if messages.is_empty() && entry.get("content").is_some() {
            messages.push((entry, bucket_group));
        }
        for (message, bucket) in messages {
            count += 1;
            ensure!(count <= 10000, "preview message budget exceeded");
            let group = bucket || grouped(entry) || grouped(message);
            let mut id = scalar(entry, &["threadId", "idTo", "groupId"]);
            if id.is_empty() {
                id = scalar(message, &["threadId", "idTo", "uidTo", "groupId"]);
            }
            let from = scalar(message, &["uidFrom", "senderId"]);
            if !group && (id == current_user || id.is_empty()) {
                id = from.clone();
            }
            if id.is_empty() || id == "0" || id.len() > 128 {
                continue;
            }
            let key = (id.clone(), group);
            if !conversations.contains_key(&key) && conversations.len() >= 512 {
                limited = true;
                continue;
            }
            let name = scalar(entry, &["displayName", "name", "title", "groupName"]);
            let thread = conversations.entry(key).or_insert_with(|| Conversation {
                id: id.clone(),
                name: if name.is_empty() {
                    format!("{} {}", if group { "Nhóm" } else { "Liên hệ" }, id)
                } else {
                    name
                },
                group,
                messages: Vec::new(),
                ts: 0,
            });
            let message_id = scalar(message, &["msgId", "globalMsgId", "cliMsgId", "id"]);
            if !message_id.is_empty() && thread.messages.iter().any(|old| old.id == message_id) {
                continue;
            }
            let text: String = match message.get("content").or_else(|| message.get("text")) {
                Some(Value::String(text)) => {
                    limited |= text.chars().take(4001).count() > 4000;
                    text.chars().take(4000).collect()
                }
                Some(_) => "[Nội dung đính kèm]".into(),
                None => continue,
            };
            text_bytes = text_bytes.saturating_add(text.len());
            ensure!(text_bytes <= 512 * 1024, "preview text budget exceeded");
            let ts = message
                .get("ts")
                .or_else(|| message.get("timestamp"))
                .and_then(|v| v.as_i64().or_else(|| v.as_str()?.parse().ok()))
                .unwrap_or(0);
            thread.ts = thread.ts.max(ts);
            thread.messages.push(Message {
                id: message_id,
                text,
                ts,
                outgoing: !from.is_empty() && from == current_user,
            });
            if thread.messages.len() > 100 {
                thread.messages.sort_by_key(|m| m.ts);
                thread.messages.remove(0);
                limited = true;
            }
        }
    }
    let mut rows: Vec<_> = conversations
        .into_values()
        .filter(|c| !c.messages.is_empty())
        .collect();
    for row in &mut rows {
        row.messages.sort_by_key(|m| m.ts);
    }
    rows.sort_by(|a, b| {
        b.ts.cmp(&a.ts)
            .then(a.id.cmp(&b.id))
            .then(a.group.cmp(&b.group))
    });
    Ok(Snapshot {
        conversations: rows,
        limited,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn observed_last_message_and_message_arrays_keep_identity_order_and_direction() {
        let result=parse(&json!({"error_code":0,"data":[{"idTo":"peer","name":"Khách thử","messages":[{"msgId":"2","content":"Sau","ts":2,"uidFrom":"self"},{"msgId":"1","content":"Trước","ts":1,"uidFrom":"peer"},{"msgId":"1","content":"duplicate","ts":1}]}]}),"self").unwrap();
        let row = &result.conversations[0];
        assert_eq!(row.name, "Khách thử");
        assert_eq!(row.messages.len(), 2);
        assert_eq!(row.messages[0].text, "Trước");
        assert!(row.messages[1].outgoing);
    }
    #[test]
    fn incoming_counterpart_group_bucket_and_nested_json_are_supported() {
        let data = json!({"msgs":[{"idTo":"self","content":"hi","uidFrom":"peer","ts":1}],"groupMsgs":[{"idTo":"123","content":{"photo":"not-loaded"},"uidFrom":"member","ts":3}]});
        let parsed = parse(&json!({"error_code":0,"data":data.to_string()}), "self").unwrap();
        assert_eq!(parsed.conversations.len(), 2);
        assert!(parsed.conversations[0].group);
        assert_eq!(parsed.conversations[1].id, "peer");
    }
    #[test]
    fn rejects_error_and_unknown_shapes_instead_of_silent_empty_success() {
        for value in [
            json!({"error_code":600,"data":[]}),
            json!({"data":{"unknown":[]}}),
            json!({"data":{"msgs":"wrong"}}),
            json!({"data":null}),
        ] {
            assert!(parse(&value, "self").is_err());
        }
        assert!(parse(&json!({"data":[]}), "").is_err());
        assert!(parse(&json!({"error_code":0,"data":[]}), "self")
            .unwrap()
            .conversations
            .is_empty());
    }
    #[test]
    fn bounds_loaded_messages_and_reports_limitation() {
        let messages: Vec<_> = (0..120)
            .map(|i| json!({"msgId":i.to_string(),"content":"fixture","ts":i}))
            .collect();
        let parsed = parse(
            &json!({"data":[{"idTo":"peer","messages":messages}]}),
            "self",
        )
        .unwrap();
        assert!(parsed.limited);
        assert_eq!(parsed.conversations[0].messages.len(), 100);
        assert_eq!(parsed.conversations[0].messages[0].ts, 20);
    }

    #[test]
    fn rejects_entry_message_and_text_budget_overflows() {
        assert!(parse(&json!({"data":vec![json!({});5001]}), "self").is_err());
        assert!(parse(
            &json!({"data":[{"idTo":"peer","messages":vec![json!({});10001]}]}),
            "self"
        )
        .is_err());
        let rows: Vec<_> = (0..140)
            .map(|i| json!({"idTo":"peer","msgId":i,"content":"x".repeat(4000)}))
            .collect();
        assert!(parse(&json!({"data":rows}), "self").is_err());
    }

    #[test]
    fn same_id_in_group_and_direct_thread_never_merges() {
        let data = json!({"msgs":[{"idTo":"123","msgId":"1","content":"direct"}],"groupMsgs":[{"idTo":"123","msgId":"1","content":"group"}]});
        let parsed = parse(&json!({"data":data}), "self").unwrap();
        assert_eq!(parsed.conversations.len(), 2);
        assert_ne!(parsed.conversations[0].group, parsed.conversations[1].group);
    }

    #[test]
    fn truncates_unicode_text_and_marks_snapshot_limited() {
        let parsed = parse(
            &json!({"data":[{"idTo":"peer","content":"😀".repeat(4001)}]}),
            "self",
        )
        .unwrap();
        assert!(parsed.limited);
        assert_eq!(
            parsed.conversations[0].messages[0].text.chars().count(),
            4000
        );
    }
}
