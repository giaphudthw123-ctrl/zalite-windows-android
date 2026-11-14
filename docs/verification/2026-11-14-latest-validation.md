# Kiểm chứng nhánh Windows + Android — 14/11/2026

Mã nền: fork `minhkhang1008/ctdozlite-next` v0.3.2 (`af9373e`), tích hợp nhánh Windows/GIF/lõi chung trong worktree `windows-android`. Không dùng tài khoản Zalo thật. Các ảnh Android dưới đây là từ giả lập Android 36 x86_64, không phải ảnh quảng cáo.

## Chạy thật

| Luồng | Bằng chứng | Giới hạn |
| --- | --- | --- |
| Windows Rust | `cargo check --locked --offline --bin ctdozlite`, `cargo build --locked --offline --bin ctdozlite` và `cargo test --locked --offline` đều thành công trên Windows. Bộ lib: 361 đạt, 2 ignored; các suite tích hợp cũng đạt. | Chạy GUI không đăng nhập 30 giây; chưa gửi tin/GIF qua Zalo. |
| APK fork và nội bộ | Gradle 9.6/AGP 9.4: hai suite unit test và hai task assembleDebug đều thành công. Cài đồng thời hai package trên giả lập bằng `adb install -r`, mở được [fork](android-fork.png) và [nội bộ](android-internal.png). | APK debug ký bằng khóa debug cục bộ. Chưa chạy trên điện thoại thật. |
| Mẫu tin Android | Tạo mẫu, đóng/mở lại app và thấy mẫu vẫn còn: [ảnh](android-template-persist.png). Tìm theo nội dung: [ảnh](android-template-search.png). | Tối đa 100 mẫu, chưa phân nhóm/nhập xuất và chưa có luồng gửi Zalo. |
| GIF Android | Chọn tệp GIF 34 byte từ Downloads, kiểm tra qua Rust JNI, nhập và hiện trong thư viện: [ảnh](android-gif-imported.png). Gửi URI GIF vào Share Sheet có quyền đọc: [ảnh](android-shared-gif.png). Dữ liệu chia sẻ sai kiểu được báo lỗi. | GIF mẫu 1×1 không chứng minh xem trước chuyển động hoặc GIF từ TikTok/Messenger; chưa gửi qua Zalo. |
| Cập nhật từ GitHub | Fork hiển thị [chưa có APK cho kênh](android-update-fork.png); nội bộ hiển thị [chưa có release công khai](android-update-internal.png). URL release, SHA-256, package, phiên bản và chữ ký được kiểm tra trước khi trình cài Android được mở. | Chưa thử cài một bản cập nhật hợp lệ; fork chưa phát hành APK, repo nội bộ chưa có APK release. Không có cơ chế tự cài. |

## Đo bản debug Windows, 30 giây, GUI không đăng nhập

| Bản | EXE MiB | RAM đỉnh working set MiB | RAM đỉnh private MiB | CPU trung bình, % một lõi | Dữ liệu tạo KiB |
| --- | ---: | ---: | ---: | ---: | ---: |
| Dự án gốc v0.1.1 | 170,90 | 102,29 | 40,95 | 45,90 | 144 |
| Bản làm việc v0.2.1 trước tích hợp | 144,77 | 120,14 | 44,89 | 24,15 | 36 |
| Fork v0.3.2 + thay đổi Windows/Android | 160,38 | 129,40 | 54,21 | 13,44 | 60 |

Số liệu thô trong `D:\user\TIN\zalo client\baseline\*.json`. Các lượt đo chạy nối tiếp, không khóa tải nền hoặc trạng thái nhiệt. CPU tính theo thời gian một lõi. Bản mới **chưa đạt mục tiêu giảm RAM** so với dự án gốc trong phép đo debug. Bộ đếm mạng của hai lượt đầu không có quyền; lượt mới đo được 272.975 byte nhận và 120.501 byte gửi của **toàn máy**, không thể quy cho ứng dụng. Chưa có số byte mạng theo tiến trình.

