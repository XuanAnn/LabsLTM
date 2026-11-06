# Kiến trúc — Lab3_MailServer

Mẫu giao diện và phân tách trách nhiệm theo Lab 1 vừa được người dùng làm lại:

- ServerUI: dashboard header, statistic cards, bảng dữ liệu, tài khoản, IP/phiên, log và cấu hình MySQL.
- ServerController: quản lý start/stop/restart dịch vụ, tách khỏi EDT.
- ClientUI: login card (nếu cần tài khoản), workspace theo nghiệp vụ, badge tài khoản/server/client IP.
- ClientController: logic nghiệp vụ và giao thức; UI không tự ghi dữ liệu server.
- MailServer/MailClient: giao thức SMTP/POP3 TCP.
- support/Db: MySQL JDBC của riêng lab, đọc config/database.properties; schema.sql trong jar khởi tạo bảng.
- support/AntDesignTheme: bản sao cục bộ từ Lab 1; FlatLaf làm nền UI. Không có import/source/runtime dependency tới Lab 1 hay lab khác.

Việc lấy Lab 1 làm mẫu không thay FTP/SMTP/POP3/HTTP bằng Java ObjectStream: các giao thức này vẫn trao đổi đúng dạng lệnh/dòng/byte riêng. Không dùng thư viện FTP, JavaMail, HTTP framework. Connector/J dùng cho database, FlatLaf chỉ dùng cho Swing.

Xem README để chạy demo, MYSQL_HEIDISQL.md để kết nối MySQL và VALIDATION.md để phân biệt kiểm thử tự động với bước còn cần làm tại môi trường thực.

## Hồ sơ và bản gửi

users.email gắn email với username. XPROFILE/XSETEMAIL và XREGISTER mở rộng phục vụ hồ sơ. ClientController dùng UIDL để xóa thư ổn định giữa các lần refresh; XBOX chọn INBOX/SENT. SMTP lưu mail + sent_mail trong cùng transaction. Gửi Gmail dùng GmailDelivery mặc định trao đổi SMTP thật; sau chấp nhận, XSAVE lưu bản sao qua Mail Server. Điểm thay thế delivery chỉ phục vụ kiểm thử, không dùng trong UI sản phẩm. GmailSettings đọc file cấu hình cục bộ bị gitignore.

## Nhóm, broadcast và file server

ComposeDialog lấy contacts qua ClientController; XCONTACTS trả tên/email từ users. Nhóm gửi nhiều RCPT TO trong cùng SMTP DATA. Broadcast dùng XRCPT ALL [SELF] để server chọn danh sách hiện tại. Không dùng Gmail delivery cho các thao tác local này.

MailText xử lý MIME và metadata IP/name giao thư. DataFiles xuất snapshot từ MySQL vào TXT; Db cập nhật account mirror, MailServer cập nhật sau commit gửi/xóa/lưu Sent. UID và MySQL là căn cứ dọn file đã xóa, không đọc TXT thành database riêng.
