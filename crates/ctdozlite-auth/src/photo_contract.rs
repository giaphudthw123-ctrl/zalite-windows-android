//! Synthetic loopback uploads/dispatches; no live account or real Zalo request.
use crate::{
    crypto,
    photo_transport::{self, PhotoInput, PhotoPolicy},
    session::{LoginInfo, Session},
};
use serde_json::{json, Value};
use std::{
    io::{Read, Write},
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    time::{Duration, Instant},
};

struct Request {
    path: String,
    params: Value,
    body: Vec<u8>,
    headers: String,
}
struct Fixture {
    host: String,
    stop: Arc<AtomicBool>,
    worker: Option<std::thread::JoinHandle<Vec<Request>>>,
}
impl Fixture {
    fn start(responses: Vec<String>) -> Self {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        listener.set_nonblocking(true).unwrap();
        let host = format!("http://{}", listener.local_addr().unwrap());
        let stop = Arc::new(AtomicBool::new(false));
        let done = stop.clone();
        let worker = std::thread::spawn(move || {
            let deadline = Instant::now() + Duration::from_secs(10);
            let mut requests = Vec::new();
            while !done.load(Ordering::Acquire) && Instant::now() < deadline {
                let (mut stream, _) = match listener.accept() {
                    Ok(value) => value,
                    Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                        std::thread::sleep(Duration::from_millis(3));
                        continue;
                    }
                    Err(error) => panic!("{error}"),
                };
                stream.set_nonblocking(false).unwrap();
                stream
                    .set_read_timeout(Some(Duration::from_secs(2)))
                    .unwrap();
                let mut header = Vec::new();
                while !header.ends_with(b"\r\n\r\n") {
                    let mut byte = [0];
                    stream.read_exact(&mut byte).unwrap();
                    header.push(byte[0]);
                    assert!(header.len() < 32768);
                }
                let headers = String::from_utf8(header).unwrap();
                let size: usize = headers
                    .lines()
                    .find_map(|line| {
                        let (k, v) = line.split_once(':')?;
                        k.eq_ignore_ascii_case("content-length")
                            .then(|| v.trim().parse().unwrap())
                    })
                    .unwrap();
                assert!(size < 2 * 1024 * 1024);
                let mut body = vec![0; size];
                stream.read_exact(&mut body).unwrap();
                let path = headers
                    .lines()
                    .next()
                    .unwrap()
                    .split_whitespace()
                    .nth(1)
                    .unwrap()
                    .to_string();
                let query = if path.contains("params=") {
                    path.split_once('?').unwrap().1.to_string()
                } else {
                    String::from_utf8(body.clone()).unwrap()
                };
                let query =
                    reqwest::Url::parse(&format!("http://fixture.invalid/?{query}")).unwrap();
                let encoded = query
                    .query_pairs()
                    .find(|(key, _)| key == "params")
                    .unwrap()
                    .1
                    .into_owned();
                let params =
                    serde_json::from_str(&crypto::decode_aes(&[7; 32], &encoded).unwrap()).unwrap();
                requests.push(Request {
                    path,
                    params,
                    body,
                    headers,
                });
                let response = responses
                    .get(requests.len() - 1)
                    .map(String::as_str)
                    .unwrap_or(r#"{"error_code":114}"#);
                write!(stream,"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: {}\r\n\r\n{}",response.len(),response).unwrap();
            }
            requests
        });
        Self {
            host,
            stop,
            worker: Some(worker),
        }
    }
    fn session(&self) -> Session {
        let mut session = Session::new(false, false, false).unwrap();
        session.login = Some(LoginInfo::default());
        session.secret_key = Some(vec![7; 32]);
        session.imei = "synthetic-device".into();
        session.zpw_sek = Some("synthetic-cookie".into());
        session.domains.file = self.host.clone();
        session
    }
    fn finish(mut self) -> Vec<Request> {
        self.stop.store(true, Ordering::Release);
        self.worker.take().unwrap().join().unwrap()
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Release);
        if let Some(worker) = self.worker.take() {
            worker.join().unwrap();
        }
    }
}
fn uploaded() -> Value {
    json!({"error_code":0,"data":{"photoId":"77","normalUrl":"https://photo.zadn.vn/normal.gif","hdUrl":"https://photo.zadn.vn/hd.gif","oriUrl":"https://photo.zadn.vn/original.gif","thumbUrl":"https://photo.zadn.vn/thumb.gif"}})
}
fn input(bytes: &[u8], original: bool) -> PhotoInput<'_> {
    PhotoInput {
        bytes,
        file_name: "fixture.gif",
        mime: "image/gif",
        caption: "Xin chào 😀",
        width: 48,
        height: 48,
        original,
    }
}

