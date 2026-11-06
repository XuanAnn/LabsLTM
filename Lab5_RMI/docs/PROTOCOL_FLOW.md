# Luồng xử lý để đọc và trình bày

## Lần theo một bản ghi đồng bộ

1. Client tìm đối tượng Store qua RMI registry rồi gọi phương thức từ xa.
2. Node.put kiểm tra dữ liệu, tạo version mới và ghi dữ liệu vào MySQL của node đó.
3. Khi đồng bộ, các node trao đổi snapshot và merge theo thứ tự version/origin được định nghĩa trong code.
4. Bản cũ không ghi đè bản mới. Cờ deleted giữ dấu xóa để node khác nhận được việc xóa, tránh làm sống lại dữ liệu cũ.
5. Transaction nhóm các thay đổi cần ghi cùng nhau; commit hoàn tất, rollback khi lỗi. Hai node dùng hai cấu hình/schema riêng.

Khi demo: thêm ở A, kiểm tra B; sửa ở B, kiểm tra A; xóa rồi đồng bộ lại. Sau đó dừng/mở lại một node để kiểm tra dữ liệu còn trong MySQL. Mô hình hai máy thật và hai máy ảo cần cấu hình IP/port thực tế trước khi nghiệm thu.

Xem [các hàm trong source](READ_CODE.md) và [cách lưu dữ liệu](DATABASE_WRITES.md).
