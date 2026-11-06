# Cách đọc code từng bước

Code ứng dụng dùng kiểu dữ liệu cụ thể, tên biến theo vai trò, vòng for/while và if/else có dấu ngoặc. Mỗi hàm giao diện xử lý một thao tác. Tab width 4 theo .editorconfig của lab.

## Bắt đầu ở các hàm này

| File:dòng | Hàm / đoạn | Vai trò |
|---|---|---|
| `src/client/ClientUI.java:18` | `ClientUI` | Nút bấm, danh sách người dùng và nhóm. |
| `src/client/ClientController.java:83` | `sendDirectMessage(` | Tạo và gửi tin nhắn riêng qua kết nối đã đăng nhập. |
| `src/client/ClientController.java:149` | `sendGroupMessage(` | Gửi tin vào địa chỉ multicast của nhóm. |
| `src/client/ClientController.java:372` | `validMulticastInterface(` | Dùng if và vòng while để chọn card mạng IPv4 phù hợp. |
| `src/server/ClientHandler.java:10` | `ClientHandler` | Đọc yêu cầu của một client và chuyển sang server controller. |
| `src/server/ServerController.java:471` | `saveAccountFile(` | Lưu các trường tài khoản ra TXT. |
| `src/server/ServerController.java:281` | `saveGroupFile(` | Lưu nhóm và địa chỉ multicast ra TXT. |

## Các mẫu Java cần hiểu

1. `event -> tenHam()` chỉ nối nút bấm với một hàm thường. Đọc tiếp hàm có tên đó để thấy các bước xử lý.
2. `if/else` kiểm tra dữ liệu và chọn cách xử lý. Các nhánh có `{}` đầy đủ.
3. `for` duyệt danh sách hoặc Map; tên như request, response, entry, connection, statement thể hiện vai trò.
4. `try (...)` tự đóng socket/file/Connection khi ra khỏi khối. `catch` xử lý lỗi nhận được.
5. Socket và database có thể chậm nên chạy ở luồng nền; `SwingUtilities.invokeLater` chuyển phần cập nhật màn hình về luồng Swing.

## Chỗ ghi dữ liệu

[DATABASE_WRITES.md](DATABASE_WRITES.md) chỉ rõ bảng/file, câu SQL và các dòng SaveOptions có thể comment. Sau khi comment, build lại và khởi động lại server/client của lab.

Lab 1 dùng TXT cho tài khoản/nhóm và MySQL cho lịch sử chat. ChatDatabase thực thi INSERT và nạp lại lịch sử cho server.

## Chạy thử

```powershell
.\build.ps1
.\test.ps1
```

Xem thêm [luồng giao thức và kịch bản demo](PROTOCOL_FLOW.md).
