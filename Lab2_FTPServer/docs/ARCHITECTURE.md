# Kiến trúc — Lab2_FTPServer

Mẫu giao diện và phân tách trách nhiệm theo Lab 1 vừa được người dùng làm lại:

- ServerUI: dashboard header, statistic cards, bảng dữ liệu, tài khoản, IP/phiên, log và cấu hình MySQL.
- ServerController: quản lý start/stop/restart dịch vụ, tách khỏi EDT.
- ClientUI: login card (nếu cần tài khoản), workspace theo nghiệp vụ, badge tài khoản/server/client IP.
- ClientController: logic nghiệp vụ và giao thức; UI không tự ghi dữ liệu server.
- FtpServer/FtpClient: giao thức FTP control/data TCP.
- support/Db: MySQL JDBC của riêng lab, đọc config/database.properties; schema.sql trong jar khởi tạo bảng.
- support/AntDesignTheme: bản sao cục bộ từ Lab 1; FlatLaf làm nền UI. Không có import/source/runtime dependency tới Lab 1 hay lab khác.

Việc lấy Lab 1 làm mẫu không thay FTP/SMTP/POP3/HTTP bằng Java ObjectStream: các giao thức này vẫn trao đổi đúng dạng lệnh/dòng/byte riêng. Không dùng thư viện FTP, JavaMail, HTTP framework. Connector/J dùng cho database, FlatLaf chỉ dùng cho Swing.

Xem README để chạy demo, MYSQL_HEIDISQL.md để kết nối MySQL và VALIDATION.md để phân biệt kiểm thử tự động với bước còn cần làm tại môi trường thực.
