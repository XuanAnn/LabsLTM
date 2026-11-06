# Luồng xử lý để đọc và trình bày

## Lần theo một lần upload

1. FtpClient đọc lời chào 220, gửi USER/PASS và TYPE I cho dữ liệu nhị phân.
2. Client gửi EPSV trên kết nối điều khiển, đọc cổng server trả về và mở socket dữ liệu riêng.
3. STOR bắt đầu upload; server trả 150 trước khi nhận dữ liệu.
4. Client ghi byte, gọi shutdownOutput để báo hết dữ liệu. Kết nối điều khiển vẫn được giữ.
5. Server trả 226 khi truyền xong. File nằm trên đĩa; MySQL lưu tài khoản, phiên, metadata và nhật ký theo SaveOptions.

Download dùng RETR; liệt kê dùng LIST. Không trộn byte file với lệnh trên kênh điều khiển. Khi demo, upload rồi download một file ZIP và so sánh file gốc.

Xem [các hàm trong source](READ_CODE.md) và [cách lưu dữ liệu](DATABASE_WRITES.md).
