# Lưu dữ liệu và các dòng cần comment — Lab4_WebServer

Phiên bản hướng dẫn: 2026-11-06. Số dòng dưới đây khớp source sau khi định dạng. Nếu sau này chèn code, tìm marker SAVE-... để xác định lại.

Database mặc định: `ltm_web`. Config của riêng lab trong `config/`; RMI có config A/B riêng. Nội dung password ứng dụng khác thông tin đăng nhập MySQL.

## Comment đúng một dòng để ngừng lưu thêm

Mở `src/common/SaveOptions.java`. Không sửa enum Kind; comment dòng `ENABLED.add(...)` của loại muốn tắt. Tất cả mục hiện đang bật theo yêu cầu bài lab.

| Mục | Dòng cần comment | Bảng / nơi lưu | Sau khi tắt |
|---|---|---|---|
| `AUDIT` | `src/common/SaveOptions.java:26` — `ENABLED.add(Kind.AUDIT);` | audit | Không thêm sự kiện nhật ký; chương trình vẫn có thể in log console. |
| `ACCOUNT` | `src/common/SaveOptions.java:28` — `ENABLED.add(Kind.ACCOUNT);` | users | Không đăng ký hoặc tạo tài khoản mẫu mới. Tài khoản đã có vẫn đăng nhập; đăng ký mới báo đang tắt lưu. |
| `SESSION` | `src/common/SaveOptions.java:30` — `ENABLED.add(Kind.SESSION);` | sessions và thời gian đăng nhập users | Không thêm phiên MySQL. FTP/Mail không cập nhật giờ đăng nhập qua sessionStart. Web giữ phiên trong RAM nên vẫn kết nối được; bảng phiên DB không tăng. |
| `HISTORY` | `src/common/SaveOptions.java:32` — `ENABLED.add(Kind.HISTORY);` | history | Không thêm lịch sử client; gửi yêu cầu và tải file vẫn hoạt động. |
| `REQUEST_LOG` | `src/common/SaveOptions.java:34` — `ENABLED.add(Kind.REQUEST_LOG);` | requests | Không thêm request vào DB server; server vẫn trả nội dung và giữ phiên nếu SESSION bật. |

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

## Luồng ghi dữ liệu của Web

1. ClientController.connect gửi POST /api/session/open, nhận ID phiên rồi gửi HEAD kiểm tra server. WebSessions ghi sessions; client và browser nhúng dùng chung ID qua X-Lab-Session. Browser thường dùng cookie LTM_SESSION.
2. Mỗi HEAD/GET/POST hoàn tất, WebServer.handle ghi requests trong finally, gồm method, action, URL, IP không port, status, bytes và session_id.
3. Tải file dùng GET có ?download=1; server gửi Content-Disposition: attachment và ghi action=DOWNLOAD. DOWNLOAD là thao tác, không phải một method HTTP mới.
4. ClientController.request/download/recordVisit ghi history phía client. Nút HEAD và POST trên thanh browser gọi bộ WebClient socket tự viết; browser nhúng tải trang/media bằng Chromium.
5. Heartbeat mỗi 10 giây cập nhật last_seen. Đăng xuất/đóng UI gửi POST close; phiên mất liên lạc quá 45 giây được đánh dấu đã ngắt. Kết nối TCP từng request đóng riêng, khác với phiên ứng dụng này.
6. Tab Kết nối & IP và bộ đếm server đọc sessions. Khi tắt SESSION, WebSessions vẫn giữ ID trong RAM để client hoạt động, nhưng không có bản ghi để dashboard DB hiển thị.

Nếu chỉ muốn ngừng tăng bảng requests, comment REQUEST_LOG; muốn ngừng history, comment HISTORY. Không cần comment HEAD/POST hoặc lệnh ghi file tải về máy.

## Những chỗ SQL ghi dữ liệu trong source

