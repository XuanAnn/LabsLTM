# Lab 2 — FTP Server / Client + MySQL

## Chạy độc lập

Mỗi folder là một project riêng, không cần source/jar/config của lab khác. JDK 21 + Maven. UI và Controller tách riêng theo Lab 1; FlatLaf + bản sao AntDesignTheme là phần giao diện Java Swing, không có frontend web (trừ nội dung HTTP của Lab 4).

1. Bật MySQL trong Laragon.
2. Làm theo [MySQL + HeidiSQL](docs/MYSQL_HEIDISQL.md), chạy sql/schema.sql và sửa config/database.properties.
3. Trong folder này:

```powershell
.\build.ps1
.\check-db.ps1
.\run.ps1 server
```

Terminal thứ hai: `.\run.ps1 client`. Có thể bấm run_server.bat / run_client.bat để tự biên dịch rồi mở UI. Chế độ không GUI: `.\run.ps1 server-cli`.

Tài khoản MySQL root/password trong config **khác** tài khoản đăng nhập ứng dụng. Password trên GUI hiện chữ, tài khoản lưu đơn giản theo yêu cầu. Không có SQLite hoặc DB file dự phòng.

## Giao diện theo mẫu Lab 1

Server: khởi động/dừng, cards thống kê, quản lý tài khoản (thêm/đổi mật khẩu/xóa), bảng file, IP client và thời gian vào/ra, audit, cấu hình MySQL.

Client: login/register card → workspace hai bảng file cục bộ và server; chọn thư mục, nháy đúp mở thư mục, Upload/Download, tạo/xóa thư mục/file, log truyền file. Phiên control FTP được giữ đến khi đăng xuất.

Có student/student123, alice/123, bob/123. Đăng ký tài khoản mới rồi đăng nhập. Các tài khoản dùng chung kho file để demo, nhưng audit/metadata ghi người thao tác. File thực nằm data/ftp/files; MySQL ltm_ftp lưu users, sessions, audit và files (path, owner, size, updated_at).

## Demo

Mở server UI, kiểm tra MySQL rồi Khởi động Server port 2121. Mở 2–3 client, đăng ký/đăng nhập, upload một file nhị phân, download ra thư mục khác rồi so sánh nội dung. Xem bảng Files và Kết nối & IP trên server, xem cùng dữ liệu qua HeidiSQL. Restart server để kiểm tra tài khoản/file còn tồn tại.

## FTP tự cài đặt

Control TCP dùng dòng CRLF; PASS sau USER xác minh tài khoản. EPSV/PASV mở ServerSocket dữ liệu riêng; 150 trước truyền và 226 sau đóng data. Có USER/PASS/QUIT/SYST/FEAT/NOOP/OPTS/TYPE/PWD/CWD/CDUP/MKD/DELE/RMD/SIZE/PASV/EPSV/LIST/NLST/RETR/STOR.

Đăng ký dùng extension SITE REGISTER username base64-password (không phải lệnh chuẩn FTP riêng). Binary demo dùng TYPE I; TYPE A hiện truyền byte nguyên trạng. Chưa active PORT/EPRT, rename, resume hay FTPS. Passive data dùng cổng động, cần đường mạng tới các cổng này. Có thể đặt FTP_PUBLIC_HOST khi cần thay IP PASV quảng bá.

```powershell
curl.exe --user student:student123 ftp://localhost:2121/
curl.exe --user student:student123 --upload-file .\README.md ftp://localhost:2121/readme.txt
curl.exe --user student:student123 ftp://localhost:2121/readme.txt --output downloads/readme.txt
```

Đã kiểm thử với curl độc lập. Với FileZilla chọn FTP plain, passive, host/IP server và port 2121; chưa kiểm thử trực tiếp FileZilla.

## Tài liệu và kiểm thử

- [Kiến trúc](docs/ARCHITECTURE.md)
- [Laragon + MySQL + HeidiSQL](docs/MYSQL_HEIDISQL.md)
- [Kết quả kiểm thử](docs/VALIDATION.md)
- [Server UI](docs/server.png) · [Login UI](docs/client.png) · [Workspace](docs/workspace.png)

Chạy `.\test.ps1` khi MySQL đang bật; test tạo schema tên ltm_test_* riêng, dọn lại sau khi chạy. `.\preview.ps1` render giao diện chưa kết nối; `.\preview-workspace.ps1` chạy client với server và schema thử thật để render workspace.

## Đọc code và tắt lưu có chọn lọc

Source Java dùng tab width 4; mỗi lab có .editorconfig riêng. Xem [các cách ghi database và dòng cần comment](docs/DATABASE_WRITES.md). SaveOptions.java của lab có từng dòng SAVE-... để bật/tắt; chạy build.ps1 và khởi động lại sau khi sửa. test-save-options.ps1 kiểm chứng các mục đã tắt bằng bản policy tạm.

## Đọc code từng bước

Đã tách các thao tác thành hàm có tên, dùng kiểu dữ liệu cụ thể, for/while và if/else. Xem [các hàm nên đọc trước và giải thích mẫu Java](docs/READ_CODE.md).

## Cấu trúc source server/client

- `src/server`: xử lý phía server.
- `src/common`: lớp hỗ trợ giao diện, giao thức và dữ liệu của riêng lab.
- `src/client`: giao diện và thao tác phía client.
- `src/main/resources`: schema và tài nguyên classpath.

Hai phần được biên dịch cùng trong lab để các lớp hỗ trợ có thể sử dụng lẫn nhau; đây không phải hai project Maven độc lập. Lệnh build.ps1 và run.ps1 giữ nguyên. Không dùng source từ lab khác.
