# Đối chiếu zBusiness và phạm vi client — 14/11/2026

Nguồn: [trang zBusiness chính thức](https://business.zbox.vn/?scroll_to=benefit-pro-package) và [mục Basic trên cùng hệ thống](https://business.zbox.vn/?tag=trial), truy cập 14/11/2026. Dấu `?` nghĩa là bảng HTML công khai không nêu con số hoặc chỉ hiển thị biểu tượng không đọc được; không suy đoán quyền. Đây là quyền Zalo bán/cấp, không phải hạn mức của ứng dụng này.

| Quyền lợi chính thức | Basic | Lite | Pro | Max |
| --- | ---: | ---: | ---: | ---: |
| Danh bạ | 2.000 | 2.500 | 5.000 | 10.000 |
| Cộng đồng sở hữu | 1 | 1 | 10 | 20 |
| Nhóm tạo/tham gia | ? | Không đổi | 2.000 | 4.000 |
| Nhãn Business hiển thị cho người khác | ? | Không | Có | Có |
| Trang giới thiệu Start page | ? | ? | ? | ? |
| Catalog chính thức | Có, hạn mức ? | 2 sản phẩm | 100 sản phẩm | **200 sản phẩm** |
| Tin nhắn nhanh chính thức | 3 | 3 | 50 | 100 |
| Tin trả lời tự động chính thức | 1 | 1 | 20 | 40 |
| Gửi đồng loạt trên Zalo PC | ? | ? | 50 người | 50 người |
| Tin tự xóa | ? | 1 ngày | 1 ngày | 1 giờ |
| Chặn chia sẻ thông tin trò chuyện | ? | ? | ? | ? |
| zCloud, zStyle, hỗ trợ ưu tiên | ? | ? | ? | ? |

Trang chính thức còn nêu shortlink qua số điện thoại ở Lite/Pro/Max, nhận tin/cuộc gọi từ người lạ, mở nhiều cửa sổ trên Zalo PC, thông báo từ người lạ, nhãn và trang giới thiệu, nhưng bảng văn bản không đủ để xác nhận mọi ô theo gói. Các mục đó cần kiểm tra trực tiếp trong tài khoản có quyền, không dùng tài khoản thật trong giai đoạn này.

## Bảng triển khai trong client

| Tính năng | Trạng thái mã hiện tại | Phạm vi thực tế / điều kiện |
| --- | --- | --- |
| Chat, gọi, ghim hội thoại, nhãn hội thoại, tìm kiếm lịch sử | Đã có trong fork | Luồng mạng theo giao thức hiện có; chưa kiểm chứng bằng tài khoản thật trong bản Windows. |
| Mẫu tin nhắn nhanh cục bộ | Fork v0.3.2 có 100 mẫu, `/shortcut`, ảnh và chèn vào bản nháp; nhánh này thêm nhóm, tìm trong trình quản lý và biến `{ten}`/`{gio}`/`{ngay}`. Android có 100 mẫu tạo/sửa/xóa/tìm/chèn | Chưa có nhập/xuất, sao lưu mã hóa hoặc bước xác nhận người nhận riêng. Android chưa gửi được tin. |
| Thư viện GIF cá nhân | Windows có nhập tệp, khử trùng, danh sách, chọn/xóa và gửi qua luồng đính kèm sẵn có; Android có nhập, xem trước động, nhận tệp GIF từ Share Sheet và danh sách 24 mục đầu | Đã thử nhập GIF cục bộ trên giả lập; chưa thử gửi qua Zalo hoặc nhận trực tiếp từ TikTok/Messenger. Nhập thư mục, bộ sticker, thẻ, yêu thích và tìm kiếm còn thiếu. |
| Lõi Rust dùng chung GIF/mẫu tin | `ctdozlite-core` và cầu nối Android JNI đã build cho ARM64/x86_64 | Chỉ có kiểm tra GIF và chuẩn hóa mẫu tin cục bộ. Android chưa dùng transport hay phiên Zalo từ desktop. |
| Tự trả lời theo điều kiện | Có mã quy tắc trong `api_mcp`, nhưng REST/MCP chủ ý vô hiệu | Chưa bật trên client cá nhân; cần thiết kế công tắc, giới hạn tần suất, chống lặp, dừng khẩn và kiểm chứng API trước mọi gửi tự động. |
| Danh mục sản phẩm, hồ sơ/thẻ liên hệ, nhãn khách và nhắc việc | Nhánh Windows có danh mục sản phẩm SQLite cục bộ với thêm/sửa/tìm/xóa, nhập/xem trước ảnh PNG/JPEG/GIF, và chèn thẻ văn bản cùng ảnh vào ô soạn; fork đã có nhãn và nhắc việc cục bộ. Hồ sơ/thẻ liên hệ riêng chưa có. | Chưa kiểm thử UI sau đăng nhập hoặc gửi ảnh thật. Dữ liệu danh mục và ảnh chưa mã hóa. Không phải Catalog, Start page, shortlink hay nhãn Business chính thức. |
| Gửi nhiều người có xác nhận | Có thể thiết kế trên luồng gửi hiện có | Chưa triển khai; cần xác minh điều khoản/API, hạn mức và chống spam. Không tăng quyền gửi đồng loạt phía máy chủ. |
| Chủ đề, nền chat, bố cục riêng | Một phần giao diện đã có chủ đề | Chỉ ảnh hưởng client này; không phải zStyle của Zalo. |
| Bảo vệ cửa sổ khỏi chụp màn hình | Có công tắc Windows dùng `SetWindowDisplayAffinity`; đọc lại giá trị `0x11` trên cửa sổ thử | Chưa thử OBS/Discord/Snipping Tool; không thể ngăn máy ảnh hoặc thiết bị khác. |
| Mã hóa dữ liệu/backup cục bộ, khóa app, cache có hạn mức | Có thể xây cục bộ | Chưa hoàn thành; không phải zCloud hay mã hóa đầu cuối của Zalo. |
| Tin tự xóa, chặn chuyển tiếp ở mọi client, số danh bạ/nhóm, nhãn Business, Catalog/Start page chính thức, zCloud/zStyle hiện cho người khác | Cần quyền/API chính thức hoặc không thể cam kết | Client không được tự nhận quyền Premium hay vượt hạn mức tài khoản. |
| Hai APK Android thử nghiệm | Đã build và cài trên giả lập Android x86_64; nhập/chia sẻ GIF và mẫu tin cục bộ hoạt động | Hai package riêng, hai kênh GitHub. Chưa đăng nhập/chat; fork chưa phát hành APK, repo nội bộ chưa có APK release, chữ ký debug chưa dùng cho phát hành. |

Ưu tiên tiếp: bảo vệ phiên và dữ liệu Windows, kiểm tra bản Windows mới nhất trên giao diện, đo release và mạng riêng cho app, hoàn thiện các luồng cục bộ; chỉ sau đó cân nhắc gửi mạng dựa trên API ổn định và cổng bảo mật.
