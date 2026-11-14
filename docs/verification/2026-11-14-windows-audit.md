# Kiểm tra ban đầu bản Windows — 14/11/2026

> Bảng kiểm tra này lập trên bản v0.2.1. Kết quả build/test/hiệu năng mới nhất sau khi tích hợp fork v0.3.2 nằm tại [báo cáo mới](2026-11-14-latest-validation.md). Các cổng bảo mật còn mở vẫn phải được kiểm tra lại trước khi dùng tài khoản thật hoặc phân phối.

## Nguồn và môi trường

- Bản làm việc: ZIP bàn giao ngày 13/11/2026, sao chép vào workspace; không có `.git` trong ZIP.
- Fork đối chiếu: `minhkhang1008/ctdozlite-next` tại `cb2cd66b72879e3d9382b3c98ce7a1dd2a290399`.
- Dự án gốc đối chiếu: `ctdoteam1110/ctdozlite` tại `7ed340813e398f5fa32f4b9a3f9c168fdac472c4`.
- Windows x86_64 GNU, Rust 1.100.0, Cargo 1.100.0, CMake 3.31.12, MSYS2 UCRT64 GCC. Toolchain và CMake ở workspace. Rustup và CMake được tải từ nguồn chính thức; SHA-256 của mỗi gói khớp giá trị công bố cùng nguồn.
- Chưa dùng tài khoản Zalo thật và chưa chạy bản cài tải về.

## Audit từng thay đổi trong bản bàn giao

| Thay đổi thử nghiệm | Kết quả soát mã | Hành động |
| --- | --- | --- |
| Tắt kiểm tra cập nhật tự động khi mở app | `#[cfg(not(target_os = "windows"))]` loại lệnh lúc khởi động trên Windows. | Giữ. Chưa thử thủ công GUI. |
| Tắt kiểm tra cập nhật định kỳ | Cùng điều kiện biên dịch loại timer 30 phút trên Windows. | Giữ. Chưa thử thủ công GUI. |
| Chỉ tải từ release của fork và có SHA-256 | Kiểm tra cũ dùng `starts_with` và hash từ metadata release; chưa đối chiếu tag, tên tệp, query và nguồn SHA256SUMS. Hash cùng nguồn chỉ xác nhận toàn vẹn theo metadata, không phải chữ ký độc lập. | Thêm bộ kiểm tra URL chính xác cho asset và SHA256SUMS; giữ cổng tải thủ công. Cần xác minh provenance bằng attestation/chữ ký trước phân phối. |
| Không tự chạy bộ cài Windows | Nhánh Windows trả về sau khi lưu tệp, nên không vào lệnh chạy installer bên dưới. | Giữ. Tệp vẫn được tải vào RAM tối đa 512 MiB trước khi lưu; cần giảm đỉnh RAM và kiểm tra trùng tên tệp. |
| Chuyển dữ liệu mặc định sang AppData | `initialize` chọn `%APPDATA%\ctdozlite-next`, không di chuyển dữ liệu cũ. `--profile` cũ vẫn rơi vào `cwd\profiles`, trái mục tiêu bảo vệ phiên. | Sửa `--profile` vào `%APPDATA%\ctdozlite-next\profiles`. `--data-dir` vẫn là đường dẫn người dùng chọn. |

## Phát hiện thêm khi soát mã

- Client HTTP của phiên có header giao thức `zpw_sek`. Reqwest chỉ tự loại các header nhạy cảm chuẩn khi đổi host; header riêng này có thể đi theo redirect. Đã tắt redirect ở client chứa phiên và chặn URL không thuộc danh sách Zalo trước khi gửi.
- File phiên và chat trên Windows hiện dựa vào ACL kế thừa của AppData; chưa có thử nghiệm giữa hai tài khoản Windows và chưa có mã hóa dữ liệu khi lưu. Đây là cổng bảo mật còn mở.
- Đã ẩn query, nội dung request/response trong logger, lỗi panic, phản hồi QR và phần lớn trace cuộc gọi; chế độ `--dump`/`--dump-raw` bị từ chối trong binary chính. Chưa chứng minh mọi nhánh debug và binary chẩn đoán phụ đều không rò dữ liệu.
- Đã giới hạn tệp đính kèm 64 MiB và không lưu bản đầy đủ của ảnh/GIF đã gửi vào thumbnail cache. Cache khác của repo vẫn chưa có hạn mức tổng.
- Thư viện GIF cá nhân đang được thêm với giới hạn 8 MiB/tệp, 100 tệp và 256 MiB; giao diện xem trước là khung đầu, luồng gửi dùng upload ảnh/đính kèm hiện có. Chưa xác nhận máy chủ Zalo chấp nhận GIF tự nhập.

## Bằng chứng kiểm tra đến thời điểm viết

- `cargo check --locked --offline --bin ctdozlite` đạt sau khi thêm CMake.
- Lần đầu `cargo test --locked --offline --lib`: 272 đạt, 1 thất bại do mock server loopback bị cổng domain mới chặn, 2 ignored. Ngoại lệ chỉ trong unit test; lần sau 275 đạt, 0 thất bại, 2 ignored. Một số sửa đổi log và domain được thêm sau lần này, cần chạy lại.
- `cargo test --locked --offline --test data_directory_cli`: 4 đạt, 0 thất bại, sau khi sửa kỳ vọng dấu phân cách đường dẫn Windows.
- `cargo build --locked --offline --bin ctdozlite` đạt. `cargo clippy --locked --offline --bin ctdozlite` đạt với 112 cảnh báo, phần lớn từ mã có sẵn; `-D warnings` dừng ở cảnh báo build.rs có sẵn. Nhật ký: `clippy-windows-2026-11-14.log`.
- Chạy GUI không đăng nhập 30 giây, binary **debug** cùng máy: bản gốc 170,9 MiB EXE; đỉnh working set 102,3 MiB, private bytes 40,9 MiB, CPU 45,9% một lõi, dữ liệu 144 KiB. Bản Windows 144,8 MiB EXE; đỉnh working set 120,1 MiB, private bytes 44,9 MiB, CPU 24,15% một lõi, dữ liệu 36 KiB. Số liệu thô: `../../../baseline/original-debug.json` và `../../../baseline/candidate-debug.json`. Bản Windows **chưa đạt** mục tiêu RAM so với bản gốc ở phép đo này. Các lần chạy theo thứ tự, chưa kiểm soát nhiệt độ/tải nền; CPU là tỷ lệ thời gian của một lõi. Bộ đếm mạng không có quyền truy cập, do đó chưa có số liệu byte mạng của app.
- Đã chạy binary Windows qua desktop và quan sát màn hình đăng nhập QR của `ctdozlite-next` v0.2.1 bằng công cụ chụp cửa sổ; sau đó đóng app. Không quét QR hoặc đăng nhập. Chưa thể thử luồng chat/GIF/mẫu tin sau đăng nhập, nên không giữ/chia sẻ ảnh QR có thể dùng đăng nhập.
- Chưa có `cargo audit`, kiểm tra Windows Defender, soát mã độc lập từ người khác, kiểm thử trên Android hoặc APK.

## Cổng còn mở

Không dùng tài khoản thật hoặc phân phối binary/APK trước khi kiểm tra ACL, log, toàn bộ đường gửi token, nguồn bản cập nhật, `cargo audit`, thử GUI trên Windows với dữ liệu giả, và thử trên Android. Các số liệu hiệu năng hiện chưa đo nên không đưa ra tuyên bố tiết kiệm tài nguyên.
