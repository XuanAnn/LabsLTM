# Dữ liệu chat MySQL — Lab 1

Bật MySQL trong Laragon. Cấu hình riêng của lab: config/database.properties, mặc định host=127.0.0.1, port=3306, database=ltm_chat, user=root, password rỗng. Điền password MySQL trên máy bạn nếu có. Server tự tạo schema và bảng; cũng có thể mở sql/schema.sql trong HeidiSQL và chạy thủ công. Client không kết nối MySQL.

Bảng chat_messages lưu id, target_type (DIRECT/GROUP), sender, target (tài khoản/nhóm), content và sent_at (epoch milliseconds). Bản ghi chia sẻ file chỉ lưu thông báo tên/kích thước, không lưu byte file. Tài khoản và danh sách nhóm vẫn dùng TXT.

Luồng ghi: ClientHandler xác định người gửi từ kết nối đã đăng nhập → ServerController.recordChat → ChatDatabase.save → PreparedStatement.setString/setLong → executeUpdate. Ghi MySQL thành công mới thêm vào lịch sử RAM và bảng server. Chat nhóm vẫn truyền UDP multicast; client gửi thêm CHAT_GROUP qua TCP để server lưu một bản, không lưu theo từng người nhận.

Khi mở lại server, ChatDatabase.loadHistory đọc theo id và giữ nguyên timestamp, rồi nạp bảng giám sát server. Chưa bổ sung tải lịch sử cũ vào cửa sổ chat của client. Tin nhắn từng tồn tại chỉ trong RAM trước bản sửa không thể khôi phục sau khi tiến trình đó đã tắt.

Muốn ngừng thêm bản ghi: trong src/server/ServerController.java tìm SAVE-CHAT và comment dòng `chatDatabase.save(record);`. Sau đó chạy build.ps1 và mở lại server. Không comment executeUpdate dùng chung trong ChatDatabase. Tắt ghi không xóa lịch sử cũ; server vẫn cần MySQL để nạp lịch sử.

Trong HeidiSQL mở schema ltm_chat, bảng chat_messages hoặc chạy:

```sql
SELECT id, target_type, sender, target, content,
       FROM_UNIXTIME(sent_at / 1000) AS sent_time
FROM chat_messages ORDER BY id DESC;
```

Test dùng schema ltm_test_chat_* riêng; không thêm/xóa dữ liệu demo ltm_chat. Có thể đặt TEST_MYSQL_HOST/PORT/USER/PASSWORD cho môi trường test. Mặc định schema thử được dọn sau khi chạy.
