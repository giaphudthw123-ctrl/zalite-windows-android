# Android: GIF trong bản nháp hội thoại và kiểm tra tệp — 2026-11-23

## Đã làm

Nút GIF trong chat mở bộ chọn ngay tại hội thoại: tìm tên/thẻ, yêu thích, phân trang, kiểm tra tệp và xem trước ảnh động. Chọn chỉ thêm metadata vào bản nháp đúng hội thoại/tài khoản; không giữ drawable cho mọi chat. Có nút bỏ GIF, giữ nguyên phần chữ. Giới hạn 32 bản nháp GIF trong RAM; không hứa phục hồi lựa chọn sau process death. Khi còn GIF được chọn, nút gửi chặn gửi để tránh gửi riêng phần chữ ngoài ý muốn.

Đây **chưa phải gửi GIF qua Zalo**. Đã đọc mã desktop: GIF cá nhân đi qua nhánh ảnh `photo_original/upload` → `photo_original/send` trong `src/chat.rs`, có xử lý message/cache riêng và fallback. Nhánh đó chưa được tách vào crate auth/JNI; không coi thiếu transport Android là quyền Premium bị khóa. Cần port transport và kiểm tra receipt/thất bại trước khi bật gửi.

Kho tệp Android mới kiểm tra ID lowercase SHA-256, tệp thường không phải symlink, giới hạn 8 MiB và hash khi đọc lại. Import đồng bộ qua executor/lock, khử trùng, hạn mức ghi mới 100 GIF/256 MiB tính cả tệp tạm cũ; ghi tệp tạm duy nhất, sync rồi atomic move. Tệp lỗi hiện có được giữ, nhập trùng không âm thầm sửa đè hay đổi tên riêng của người dùng. Thư viện cũ quá hạn mức không bị xóa tự động.

