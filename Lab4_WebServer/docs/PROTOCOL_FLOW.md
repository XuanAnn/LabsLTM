# Luồng xử lý để đọc và trình bày

## Lần theo một yêu cầu HTTP

1. Client gửi dòng method + đường dẫn + phiên bản HTTP, tiếp theo là header và dòng trống; POST có thêm body.
2. WebServer đọc yêu cầu, chọn route dịch vụ rồi ghi status, header và dữ liệu phản hồi.
3. GET lấy nội dung. HEAD chỉ trả header, không gửi body. POST gửi nội dung lên server.
4. Download dùng GET với dấu hiệu download; server đặt Content-Disposition để tải file và ghi action DOWNLOAD.
5. ClientUI có phần gửi yêu cầu bằng socket tự viết. EmbeddedBrowser dùng JCEF để hiển thị trang, media và PDF trong cửa sổ Java.
6. Phiên kết nối được liên kết với yêu cầu; heartbeat giữ trạng thái, đóng ứng dụng/timeout kết thúc phiên. MySQL lưu theo SaveOptions.

Khi demo: mở /music, /videos, /documents, /photos và /downloads; bấm HEAD nhiều lần, gửi POST rồi tải file. Đối chiếu method/action/session trên server. Đường dẫn route không cần dấu #.

Xem [các hàm trong source](READ_CODE.md) và [cách lưu dữ liệu](DATABASE_WRITES.md).
