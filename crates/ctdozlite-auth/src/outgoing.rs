//! Bounded, memory-only send intents. An uncertain POST is never automatically retried.
use anyhow::{ensure, Result};
use std::collections::BTreeMap;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Phase {
    Prepared,
    InFlight,
    Accepted,
    Rejected,
    Unknown,
}
impl Phase {
    pub fn name(self) -> &'static str {
        match self {
            Self::Prepared => "prepared",
            Self::InFlight => "sending",
            Self::Accepted => "accepted",
            Self::Rejected => "rejected",
            Self::Unknown => "unknown",
        }
    }
}
#[derive(Clone)]
pub struct Intent {
    pub id: u64,
    pub generation: u64,
    pub account: String,
    pub target: String,
    pub group: bool,
    pub text: String,
    pub phase: Phase,
    created: std::time::Instant,
}
#[derive(Default)]
pub struct Ledger {
    entries: BTreeMap<u64, Intent>,
}

fn canonical_target(target: &str, group: bool) -> &str {
    if group {
        target
            .trim_start_matches("group_")
            .trim_start_matches('g')
            .trim_start_matches('G')
    } else {
        target
    }
}

impl Ledger {
    pub fn prepare(
        &mut self,
        generation: u64,
        account: &str,
        target: &str,
        group: bool,
        text: &str,
    ) -> Result<u64> {
        self.entries.retain(|_, entry| {
            entry.phase != Phase::Prepared
                || entry.created.elapsed() < std::time::Duration::from_secs(120)
        });
        let target = canonical_target(target, group);
        for id in [account, target] {
            ensure!(
                !id.is_empty() && id.len() <= 64 && id.bytes().all(|c| c.is_ascii_digit()),
                "invalid account or recipient"
            );
        }
        ensure!(
            !text.trim().is_empty() && text.encode_utf16().count() <= 4000,
            "invalid text length"
        );
        ensure!(
            !text
                .chars()
                .any(|c| c.is_control() && !matches!(c, '\n' | '\r' | '\t')),
            "invalid text controls"
        );
        ensure!(
            !self
                .entries
                .values()
                .any(|entry| entry.generation == generation
                    && entry.account == account
                    && entry.target == target
                    && entry.group == group
                    && entry.text == text
                    && matches!(
                        entry.phase,
                        Phase::Prepared | Phase::InFlight | Phase::Unknown
                    )),
            "equivalent intent pending or uncertain"
        );
        if self.entries.len() >= 32 {
            if let Some(id) = self
                .entries
                .iter()
                .find(|(_, entry)| matches!(entry.phase, Phase::Accepted | Phase::Rejected))
                .map(|(id, _)| *id)
            {
                self.entries.remove(&id);
            }
        }
        ensure!(self.entries.len() < 32, "outbox capacity reached");
        let id = crate::text_transport::next_client_message_id();
        self.entries.insert(
            id,
            Intent {
                id,
                generation,
                account: account.into(),
                target: target.into(),
                group,
                text: text.into(),
                phase: Phase::Prepared,
                created: std::time::Instant::now(),
            },
        );
        Ok(id)
    }
    pub fn begin(&mut self, id: u64, generation: u64) -> Result<Intent> {
        ensure!(
            !self
                .entries
                .values()
                .any(|entry| entry.phase == Phase::InFlight),
            "send already in progress"
        );
        let entry = self
            .entries
            .get_mut(&id)
            .ok_or_else(|| anyhow::anyhow!("unknown intent"))?;
        ensure!(entry.generation == generation, "account changed");
        ensure!(entry.phase == Phase::Prepared, "intent already consumed");
        ensure!(
            entry.created.elapsed() < std::time::Duration::from_secs(120),
            "preview expired"
        );
        entry.phase = Phase::InFlight;
        Ok(entry.clone())
    }
    pub fn finish(&mut self, id: u64, phase: Phase) {
        if let Some(entry) = self.entries.get_mut(&id) {
            if entry.phase == Phase::InFlight
                && matches!(phase, Phase::Accepted | Phase::Rejected | Phase::Unknown)
            {
                entry.phase = phase;
            }
        }
    }
    pub fn cancel_preview(&mut self, id: u64) {
        if self
            .entries
            .get(&id)
            .is_some_and(|entry| entry.phase == Phase::Prepared)
        {
            self.entries.remove(&id);
        }
    }
    pub fn get(&self, id: u64, generation: u64) -> Option<&Intent> {
        self.entries
            .get(&id)
            .filter(|entry| entry.generation == generation)
    }
    pub fn latest(&self, generation: u64, target: &str, group: bool) -> Option<&Intent> {
        let target = canonical_target(target, group);
        self.entries.values().rev().find(|entry| {
            entry.generation == generation && entry.target == target && entry.group == group
        })
    }
    pub fn clear_other_accounts(&mut self, generation: u64) {
        self.entries
            .retain(|_, entry| entry.generation == generation || entry.phase == Phase::InFlight);
    }
}

