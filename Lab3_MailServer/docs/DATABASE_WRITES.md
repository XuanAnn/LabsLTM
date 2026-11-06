# Lưu dữ liệu và các dòng cần comment — Lab3_MailServer

Phiên bản hướng dẫn: 2026-11-06. Số dòng dưới đây khớp source sau khi định dạng. Nếu sau này chèn code, tìm marker SAVE-... để xác định lại.

Database mặc định: `ltm_mail`. Config của riêng lab trong `config/`; RMI có config A/B riêng. Nội dung password ứng dụng khác thông tin đăng nhập MySQL.

## Comment đúng một dòng để ngừng lưu thêm

Mở `src/common/SaveOptions.java`. Không sửa enum Kind; comment dòng `ENABLED.add(...)` của loại muốn tắt. Tất cả mục hiện đang bật theo yêu cầu bài lab.

| Mục | Dòng cần comment | Bảng / nơi lưu | Sau khi tắt |
|---|---|---|---|
| `AUDIT` | `src/common/SaveOptions.java:27` — `ENABLED.add(Kind.AUDIT);` | audit | Không thêm sự kiện nhật ký; chương trình vẫn có thể in log console. |
| `ACCOUNT` | `src/common/SaveOptions.java:29` — `ENABLED.add(Kind.ACCOUNT);` | users | Không đăng ký hoặc tạo tài khoản mẫu mới. Tài khoản đã có vẫn đăng nhập; đăng ký mới báo đang tắt lưu. |
| `SESSION` | `src/common/SaveOptions.java:31` — `ENABLED.add(Kind.SESSION);` | sessions và thời gian đăng nhập users | Không thêm phiên MySQL. FTP/Mail không cập nhật giờ đăng nhập qua sessionStart. Web giữ phiên trong RAM nên vẫn kết nối được; bảng phiên DB không tăng. |
| `MAIL_INBOX` | `src/common/SaveOptions.java:33` — `ENABLED.add(Kind.MAIL_INBOX);` | mail | SMTP local từ chối thư mới bằng mã 550; không báo gửi thành công rồi bỏ mất thư. Thư cũ vẫn đọc được. |
| `MAIL_SENT` | `src/common/SaveOptions.java:35` — `ENABLED.add(Kind.MAIL_SENT);` | sent_mail | Không thêm bản Đã gửi, kể cả bản sao sau gửi Gmail. Không dừng việc gửi Gmail; hoãn backfill dữ liệu Đã gửi cũ khi khởi động. |
| `TEXT_EXPORT` | `src/common/SaveOptions.java:37` — `ENABLED.add(Kind.TEXT_EXPORT);` | server/data (file TXT) | Không xuất/cập nhật bản sao TXT. MySQL vẫn lưu bình thường nếu các mục SQL khác đang bật. |

Ví dụ chỉ tắt nhật ký:

```java
// ENABLED.add(Kind.AUDIT);
```

Bỏ `//` để bật lại. Comment công tắc không xóa dữ liệu đã có. Các thao tác UPDATE/DELETE trên dữ liệu cũ và phần tạo/nâng cấp schema là những việc riêng, không phải thêm bản ghi mới. Chạy SQL thủ công trong HeidiSQL không chịu tác động của công tắc Java.

## Build và chạy lại

1. Dừng server/client của lab đang sửa. Với RMI, thao tác trên cả node cần thay đổi.
2. Lưu SaveOptions.java.
3. Mở PowerShell tại chính folder lab:

```powershell
.\build.ps1
.\run.ps1 server
# Terminal khác:
.\run.ps1 client
```

Nếu chạy từ IntelliJ, Rebuild Project rồi Run lại đúng main class. Chỉ chạy lại jar cũ sẽ không nhận thay đổi trong source. `run_client.bat` không phải lúc nào cũng build lại nếu jar đã tồn tại, vì vậy cần chạy build.ps1 sau khi comment.

## JDBC ghi như thế nào?

- `Db.open()` tạo Connection từ file properties bằng DriverManager. Caller đóng bằng try-with-resources.
- `Db.exec(sql, values...)` tạo PreparedStatement, bind từng dấu ? bằng setObject, rồi executeUpdate. Mỗi lần gọi dùng auto-commit.
- `Db.rows(sql, values...)` gọi executeQuery, duyệt ResultSet để đọc. Bản thân rows không INSERT dữ liệu nghiệp vụ.
- `Db.register()` dùng executeUpdate trả số dòng bị ảnh hưởng để biết có tạo tài khoản mới không. INSERT IGNORE không nhân đôi khóa đã có.
- `setAutoCommit(false)` + `addBatch/executeBatch` + `commit` dùng ở nơi cần ghi nhiều dòng cùng lúc; không comment commit hoặc executeUpdate dùng chung để tắt một bảng.

## Luồng ghi dữ liệu của Mail

