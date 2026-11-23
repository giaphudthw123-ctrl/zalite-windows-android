//! One cancellable companion login owner; no filesystem writes or credential logging.
use ctdozlite_auth::{login, qr, session::Session};
use serde_json::{json, Value};
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc, Mutex, OnceLock,
};

struct State {
    generation: u64,
    active: bool,
    cancel: Arc<AtomicBool>,
    status: Value,
    saved: Option<Vec<u8>>,
    session: Option<Session>,
}
fn state() -> &'static Mutex<State> {
    static STATE: OnceLock<Mutex<State>> = OnceLock::new();
    STATE.get_or_init(|| {
        Mutex::new(State {
            generation: 0,
            active: false,
            cancel: Arc::new(AtomicBool::new(false)),
            status: json!({"phase":"idle"}),
            saved: None,
            session: None,
        })
    })
}
fn publish(generation: u64, value: Value, saved: Option<Vec<u8>>, terminal: bool) {
    if let Ok(mut s) = state().lock() {
        if s.generation != generation {
            return;
        }
        s.status = value;
        s.saved = saved;
        if terminal {
            s.active = false;
        }
    }
}
pub fn start() -> u64 {
    let Ok(mut s) = state().lock() else {
        return 0;
    };
    if s.active {
        return s.generation;
    }
    s.generation += 1;
    let generation = s.generation;
    let cancel = Arc::new(AtomicBool::new(false));
    s.cancel = cancel.clone();
    s.active = true;
    s.saved = None;
    s.session = None;
    s.status = json!({"phase":"requesting"});
    std::thread::spawn(move || {
        let Ok(runtime) = tokio::runtime::Runtime::new() else {
            publish(
                generation,
                json!({"phase":"error","message":"Không tạo được tác vụ đăng nhập."}),
                None,
                true,
            );
            return;
        };
        runtime.block_on(async move {
            let flow = async {
                let mut session = Session::new(false,false,false)?;
                login::get_server_info(&session).await?;
                let ticket = qr::request_qr(&session,"vi").await?;
                let payload = ticket.payload().ok_or_else(|| anyhow::anyhow!("missing QR"))?;
                let code = qrcode::QrCode::new(payload.as_bytes())?;
                let width = code.width();
                if width > 177 { anyhow::bail!("QR too large"); }
                let pixels: String = code.to_colors().into_iter().map(|c| if c == qrcode::Color::Dark {'1'} else {'0'}).collect();
                publish(generation,json!({"phase":"waiting_scan","width":width,"pixels":pixels}),None,false);
                qr::wait_for_scan(&session,&ticket).await?;
                publish(generation,json!({"phase":"waiting_confirm"}),None,false);
                let response = qr::confirm(&session,&ticket).await?;
                if let Some(sek) = qr::extract_zpw_sek(&response) { session.set_zpw_sek(sek); }
                let info = login::get_login_info(&session).await?;
                if let Some(sek) = qr::extract_zpw_sek(&info) { session.set_zpw_sek(sek); }
                let config = info.get("data").ok_or_else(|| anyhow::anyhow!("missing configuration"))?;
                login::apply_login_config(&mut session,config); session.apply_server_info(config);
                if session.login.as_ref().map_or(true,|l| l.uin.is_empty()) || session.zpw_sek.as_ref().map_or(true,|k| k.is_empty()) || session.secret_key.as_ref().map_or(true,|k|k.len()!=32) { anyhow::bail!("incomplete session"); }
                let bytes = session.serialize_for_secure_storage()?;
                Ok::<_,anyhow::Error>((bytes,session))
            };
            tokio::pin!(flow);
            let cancellation = async { while !cancel.load(Ordering::Relaxed) { tokio::time::sleep(std::time::Duration::from_millis(100)).await; } };
            tokio::select! {
                _ = cancellation => publish(generation,json!({"phase":"cancelled"}),None,true),
                result = tokio::time::timeout(std::time::Duration::from_secs(240), &mut flow) => match result {
                    Ok(Ok((bytes,session))) => {
                        if let Ok(mut s) = state().lock() { if s.generation == generation { s.active=false; if !s.cancel.load(Ordering::Relaxed) { s.status=json!({"phase":"authenticated"}); s.saved=Some(bytes); s.session=Some(session); } else { s.status=json!({"phase":"cancelled"}); } } }
                    },
                    _ => publish(generation,json!({"phase":"error","message":"Không hoàn tất xác thực. Hãy thử lại hoặc kiểm tra mạng."}),None,true),
                }
            }
        });
    });
    generation
}
pub fn status(generation: u64) -> String {
    state()
        .lock()
        .ok()
        .filter(|s| s.generation == generation)
        .map(|s| s.status.to_string())
        .unwrap_or_else(|| json!({"phase":"expired"}).to_string())
}
pub fn cancel(generation: u64) {
    if let Ok(mut s) = state().lock() {
        if s.generation == generation {
            s.cancel.store(true, Ordering::Relaxed);
            s.saved = None;
            s.session = None;
        }
    }
}
pub fn take(generation: u64) -> Option<Vec<u8>> {
    state()
        .lock()
        .ok()
        .filter(|s| s.generation == generation)
        .and_then(|mut s| s.saved.take())
}

