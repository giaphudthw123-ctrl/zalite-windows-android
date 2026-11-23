//! Narrow JNI bridge to the portable local feature rules.

use jni::objects::{JByteArray, JObject, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;
mod auth;

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeRecentConversations(
    env: JNIEnv,
    _this: JObject,
) -> jstring {
    let result = if let Some((generation, read_generation, session)) = auth::snapshot() {
        match tokio::runtime::Runtime::new() {
            Ok(runtime)=>runtime.block_on(async {
                let cancelled=async { while auth::is_current(generation,read_generation){tokio::time::sleep(std::time::Duration::from_millis(100)).await;} };
                let request=async {
                    let user=session.login.as_ref().ok_or_else(||anyhow::anyhow!("missing identity"))?.uin.clone();
                    let response=ctdozlite_auth::conversation_transport::get_last_msgs(&session,&[]).await?;
                    Ok::<_,anyhow::Error>((ctdozlite_auth::recent::parse(&response,&user)?,user))
                };
                tokio::select! {
                    _=cancelled=>serde_json::json!({"phase":"cancelled"}),
                    response=tokio::time::timeout(std::time::Duration::from_secs(30),request)=>match response {
                        Ok(Ok((snapshot,user))) if auth::is_current(generation,read_generation)=>serde_json::json!({"phase":"loaded","data":snapshot,"account":user}),
                        _=>serde_json::json!({"phase":"error","message":"Không tải được tin gần đây. Hãy kiểm tra mạng hoặc phiên đăng nhập."}),
                    }
                }
            }),
            Err(_)=>serde_json::json!({"phase":"error","message":"Không tạo được tác vụ tải hội thoại."}),
        }
    } else {
        serde_json::json!({"phase":"requires_login"})
    };
    env.new_string(result.to_string())
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeAuthRestore(
    env: JNIEnv,
    _this: JObject,
    input: JByteArray,
    expected_generation: jlong,
) -> jstring {
    let result = if env
        .get_array_length(&input)
        .map_or(true, |size| size <= 0 || size > 8 * 1024 * 1024)
    {
        "{\"phase\":\"restore_error\"}".to_owned()
    } else if let Ok(mut bytes) = env.convert_byte_array(input) {
        let result = auth::restore(&bytes, expected_generation as u64);
        bytes.fill(0);
        result
    } else {
        "{\"phase\":\"restore_error\"}".to_owned()
    };
    env.new_string(result)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeAuthRestoreRevision(
    _env: JNIEnv,
    _this: JObject,
) -> jlong {
    auth::restore_revision()
        .map(|revision| revision as jlong)
        .unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeAuthLocalState(
    env: JNIEnv,
    _this: JObject,
) -> jstring {
    env.new_string(auth::local_state())
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeAuthIsSession(
    _env: JNIEnv,
    _this: JObject,
    generation: jlong,
) -> jni::sys::jboolean {
    u8::from(auth::is_session(generation as u64))
}

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeRecentCancel(
    _env: JNIEnv,
    _this: JObject,
) {
    auth::cancel_reads();
}

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeAuthStart(
    _env: JNIEnv,
    _this: JObject,
) -> jlong {
    auth::start() as jlong
}
#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeAuthCancel(
    _env: JNIEnv,
    _this: JObject,
    generation: jlong,
) {
    auth::cancel(generation as u64);
}
#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeAuthStatus(
    env: JNIEnv,
    _this: JObject,
    generation: jlong,
) -> jstring {
    env.new_string(auth::status(generation as u64))
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeAuthTake(
    env: JNIEnv,
    _this: JObject,
    generation: jlong,
) -> jni::sys::jbyteArray {
    let Some(mut bytes) = auth::take(generation as u64) else {
        return std::ptr::null_mut();
    };
    let result = env
        .byte_array_from_slice(&bytes)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut());
    bytes.fill(0);
    result
}

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeExpandTemplate(
    mut env: JNIEnv,
    _receiver: JObject,
    input: JString,
    recipient: JString,
    time: JString,
    date: JString,
) -> jstring {
    let mut values = Vec::new();
    for value in [&input, &recipient, &time, &date] {
        let Ok(text) = env.get_string(value) else {
            return std::ptr::null_mut();
        };
        let text = text.to_string_lossy().into_owned();
        if text.len() > 4096 {
            return std::ptr::null_mut();
        }
        values.push(text);
    }
    let expanded =
        ctdozlite_core::expand_local_variables(&values[0], &values[1], &values[2], &values[3]);
    env.new_string(expanded)
        .map(|text| text.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeGifDimensions(
    env: JNIEnv,
    _receiver: JObject,
    bytes: JByteArray,
) -> jlong {
    let Ok(bytes) = env.convert_byte_array(bytes) else {
        return 0;
    };
    if bytes.len() as u64 > ctdozlite_core::MAX_GIF_BYTES {
        return 0;
    }
    ctdozlite_core::gif_dimensions(&bytes)
        .map(|size| ((size.width as jlong) << 32) | size.height as jlong)
        .unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_org_ctdozlite_mobile_MainActivity_nativeNormalizeTemplate(
    mut env: JNIEnv,
    _receiver: JObject,
    input: JString,
) -> jstring {
    let Ok(input) = env.get_string(&input) else {
        return std::ptr::null_mut();
    };
    let Some(normalized) = ctdozlite_core::normalize_template(&input.to_string_lossy()) else {
        return std::ptr::null_mut();
    };
    env.new_string(normalized)
        .map(|text| text.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
