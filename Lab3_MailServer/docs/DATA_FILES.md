# Dữ liệu local của Mail Server

Mặc định, đường dẫn tính từ folder Lab3_MailServer:

```text
server/
  data/
    accounts/
      accounts.txt
      alice/
        account.txt
      bob/
        account.txt
    mails/
      mails.txt
      alice/
        inbox/
          <uid>.txt
        sent/
          <uid>.txt
      bob/
        inbox/
          <uid>.txt
```

## Account

Mỗi thông tin một dòng, đúng ba trường trong account.txt:

```text
username: alice
password: 123
createdat: 2026-11-05 21:00:00
```

accounts.txt tổng hợp các account theo cùng định dạng, ngăn cách tài khoản bằng dòng trống. Email, IP và thời gian vào/ra vẫn có trong bảng users của MySQL, không thêm vào file account ba dòng.

## Mail

Mỗi thư có file TXT riêng theo UID ổn định. Ví dụ dữ liệu thử:

```text
IP gửi: 127.0.0.1
IP nhận: 127.0.0.1
Người gửi: alice
Email gửi: alice@localhost
Người nhận: bob
Email nhận: bob@localhost
Tiêu đề: Lời chào
Gửi lúc: 2026-11-05 21:00:00 +07:00
Thư mục: Thư đến
Mã thư: <uid>
Nội dung:
Xin chào Bob!
Dòng nội dung tiếp theo.
```

Với bản Sent gửi nhóm, Người nhận liệt kê các tài khoản đích và IP nhận có dạng bob=127.0.0.1; carol=Chưa kết nối. IP không kèm số cổng. Với thư bên ngoài, IP nhận là Không xác định (bên ngoài) vì server local không quan sát được thiết bị nhận Gmail.

mails.txt là chỉ mục gồm account, folder, UID, đường dẫn. Nội dung đầy đủ nằm ở từng file thư và được giải mã thành văn bản, không phải Base64 hay file EML/JSON.

## Đối chiếu database

- users → accounts/<username>/account.txt.
- mail → mails/<recipient>/inbox/<uid>.txt.
- sent_mail → mails/<owner>/sent/<uid>.txt, gồm cả bản thư Gmail gửi thành công.

MySQL là nguồn chính. Khởi động/kiểm tra DB xuất lại dữ liệu hiện có; đăng ký, sửa tài khoản, gửi, lưu bản Gmail, lấy thư và xóa thư cập nhật bản TXT. Xóa một bản Sent chỉ xóa file Sent tương ứng, không xóa bản Inbox của người nhận. Khi restart, bản đã xóa không bị tạo lại. Các file sinh bởi app được thay bằng file tạm rồi đổi tên; file khác do người dùng tự đặt không bị dọn tùy tiện.

Nếu sửa database trực tiếp bằng HeidiSQL, mở lại server hoặc chạy check-db.ps1 để xuất lại các file. Sửa TXT không tự ghi ngược vào MySQL. Không có chế độ chạy chỉ bằng TXT khi database tắt.

```properties
data.directory=server/data
```

Có thể đổi data.directory trong config/database.properties. Test luôn dùng thư mục riêng dưới target, không trộn file thử với dữ liệu server thật. server/data được gitignore; mật khẩu ứng dụng Gmail vẫn ở cấu hình client riêng, không được sao chép vào các account mẫu của server.
