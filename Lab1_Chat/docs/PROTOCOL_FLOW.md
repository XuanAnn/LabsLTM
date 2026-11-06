# Luồng xử lý để đọc và trình bày

## Lần theo một tin nhắn

1. Bấm Gửi trong ClientUI; ClientController lấy người nhận và nội dung.
2. Chat riêng truyền đối tượng qua TCP; server xác định người gửi từ kết nối đã đăng nhập rồi chuyển tới người nhận.
3. Chat nhóm dùng UDP multicast. Client phải tham gia đúng địa chỉ nhóm trên đúng card mạng IPv4.
4. Gửi file tất cả: server chuyển tiếp file qua từng kết nối TCP (fan-out). Đây không phải gói UDP broadcast.
5. Tài khoản và nhóm lưu TXT; lịch sử chat lưu MySQL, có bản trong RAM để hiển thị.

Khi demo: mở nhiều client, gửi riêng rồi gửi nhóm; kiểm tra client ngoài nhóm không nhận tin nhóm. Sau đó gửi một file cho tất cả và so sánh nội dung file nhận.

Xem [các hàm trong source](READ_CODE.md) và [cách lưu dữ liệu](DATABASE_WRITES.md).
