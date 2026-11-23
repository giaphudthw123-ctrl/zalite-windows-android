//! Process-owned, bounded send ledger; only confirmation invokes the network.
use ctdozlite_auth::outgoing::{Ledger, Phase};
use serde_json::{json, Value};
use std::sync::{Mutex, OnceLock};
fn ledger() -> &'static Mutex<Ledger> {
    static LEDGER: OnceLock<Mutex<Ledger>> = OnceLock::new();
    LEDGER.get_or_init(|| Mutex::new(Ledger::default()))
}
fn error(message: &str) -> Value {
    json!({"phase":"error","message":message})
}

pub fn prepare(account: &str, target: &str, group: bool, text: &str) -> Value {
    let Some((generation, _, session)) = crate::auth::snapshot() else {
        return json!({"phase":"requires_login"});
    };
    if session.login.as_ref().map(|login| login.uin.as_str()) != Some(account) {
        return error("Tài khoản đã thay đổi. Hãy mở lại hội thoại.");
    }
    let Ok(mut ledger) = ledger().lock() else {
        return error("Không mở được danh sách lượt gửi.");
    };
    ledger.clear_other_accounts(generation);
    match ledger.prepare(generation,account,target,group,text) {
        Ok(id)=>json!({"phase":"prepared","ticket":id.to_string()}),
        Err(_)=>error("Không tạo được lượt gửi. Kiểm tra nội dung, người nhận và trạng thái lượt trước; tin chưa rõ kết quả sẽ không được tự gửi lại."),
    }
}
pub fn cancel(id: u64) {
    if let Ok(mut ledger) = ledger().lock() {
        ledger.cancel_preview(id);
    }
}
pub fn status(account: &str, target: &str, group: bool) -> Value {
    let Some((generation, _, session)) = crate::auth::snapshot() else {
        return json!({"phase":"requires_login"});
    };
    if session.login.as_ref().map(|login| login.uin.as_str()) != Some(account) {
        return error("Tài khoản đã thay đổi.");
    }
    let Ok(ledger) = ledger().lock() else {
        return error("Không đọc được trạng thái gửi.");
    };
    ledger
        .latest(generation, target, group)
        .map(|entry| json!({"phase":entry.phase.name(),"ticket":entry.id.to_string(),"text":entry.text}))
        .unwrap_or_else(|| json!({"phase":"none"}))
}
pub fn confirm(id: u64) -> Value {
    let Some((generation, _, session)) = crate::auth::snapshot() else {
        return json!({"phase":"requires_login"});
    };
    let intent = {
        let Ok(mut ledger) = ledger().lock() else {
            return error("Không mở được lượt gửi.");
        };
        match ledger.begin(id, generation) {
            Ok(intent) => intent,
            Err(_) => return error("Lượt gửi đã dùng, hết hạn, đang bận hoặc thuộc phiên khác."),
        }
    };
    let phase = if !crate::auth::is_session(generation) {
        Phase::Rejected
    } else {
        match tokio::runtime::Runtime::new() {
            Ok(runtime) => runtime.block_on(async {
                match tokio::time::timeout(
                    std::time::Duration::from_secs(30),
                    ctdozlite_auth::text_transport::send_text(
                        &session,
                        &intent.target,
                        intent.group,
                        &intent.text,
                        None,
                        intent.id,
                    ),
                )
                .await
                {
                    Ok(Ok(response)) => ctdozlite_auth::outgoing::response_phase(&response),
                    Ok(Err(error))
                        if error
                            .downcast_ref::<ctdozlite_auth::session::SessionError>()
                            .is_some_and(|e| {
                                matches!(e.code, 600 | -102 | 114 | 18022 | 18060 | 429)
                            }) =>
                    {
                        Phase::Rejected
                    }
                    _ => Phase::Unknown,
                }
            }),
            Err(_) => Phase::Rejected,
        }
    };
    if let Ok(mut ledger) = ledger().lock() {
        ledger.finish(id, phase);
    }
    json!({"phase":phase.name(),"ticket":id.to_string()})
}
