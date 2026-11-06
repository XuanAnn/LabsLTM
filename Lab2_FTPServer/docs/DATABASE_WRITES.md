# Lưu dữ liệu và các dòng cần comment — Lab2_FTPServer

Phiên bản hướng dẫn: 2026-11-06. Số dòng dưới đây khớp source sau khi định dạng. Nếu sau này chèn code, tìm marker SAVE-... để xác định lại.

Database mặc định: `ltm_ftp`. Config của riêng lab trong `config/`; RMI có config A/B riêng. Nội dung password ứng dụng khác thông tin đăng nhập MySQL.

## Comment đúng một dòng để ngừng lưu thêm

Mở `src/common/SaveOptions.java`. Không sửa enum Kind; comment dòng `ENABLED.add(...)` của loại muốn tắt. Tất cả mục hiện đang bật theo yêu cầu bài lab.

| Mục | Dòng cần comment | Bảng / nơi lưu | Sau khi tắt |
|---|---|---|---|
| `AUDIT` | `src/common/SaveOptions.java:25` — `ENABLED.add(Kind.AUDIT);` | audit | Không thêm sự kiện nhật ký; chương trình vẫn có thể in log console. |
| `ACCOUNT` | `src/common/SaveOptions.java:27` — `ENABLED.add(Kind.ACCOUNT);` | users | Không đăng ký hoặc tạo tài khoản mẫu mới. Tài khoản đã có vẫn đăng nhập; đăng ký mới báo đang tắt lưu. |
| `SESSION` | `src/common/SaveOptions.java:29` — `ENABLED.add(Kind.SESSION);` | sessions và thời gian đăng nhập users | Không thêm phiên MySQL. FTP/Mail không cập nhật giờ đăng nhập qua sessionStart. Web giữ phiên trong RAM nên vẫn kết nối được; bảng phiên DB không tăng. |
| `FILE_METADATA` | `src/common/SaveOptions.java:31` — `ENABLED.add(Kind.FILE_METADATA);` | files | Không INSERT/UPSERT thông tin upload. File thật vẫn được truyền và lưu trên đĩa; lệnh xóa vẫn dọn metadata cũ. |

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

## Luồng ghi dữ liệu của FTP

1. FtpClient / ClientController gửi USER, PASS, STOR, RETR qua socket; UI không tự INSERT dữ liệu file server.
2. FtpServer xác minh tài khoản bằng Db.auth (SELECT). Khi đăng nhập thành công, Db.sessionStart ghi sessions và cập nhật thời gian trong users.
3. STOR ghi dữ liệu vào file tạm, chuyển sang file đích rồi INSERT ... ON DUPLICATE KEY UPDATE bảng files. File bytes nằm trên đĩa, không nằm trong bảng files.
4. Db.audit ghi audit. DELE xóa file vật lý và DELETE metadata tương ứng. Đăng ký SITE REGISTER đi vào Db.register.
5. Tài khoản mẫu trong resource schema.sql cũng được kiểm tra công tắc ACCOUNT khi ứng dụng khởi động.

Tắt AUDIT/FILE_METADATA không chặn truyền file. Khi tắt ACCOUNT, cần có tài khoản cũ để đăng nhập.

## Những chỗ SQL ghi dữ liệu trong source

Bảng này giúp lần theo code thực thi. Để tắt có chọn lọc, dùng dòng công tắc phía trên thay vì comment một phần transaction hoặc câu lệnh nhiều dòng.

| File:dòng | Câu SQL bắt đầu |
|---|---|
| `src/server/FtpServer.java:159` | `DELETE FROM files WHERE path=?` |
| `src/server/FtpServer.java:244` | `INSERT INTO files(path,owner,size)` |
| `src/common/AdminFrame.java:219` | `UPDATE sessions SET disconnected_at=NOW() WHERE disconnected_at IS NULL` |
| `src/common/AdminFrame.java:293` | `UPDATE users SET password=? WHERE name=?` |
| `src/common/AdminFrame.java:316` | `DELETE FROM users WHERE name=?` |
| `src/common/Db.java:79` | `INSERT IGNORE INTO users` |
| `src/common/Db.java:143` | `INSERT INTO audit(event) VALUES(?)` |
| `src/common/Db.java:152` | `INSERT IGNORE INTO users(name,password) VALUES(?,?)` |
| `src/common/Db.java:162` | `INSERT IGNORE INTO users(name,password) VALUES(?,?)` |
| `src/common/Db.java:183` | `INSERT INTO sessions(id,username,remote_ip,protocol) VALUES(?,?,?,?)` |
| `src/common/Db.java:188` | `UPDATE users SET last_login=NOW() WHERE name=?` |
| `src/common/Db.java:198` | `UPDATE users u JOIN sessions s ON s.username=u.name SET u.last_logout=NOW() WHERE` |
| `src/common/Db.java:201` | `UPDATE sessions SET disconnected_at=NOW() WHERE id=?` |


Schema khởi tạo và migration nằm trong `src/main/resources/schema.sql`; file mở bằng HeidiSQL nằm trong `sql/`. CREATE/ALTER tạo cấu trúc bảng, không phải log hoạt động. Lab 2/3 có INSERT tài khoản mẫu trong resource schema và được kiểm tra ACCOUNT khi ứng dụng khởi động. Lab 3 có backfill sent_mail và mốc migration; khi MAIL_SENT tắt, hai bước đó được hoãn cùng nhau. Lab 5 giữ INSERT meta để xác minh định danh node.

## Kiểm chứng công tắc

```powershell
.\test-save-options.ps1
```

Script tạo một bản SaveOptions tạm trong target, comment các dòng bật ở bản tạm, biên dịch bản này trước jar trên classpath và kiểm tra với schema MySQL thử riêng. Source ứng dụng, jar mặc định và dữ liệu demo không bị tắt/xóa. Kiểm tra xác minh thực sự không tăng các bảng tương ứng; file FTP/Web vẫn truyền được, Mail/RMI từ chối thao tác ghi đã tắt.

MySQL vẫn cần chạy để ứng dụng đọc tài khoản/dữ liệu có sẵn. Đây là điều khiển từng điểm ghi, không thay database bằng một hệ lưu trữ khác.

