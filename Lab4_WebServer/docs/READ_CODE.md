# Cách đọc code từng bước

Code ứng dụng dùng kiểu dữ liệu cụ thể, tên biến theo vai trò, vòng for/while và if/else có dấu ngoặc. Mỗi hàm giao diện xử lý một thao tác. Tab width 4 theo .editorconfig của lab.

## Bắt đầu ở các hàm này

| File:dòng | Hàm / đoạn | Vai trò |
|---|---|---|
| `src/client/ClientUI.java:58` | `registerEvents(` | Các nút gọi connectToServer, sendRequest, chooseDownloadFile. |
| `src/client/ClientUI.java:155` | `private void loadResponse(` | Gửi socket trên luồng nền rồi cập nhật UI bằng showResponse. |
| `src/client/ClientUI.java:163` | `private void showResponse(` | Vòng for ghép các header; if/else phân biệt text và dữ liệu nhị phân. |
| `src/client/ClientController.java:92` | `public WebClient.Response request(` | Gửi yêu cầu và lưu history nếu đang bật. |
| `src/client/WebClient.java:22` | `public static Response request(` | Tự viết dòng request, header và đọc response HTTP. |
| `src/server/WebServer.java:67` | `private void handle(` | Đọc method/URL/header, chọn dịch vụ rồi trả dữ liệu. |
| `src/client/EmbeddedBrowser.java:146` | `private void registerBrowserEvents(` | Các callback thư viện được chia theo trang, cửa sổ và download. |
| `src/common/Db.java:113` | `public void exec(` | Điểm JDBC thực thi câu ghi dữ liệu. |

## Các mẫu Java cần hiểu

1. `event -> tenHam()` chỉ nối nút bấm với một hàm thường. Đọc tiếp hàm có tên đó để thấy các bước xử lý.
2. `if/else` kiểm tra dữ liệu và chọn cách xử lý. Các nhánh có `{}` đầy đủ.
3. `for` duyệt danh sách hoặc Map; tên như request, response, entry, connection, statement thể hiện vai trò.
4. `try (...)` tự đóng socket/file/Connection khi ra khỏi khối. `catch` xử lý lỗi nhận được.
5. Socket và database có thể chậm nên chạy ở luồng nền; `SwingUtilities.invokeLater` chuyển phần cập nhật màn hình về luồng Swing.

## Chỗ ghi dữ liệu

[DATABASE_WRITES.md](DATABASE_WRITES.md) chỉ rõ bảng/file, câu SQL và các dòng SaveOptions có thể comment. Sau khi comment, build lại và khởi động lại server/client của lab.

Trong Db, đọc theo thứ tự constructor → readSettings → initializeSchema → open → exec/rows. `?` trong SQL nhận giá trị qua setObject/setString; `executeUpdate` ghi, `executeQuery` đọc. Mặc định auto-commit; Mail/RMI dùng transaction riêng khi ghi nhiều dòng.

Phần JCEF nằm trong EmbeddedBrowser vì thư viện cần callback riêng. Đọc ClientUI/WebClient/WebServer trước; sau đó đọc registerPageEvents/registerDownloadEvents. HEAD/POST trên thanh trình duyệt vẫn gọi WebClient tự viết.

## Chạy thử

```powershell
.\build.ps1
.\test.ps1
```

Chạy thêm `.\test-save-options.ps1` để xem các trường hợp không ghi DB đã được kiểm chứng.

Xem thêm [luồng giao thức và kịch bản demo](PROTOCOL_FLOW.md).
