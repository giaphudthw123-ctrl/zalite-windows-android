# Shared photo transport — source/fixture evidence, 2026-11-23

This note derives from the desktop `src/chat.rs::send_attachment_ext` implementation at the previous source checkpoint. It is **not** a new authenticated Zalo capture or an official API guarantee.

The extracted flow preserves:

- Direct/group photo_original/upload paths, multipart chunkContent, 1 MiB chunks and type 2/11.
- Upload totalChunk/fileName/clientId/totalSize/imei/isE2EE=0/jxl=0/chunkId and toid/grid.
- photoId/clientFileId and normal/HD/original/thumbnail URL selection from upload metadata.
- photo_original/send form params, unchanged width/height/desc/client ID, URL fields, HD/original flags, zsource=-1, jcp and group visibility=0.
- Explicit desktop compatibility fallback to message/photo or group/sendpt with fileContent after positive object-shaped upload replies lacking photo metadata.

The deliberate safety change stops on each negative/missing/nonnumeric upload error_code and malformed final data, before fallback or send. The regression first observed two desktop POSTs after error 114; after delegation it observes one. Companion policy additionally refuses fallback, untrusted/non-HTTPS returned URLs and dispatch acknowledgements missing a nonzero message identity.

`crates/ctdozlite-auth/src/photo_contract.rs` runs six contract functions, covering 35 synthetic POSTs across successful chunked direct/group sends, explicit desktop fallback, rejected/malformed uploads, encrypted response envelopes, ambiguous dispatch and changed ownership. It verifies encoded params and multipart content; it sends no request to real Zalo and uses only synthetic credentials. `src/chat.rs` holds the desktop rejection regression. Live service acceptance and animation preservation are still open.