fn decode_saved(bytes: &[u8]) -> anyhow::Result<Session> {
    let mut session = Session::new(false, false, false)?;
    session.restore_from_secure_storage(bytes)?;
    let login = session
        .login
        .as_ref()
        .ok_or_else(|| anyhow::anyhow!("missing identity"))?;
    anyhow::ensure!(
        !login.uin.is_empty()
            && login.uin.len() <= 64
            && login.uin.bytes().all(|b| b.is_ascii_alphanumeric()),
        "invalid identity"
    );
    anyhow::ensure!(session.expired_at.is_none(), "expired session");
    anyhow::ensure!(
        session
            .secret_key
            .as_ref()
            .map_or(false, |key| key.len() == 32),
        "invalid key"
    );
    Ok(session)
}

pub fn restore(bytes: &[u8]) -> String {
    let Ok(session) = decode_saved(bytes) else {
        return json!({"phase":"restore_error"}).to_string();
    };
    let name = session
        .login
        .as_ref()
        .map(|login| login.display_name.chars().take(160).collect::<String>())
        .unwrap_or_default();
    let Ok(mut s) = state().lock() else {
        return json!({"phase":"restore_error"}).to_string();
    };
    if s.active {
        return json!({"phase":"busy"}).to_string();
    }
    s.generation += 1;
    s.saved = None;
    s.cancel = Arc::new(AtomicBool::new(false));
    s.session = Some(session);
    s.status = json!({"phase":"restored_unverified","name":name});
    s.status.to_string()
}

pub fn snapshot() -> Option<(u64, Session)> {
    state().lock().ok().and_then(|s| {
        if s.active || s.cancel.load(Ordering::Relaxed) {
            None
        } else {
            s.session
                .as_ref()
                .map(|session| (s.generation, session.clone()))
        }
    })
}
pub fn is_current(generation: u64) -> bool {
    state().lock().ok().is_some_and(|s| {
        s.generation == generation && s.session.is_some() && !s.cancel.load(Ordering::Relaxed)
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn protected_adapter_restore_validates_without_network_or_files() {
        let mut fixture = Session::new(false, false, false).unwrap();
        fixture.secret_key = Some(vec![7; 32]);
        fixture.set_zpw_sek("synthetic-token".into());
        fixture.login = Some(ctdozlite_auth::session::LoginInfo {
            uin: "10001".into(),
            display_name: "Fixture".into(),
            zpw_sek: "synthetic-token".into(),
            ..Default::default()
        });
        let bytes = fixture.serialize_for_secure_storage().unwrap();
        assert_eq!(decode_saved(&bytes).unwrap().login.unwrap().uin, "10001");
        assert!(decode_saved(b"broken").is_err());
        fixture.expired_at = Some(1);
        assert!(decode_saved(&fixture.serialize_for_secure_storage().unwrap()).is_err());
        fixture.expired_at = None;
        fixture.secret_key = Some(vec![1; 16]);
        assert!(decode_saved(&fixture.serialize_for_secure_storage().unwrap()).is_err());
    }
    #[test]
    fn stale_events_and_credentials_are_scoped_and_consumed_once() {
        {
            let mut s = state().lock().unwrap();
            s.generation = 42;
            s.active = true;
            s.cancel = Arc::new(AtomicBool::new(false));
            s.status = json!({"phase":"requesting"});
            s.saved = None;
        }
        publish(
            41,
            json!({"phase":"authenticated"}),
            Some(b"wrong".to_vec()),
            true,
        );
        assert!(take(41).is_none());
        assert!(take(42).is_none());
        assert!(status(42).contains("requesting"));
        assert!(status(41).contains("expired"));
        publish(
            42,
            json!({"phase":"authenticated"}),
            Some(b"synthetic".to_vec()),
            true,
        );
        assert_eq!(take(42).unwrap(), b"synthetic");
        assert!(take(42).is_none());
        cancel(41);
        assert!(!state().lock().unwrap().cancel.load(Ordering::Relaxed));
        cancel(42);
        assert!(state().lock().unwrap().cancel.load(Ordering::Relaxed));
        publish(42, json!({"phase":"cancelled"}), None, true);
        assert!(!state().lock().unwrap().active);
        let previous = status(42);
        assert!(restore(b"malformed").contains("restore_error"));
        assert_eq!(status(42), previous);
        let mut fixture = Session::new(false, false, false).unwrap();
        fixture.secret_key = Some(vec![7; 32]);
        fixture.set_zpw_sek("synthetic-token".into());
        fixture.login = Some(ctdozlite_auth::session::LoginInfo {
            uin: "10001".into(),
            ..Default::default()
        });
        assert!(restore(&fixture.serialize_for_secure_storage().unwrap())
            .contains("restored_unverified"));
        let (generation, _) = snapshot().unwrap();
        assert!(is_current(generation));
        assert!(!is_current(42));
        cancel(generation);
        assert!(!is_current(generation));
        assert!(snapshot().is_none());
    }
}