#[tokio::test]
async fn multipart_chunks_and_encrypted_dispatch_preserve_direct_and_group_wire_shapes() {
    for group in [false, true] {
        let fixture = Fixture::start(vec![
            json!({"error_code":0,"data":{}}).to_string(),
            uploaded().to_string(),
            json!({"error_code":0,"data":{"msgId":"synthetic-server-id"}}).to_string(),
        ]);
        let session = fixture.session();
        let bytes = vec![0x5a; 1_048_579];
        let receipt = photo_transport::send_photo(
            &session,
            if group { "group_300" } else { "200" },
            group,
            &input(&bytes, group),
            123,
            PhotoPolicy::Companion,
        )
        .await
        .unwrap();
        assert_eq!(receipt.response["data"]["msgId"], "synthetic-server-id");
        let requests = fixture.finish();
        assert_eq!(requests.len(), 3);
        for (index, request) in requests[..2].iter().enumerate() {
            assert!(request.path.starts_with(if group {
                "/api/group/photo_original/upload?"
            } else {
                "/api/message/photo_original/upload?"
            }));
            assert!(request
                .path
                .ends_with(if group { "&type=11" } else { "&type=2" }));
            let mut expected = json!({"totalChunk":2,"fileName":"fixture.gif","clientId":123,"totalSize":1048579,"imei":"synthetic-device","isE2EE":0,"jxl":0,"chunkId":index+1});
            expected[if group { "grid" } else { "toid" }] =
                json!(if group { "300" } else { "200" });
            assert_eq!(request.params, expected);
            assert!(request.headers.contains("synthetic-cookie"));
            let body = &request.body;
            let content = body
                .windows(4)
                .position(|window| window == b"\r\n\r\n")
                .unwrap()
                + 4;
            let count = if index == 0 { 1_048_576 } else { 3 };
            assert_eq!(
                &body[content..content + count],
                &bytes[index * 1_048_576..index * 1_048_576 + count]
            );
            let head = String::from_utf8_lossy(&body[..content]);
            assert!(head.contains("name=\"chunkContent\"; filename=\"fixture.gif\""));
            assert!(head.contains("Content-Type: image/gif"));
        }
        let request = &requests[2];
        assert!(request.path.starts_with(if group {
            "/api/group/photo_original/send?"
        } else {
            "/api/message/photo_original/send?"
        }));
        let mut expected = json!({"photoId":77,"clientId":123,"desc":"Xin chào 😀","width":48,"height":48,"rawUrl":"https://photo.zadn.vn/normal.gif","hdUrl":"https://photo.zadn.vn/hd.gif","thumbUrl":"https://photo.zadn.vn/thumb.gif","oriUrl":if group{"https://photo.zadn.vn/original.gif"}else{"https://photo.zadn.vn/normal.gif"},"normalUrl":"https://photo.zadn.vn/normal.gif","hdSize":if group{1048579}else{0},"isHD":if group{1}else{0},"hd":if group{1}else{0},"isOriginal":if group{1}else{0},"zsource":-1,"jcp":"{\"convertible\":true}","imei":"synthetic-device"});
        expected[if group { "grid" } else { "toid" }] = json!(if group { "300" } else { "200" });
        if group {
            expected["visibility"] = json!(0);
        }
        assert_eq!(request.params, expected);
    }
}

#[tokio::test]
async fn upload_rejections_or_malformed_envelopes_never_fall_back_or_dispatch() {
    for policy in [PhotoPolicy::Companion, PhotoPolicy::DesktopCompatibility] {
        for response in [
            r#"{"error_code":114}"#,
            "{}",
            r#"{"error_code":"0"}"#,
            "",
            r#"{"error_code":0,"data":null}"#,
        ] {
            let fixture = Fixture::start(vec![response.into()]);
            let result = photo_transport::send_photo(
                &fixture.session(),
                "200",
                false,
                &input(&[1, 2, 3], true),
                124,
                policy,
            )
            .await;
            assert!(result.is_err());
            assert_eq!(fixture.finish().len(), 1);
        }
    }
}

