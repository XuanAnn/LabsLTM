# Cách đọc code từng bước

Code ứng dụng dùng kiểu dữ liệu cụ thể, tên biến theo vai trò, vòng for/while và if/else có dấu ngoặc. Mỗi hàm giao diện xử lý một thao tác. Tab width 4 theo .editorconfig của lab.

## Bắt đầu ở các hàm này

| File:dòng | Hàm / đoạn | Vai trò |
|---|---|---|
| `src/client/ClientUI.java:42` | `registerEvents(` | Nút lưu/xóa gọi saveEntry và deleteEntry. |
| `src/client/ClientUI.java:165` | `private void showSnapshot(` | TreeMap sắp xếp theo key; vòng for thêm từng dòng. |
| `src/client/ClientController.java:21` | `public Store.Value put(` | Gọi Store trên server qua RMI. |
| `src/server/Node.java:61` | `public synchronized Value put(` | Tạo giá trị và phiên bản mới rồi gọi merge. |
| `src/server/Node.java:89` | `private long nextVersion(` | Duyệt các bản ghi, lấy version lớn nhất rồi cộng 1. |
| `src/server/Node.java:99` | `public synchronized void merge(` | Kiểm tra bản nhận được, bỏ bản cũ rồi ghi SQL theo batch. |
| `src/server/Node.java:148` | `private void addValueToBatch(` | Gán từng cột vào PreparedStatement trước addBatch. |
| `src/common/Db.java:96` | `public void exec(` | Thực thi INSERT/UPDATE/DELETE bằng JDBC. |

## Các mẫu Java cần hiểu

1. `event -> tenHam()` chỉ nối nút bấm với một hàm thường. Đọc tiếp hàm có tên đó để thấy các bước xử lý.
2. `if/else` kiểm tra dữ liệu và chọn cách xử lý. Các nhánh có `{}` đầy đủ.
3. `for` duyệt danh sách hoặc Map; tên như request, response, entry, connection, statement thể hiện vai trò.
4. `try (...)` tự đóng socket/file/Connection khi ra khỏi khối. `catch` xử lý lỗi nhận được.
5. Socket và database có thể chậm nên chạy ở luồng nền; `SwingUtilities.invokeLater` chuyển phần cập nhật màn hình về luồng Swing.

## Chỗ ghi dữ liệu

[DATABASE_WRITES.md](DATABASE_WRITES.md) chỉ rõ bảng/file, câu SQL và các dòng SaveOptions có thể comment. Sau khi comment, build lại và khởi động lại server/client của lab.

Trong Db, đọc theo thứ tự constructor → readSettings → initializeSchema → open → exec/rows. `?` trong SQL nhận giá trị qua setObject/setString; `executeUpdate` ghi, `executeQuery` đọc. Mặc định auto-commit; Mail/RMI dùng transaction riêng khi ghi nhiều dòng.

Version dùng để hai node thống nhất dữ liệu. Khi version bằng nhau, Store.Value.compare dùng origin để chọn nhất quán. deleted=true là dấu xóa được đồng bộ; không thay bằng xóa vật lý vì node cũ có thể gửi lại bản chưa xóa.

## Chạy thử

```powershell
.\build.ps1
.\test.ps1
```

Chạy thêm `.\test-save-options.ps1` để xem các trường hợp không ghi DB đã được kiểm chứng.

Xem thêm [luồng giao thức và kịch bản demo](PROTOCOL_FLOW.md).
