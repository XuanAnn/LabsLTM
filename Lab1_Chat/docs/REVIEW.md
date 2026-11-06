# Kiểm tra Lab 1 người dùng làm lại

## Giữ nguyên kiến trúc

Java Swing UI / Controller / common models. Account và group dùng file text; lịch sử chat lưu MySQL. Giao thức app chat dùng ObjectInputStream/ObjectOutputStream trên TCP; tin nhóm dùng UDP multicast. File broadcast thực hiện fan-out qua TCP từ server, không phải UDP IP broadcast. History lưu MySQL và nạp lại vào bảng lịch sử server sau restart.

## Các lỗi đã sửa

1. Sau loadGroups, bộ cấp phát IP multicast chưa tiến theo địa chỉ đã lưu: nhóm mới sau restart có thể dùng lại IP cũ. Nay cập nhật cả IP và port.
2. Khi đổi phiên cùng user, handler cũ có thể remove phiên mới. Nay map thay thế trước, remove chỉ khi đúng handler hiện tại; một kết nối đã login phải logout trước khi đổi user.
3. Deadlock khi nhiều client ngắt đồng thời: callback vào ServerController khi giữ khóa ClientHandler tạo vòng chờ. Thread dump đã xác nhận lỗi; nay write lock chỉ bảo vệ stream, đóng socket/callback không giữ khóa handler.
4. Gửi file chỉ read một lần và bỏ file rỗng. Nay readAllBytes để truyền đủ byte, kể cả file 0 byte.
5. Thread pool còn giữ luồng khi stop. Nay shutdown khi stop và tạo lại khi restart; isRunning volatile.
6. Theme chỉ gán UIManager tokens, chưa bật FlatLaf dù có jar. Nay setup FlatLightLaf trước khi áp tokens. Script bat chuyển đúng working directory và biên dịch source trước khi chạy, tránh chạy bin cũ.

7. Sender UDP và receiver multicast trước đây có thể chọn hai card khác nhau (đặc biệt máy có VMware/Hyper-V). Nay chọn cùng adapter IPv4, reuse-address trước bind, TTL=1 và lọc đúng group ID.

## Đã kiểm thử

22 kiểm tra tích hợp bằng 6 client TCP thật: đăng ký/login, unicast đúng danh tính, broadcast file rỗng tới 6 client, thay phiên cùng user, account text persist, IP/port nhóm không trùng sau reload; dừng server thoát sạch.

Test dùng -Dlab1.data.dir trỏ tới thư mục target tách biệt, không đọc/ghi credential hoặc group thật trong src/server. Đã kiểm thử UDP multicast tới hai ClientController thực trên cùng máy; sender/receiver nay cùng chọn adapter IPv4 theo route hoặc kết nối TCP, không chọn nhầm adapter VM qua getLocalHost. Chưa chạy nghiệm thu multicast giữa các máy vật lý; giữ hiện trạng file broadcast TCP rõ ràng để đối chiếu tiêu chí giảng viên.
