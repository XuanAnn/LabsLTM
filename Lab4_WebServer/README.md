# Lab 4 — HTTP Server / Client + MySQL

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

Server dashboard: start/stop, request table, cấu hình MySQL và nhật ký. Client Swing có trình duyệt thật nhúng trong tab **Trình duyệt**: lùi/tiến, tải lại, trang chủ, thanh địa chỉ, JavaScript, media, PDF và tải file qua hộp thoại lưu. Tab **Yêu cầu** giữ GET/HEAD/POST tự viết, body nhiều dòng và phản hồi/header. Tab **Lịch sử** lưu MySQL; bấm đúp địa chỉ để mở lại. HTTP không bắt buộc tài khoản.

## Dịch vụ và dữ liệu

Mặc định http://localhost:8080, root public. Có GET / (HTML), GET /hello.txt (download), GET /api/time (JSON), POST /api/echo (body UTF-8). Trang chủ có 5 dịch vụ: nghe nhạc, xem video, đọc PDF, xem ảnh và tải file. Có bản thu Moonlight Sonata (7 phút 53 giây), video hoa/ong quay thực tế, trích đoạn Sintel có âm thanh, ảnh NASA Apollo 17, PDF RFC 9110 nguyên bản 194 trang và ZIP tài liệu. File được lưu tại server. API /api/library tự liệt kê file; thêm file rồi bấm Làm mới, không cần restart. MIME và byte range/suffix range hỗ trợ tua media.

MySQL ltm_web.requests ghi dữ liệu tại server; history ghi thao tác client. Khi chạy client trên máy khác, cấu hình MySQL của client phải tới được MySQL muốn lưu history (hoặc dùng một MySQL/schema riêng cho client). Nghe nhạc/video, đọc PDF và xem ảnh ngay trong tab Trình duyệt của ứng dụng Java. Không cần mở Edge/Chrome ngoài.

```powershell
curl.exe -I http://localhost:8080/hello.txt
curl.exe --data 'message=hello' http://localhost:8080/api/echo
curl.exe -H 'Range: bytes=0-9' http://localhost:8080/hello.txt
```

## HTTP tự cài đặt

ServerSocket HTTP/1.1, một request mỗi connection, Connection: close. Content-Length là byte, HEAD chỉ metadata, POST echo tối đa 1 MiB. Có 200/206/400/403/404/405/411/413/416/417; xử lý range không hợp lệ và request framing mơ hồ. Không dùng HttpServer/HttpClient hay framework. Client đọc Content-Length/chunked/close-delimited, tối đa 32 MiB; chưa HTTPS, redirect tự động, proxy, HTTP/2, request chunked hoặc upload multipart.

## Tài liệu và kiểm thử

- [Kiến trúc](docs/ARCHITECTURE.md)
- [Laragon + MySQL + HeidiSQL](docs/MYSQL_HEIDISQL.md)
- [Kết quả kiểm thử](docs/VALIDATION.md)
- [Server UI](docs/server.png) · [Login UI](docs/client.png) · [Workspace](docs/workspace.png)

Chạy `.\test.ps1` khi MySQL đang bật; test tạo schema tên ltm_test_* riêng, dọn lại sau khi chạy. `.\preview.ps1` render giao diện chưa kết nối; `.\preview-workspace.ps1` chạy client với server và schema thử thật để render workspace.

## Thư viện media và tải file

| Dịch vụ | Folder | Định dạng |
|---|---|---|
| Nghe nhạc | public/media/music | mp3, wav, ogg, m4a |
| Xem video | public/media/videos | mp4, webm, ogv |
| Đọc tài liệu | public/media/documents | pdf |
| Xem ảnh | public/media/photos | png, jpg, jpeg, gif, webp, svg |
| Tải file | public/media/downloads | mọi loại file |

Chép file trực tiếp vào folder tương ứng. Danh sách có tìm kiếm, chọn nội dung, dung lượng và tải bản gốc. Nội dung thật đã tải về project, có tác giả/nguồn/giấy phép; xem [nguồn media](docs/MEDIA_SOURCES.md). Metadata tùy chọn nằm trong public/media/credits.properties. Video/ảnh vẽ và tiếng sine thử trước đó đã được thay bằng nội dung thật.

