# Lab 3 — Mail Server và Client

## Chạy

Java 21, Maven, MySQL Laragon. Mỗi project độc lập. Bật MySQL, làm theo [HeidiSQL](docs/MYSQL_HEIDISQL.md), sửa config/database.properties rồi chạy trong folder này:

```powershell
.\build.ps1
.\check-db.ps1
.\run.ps1 server
# terminal khác
.\run.ps1 client
```

run_server.bat / run_client.bat mở UI; server-cli chạy không GUI. Sau cập nhật phải mở lại cả server lẫn client. Không cần xóa database cũ: schema tự thêm email và bảng Đã gửi, giữ tài khoản và dữ liệu hiện có.

## Tài khoản và email

Đăng ký nhập tên tài khoản, email và mật khẩu. Tên tài khoản dùng để đăng nhập; email dùng khi gửi/nhận. Username và email đều không được trùng tài khoản khác. Có alice/123 và bob/123, email mặc định alice@localhost và bob@localhost. Người dùng cũ được bổ sung email username@localhost tự động.

Sau đăng nhập chọn Tài khoản để đổi email của mình. Thư mới hiển thị From dạng Tên tài khoản <email>. Để gắn email thật với một tài khoản đã có, dùng chức năng này; không tự ghi đè hồ sơ người dùng bằng tài khoản Gmail cấu hình trên máy.

UI dùng Java Swing, password nhập hiện chữ theo yêu cầu. Giao diện không có các dòng giới thiệu công nghệ/ngôn ngữ. IP client chỉ hiển thị IP, không hiển thị cổng; địa chỉ server vẫn có cổng.

## Thư đến, Đã gửi và xóa thư

- Soạn thư gửi nội bộ: người nhận có thể là email đã gắn với tài khoản, hoặc alias username@localhost. SMTP xác minh tài khoản và email người gửi.
- Đã gửi lưu bền vững trên MySQL. SMTP nội bộ ghi mailbox người nhận và bản gửi của người gửi trong cùng transaction.
- Soạn bằng Gmail gửi qua tài khoản Gmail đã cấu hình; sau khi Gmail chấp nhận, client đưa bản sao về Mail Server qua phiên đã đăng nhập để lưu MySQL sent_mail. Không chỉ lưu trên máy client.
- Xóa thư có xác nhận, xóa bản trong thư mục đang chọn bằng UID ổn định. Xóa Đã gửi không xóa bản bên nhận. Xóa bản sao Gmail tại đây không xóa thư trên Gmail từ xa.
- Nếu Gmail đã chấp nhận nhưng lưu bản sao gặp lỗi, ứng dụng báo rõ thư đã gửi nhưng chưa lưu; không tự gửi lại gây thư trùng.

Trong HeidiSQL, xem users (tài khoản/email), mail (thư đến), sent_mail (bản gửi nội bộ và Gmail), sessions (IP/phiên) và audit. sent_mail có owner (tài khoản cục bộ), sender/recipient (email), uid, created_at và body chứa toàn bộ MIME: tên hiển thị, tiêu đề, nội dung, ngày gửi. UI giải mã MIME để hiển thị. Chạy sql/verify.sql để xem dữ liệu; có truy vấn CONVERT(body USING utf8mb4).

Lịch sử gửi nội bộ còn trong mail được đưa vào sent_mail một lần khi nâng cấp; marker migration ngăn thư đã xóa xuất hiện lại sau restart.

## Gmail

Chọn Cài đặt gửi thư để sửa tài khoản Gmail và mật khẩu ứng dụng, sau đó Soạn bằng Gmail. Thiết lập máy hiện tại ở config/gmail.local.properties, đã bị loại khỏi Git; không có mật khẩu ứng dụng trong source/SQL/tài liệu. Cấu hình file được ưu tiên, biến GMAIL_USER/GMAIL_APP_PASSWORD là giá trị dự phòng khi chưa có file.

```powershell
.\check-gmail.ps1
```

Lệnh này chỉ kiểm tra đăng nhập bằng STARTTLS/AUTH rồi NOOP/QUIT, không gửi thư. Đăng nhập Gmail thật đã được xác minh thành công. Chưa gửi thư thử ra ngoài; kiểm tra hộp thư người nhận/spam khi thực hiện demo gửi thư thật.

