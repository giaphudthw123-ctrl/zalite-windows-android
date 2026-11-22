//! Narrow JNI bridge to the portable local feature rules.

use jni::objects::{JByteArray, JObject, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;
mod auth;

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