#[tokio::test]
async fn only_explicit_desktop_compatibility_can_use_single_shot_after_successful_upload() {
    for policy in [PhotoPolicy::Companion, PhotoPolicy::DesktopCompatibility] {
        let fixture = Fixture::start(vec![
            json!({"error_code":0,"data":{}}).to_string(),
            json!({"error_code":0,"data":{"msgId":"fallback-id"}}).to_string(),
        ]);
        let result = photo_transport::send_photo(
            &fixture.session(),
            "group_300",
            true,
            &input(&[1, 2, 3], true),
            125,
            policy,
        )
        .await;
        let requests = fixture.finish();
        if policy == PhotoPolicy::Companion {
            assert!(result.is_err());
            assert_eq!(requests.len(), 1);
        } else {
            assert!(result.is_ok());
            assert_eq!(requests.len(), 2);
            assert!(requests[1].path.starts_with("/api/group/sendpt?"));
            assert_eq!(
                requests[1].params,
                json!({"clientId":125,"desc":"Xin chào 😀","width":48,"height":48,"imei":"synthetic-device","grid":"300","visibility":0})
            );
            assert!(String::from_utf8_lossy(&requests[1].body).contains("name=\"fileContent\""));
        }
    }
}

#[tokio::test]
async fn unsafe_upload_urls_and_ambiguous_dispatch_are_never_retried() {
    for url in [
        "https://evil.example/a.gif",
        "http://photo.zadn.vn/a.gif",
        "file:///private.gif",
    ] {
        let mut response = uploaded();
        response["data"]["normalUrl"] = json!(url);
        let fixture = Fixture::start(vec![response.to_string()]);
        assert!(photo_transport::send_photo(
            &fixture.session(),
            "200",
            false,
            &input(&[1], true),
            126,
            PhotoPolicy::Companion
        )
        .await
        .is_err());
        assert_eq!(fixture.finish().len(), 1);
    }
    for response in [
        "{}",
        r#"{"error_code":0,"data":null}"#,
        r#"{"error_code":0,"data":{"msgId":0}}"#,
        r#"{"error_code":114}"#,
    ] {
        let fixture = Fixture::start(vec![uploaded().to_string(), response.into()]);
        assert!(photo_transport::send_photo(
            &fixture.session(),
            "200",
            false,
            &input(&[1], true),
            127,
            PhotoPolicy::Companion
        )
        .await
        .is_err());
        assert_eq!(fixture.finish().len(), 2);
    }
}

#[tokio::test]
async fn changed_scope_stops_before_upload_next_chunk_or_dispatch() {
    for bytes in [vec![1], vec![1; 1_048_579]] {
        let fixture = Fixture::start(vec![uploaded().to_string()]);
        let calls = std::sync::atomic::AtomicUsize::new(0);
        let result = photo_transport::send_photo_if_current(
            &fixture.session(),
            "200",
            false,
            &input(&bytes, true),
            128,
            PhotoPolicy::Companion,
            || calls.fetch_add(1, Ordering::Relaxed) == 0,
        )
        .await;
        assert!(result.is_err());
        assert_eq!(fixture.finish().len(), 1);
    }
    let fixture = Fixture::start(vec![]);
    assert!(photo_transport::send_photo_if_current(
        &fixture.session(),
        "200",
        false,
        &input(&[1], true),
        129,
        PhotoPolicy::Companion,
        || false
    )
    .await
    .is_err());
    assert!(fixture.finish().is_empty());
}

#[tokio::test]
async fn encrypted_response_envelopes_preserve_success_and_inner_rejection() {
    let seal = |value: Value| {
        json!({"error_code":0,"data":crypto::encode_aes(&[7;32],&value.to_string()).unwrap()})
            .to_string()
    };
    let fixture = Fixture::start(vec![
        seal(uploaded()),
        seal(json!({"error_code":0,"data":{"msgId":"encrypted-fixture-id"}})),
    ]);
    let receipt = photo_transport::send_photo(
        &fixture.session(),
        "200",
        false,
        &input(&[1], true),
        130,
        PhotoPolicy::Companion,
    )
    .await
    .unwrap();
    assert_eq!(receipt.response["data"]["msgId"], "encrypted-fixture-id");
    assert_eq!(fixture.finish().len(), 2);
    let fixture = Fixture::start(vec![seal(json!({"error_code":114,"data":{}}))]);
    let error = photo_transport::send_photo(
        &fixture.session(),
        "200",
        false,
        &input(&[1], true),
        131,
        PhotoPolicy::Companion,
    )
    .await
    .err()
    .unwrap();
    assert_eq!(
        error
            .downcast_ref::<crate::session::SessionError>()
            .unwrap()
            .code,
        114
    );
    assert_eq!(fixture.finish().len(), 1);
}