Bảng này giúp lần theo code thực thi. Để tắt có chọn lọc, dùng dòng công tắc phía trên thay vì comment một phần transaction hoặc câu lệnh nhiều dòng.

| File:dòng | Câu SQL bắt đầu |
|---|---|
| `src/client/ClientController.java:96` | `INSERT INTO history(url,method,status) VALUES(?,?,?)` |
| `src/client/ClientController.java:121` | `INSERT INTO history(url,method,status) VALUES(?,?,?)` |
| `src/client/ClientController.java:135` | `INSERT INTO history(url,method,status) VALUES(?,?,?)` |
| `src/common/AdminFrame.java:227` | `UPDATE sessions SET disconnected_at=NOW() WHERE disconnected_at IS NULL` |
| `src/common/AdminFrame.java:301` | `UPDATE users SET password=? WHERE name=?` |
| `src/common/AdminFrame.java:324` | `DELETE FROM users WHERE name=?` |
| `src/common/Db.java:94` | `ALTER TABLE` |
| `src/common/Db.java:154` | `INSERT INTO audit(event) VALUES(?)` |
| `src/common/Db.java:163` | `INSERT IGNORE INTO users(name,password) VALUES(?,?)` |
| `src/common/Db.java:173` | `INSERT IGNORE INTO users(name,password) VALUES(?,?)` |
| `src/common/Db.java:194` | `INSERT INTO sessions(id,username,remote_ip,protocol) VALUES(?,?,?,?)` |
| `src/common/Db.java:199` | `UPDATE users SET last_login=NOW() WHERE name=?` |
| `src/common/Db.java:209` | `UPDATE users u JOIN sessions s ON s.username=u.name SET u.last_logout=NOW() WHERE` |
| `src/common/Db.java:212` | `UPDATE sessions SET disconnected_at=NOW() WHERE id=?` |
| `src/client/WebClient.java:145` | `INSERT INTO history(url,method,status)` |
| `src/client/WebClient.java:170` | `INSERT INTO history(url,method,status) VALUES(?,?,?)` |
| `src/server/WebServer.java:315` | `INSERT INTO` |
| `src/server/WebSessions.java:46` | `UPDATE sessions SET last_seen=NOW() WHERE id=?` |
| `src/server/WebSessions.java:55` | `INSERT INTO sessions(id,username,remote_ip,protocol,last_seen)` |
| `src/server/WebSessions.java:69` | `UPDATE sessions SET disconnected_at=NOW(),last_seen=NOW() WHERE id=? AND` |
| `src/server/WebSessions.java:81` | `UPDATE sessions SET disconnected_at=NOW() WHERE protocol='HTTP' AND` |


Schema khởi tạo và migration nằm trong `src/main/resources/schema.sql`; file mở bằng HeidiSQL nằm trong `sql/`. CREATE/ALTER tạo cấu trúc bảng, không phải log hoạt động. Lab 2/3 có INSERT tài khoản mẫu trong resource schema và được kiểm tra ACCOUNT khi ứng dụng khởi động. Lab 3 có backfill sent_mail và mốc migration; khi MAIL_SENT tắt, hai bước đó được hoãn cùng nhau. Lab 5 giữ INSERT meta để xác minh định danh node.

## Kiểm chứng công tắc

```powershell
.\test-save-options.ps1
```

Script tạo một bản SaveOptions tạm trong target, comment các dòng bật ở bản tạm, biên dịch bản này trước jar trên classpath và kiểm tra với schema MySQL thử riêng. Source ứng dụng, jar mặc định và dữ liệu demo không bị tắt/xóa. Kiểm tra xác minh thực sự không tăng các bảng tương ứng; file FTP/Web vẫn truyền được, Mail/RMI từ chối thao tác ghi đã tắt.

MySQL vẫn cần chạy để ứng dụng đọc tài khoản/dữ liệu có sẵn. Đây là điều khiển từng điểm ghi, không thay database bằng một hệ lưu trữ khác.

