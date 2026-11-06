# Luồng xử lý để đọc và trình bày

## Lần theo một lá thư

1. ComposeDialog lấy người nhận, tiêu đề và nội dung; ClientController chuẩn bị thư.
2. SMTP dùng để gửi. POP3 dùng để lấy thư từ hộp thư local.
3. Gửi nhóm là gửi tới danh sách người nhận qua SMTP; gửi tất cả lấy danh sách tài khoản server local. Hai thao tác này không dùng UDP multicast/broadcast.
4. Thư local và bản Đã gửi được lưu theo các công tắc MAIL_INBOX/MAIL_SENT. Gửi qua Gmail cũng có đường lưu bản Đã gửi.
5. MySQL là dữ liệu chính. TEXT_EXPORT điều khiển bản sao TXT trong server/data; tắt nó không tắt ghi SQL.

Khi demo: đăng nhập hai tài khoản, gửi thư riêng rồi nhóm/tất cả; kiểm tra Thư đến, Đã gửi và xóa thư. Gửi Gmail cần cấu hình tài khoản và App Password của bạn, không ghi mật khẩu vào báo cáo.

Xem [các hàm trong source](READ_CODE.md) và [cách lưu dữ liệu](DATABASE_WRITES.md).
