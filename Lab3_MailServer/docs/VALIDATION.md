# Kiểm thử Lab 3

Ngày: 2026-11-05. Java 21, Windows, MySQL 8.0.30. **50/50 kiểm tra tích hợp đạt** qua test.ps1.

## Đã kiểm tra

- SMTP/POP3, đăng ký, username + email, From có tên/email, hồ sơ, MIME Unicode, dữ liệu MySQL sau restart.
- Gmail send path được gọi với delivery bên ngoài giả lập và Mail Server/MySQL thật; bản gửi vào MySQL và TXT, không ghi nhầm thư bị từ chối thành Đã gửi.
- Danh sách tài khoản từ server; gửi nhóm chỉ tới người chọn, chống trùng cùng account qua nhiều alias.
- Broadcast lấy danh sách mới nhất trên server, gồm user ngoại tuyến và user đăng ký sau khi client tải danh bạ; tùy chọn gửi cả cho mình.
- Địa chỉ sai làm dừng trước DATA, không giao một phần và không tạo Sent giả.
- Xóa đúng UID và chỉ đúng bản; restart không làm thư đã xóa xuất hiện lại.
- IP gửi/nhận không có port; user chưa kết nối được ghi chưa có IP rồi cập nhật khi kết nối; bên ngoài không gán IP nhận giả.
- Account TXT đúng ba dòng username/password/createdat. Mail TXT có IP, tên/email, tiêu đề, thời gian, nội dung nhiều dòng; không có file JSON/EML trong dữ liệu mới.
- Xóa thư xóa TXT tương ứng nhưng giữ bản người nhận khác; startup tái tạo dữ liệu local theo MySQL.
- Start/stop/restart server trên cùng cổng và nâng cấp schema cũ.

Test tạo schema và data.directory dưới target riêng, dọn schema thử sau khi chạy; không trộn dữ liệu thử với server/data thật.

## Giao diện thực

preview-compose.ps1 đã thực hiện **hai kiểm thử UI gửi thật qua server local/MySQL**: checkbox picker chọn đúng Bob/Carol, Dave không nhận; composer Gửi tất cả giao tới Bob/Carol/Dave và không tự gửi lại Alice. Đã xem ảnh checkbox, soạn nhóm, broadcast, Inbox và Sent.

[Nhóm](compose-group.png) · [Chọn người nhận](recipient-picker.png) · [Tất cả](compose-broadcast.png) · [Thư đến](workspace.png) · [Đã gửi](sent.png).

## Gmail thật

Đăng nhập Gmail thật qua STARTTLS/AUTH, NOOP/QUIT đã thành công ở lượt kiểm tra trước. Không gửi email thử ra ngoài. Kiểm thử lưu sau gửi dùng delivery bên ngoài giả lập để không phát thư thật; phần lưu MySQL/TXT và UI nội bộ đều thật.

## Còn cần nghiệm thu

Chạy trên các máy vật lý/VM của người dùng và thực hiện gửi/nhận Gmail thật khi có người nhận thử được chỉ định. Chức năng gửi nhóm/broadcast đã được người dùng xác nhận là SMTP nhiều người nhận, không yêu cầu UDP IP multicast/broadcast.

## Cập nhật định dạng và điều khiển lưu — 2026-11-06

Source/test Java dùng tab width 4. test-save-options.ps1 đã kiểm chứng bản SaveOptions tạm có các dòng bật được comment; không thay source/jar mặc định. Xem DATABASE_WRITES.md để biết đúng file/dòng và ảnh hưởng khi tắt từng mục. Lượt kiểm tra cuối dùng MySQL tạm tại cổng/data directory riêng, schema thử riêng và không sửa cấu hình MySQL của ứng dụng.


Đã sửa nạp cấu hình Gmail khi chạy từ thư mục LabsLTM/IntelliJ: tìm file theo vị trí class/jar của Lab 3, dùng cùng đường dẫn cho load và save; có thể chỉ định -Dlab.gmail.config. Nếu thiếu thông tin, nút Soạn bằng Gmail mở cài đặt trước. Đạt 8/8 kiểm tra cấu hình và 50/50 tích hợp; kiểm tra từ workspace root xác nhận cấu hình hiện có được tìm thấy và đủ thông tin, không hiển thị mật khẩu và không gửi email thật.
