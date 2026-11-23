# Shared text-send extraction — 2026-11-23

Before this change, `chat::send_message_with_client_id` and `send_group_message_with_client_id` built their own Session-encrypted POSTs. Shared transport retains the original `toid`/`grid`, `message`, `clientId`, `imei`, optional string `textProperties`, group `visibility:0`, common params and `maybe_decrypt` path. The fallback URL is the unchanged existing desktop value, now named in endpoints.

No new authenticated capture was collected. Synthetic fixture data checks exact decrypted payloads for both paths and reuses the existing request-ID fallback regression on Windows. Fixture `error_code:0` is not a claim that Zalo accepted a live Android request. A numeric zero is represented as a transport acknowledgement, not delivered/read evidence. Mobile has no automatic POST retry; unknown outcomes remain uncertain.

Mobile additionally requires a server-message identity because Session synthesizes `{"error_code":0,"data":null}` for empty keepalive bodies. A regression first failed with Accepted instead of Unknown; the stricter classifier now passes. This does not alter the extracted desktop request protocol or establish authenticated Android interoperability.
