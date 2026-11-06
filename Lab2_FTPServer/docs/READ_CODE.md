# Cách đọc code từng bước

Code ứng dụng dùng kiểu dữ liệu cụ thể, tên biến theo vai trò, vòng for/while và if/else có dấu ngoặc. Mỗi hàm giao diện xử lý một thao tác. Tab width 4 theo .editorconfig của lab.

## Bắt đầu ở các hàm này

| File:dòng | Hàm / đoạn | Vai trò |
|---|---|---|
| `src/client/ClientUI.java:47` | `registerEvents(` | Các listener ngắn gọi hàm xử lý có tên. |
| `src/client/ClientUI.java:178` | `uploadSelectedFile(` | Đọc dòng đang chọn rồi chuyển cho hàm uploadFile. |
| `src/client/ClientController.java:39` | `upload(` | Chuyển thư mục server bằng CWD, sau đó upload file. |
| `src/client/FtpClient.java:64` | `upload(` | Mở kênh dữ liệu, gửi STOR rồi gửi byte của file. |
| `src/server/FtpServer.java:201` | `case "LIST",` | Server xử lý LIST/RETR/STOR trong switch. |
| `src/common/Db.java:66` | `initializeSchema(` | Đọc schema.sql và tạo bảng; kiểm tra công tắc ACCOUNT cho dữ liệu mẫu. |
| `src/common/Db.java:102` | `public void exec(` | Bind từng tham số rồi thực thi INSERT/UPDATE/DELETE. |

## Các mẫu Java cần hiểu

1. `event -> tenHam()` chỉ nối nút bấm với một hàm thường. Đọc tiếp hàm có tên đó để thấy các bước xử lý.
2. `if/else` kiểm tra dữ liệu và chọn cách xử lý. Các nhánh có `{}` đầy đủ.
3. `for` duyệt danh sách hoặc Map; tên như request, response, entry, connection, statement thể hiện vai trò.
4. `try (...)` tự đóng socket/file/Connection khi ra khỏi khối. `catch` xử lý lỗi nhận được.
5. Socket và database có thể chậm nên chạy ở luồng nền; `SwingUtilities.invokeLater` chuyển phần cập nhật màn hình về luồng Swing.

## Chỗ ghi dữ liệu

[DATABASE_WRITES.md](DATABASE_WRITES.md) chỉ rõ bảng/file, câu SQL và các dòng SaveOptions có thể comment. Sau khi comment, build lại và khởi động lại server/client của lab.

Trong Db, đọc theo thứ tự constructor → readSettings → initializeSchema → open → exec/rows. `?` trong SQL nhận giá trị qua setObject/setString; `executeUpdate` ghi, `executeQuery` đọc. Mặc định auto-commit; Mail/RMI dùng transaction riêng khi ghi nhiều dòng.

## Chạy thử

```powershell
.\build.ps1
.\test.ps1
```

Chạy thêm `.\test-save-options.ps1` để xem các trường hợp không ghi DB đã được kiểm chứng.

Xem thêm [luồng giao thức và kịch bản demo](PROTOCOL_FLOW.md).
