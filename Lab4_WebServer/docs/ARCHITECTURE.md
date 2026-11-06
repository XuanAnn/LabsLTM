# Kiến trúc — Lab4_WebServer

Mẫu giao diện và phân tách trách nhiệm theo Lab 1 vừa được người dùng làm lại:

- ServerUI: dashboard header, statistic cards, bảng dữ liệu, tài khoản, IP/phiên, log và cấu hình MySQL.
- ServerController: quản lý start/stop/restart dịch vụ, tách khỏi EDT.
- ClientUI: login card (nếu cần tài khoản), workspace theo nghiệp vụ, badge tài khoản/server/client IP.
- ClientController: logic nghiệp vụ và giao thức; UI không tự ghi dữ liệu server.
- WebServer/WebClient: giao thức HTTP/1.1 qua TCP.
- support/Db: MySQL JDBC của riêng lab, đọc config/database.properties; schema.sql trong jar khởi tạo bảng.
- support/AntDesignTheme: bản sao cục bộ từ Lab 1; FlatLaf làm nền UI. Không có import/source/runtime dependency tới Lab 1 hay lab khác.

Việc lấy Lab 1 làm mẫu không thay FTP/SMTP/POP3/HTTP bằng Java ObjectStream: các giao thức này vẫn trao đổi đúng dạng lệnh/dòng/byte riêng. Không dùng thư viện FTP, JavaMail, HTTP framework. Connector/J dùng cho database, FlatLaf chỉ dùng cho Swing.

Xem README để chạy demo, MYSQL_HEIDISQL.md để kết nối MySQL và VALIDATION.md để phân biệt kiểm thử tự động với bước còn cần làm tại môi trường thực.

Trang media dùng public/index.html + library.css + library.js (không có framework web). GET/HEAD /api/library quét các folder music/videos/documents/photos/downloads, trả tên, URL, kích thước và metadata tác giả/nguồn từ media/credits.properties; danh sách không lưu cache. Browser dùng audio/video/iframe/img, tải file bằng liên kết download. WebServer stream file trực tiếp và byte range; MySQL requests ghi mọi request. Nội dung file ở public/media, database giữ nhật ký.

EmbeddedBrowser chứa native Chromium/JCEF trong JPanel của tab Trình duyệt. BrowserRuntime tải/cài native theo hệ điều hành vào browser-runtime, khởi tạo ngoài EDT và dùng một CefApp cho mỗi tiến trình. Mỗi kết nối có CefClient/CefBrowser riêng; logout đóng browser/client, đóng cửa sổ chính dispose CefApp. Trạng thái hiển thị được đồng bộ khi browser tạo xong và khi đổi tab. Callback download chụp giá trị cần dùng trước khi chuyển sang EDT, vì CefDownloadItem chỉ hợp lệ trong callback native.

Tab Trình duyệt dùng mạng của Chromium; tab Yêu cầu dùng WebClient tự cài qua socket. Hai phần được phân biệt trong README để trình diễn đúng yêu cầu tự viết giao thức. History client nhận điều hướng và download hoàn tất từ browser; file được lưu qua JFileChooser. Server EC2 không khởi tạo CefApp và không cần native browser.

run-server.sh chạy cùng jar trên Linux; deploy/lab4-web.service dành cho EC2 Ubuntu, cấp quyền bind cổng 80. Các bước cài đặt trong EC2.md.
