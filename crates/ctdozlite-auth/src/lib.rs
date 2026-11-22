//! Shared companion-protocol sources without desktop UI, audio, SQLite or WASM.
//! Compilation does not imply primary Android-account login compatibility.
#[path = "../../../src/crypto.rs"]
pub mod crypto;
#[path = "../../../src/param_cipher.rs"]
pub mod param_cipher;
#[path = "../../../src/endpoints.rs"]
pub mod endpoints;
#[path = "../../../src/protocol_identity.rs"]
pub mod protocol_identity;
#[path = "../../../src/session.rs"]
pub mod session;
#[path = "../../../src/login.rs"]
pub mod login;
#[path = "../../../src/qr.rs"]
pub mod qr;
#[path = "../../../src/logger.rs"]
pub mod logger;
#[path = "../../../src/data_dir.rs"]
pub mod data_dir;
#[path = "../../../src/session_storage.rs"]
pub mod session_storage;
