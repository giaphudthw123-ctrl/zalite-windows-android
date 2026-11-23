# ADR 0017: Android GIF composer selection and bounded file identity

- **Status:** Accepted for prototype testing
- **Date:** 2026-11-23

## Evidence

The chat GIF button opened a separate tools page. File validation occurred at import; later preview read a path without rechecking its content identity or compressed size. Imports had a count limit but no total-byte quota, and tool previews started an unrestricted thread per click. Desktop personal GIF sending uses the photo upload/dispatch branch of `src/chat.rs`, which depends on desktop message/display/cache work; it cannot be assumed usable through the Android auth crate.

## Decision

Open the GIF picker directly in the active composer. Search/favorite/page metadata without loading all images. Read one requested file on the GIF worker, verify a lowercase 64-hex identity, regular non-symlink shape, 8 MiB bound and matching SHA-256, then validate dimensions using the existing shared Rust JNI function. Decode with Android ImageDecoder, reject partial images/other MIME formats and downscale the preview to a maximum 384-pixel edge. Do not mutate the backing ByteBuffer while its animated drawable may still read it.

Use AtomicLong request generations, UI lifecycle tickets and active composer/account checks to reject late results. Separate decoded preview lifetime from metadata-only draft selection. Stop/clear previews and listeners on dismissal/navigation/background. Animate twice per explicit preview action; tapping the image replays. The dialog uses the existing secure Builder. Selected GIF metadata is bounded to 32 conversations in memory and cleared on Session identity reset; it is not durable draft storage.

Put content-addressed files through GifFiles under a shared worker-side lock. New imports are validated/decoded before publish, deduplicated without consuming quota twice, checked against 100-item/256 MiB new-write budgets including leftover file bytes, synced to a unique temporary file and atomically moved. Invalid existing files are preserved; a duplicate import must not silently overwrite corrupt contents or reset the user's label. Existing oversized libraries are not silently deleted. Recheck content when using an item rather than trusting an earlier preview.

## Limits and next transport boundary

The selected metadata is not a sent GIF. Block the send action while selected so the user cannot unknowingly send only the text portion. Port the actual desktop photo upload/send transport, request validation, receipt handling and bounded content snapshot before enabling GIF delivery. Animated rendering alone is not evidence that Zalo recipients will receive animation. No change to desktop transport or server protocol is made here.

GIF bytes/metadata remain plaintext app-local files; SHA-256 identity is not encryption or protection against a privileged writer replacing both content and index. Frame-count/total decoded-work limits, WebP/video import, folder/sticker-pack workflows, external source licensing, physical-device testing and independent review remain separate gates. See [ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder) and [AnimatedImageDrawable](https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable) for decode sizing, backing-buffer lifetime and repeat behavior.