Trước decoder vẫn dùng hàm Rust JNI kiểm tra header/kích thước tối đa 4 triệu pixel. Android xác nhận MIME GIF, từ chối ảnh partial và giảm preview tối đa 384 pixel/cạnh. Không decode cả thư viện khi mở. Mã đặt hai lượt phát cho mỗi lần mở, có chạm phát lại; dừng/clear khi đổi trang, đóng picker hoặc ra nền. Các yêu cầu cũ bị loại bằng generation/UiLifecycle và kiểm tra composer/account. Xem ADR 0017 và [tài liệu decoder Android](https://developer.android.com/reference/android/graphics/ImageDecoder).

## Bằng chứng

| Kiểm tra | Kết quả |
| --- | --- |
| JVM cuối | 28 test mỗi flavor qua, 0 lỗi; 8 test mới cho tệp, 20 test cũ vẫn qua. |
| Tệp | Khử trùng không tính quota hai lần; ID/path sai bị từ chối; hash khác không đọc được và không overwrite khi nhập lại; quá dung lượng/kích thước/số lượng không làm mất tệp cũ; tệp tạm cũ tính vào quota; thư mục sai hình dạng báo lỗi. |
| Cạnh tranh | Hai GifFiles instance cùng tranh phần quota 4 byte, mỗi bên ghi 3 byte: chỉ một thành công, chỉ còn một tệp đúng, không còn temp. Test có đồng hồ chờ giới hạn. Không phải test lỗi mất điện/đầy đĩa. |
| Build | Hai APK debug versionCode 12 clean build qua; có cảnh báo deprecated/Gradle và không strip native lib. Hai test cạnh tranh/quota được bổ sung sau build rồi chạy lại cả suite; không thay nguồn APK ở bước test-only này. |
| Import thật | Tạo GIF giả hai khung màu 48×48, 3.551 byte; đưa vào Downloads rồi chọn qua ACTION_OPEN_DOCUMENT của Android trên flavor nội bộ. Kho chứa tệp hash tương ứng, quyền rw-------. Không chép thẳng fixture vào kho để thay bằng chứng import. |
| Ảnh động | Bộ chọn hiển thị 48×48/3.551 byte, nút chọn bật sau kiểm tra; hai screenshot cách nhau ~550 ms cho hai màu khác nhau sau chạm phát lại. |
| Bản nháp | Chọn ảnh động hiện tên/kích thước ở chat, hai tin mẫu không đổi. Bấm gửi bị chặn rõ chưa hỗ trợ GIF; không gọi transport tin chữ. |
| Tệp bị sửa | Sao lưu riêng GIF giả, thay bằng 3 byte `bad`; picker báo thiếu/thay đổi/không hợp lệ và nút chọn disabled. Tệp vẫn 3 byte. Khôi phục và kiểm tra SHA-256 khớp fixture. Không động Session hay kho mẫu. |
| Ra nền | Đang xem GIF 1×1 khác nhưng chưa chọn: bấm Home, xác nhận Launcher foreground, quay lại; hộp đóng và bản nháp vẫn là GIF 48×48 đã chọn trước đó. |
| Bỏ GIF trên APK cuối | Chọn GIF, nhập `giu_ban_nhap`, bỏ GIF: chữ còn nguyên, thẻ GIF biến mất và báo đã bỏ. |
| Fork cuối | Mở picker trong chat, xem GIF 1×1 cũ/34 byte, nút chọn bật; bật lọc yêu thích rỗng: 0 GIF và nút chọn disabled. Chưa lặp import ảnh động/tamper/vòng đời đầy đủ trên fork. |

Giả lập emulator-5554, Android 16/API 36, x86_64, 320×640. Phần lớn luồng picker/ra nền/tamper dùng candidate cùng v12 trước thay đổi cuối chỉ sửa câu báo khi bỏ GIF. Hai APK cuối đã cài lại; đã kiểm tra chọn/bỏ trên nội bộ và picker/favorite trên fork cuối. Một số chạm theo tọa độ cũ không mở lại picker sau khi dòng trạng thái đổi chiều cao; không coi thao tác đó là bằng chứng phục hồi UI. Hash tệp đã khôi phục được kiểm tra riêng, và picker mở lại thành công sau cài APK cuối.

## Ảnh và fixture

Tất cả chỉ có dữ liệu giả, đã xem trực tiếp tám PNG:

- [Bộ chọn](assets/android-v12-gif-picker.png), [khung xanh](assets/android-v12-gif-frame-a.png), [khung cam](assets/android-v12-gif-frame-b.png).
- [Bản nháp GIF](assets/android-v12-gif-draft.png), [tệp lỗi bị từ chối](assets/android-v12-gif-tamper.png), [trở lại từ nền](assets/android-v12-gif-background-return.png).
- [Bỏ GIF vẫn giữ chữ](assets/android-v12-gif-remove.png), [picker fork](assets/android-v12-fork-gif-picker.png).

[Fixture GIF](sample-animated-48x48.gif) tự tạo bằng hai khung màu, không lấy tài nguyên của dịch vụ khác. SHA-256 `a16acbe6759de0f4d8b6b525830cc7aeb26e01fd44fe96bc9a07af61c2496e46`.

## Artifact và số đo

| APK debug, chưa phát hành | Byte | SHA-256 |
| --- | ---: | --- |
| `android/app/build/outputs/apk/fork/debug/app-fork-debug.apk` | 14860341 | `C41648F17D9EF5A3C962E59EADB386E8AC8519DC51D4477DAFC51F097DF01FCC` |
| `android/app/build/outputs/apk/internal/debug/app-internal-debug.apk` | 14860349 | `EFB8188F3B2FE6E040BF736C6A40EE29DD657D899A912252D4BD2AFCF0D77DEE` |

[Metric gốc](metrics/android-v12-gif-composer.json): nội bộ PSS 46.724 KiB/RSS 179.396 KiB sau thao tác chọn/bỏ GIF và gõ bản nháp mẫu; files/cache/no_backup 32/8/24 KiB với hai GIF và bốn mẫu. Không có CPU/byte mạng mới, dung lượng cài đầy đủ hay baseline Zalo đối xứng; không tuyên bố tối ưu hơn Zalo từ mẫu này. Không đổi Rust/Windows nên không nhận test/build cũ thành kết quả mới.

## Phần chưa xong

Gửi GIF lên Zalo, xác nhận người nhận/receipt upload, GIF nhận hiển thị động thật, folder/WebP/video import, bộ sticker, mã hóa GIF/metadata, giới hạn toàn bộ frame/decoded-work, thử stress codec lớn và thiết bị ARM64 thật, review độc lập và cổng phát hành còn mở. SHA-256 theo ID không phải mã hóa hoặc chống người có quyền sửa cả tên/index. Preview hoạt động không chứng minh server nhận được ảnh động. Mục tiêu tổng thể vẫn đang thực hiện; không quét QR, đăng nhập/gửi tin từ tài khoản thật hoặc phát hành APK.