pub fn response_phase(response: &serde_json::Value) -> Phase {
    match response
        .get("error_code")
        .and_then(serde_json::Value::as_i64)
    {
        Some(0) => {
            let identity = [
                response,
                response.get("data").unwrap_or(&serde_json::Value::Null),
            ]
            .iter()
            .any(|value| {
                ["msgId", "globalMsgId", "messageId", "msg_id"]
                    .iter()
                    .any(|key| match value.get(*key) {
                        Some(serde_json::Value::String(id)) => {
                            !id.is_empty()
                                && id != "0"
                                && id.len() <= 128
                                && !id.chars().any(|c| c.is_whitespace() || c.is_control())
                        }
                        Some(serde_json::Value::Number(id)) => id.as_u64().is_some_and(|n| n > 0),
                        _ => false,
                    })
            });
            if identity {
                Phase::Accepted
            } else {
                Phase::Unknown
            }
        }
        Some(_) => Phase::Rejected,
        None => Phase::Unknown,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn validates_unicode_limits_controls_and_recipient_boundaries() {
        let mut ledger = Ledger::default();
        for target in ["", "peer", "1/2", "1?token=x", "١٢٣"] {
            assert!(ledger.prepare(1, "100", target, false, "hello").is_err());
        }
        for text in [" ", "a\0b", "a\u{1b}b"] {
            assert!(ledger.prepare(1, "100", "200", false, text).is_err());
        }
        assert!(ledger
            .prepare(1, "100", "200", false, &"😀".repeat(2001))
            .is_err());
        assert!(ledger
            .prepare(1, "100", "200", false, &"😀".repeat(2000))
            .is_ok());
        assert!(ledger
            .prepare(1, "100", "group_300", true, "Xin chào\nDòng hai")
            .is_ok());
        assert_eq!(ledger.latest(1, "group_300", true).unwrap().target, "300");
        assert!(ledger.latest(2, "group_300", true).is_none());
    }
    #[test]
    fn confirmation_is_single_use_and_preserves_exact_text_and_client_id() {
        let mut ledger = Ledger::default();
        let id = ledger
            .prepare(1, "100", "200", false, "**literal**\nXin chào")
            .unwrap();
        let intent = ledger.begin(id, 1).unwrap();
        assert_eq!(intent.id, id);
        assert_eq!(intent.text, "**literal**\nXin chào");
        assert!(ledger.begin(id, 1).is_err());
        ledger.finish(id, Phase::Accepted);
        assert!(ledger.begin(id, 1).is_err());
    }
    #[test]
    fn account_change_and_expired_preview_never_start_send() {
        let mut ledger = Ledger::default();
        let id = ledger.prepare(1, "100", "200", false, "hello").unwrap();
        assert!(ledger.begin(id, 2).is_err());
        assert!(ledger.get(id, 2).is_none());
        ledger.entries.get_mut(&id).unwrap().created =
            std::time::Instant::now() - std::time::Duration::from_secs(121);
        assert!(ledger.begin(id, 1).is_err());
    }
    #[test]
    fn uncertain_result_blocks_duplicate_without_retry() {
        let mut ledger = Ledger::default();
        let id = ledger.prepare(1, "100", "200", false, "hello").unwrap();
        ledger.begin(id, 1).unwrap();
        ledger.finish(id, Phase::Unknown);
        assert!(ledger.begin(id, 1).is_err());
        assert!(ledger.prepare(1, "100", "200", false, "hello").is_err());
        assert!(ledger.prepare(1, "100", "201", false, "hello").is_ok());
    }
    #[test]
    fn no_parallel_send_and_cancel_only_removes_unsent_preview() {
        let mut ledger = Ledger::default();
        let a = ledger.prepare(1, "100", "200", false, "one").unwrap();
        let b = ledger.prepare(1, "100", "200", false, "two").unwrap();
        ledger.begin(a, 1).unwrap();
        assert!(ledger.begin(b, 1).is_err());
        ledger.cancel_preview(a);
        assert!(ledger.get(a, 1).is_some());
        ledger.cancel_preview(b);
        assert!(ledger.get(b, 1).is_none());
    }
    #[test]
    fn capacity_is_bounded_and_unknown_receipts_are_not_silently_retried() {
        let mut ledger = Ledger::default();
        for i in 0..32 {
            let id = ledger
                .prepare(1, "100", "200", false, &i.to_string())
                .unwrap();
            ledger.begin(id, 1).unwrap();
            ledger.finish(id, Phase::Unknown);
        }
        assert!(ledger.prepare(1, "100", "200", false, "new").is_err());
        ledger.clear_other_accounts(2);
        assert!(ledger.prepare(2, "300", "200", false, "new").is_ok());
    }
    #[test]
    fn keepalive_or_missing_identity_is_not_an_acknowledgement() {
        use serde_json::json;
        assert_eq!(response_phase(&json!({"error_code":0})), Phase::Unknown);
        assert_eq!(
            response_phase(&json!({"error_code":0,"data":null})),
            Phase::Unknown
        );
        assert_eq!(
            response_phase(&json!({"error_code":0,"data":{"msgId":"server-1"}})),
            Phase::Accepted
        );
        for id in [json!(null), json!(0), json!("0"), json!(""), json!("a\nb")] {
            assert_eq!(
                response_phase(&json!({"error_code":0,"data":{"msgId":id}})),
                Phase::Unknown
            );
        }
        assert_eq!(response_phase(&json!({"error_code":600})), Phase::Rejected);
        for value in [json!({}), json!({"error_code":"0"}), json!(null)] {
            assert_eq!(response_phase(&value), Phase::Unknown);
        }
    }
}
