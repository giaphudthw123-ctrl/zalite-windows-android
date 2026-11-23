//! Synthetic loopback contract, not an authenticated Zalo capture.
use crate::{
    crypto,
    session::{LoginInfo, Session},
    text_transport,
};
use serde_json::{json, Value};
use std::io::{Read, Write};
use std::time::{Duration, Instant};

#[tokio::test]
async fn encrypted_text_posts_preserve_target_properties_and_ids_and_report_errors() {
    let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
    listener.set_nonblocking(true).unwrap();
    let host = format!("http://{}", listener.local_addr().unwrap());
    let fixture = std::thread::spawn(move || {
        let deadline = Instant::now() + Duration::from_secs(15);
        let mut requests = Vec::new();
        while requests.len() < 4 {
            let (mut stream, _) = match listener.accept() {
                Ok(value) => value,
                Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                    assert!(Instant::now() < deadline, "fixture request deadline");
                    std::thread::sleep(Duration::from_millis(10));
                    continue;
                }
                Err(error) => panic!("fixture accept: {error}"),
            };
            stream
                .set_read_timeout(Some(Duration::from_secs(3)))
                .unwrap();
            let mut headers = Vec::new();
            while !headers.ends_with(b"\r\n\r\n") {
                let mut byte = [0];
                stream.read_exact(&mut byte).unwrap();
                headers.push(byte[0]);
                assert!(headers.len() < 16384);
            }
            let headers = String::from_utf8(headers).unwrap();
            let size: usize = headers
                .lines()
                .find_map(|line| {
                    let (name, value) = line.split_once(':')?;
                    name.eq_ignore_ascii_case("content-length")
                        .then(|| value.trim().parse().unwrap())
                })
                .unwrap();
            assert!(size < 32768);
            let mut form = vec![0; size];
            stream.read_exact(&mut form).unwrap();
            let query = reqwest::Url::parse(&format!(
                "http://fixture.invalid/?{}",
                String::from_utf8(form).unwrap()
            ))
            .unwrap();
            let pairs: Vec<_> = query.query_pairs().collect();
            assert_eq!(pairs.len(), 1);
            assert_eq!(pairs[0].0, "params");
            let body: Value =
                serde_json::from_str(&crypto::decode_aes(&[7; 32], &pairs[0].1).unwrap()).unwrap();
            let path = headers
                .lines()
                .next()
                .unwrap()
                .split_whitespace()
                .nth(1)
                .unwrap()
                .to_string();
            requests.push((path, body));
            let response = match requests.len() {
                1 | 2 => r#"{"error_code":0,"data":{"msgId":"synthetic-server-id"}}"#,
                3 => r#"{"error_code":114,"error_message":"fixture rejected"}"#,
                _ => "{}",
            };
            write!(stream,"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: {}\r\n\r\n{}",response.len(),response).unwrap();
        }
        requests
    });
    let mut session = Session::new(false, false, false).unwrap();
    session.login = Some(LoginInfo::default());
    session.secret_key = Some(vec![7; 32]);
    session.imei = "synthetic-device".into();
    session.domains.chat = host.clone();
    session.domains.group = host;
    let text = "**literal**\nXin chào 😀";
    let direct = text_transport::send_text(&session, "200", false, text, None, 101)
        .await
        .unwrap();
    assert_eq!(direct["error_code"], 0);
    let props = r#"{"styles":[],"ver":0}"#;
    text_transport::send_text(&session, "group_300", true, "group", Some(props), 102)
        .await
        .unwrap();
    let rejected = text_transport::send_text(&session, "200", false, "rejected", None, 103)
        .await
        .unwrap_err();
    assert_eq!(
        rejected
            .downcast_ref::<crate::session::SessionError>()
            .unwrap()
            .code,
        114
    );
    let unknown = text_transport::send_text(&session, "200", false, "unknown", None, 104)
        .await
        .unwrap();
    assert_eq!(
        crate::outgoing::response_phase(&unknown),
        crate::outgoing::Phase::Unknown
    );
    let requests = fixture.join().unwrap();
    assert!(requests[0].0.starts_with("/api/message/sms?"));
    assert!(requests[1].0.starts_with("/api/group/sendmsg?"));
    assert_eq!(
        requests[0].1,
        json!({"toid":"200","message":text,"clientId":101,"imei":"synthetic-device"})
    );
    assert_eq!(
        requests[1].1,
        json!({"grid":"300","message":"group","clientId":102,"imei":"synthetic-device","visibility":0,"textProperties":props})
    );
    assert_eq!(requests[2].1["clientId"], 103);
    assert_eq!(requests[3].1["clientId"], 104);
}
