# Kế hoạch Windows → Android (13/11/2026)

## Nguyên tắc đo và cổng phát hành

Mỗi mốc có một báo cáo trong `docs/verification/` ghi commit nguồn, phiên bản công cụ, Windows/Android và phần cứng, bộ dữ liệu giả lập, lệnh chạy, kết quả, lỗi đã sửa và việc chưa kiểm chứng. Không dùng tài khoản Zalo thật trong kiểm thử. Chỉ dùng tài khoản thử nghiệm sau khi các cổng bảo mật tĩnh, build, test và kiểm tra mạng đã đạt. Không phát hành bộ cài hoặc APK chỉ vì build thành công.

Đo cùng một máy, cùng chế độ, cùng khoảng thời gian và cùng dữ liệu cho bản gốc, fork và bản sửa. Báo cáo RAM dưới dạng working set và private bytes; CPU là phần trăm của một lõi và tổng thời gian CPU; đĩa gồm kích thước binary, thư mục dữ liệu và cache; mạng gồm số byte gửi/nhận trong từng kịch bản. Nếu chỉ đo được lưu lượng toàn máy, ghi rõ đó là phép đo gián tiếp, không gán cho riêng ứng dụng.

## Các mốc

| Mốc | Công việc | Cổng hoàn thành |
| --- | --- | --- |
| 0. Đường cơ sở | Build mã gốc `ctdoteam1110/ctdozlite` từ commit cố định trên Windows; chạy không đăng nhập và đo khởi động, chờ, đóng; ghi RAM/CPU/đĩa/mạng. | Có binary build từ nguồn, bản ghi phép đo tái lập được và giới hạn đo. |
| 1. Windows an toàn | Đối chiếu từng thay đổi trong bản bàn giao với fork; sửa vị trí dữ liệu và quyền truy cập, kiểm soát cookie/token và chuyển hướng, kiểm tra cập nhật, giới hạn tệp/cache. | `cargo check --locked`, test liên quan và toàn bộ test đạt; soát mã độc lập; chạy Windows với dữ liệu giả và quan sát lưu lượng. |
| 2. Tiện ích cục bộ | Thư viện GIF cá nhân nhập từ tệp đã lưu, giới hạn kích thước/định dạng, xem trước, chọn, xóa và gửi qua luồng đính kèm hiện có; mẫu trả lời nhanh có tạo/sửa/xóa/sử dụng. | Unit/integration test và thử thủ công trên Windows; không đọc tài khoản TikTok/Messenger. |
| 3. Đo tối ưu | Đo lại cùng kịch bản; tối ưu các điểm nóng được số liệu chỉ ra, gồm polling, cache và giải mã ảnh. | Bảng trước/sau với RAM, CPU, đĩa, mạng, cùng tiêu chí chất lượng chat. |
| 4. Lõi dùng chung | Tách model, lưu trữ, kiểm tra đầu vào và giao tiếp Zalo phù hợp thành thư viện Rust không phụ thuộc egui/desktop; giao diện Android riêng, cầu nối JNI và kho khóa Android. | Test lõi chạy trên Windows và Android; không tái sử dụng giao diện desktop trong APK. |
| 5. APK thử nghiệm | Build APK cho thiết bị thử, kiểm tra cài đặt, vòng đời, quyền tệp, lưu phiên, GIF và nhắn tin trên Android. | APK debug có hash, test trên máy thật/giả lập, báo cáo bảo mật và hiệu năng; chưa phân phối công khai. |

## Ranh giới tính năng

Mẫu trả lời nhanh, thư viện GIF cá nhân, tìm kiếm cục bộ, quản lý cache và các tùy chọn giao diện có thể xây dựng ở client. Hạn mức, quyền Premium, các tiện ích được Zalo cấp trên máy chủ và thanh toán do máy chủ quyết định. Ứng dụng chỉ dùng quyền máy chủ thực sự cấp; không sửa token, phản hồi thanh toán hoặc trạng thái Premium.

## Trạng thái ban đầu

Bản bàn giao là ảnh chụp mã chưa build. Fork hiện có mẫu trả lời nhanh và tìm GIF bằng `@gif`; chưa có thư viện GIF cá nhân. Chưa có kết quả đo được kiểm chứng cho bản gốc trên máy này, chưa có kiểm thử đăng nhập, chưa có APK.