Bản gốc **release**: EXE 84,98 MiB; 30 giây GUI không đăng nhập, RAM working set đỉnh 89,54 MiB, private 40,82 MiB, CPU trung bình 0,85% của một lõi, dữ liệu 144 KiB. Bản mới **release** ở mốc trước khi sửa giao diện cuối: EXE 78,84 MiB; RAM working set đỉnh 121,45 MiB, private 45,16 MiB, CPU trung bình 1,91% của một lõi, dữ liệu 60 KiB. Bản mới giảm dung lượng EXE và dữ liệu tạo, nhưng **chưa giảm RAM hay CPU nền** trong phép đo này. Số mạng 976.790 byte nhận và 503.812 byte gửi là **toàn máy**, không phải số mạng của app. Bản mới sau chỉnh sửa giao diện cần đo lại để đối chiếu chính xác.

Hai APK debug cuối có kích thước 3.568.866 byte (fork, SHA-256 `087ED58EE21E1F095D1DC2EB66210118C48EDEB339807804DC4AF833E2D81277`) và 3.568.898 byte (nội bộ, SHA-256 `3639C0FB3B33EAAF7F562C1D81BD742584132BE25208703AF990DB9AD66D46C5`). Sau khi chuyển nhập/xem trước GIF sang luồng nền, cả hai APK build và cài lại được; [ảnh Share Sheet sau build](android-shared-gif.png) hiển thị GIF đã nhập. GIF mẫu trong suốt nên vùng xem trước màu trắng.

## Cổng còn mở

- Phiên/chat SQLite trên Windows chưa mã hóa khi lưu; ACL giữa nhiều tài khoản Windows chưa kiểm chứng. Chưa có khóa app, sao lưu mã hóa, hạn mức toàn bộ cache hoặc bảo vệ chụp cửa sổ.
- SHA-256 từ cùng GitHub release chỉ chứng minh khớp metadata, không phải nguồn phát hành độc lập; bản Windows chưa có kiểm tra chữ ký/attestation đủ mạnh để phân phối. Chưa chạy `cargo audit` hay kiểm tra antivirus.
- Chưa có review mã độc lập bởi người khác. GUI Windows mới nhất mới chạy ở màn hình đăng nhập trong đo hiệu năng; chưa xác nhận mọi chức năng bằng thao tác thủ công. Ảnh QR đăng nhập không được lưu để tránh lộ phiên.
- Android chỉ có tiện ích cục bộ. Chưa có đăng nhập/chat Zalo; kênh fork chưa có APK và kênh nội bộ chưa có APK release. Chưa có khóa ký phát hành ổn định.
- Repo riêng [zalite-windows-android](https://github.com/giaphudthw123-ctrl/zalite-windows-android) đã được chủ tài khoản tạo và GitHub plugin hiện thấy quyền cài đặt. Mã nguồn chỉ được coi là đã đăng sau khi kiểm tra commit trên repo. Không đăng bản cài công khai trước khi hoàn thành kiểm tra nguồn, phiên, dữ liệu và luồng cập nhật.

## Trạng thái tính năng

| Chạy thật | Chỉ hoạt động cục bộ | Cần quyền Zalo | Chưa xong |
| --- | --- | --- | --- |
| Windows build/test; hai APK build/cài/mở; Android nhập GIF và lưu mẫu | GIF cá nhân, mẫu tin, tìm mẫu Android, nhóm và biến mẫu Windows, nhãn/nhắc việc từ fork, cập nhật thủ công báo trạng thái | Business badge, Catalog/Start page chính thức, hạn mức, zCloud/zStyle và gửi đồng loạt chính thức | Android chat; bảo vệ phiên/backup; nhóm và tìm GIF; nhập/xuất mẫu; APK release; hiệu năng RAM; thử gửi GIF thật |