Trình duyệt nhúng hiện kiểm thử OGG Vorbis và WebM VP8/Vorbis/VP9. Ưu tiên WebM/OGG để demo; H.264/AAC trong MP4/M4A có thể không phát trên bản build Chromium này. Các file vẫn có thể tải bản gốc. Browser ngoài có thể hỗ trợ codec khác.

## Bộ trình duyệt cho client Java

Chạy `.\prepare-browser.ps1` sau build để chuẩn bị trước; client cũng tự tải lần đầu nếu chưa có. Bộ native nằm ở **browser-runtime/** của riêng Lab 4, cache ở browser-cache/, không bị build xóa. Máy hiện tại đã chuẩn bị Windows x64/JDK 21 và kiểm thử. Khi demo offline trên Windows x64 khác, chép kèm browser-runtime hoặc chuẩn bị trước trên máy đó; không dùng bộ Windows cho Linux. Sau khi chuẩn bị, không cần Internet để xem nội dung local.

JCEF/Chromium xử lý hiển thị và mạng của tab Trình duyệt; nó không phải bộ HTTP client tự viết. Giao thức tự cài vẫn nằm trong WebServer và WebClient, được dùng ở tab Yêu cầu. Giới hạn 32 MiB/không HTTPS ở phần trên thuộc WebClient tự viết; browser nhúng dùng bộ mạng Chromium và tải file theo luồng.

`.\test-browser.ps1` chạy **19 kiểm tra native UI** với server và schema MySQL thử: phát audio/video, tua, PDF, ảnh, tải ZIP so byte, history, lùi/tiến và kết nối lại. Xem [ảnh cửa sổ Java](docs/embedded-video.png).

## Public lên EC2

Xem [hướng dẫn EC2 chi tiết](docs/EC2.md): Ubuntu 24.04, Java 21, MySQL, upload từ Windows, chạy thử, systemd cổng 80, thêm nội dung và xử lý lỗi. Có sẵn run-server.sh và deploy/lab4-web.service. Server EC2 chạy headless, không cần tải/cài Chromium; chỉ client Java trên máy người dùng cần bộ trình duyệt. Chưa triển khai vào AWS thực tế.

Các mục có đường dẫn trực tiếp /music, /videos, /documents, /photos, /downloads; mở trực tiếp hoặc tải lại đều được, không dùng dấu #.

## Đọc code và tắt lưu có chọn lọc

Source Java dùng tab width 4; mỗi lab có .editorconfig riêng. Xem [các cách ghi database và dòng cần comment](docs/DATABASE_WRITES.md). SaveOptions.java của lab có từng dòng SAVE-... để bật/tắt; chạy build.ps1 và khởi động lại sau khi sửa. test-save-options.ps1 kiểm chứng các mục đã tắt bằng bản policy tạm.

HEAD/POST có nút riêng trên thanh trình duyệt. HEAD có thể bấm nhiều lần; POST mặc định gửi tới /api/echo. Tải file được ghi action=DOWNLOAD, method vẫn là GET. Tab Kết nối & IP phía server hiển thị phiên/last_seen/trạng thái; client và browser dùng chung phiên, heartbeat 10 giây, mất liên lạc 45 giây thì đánh dấu đã ngắt.


## Đọc code từng bước

Đã tách các thao tác thành hàm có tên, dùng kiểu dữ liệu cụ thể, for/while và if/else. Xem [các hàm nên đọc trước và giải thích mẫu Java](docs/READ_CODE.md).

## Cấu trúc source server/client

- `src/server`: xử lý phía server.
- `src/common`: lớp hỗ trợ giao diện, giao thức và dữ liệu của riêng lab.
- `src/client`: giao diện và thao tác phía client.
- `src/main/resources`: schema và tài nguyên classpath.

Hai phần được biên dịch cùng trong lab để các lớp hỗ trợ có thể sử dụng lẫn nhau; đây không phải hai project Maven độc lập. Lệnh build.ps1 và run.ps1 giữ nguyên. Không dùng source từ lab khác.