1. Đăng ký → POP3 XREGISTER → Db.register → INSERT IGNORE users. Tên tài khoản, email, mật khẩu là dữ liệu đăng ký; không liên quan mật khẩu MySQL.
2. SMTP AUTH/POP3 PASS → Db.auth (SELECT) → Db.sessionStart (sessions, last_login, last_ip).
3. SMTP DATA → Connection.setAutoCommit(false) → PreparedStatement.addBatch cho từng người nhận → executeBatch vào mail → saveSent vào sent_mail → commit. Chỉ sau commit server mới trả 250. Đây là transaction nhiều bản ghi.
4. Gửi Gmail: thư được bên ngoài chấp nhận trước, sau đó ClientController gửi XSAVE SENT về server local để saveSent lưu bản Đã gửi vào MySQL. Các bài test không gửi thư thật ra ngoài.
5. POP3 DELE đánh dấu trong phiên; QUIT mới DELETE và commit. Ngắt socket trước QUIT không xóa thư.
6. Db.exec tự đồng bộ TXT khi thay users/mail/sent_mail; các transaction trực tiếp gọi Db.syncData sau commit. DataFiles.sync đọc SELECT rồi ghi server/data/accounts và server/data/mails. TXT là bản sao, MySQL là nguồn dữ liệu chính; không có import TXT ngược vào DB.
7. UPDATE body bổ sung IP nhận khi client kết nối; UPDATE email lưu email tài khoản. Đây là cập nhật dòng cũ, không phải thêm thư mới.

MAIL_SENT mặc định phải bật để đáp ứng yêu cầu gửi Gmail vẫn lưu DB. TEXT_EXPORT chỉ điều khiển file TXT, không tắt INSERT MySQL. MAIL_INBOX tắt nghĩa là tạm ngừng nhận thư mới, không phải một chế độ hộp thư chỉ lưu RAM.

## Những chỗ SQL ghi dữ liệu trong source

Bảng này giúp lần theo code thực thi. Để tắt có chọn lọc, dùng dòng công tắc phía trên thay vì comment một phần transaction hoặc câu lệnh nhiều dòng.

| File:dòng | Câu SQL bắt đầu |
|---|---|
| `src/server/MailServer.java:291` | `INSERT INTO mail(recipient,sender,body,uid)` |
| `src/server/MailServer.java:375` | `INSERT IGNORE INTO sent_mail(owner,recipient,sender,body,uid)` |
| `src/server/MailServer.java:519` | `UPDATE` |
| `src/server/MailServer.java:556` | `DELETE FROM sent_mail WHERE id=? AND owner=?` |
| `src/server/MailServer.java:557` | `DELETE FROM mail WHERE id=? AND` |
| `src/server/MailServer.java:694` | `UPDATE users SET email=? WHERE name=?` |
| `src/common/AdminFrame.java:220` | `UPDATE sessions SET disconnected_at=NOW() WHERE disconnected_at IS NULL` |
| `src/common/AdminFrame.java:294` | `UPDATE users SET password=? WHERE name=?` |
| `src/common/AdminFrame.java:317` | `DELETE FROM users WHERE name=?` |
| `src/common/Db.java:85` | `INSERT IGNORE INTO USERS` |
| `src/common/Db.java:91` | `INSERT IGNORE INTO SENT_MAIL` |
| `src/common/Db.java:93` | `INSERT IGNORE INTO MAIL_SCHEMA_MIGRATIONS` |
| `src/common/Db.java:183` | `INSERT INTO audit(event) VALUES(?)` |
| `src/common/Db.java:193` | `INSERT IGNORE INTO users(name,password,email) VALUES(?,?,?)` |
| `src/common/Db.java:218` | `INSERT IGNORE INTO users(name,password,email) VALUES(?,?,?)` |
| `src/common/Db.java:251` | `INSERT INTO sessions(id,username,remote_ip,protocol) VALUES(?,?,?,?)` |
| `src/common/Db.java:257` | `UPDATE users SET last_login=NOW(),last_ip=? WHERE name=?` |
| `src/common/Db.java:269` | `UPDATE users u JOIN sessions s ON s.username=u.name SET u.last_logout=NOW() WHERE` |
| `src/common/Db.java:272` | `UPDATE sessions SET disconnected_at=NOW() WHERE id=?` |


Schema khởi tạo và migration nằm trong `src/main/resources/schema.sql`; file mở bằng HeidiSQL nằm trong `sql/`. CREATE/ALTER tạo cấu trúc bảng, không phải log hoạt động. Lab 2/3 có INSERT tài khoản mẫu trong resource schema và được kiểm tra ACCOUNT khi ứng dụng khởi động. Lab 3 có backfill sent_mail và mốc migration; khi MAIL_SENT tắt, hai bước đó được hoãn cùng nhau. Lab 5 giữ INSERT meta để xác minh định danh node.

## Kiểm chứng công tắc

```powershell
.\test-save-options.ps1
```

Script tạo một bản SaveOptions tạm trong target, comment các dòng bật ở bản tạm, biên dịch bản này trước jar trên classpath và kiểm tra với schema MySQL thử riêng. Source ứng dụng, jar mặc định và dữ liệu demo không bị tắt/xóa. Kiểm tra xác minh thực sự không tăng các bảng tương ứng; file FTP/Web vẫn truyền được, Mail/RMI từ chối thao tác ghi đã tắt.

MySQL vẫn cần chạy để ứng dụng đọc tài khoản/dữ liệu có sẵn. Đây là điều khiển từng điểm ghi, không thay database bằng một hệ lưu trữ khác.

