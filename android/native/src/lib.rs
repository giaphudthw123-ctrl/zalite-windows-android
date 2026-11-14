//! Narrow JNI bridge to the portable local feature rules.

use jni::objects::{JByteArray, JObject, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;

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
