# MySQL với Laragon và HeidiSQL — Lab2_FTPServer

## 1. Khởi động MySQL trong Laragon

Máy hiện tại có Laragon ở C:\laragon và MySQL 8.0.30 ở C:\laragon\bin\mysql\mysql-8.0.30-winx64. Mở Laragon, bật dịch vụ MySQL (hoặc Start All). Java không cần Apache/PHP, không cần chép project vào www. MySQL là database server; HeidiSQL chỉ là ứng dụng quản lý dữ liệu.

Kiểm tra MySQL hiển thị Running và cổng đang dùng. Cấu hình dự án đặt mặc định 127.0.0.1:3306, user root, password rỗng. Nếu bạn đã đặt password hoặc đổi port trong Laragon, dùng giá trị thực của bạn; không có thao tác reset password tự động.

## 2. Tạo kết nối HeidiSQL

1. Bấm Database trong Laragon để mở HeidiSQL, hoặc mở HeidiSQL riêng.
2. Trong Session manager chọn New, đặt tên Laragon - Lab2_FTPServer.
3. Network type: MariaDB or MySQL (TCP/IP).
4. Hostname / IP: 127.0.0.1; Port: 3306 (hoặc cổng Laragon thực).
5. User: root; Password: mật khẩu MySQL của bạn, để trống chỉ khi MySQL của bạn chưa đặt.
6. Bấm Open. Nếu thất bại, kiểm tra dịch vụ MySQL đã bật, đúng port/password; không phải bật server Java mới kết nối được database.
7. Ở tab Query chạy SELECT VERSION(), @@port; để kiểm tra server đang kết nối. Không chọn PostgreSQL hoặc SQL Server.

## 3. Tạo database riêng cho lab

Mở File → Load SQL file và chọn sql/schema.sql trong folder lab này (hoặc mở nội dung vào tab Query). Chạy toàn bộ script bằng F9 / Run. Script chỉ CREATE IF NOT EXISTS và INSERT IGNORE, không xóa dữ liệu cũ. Refresh cây database; sẽ thấy schema ltm_ftp.

Không dùng schema của lab khác; mỗi lab tự tạo bảng và lưu dữ liệu riêng.

## 4. Kết nối từ Java

Sửa config/database.properties của chính lab:

```properties
host=127.0.0.1
port=3306
database=ltm_ftp
user=root
password=
```

Giá trị password phải giống password HeidiSQL vừa dùng; password MySQL khác mật khẩu tài khoản đăng nhập ứng dụng (alice/123...). Không bọc password bằng dấu nháy. Các ô Password trong GUI Java đều hiện chữ theo yêu cầu.

```powershell
.\build.ps1
.\check-db.ps1
.\run.ps1 server
.\run.ps1 client
```

check-db.ps1 phải báo CONNECTED MySQL và tên schema. Khi khởi động, JDBC cũng tự tạo schema/bảng còn thiếu nếu MySQL user có quyền; script HeidiSQL giúp thấy cấu trúc rõ ràng trước khi chạy.

Server UI có tab Cấu hình MySQL để nạp/sửa/lưu file và kiểm tra kết nối. Lưu cấu hình rồi Khởi động Server; khi đổi database cần dừng server trước để phiên đang chạy không giữ cấu hình cũ.



## 5. Quan sát dữ liệu trong HeidiSQL

Sau khi chạy chức năng trong GUI, Refresh bảng hoặc chạy sql/verify.sql:

```sql
USE ltm_ftp;
SELECT * FROM users;
SELECT * FROM files;
SELECT * FROM sessions ORDER BY connected_at DESC;
SELECT * FROM audit ORDER BY id DESC;
```

Bảng users lưu tài khoản đăng ký, password đơn giản, thời gian vào/ra; sessions lưu IP và phiên kết nối; audit ghi hoạt động. files lưu metadata file, byte file nằm trên disk server.

Không có SQLite hoặc fallback database file. Nếu MySQL chưa chạy, ứng dụng báo lỗi kết nối để bạn sửa cấu hình.

## Tài liệu chính thức

- [Laragon: quản lý dịch vụ và phiên bản MySQL](https://laragon.org/docs/operations)
- [MySQL Connector/J: cấu trúc JDBC URL](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-jdbc-url-format.html)
- [HeidiSQL: tài liệu trợ giúp](https://www.heidisql.com/help.php)
