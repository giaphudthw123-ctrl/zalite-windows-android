# Android: Cá nhân, điều hướng tiện ích và bảo vệ màn hình — 2026-11-23

## Kết quả

Màn hình Cá nhân/Khám phá có các hàng tiện ích dễ thấy: GIF, mẫu tin, bản nháp, bảo mật và cập nhật. Mỗi mục mở đúng vùng chức năng hiện có; hiển thị repo cập nhật tương ứng flavor. Không tạo hồ sơ, nhãn Business hay quyền zBusiness chính thức.

Trang tiện ích dài trước đây không xử lý system inset và khi mở giữa trang không có nút quay lại. Thử trên giả lập phát hiện nội dung lấn thanh hệ thống; đã thêm khung inset và thanh quay lại cố định, build sạch và thử lại bản cuối. Trang quản lý GIF/mẫu vẫn dùng nhiều nút mặc định và chưa đạt yêu cầu giao diện Zalo hoàn chỉnh.

Android có công tắc hạn chế chụp màn hình dùng [FLAG_SECURE theo tài liệu Android](https://developer.android.com/security/fraud-prevention/activities). Áp dụng cửa sổ Activity và toàn bộ điểm tạo AlertDialog hiện có qua Builder chung; cả các hộp thoại được tạo trước đó được theo dõi yếu để cập nhật cờ. I/O cài đặt chạy nền. Khi khởi động bật cờ trước khi vẽ; đọc thành công mới áp dụng lựa chọn đã lưu. Lần đầu chưa có tệp mặc định tắt sau khi đọc. Tệp lỗi giữ cờ bật và không cho chỉnh cho tới khi đọc lại thành công. Bật áp dụng ngay; tắt đợi ghi AtomicFile và đọc xác minh. Xem ADR 0016.

## Kiểm tra và giới hạn bằng chứng

| Hạng mục | Đã quan sát |
| --- | --- |
| Test JVM | 20 bài mỗi flavor qua, không lỗi/bỏ qua. Ba bài mới kiểm tra trạng thái bật/tắt và từ chối frame thiếu/thừa, header/version/boolean sai. Không coi đây là kiểm thử nền tảng Android hoặc lỗi đầy đĩa. |
| Build | Hai APK debug versionCode 11 build sạch thành công. Còn cảnh báo deprecated API/Gradle và native library không strip được. Không sửa Rust/Windows, không chạy lại/nhận kết quả cũ thành test mới. |
| Điều hướng nội bộ | Cá nhân → mẫu mở đúng phần quản lý với bốn mẫu giả; quay lại luôn hiện. Cá nhân → cập nhật hiện `giaphudthw123-ctrl/zalite-windows-android`; Cá nhân → GIF hiện nhập/tìm/danh sách. Không bấm tải/cài APK. |
| Bật nội bộ | Công tắc có checked=true và báo bật sau ghi; dumpsys có SECURE; screencap cho vùng đen. |
| Hộp thoại nội bộ | Bộ chọn mẫu có dữ liệu giả; dumpsys xác nhận cả `DIM_BEHIND SECURE` ở dialog và `BASE_APPLICATION ... SECURE`. Ảnh chụp không lộ nội dung, thanh hệ thống có thể vẫn hiện. |
| Lưu bật qua restart | Force-stop app thử, mở lại; cửa sổ còn SECURE và tệp policy riêng dài 4 byte, quyền rw-------. |
| Tệp lỗi | Sao lưu riêng tệp policy không chứa bí mật, thay bằng 3 byte `bad`, mở lại: giữ SECURE, công tắc bật nhưng vô hiệu hóa, báo không đọc được. Tệp lỗi vẫn dài 3 byte, không bị ghi đè. Khôi phục bản đã sao lưu, bấm đọc lại: công tắc dùng được, vẫn bật. Không chạm Session/kho mẫu. |
| Tắt nội bộ | Sau lưu trạng thái tắt: không còn cửa sổ SECURE; ảnh UI đọc được. Restart trạng thái vẫn tắt. Bản sao thử đã được di chuyển trả lại, không còn tệp backup thử. |
| Fork cuối | Bật/tắt qua UI có trạng thái đúng và cờ SECURE khi bật; ảnh bảo vệ đen. Mục cập nhật hiện `minhkhang1008/ctdozlite-next`. Chưa thử lỗi policy/restart/dialog đầy đủ lần nữa trên fork. |

Thiết bị thử: emulator-5554 Android 16/API 36, x86_64, 320×640. Các ca lỗi policy, restart và dialog nội bộ được thử trước thay đổi bổ sung thanh quay lại/system inset; nguồn cơ chế bảo vệ không đổi sau đó. Hai APK cuối đã cài lại; điều hướng nội bộ, bật/tắt fork và nguồn cập nhật hai bản được thử trên artifact cuối. Một số lần dump ngay khi chuyển Activity trả null/chạm quá sớm không đổi trang; đã kiểm tra lại UI thực, không dùng thao tác chưa thấy kết quả làm bằng chứng.

Chỉ hạn chế đường chụp/chia sẻ màn hình mà Android hỗ trợ. Chưa thử MediaProjection quay màn hình, casting, recent-app thumbnail trên máy thật, mọi loại dialog và mọi Android/OEM. Không ngăn máy ảnh, thiết bị can thiệp, cửa sổ bàn phím/picker khác, đọc cây trợ năng, người nhận lưu/chuyển tiếp hay client khác. Không thực hiện PIN lock, mã hóa toàn bộ lịch sử, sao lưu chuyển máy hoặc E2EE.

## Ảnh dữ liệu giả

- [Cá nhân](assets/android-v11-personal.png), [các mục Cài đặt](assets/android-v11-personal-settings.png).
- [Cài đặt bảo mật khi tắt](assets/android-v11-privacy-off.png), [ảnh khi bật](assets/android-v11-privacy-protected.png).
- [Dialog được bảo vệ](assets/android-v11-dialog-protected.png), [fork được bảo vệ](assets/android-v11-fork-protected.png).
- [Đích mẫu tin sau sửa inset/back](assets/android-v11-template-destination.png), [kênh nội bộ](assets/android-v11-update-channel.png), [kênh fork](assets/android-v11-fork-channel.png).

Đã xem trực tiếp chín PNG; không có tài khoản, cookie, QR hay tin Zalo thật. Ảnh Cá nhân/bảo mật nội bộ chụp trước bước sửa khung trang tiện ích; ảnh đích tiện ích và fork dùng APK cuối.

## Artifact cuối và tài nguyên

| Artifact debug, chưa phát hành | Byte | SHA-256 |
| --- | ---: | --- |
| `android/app/build/outputs/apk/fork/debug/app-fork-debug.apk` | 14843957 | `9D97462B2A0C21FB22996A83F59B98E91C55441305AA38A1E7ACE1337F0BC245` |
| `android/app/build/outputs/apk/internal/debug/app-internal-debug.apk` | 14843965 | `4E1F6B2B40523912E2B75239C83B1254A68C5DFECB1324A768CB44F6AF26D0F2` |

[Số đo gốc](metrics/android-v11-personal-privacy.json): PSS 44.581 KiB/RSS 173.184 KiB khi ở Cá nhân sau thử tiện ích; files/cache/no_backup lần lượt 24/8/24 KiB với một GIF giả nhỏ và bốn mẫu. CPU nền: 656 → 656 ticks ở 100 Hz trong 20,166 giây, tương ứng 0% quan sát trong cửa sổ này. Không suy ra luôn không dùng CPU; chưa có baseline Zalo đối xứng, byte mạng, dung lượng cài thực hay dữ liệu tài khoản thật. APK là nhiều ABI và debug, không phải kích thước release tối ưu.

Mục tiêu tổng thể còn đang thực hiện: UI còn phần sơ bộ, GIF gửi trong chat, thư viện mẫu lớn/nhập xuất/sao lưu, kiểm chứng máy chủ, review độc lập, thiết bị thật và cổng phát hành vẫn mở. Không đăng nhập/gửi tin bằng tài khoản thật; không phát hành APK hay bộ cài.
