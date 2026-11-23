# Lõi tải/gửi ảnh dùng chung và sửa lỗi Windows — 2026-11-23

## Kết quả thực tế

Đã tách nhánh photo upload/dispatch từ desktop sang `src/photo_transport.rs`; Windows dùng trực tiếp mô-đun này. Crate companion cũng xuất cùng nguồn, đã build lại cho Android ARM64/x86_64. Không đưa decoder, cache thumbnail hay ChatMessage desktop vào Android.

**Lỗi sửa trên Windows:** upload trả error_code 114 nhưng mã cũ vẫn thử đường gửi dự phòng. Ca hồi quy localhost đã thất bại trước sửa với **2 POST thay vì 1**; sau sửa chỉ một POST rồi trả lỗi. Mọi upload chunk phải có mã số 0 rõ ràng; thiếu/sai kiểu mã hoặc final data sai hình dạng đều dừng. Desktop chỉ còn fallback tương thích sau phản hồi upload thành công có object nhưng thiếu metadata ảnh sử dụng được.

Nhánh companion mới giới hạn 8 MiB, yêu cầu định danh upload/URL HTTPS Zalo tin cậy và ack có định danh tin nhắn; không fallback hoặc tự retry. Có predicate kiểm tra Session trước từng chunk và trước dispatch; không thể thu hồi POST đã đi. Caller phải có deadline tổng và quản lý lượt gửi.

**Android chưa nối JNI intent/xác nhận vào photo transport.** Hai APK 0.13 vẫn giữ chặn gửi khi đính GIF. Việc compile/typecheck mô-đun không chứng minh Android đã gửi được GIF; phần này còn phải triển khai, không phải quyền Premium hay hạn mức cần giả mạo. Xem ADR 0018 và [ghi chú giao thức](../../re-notes/mobile-photo-transport-2026-11-23.md).

## Kiểm thử

| Phạm vi | Kết quả cuối |
| --- | --- |
| Thư viện Windows | 386 passed, 2 ignored; ca hồi quy upload bị từ chối đã qua sau sửa. |
| Tích hợp bảo mật | 4 hàm qua đủ 1.024 tình huống đầu vào, không phải 1.024 hàm test. |
| Lõi auth/companion | 82 passed, 0 lỗi/bỏ qua, doc-tests 0. Gồm 2 unit test mô-đun ảnh và 6 contract functions. |
| Contract ảnh | 35 POST giả: chunkContent đúng byte/1 MiB; so sánh toàn bộ params đã giải mã cho direct/group; giữ client ID/người nhận/HD-original/visibility; fallback desktop rõ ràng; mã lỗi/JSON sai/body rỗng; URL không tin cậy; ack mơ hồ; đổi Session trước upload, giữa chunk và trước send; phản hồi AES thành công và lỗi bên trong envelope. |
| Android | Hai native ABI release build qua; clean build hai APK versionCode13 qua; 28 JVM test/flavor qua. Cài cả hai cuối thành công; nội bộ mở chat mẫu và xem GIF 48×48/3.551 byte từ thư viện cũ; fork cuối mở được màn hình chính. Chưa thử lại toàn bộ luồng fork/GIF trên v13. |
| Windows release | Build thành công. GUI mới mở, Responding=true, thư mục dữ liệu thử riêng, proxy process-local localhost:9; chỉ quan sát startup chưa có tài khoản, không thử gửi thật. Đã đóng đúng PID thử 8544; không đóng app người dùng. |

Lỗi trong quá trình kiểm tra được phân biệt với lỗi sản phẩm: fixture multipart đầu tiên bị allowlist chặn localhost nên không phát sinh POST; sau thêm ngoại lệ **chỉ cfg(test)** mới tái hiện đúng lỗi cũ 2 POST. Lần chạy toàn suite phát hiện socket nhận của fixture kế thừa nonblocking trên Windows, gây WouldBlock; đã đặt blocking + read timeout trong fixture và chạy lại 82 test qua. Production allowlist không cho HTTP localhost; hàm is_trusted_zalo_url không đổi. Initial build profile phải biên dịch lại nhiều dependency; thời gian build không phải CPU nền của sản phẩm.

Không thay đổi thuật toán mã hóa/signkey hay miền gửi cookie ở production. Cảnh báo unused_mut trong data_dir.rs, deprecated API/Gradle và strip native library vẫn có; không coi là đã qua mọi cổng phát hành.

## Artifact cuối

| Artifact cục bộ, chưa phát hành | Byte | SHA-256 |
| --- | ---: | --- |
| `../ctdozlite-ui-target/release/ctdozlite.exe` | 83052268 | `9988979C4A53A3DE70B4AC2DF5E82741E4297047C478CFF00A3BA169BB2B6FE4` |
| `android/app/build/outputs/apk/fork/debug/app-fork-debug.apk` | 14858965 | `3672F6289EDFEEBFC877E4B6B8FB3A8E8E91F4A7F45CF5299A908CF34E3B17A5` |
| `android/app/build/outputs/apk/internal/debug/app-internal-debug.apk` | 14858973 | `7C0F97A3474F5225349183B75133B69803E6C4BBD32C7B3CA4BAB32BE8EC57C6` |

Native ARM64: 6.087.824 byte, SHA-256 `4322D15A2BA08B5AEDC5A56ED092A895E56E6DFBBC0040A905544645AC32F998`; x86_64: 6.024.480 byte, SHA-256 `E4AEC9D8A4D732885716A35B3ADE9E7B649126CE29A71FC50B8754D0B5D4B900`. Native photo entry point chưa có; linker có thể loại mã chưa được JNI gọi.

## Quan sát giao diện và tài nguyên

- [Windows startup dữ liệu riêng](assets/windows-photo-transport-startup.png): không có QR hiển thị/tài khoản. Lần capture đầu trả ảnh cửa sổ khác nên không lưu/không dùng làm bằng chứng; sau activate đúng cửa sổ đã quan sát và chỉ lưu ảnh của tiến trình thử.
- [Picker Android v13](assets/android-v13-gif-picker-smoke.png), [preview từ kho cũ](assets/android-v13-gif-preview-smoke.png): chỉ có dữ liệu giả, không phải tin hoặc tài khoản thật.

[Số đo gốc Windows](metrics/windows-photo-transport-smoke.json): Working Set 105.381.888 byte (~100,5 MiB), Private Memory 47.378.432 byte (~45,2 MiB); CPU tăng 0,09375 giây/20,023 giây, khoảng 0,47% của một core; dữ liệu thư mục thử 77.824 byte. Đây là startup chưa đăng nhập, proxy kết nối thất bại cục bộ, không phải chat workload hay baseline Zalo đối xứng. Không có số byte mạng, cache/cài đặt tổng hoặc số Android RAM/CPU mới trong mốc này. Không tuyên bố tốt hơn Zalo từ phép đo này.

## Chưa kiểm chứng/hoàn thành

Android GIF intent/confirmation/status/UI wiring và tổng deadline; upload/ack thực tế trên máy chủ Zalo; nhận/hiển thị ảnh động ở người nhận; Session restart bằng tài khoản thật; kiểm thử giao diện gửi ảnh Windows thật; kiểm toán delivery-state desktop ngoài lỗi upload đã sửa; thiết bị ARM64 thật; review mã độc lập; baseline hiệu năng tương đương; ký release/phát hành. Không quét QR/đăng nhập/gửi từ tài khoản thật, không phát hành APK hay bộ cài. Mục tiêu đầy đủ vẫn đang thực hiện.
