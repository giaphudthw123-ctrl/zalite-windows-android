//! Legacy companion-protocol device identity, shared with portable authentication.
/// Generate the existing companion device ID without a desktop call dependency.
pub fn official_imei() -> String {
    let hex = crate::param_cipher::random_hex(32);
    let variant = ['8', '9', 'a', 'b'][(hex.as_bytes()[16] as usize) % 4];
    let uuid = format!(
        "{}-{}-4{}-{}{}-{}",
        &hex[0..8],
        &hex[8..12],
        &hex[13..16],
        variant,
        &hex[17..20],
        &hex[20..32]
    );
    let ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) \
              Zalo/26.10.11 Chrome/122.0.6261.95 Electron/29.1.5 Safari/537.36";
    format!("{uuid}-{}", crate::crypto::md5_hex(ua))
}