Gửi Gmail vẫn dùng SMTP tự cài đặt tới smtp.gmail.com:587, JSSE cho TLS, không JavaMail. Xác thực/TLS là yêu cầu của Gmail; [hướng dẫn Google](https://support.google.com/mail/answer/7104828?hl=en).

## Giao thức và kiến trúc

SMTP: EHLO/HELO, AUTH LOGIN, MAIL FROM, RCPT TO, DATA, RSET, NOOP, QUIT; dot-stuffing và giới hạn 10 MiB. POP3: USER/PASS, CAPA, STAT, LIST, UIDL, RETR, DELE, RSET, NOOP, QUIT; xóa chỉ commit khi QUIT, ngắt kết nối không xóa.

Extension riêng của bài: XREGISTER username base64-password [base64-email]; XPROFILE/XSETEMAIL cho hồ sơ; XBOX INBOX/SENT chọn thư mục; XSAVE SENT lưu bản gửi bên ngoài. Đây không phải các lệnh chuẩn POP3. Base64 chỉ đóng gói dữ liệu.

UI/Controller tách riêng, server start/stop/restart và dashboard quản lý tài khoản, email, thư đến/đã gửi, phiên/IP, dữ liệu và nhật ký. Các thành phần kỹ thuật được mô tả trong tài liệu, không đưa vào banner giao diện.

## Kiểm thử và ảnh

- [50/50 kiểm tra tích hợp](docs/VALIDATION.md)
- [Kiến trúc](docs/ARCHITECTURE.md)
- [MySQL + HeidiSQL](docs/MYSQL_HEIDISQL.md)
- [Đăng nhập](docs/client.png) · [Thư đến](docs/workspace.png) · [Đã gửi](docs/sent.png) · [Server](docs/server.png)

.\test.ps1 tạo schema ltm_test_* riêng và dọn lại, không sửa schema demo. .\preview.ps1 render UI; .\preview-workspace.ps1 kết nối server/database thật để kiểm tra workspace Thư đến và Đã gửi.

## Gửi riêng, gửi nhóm và gửi tất cả

Trong Soạn thư, chọn Cách gửi:

- Gửi riêng: một người nhận.
- Gửi nhóm: bấm Chọn người nhận để đánh dấu từ hai tài khoản; bảng có tên tài khoản và email, Chọn tất cả, Bỏ chọn, Xác nhận. Có thể nhập nhiều email/tên <email>, cách nhau bằng dấu phẩy.
- Gửi tất cả: server tự lấy mọi tài khoản local tại thời điểm gửi, gồm người ngoại tuyến. Mặc định không gửi vào Inbox của người gửi; bật Gửi cả cho tôi nếu muốn nhận cả bản đó.

Theo xác nhận của người dùng, đây là gửi nhiều người qua SMTP, không phải UDP IP multicast/broadcast. Một lần gửi tạo một bản Sent và một bản Inbox cho mỗi tài khoản đích, không trùng nếu cùng người nhận được chọn qua nhiều alias. Nếu có địa chỉ không hợp lệ, client dừng trước DATA để không giao thư một phần. Gửi tất cả chỉ giao trong server local, không chuyển những email đã đăng ký ra Gmail.

XCONTACTS cung cấp danh sách tài khoản qua phiên POP3 đã đăng nhập. XRCPT ALL [SELF] là extension SMTP local để server chốt danh sách broadcast; gửi nhóm dùng nhiều RCPT TO rồi một DATA.

[Soạn nhóm](docs/compose-group.png) · [Chọn người nhận](docs/recipient-picker.png) · [Gửi tất cả](docs/compose-broadcast.png).

## IP và dữ liệu TXT trên server

[Định dạng và cấu trúc file](docs/DATA_FILES.md). MySQL vẫn là nguồn dữ liệu chính, đồng thời chương trình xuất bản local sang server/data/accounts và server/data/mails. Không thay MySQL bằng file. Cấu hình thư mục bằng data.directory trong config/database.properties.

IP gửi/nhận không kèm cổng. IP gửi là địa chỉ client được server local quan sát. IP nhận lấy từ lần kết nối đã biết của tài khoản đích; người chưa từng kết nối được ghi Chưa kết nối, khi đăng nhập lần đầu sẽ có IP quan sát được. Với Gmail bên ngoài, không biết IP thiết bị người nhận nên ghi Không xác định (bên ngoài), không gán IP giả.

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
