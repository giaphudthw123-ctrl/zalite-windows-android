//! Shared companion-protocol sources without desktop UI, audio, SQLite or WASM.
//! Compilation does not imply primary Android-account login compatibility.
#[path = "../../../src/conversation_transport.rs"]
pub mod conversation_transport;
#[path = "../../../src/crypto.rs"]
pub mod crypto;
#[path = "../../../src/data_dir.rs"]
pub mod data_dir;
#[path = "../../../src/endpoints.rs"]
pub mod endpoints;
#[path = "../../../src/logger.rs"]
pub mod logger;
#[path = "../../../src/login.rs"]
pub mod login;
pub mod outgoing;
#[path = "../../../src/param_cipher.rs"]
pub mod param_cipher;
#[path = "../../../src/protocol_identity.rs"]
pub mod protocol_identity;
#[path = "../../../src/qr.rs"]
pub mod qr;
pub mod recent;
#[path = "../../../src/session.rs"]
pub mod session;
#[path = "../../../src/session_storage.rs"]
pub mod session_storage;
#[cfg(test)]
mod text_contract;
#[path = "../../../src/text_transport.rs"]
pub mod text_transport;
