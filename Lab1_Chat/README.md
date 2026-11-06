# HỆ THỐNG CHAT CLIENT - SERVER VỚI GIAO DIỆN ANT DESIGN (JAVA SWING)

Dự án đã được tích hợp bộ phong cách thiết kế **Ant Design (v5.x Design Tokens)** hoàn chỉnh trên nền tảng **Java Swing**:
- **Bảng màu Ant Design chuẩn:** Primary (`#1677ff` - Daybreak Blue), Success (`#52c41a`), Warning (`#faad14`), Error (`#ff4d4f`), Nền Layout (`#f5f5f5`), Nền Card (`#ffffff`), Viền bo góc `arc: 6px`.
- **Thành phần Ant Design:** Card thống kê (Statistic Cards), Tag trạng thái (Status Tags/Badges), Table hiện đại không đường kẻ dọc, Button các loại (Primary, Default, Danger), Message Bubbles Ant Design có đổ bóng và phân màu tương phản cao.

---

## 🌟 1. Server UI - Ant Design Admin Dashboard ([ServerUI.java](src/server/ServerUI.java))
1. **Header Bar:**
   - Logo thương hiệu phong cách Ant Design.
   - Thẻ Tag trạng thái: `● ĐANG CHẠY : 8888` (Success Tag) hoặc `● ĐÃ DỪNG` (Error Tag).
   - Nút bật/tắt máy chủ dạng Ant Danger / Primary Button.
2. **Thẻ Thống kê (Ant Statistic Cards):**
   - 🟢 Đang Online
   - 👤 Tổng Tài khoản
   - 👥 Nhóm Multicast
   - 💬 Tổng Tin nhắn ghi nhận
3. **Các Tab quản trị:**
   - **Tài khoản & Trạng thái:** Bảng hiển thị người dùng kèm Tag Ant Design `Online` (xanh lá nhạt), `Offline` (xám), **Tag IP Đang Kết Nối** (xanh dương nhạt), cùng 2 cột theo dõi thời gian thực: **Thời Gian Vào (Login)** và **Thời Gian Ra (Logout)**. Hỗ trợ thêm tài khoản, đổi mật khẩu, kick người dùng, xoá tài khoản.
   - **Nhóm Chat & Multicast IP:** Quản lý dải IP Multicast (`230.0.x.x`), cổng UDP và số lượng thành viên.
   - **Giám sát Tin nhắn & Broadcast:** Theo dõi toàn bộ luồng chat 1-1 và Multicast. Có thanh **Ant Alert/Broadcast Bar** màu xanh dương nhạt để phát thông báo khẩn tới toàn bộ client.
   - **Nhật ký Hệ thống:** Console đen hiện đại với font monospace `Consolas`.

---

## 💬 2. Client UI - Ant Design Chat Client ([ClientUI.java](src/client/ClientUI.java))
1. **Màn hình Đăng nhập (Ant Design Login Card):**
   - Hộp thoại thẻ trắng đổ bóng viền mờ với tiêu đề và mô tả chào mừng.
   - Các ô nhập Server Host, Port, Username, Password chuẩn Ant Form Input.
   - Nút Primary Button cho Đăng nhập và Default Button cho Đăng ký mới.
2. **Màn hình Trò chuyện (Modern Workspace):**
   - **Thanh Header:** 
     - Huy hiệu hiển thị tài khoản đang đăng nhập.
     - **Thẻ Tag IP Server:** `🌐 Server: <IP>:<Port>` hiển thị địa chỉ máy chủ đang kết nối.
     - **Thẻ Tag IP Client:** `💻 Client IP: <IP>:<Port>` hiển thị địa chỉ IP cục bộ của máy client.
     - Nút Đăng xuất Ant Design.
   - **Cột bên trái (Tabs):**
     - Tab **👤 Danh bạ (1-1):** Danh sách người dùng với Tag `Online`/`Offline`, highlight màu nền `primary-bg` (`#e6f4ff`) khi được chọn.
     - Tab **👥 Nhóm Multicast:** Danh sách nhóm, huy hiệu `Joined`, các nút `+ Tạo nhóm` và `Vào / Rời nhóm`.
   - **Cột bên phải (Khung chat):**
     - Header hiển thị Tên liên hệ/Nhóm + Thẻ Tag phân loại (`TRỰC TIẾP`, `ĐÃ THAM GIA`, `CHƯA THAM GIA`).
     - Bong bóng tin nhắn Ant Design: Tin nhắn người gửi màu xanh `#1677ff` chữ trắng căn phải; tin nhắn đối phương nền trắng viền `#d9d9d9` căn trái kèm thời gian và tên.

---

## 📁 Cấu trúc lưu trữ tài khoản người dùng

Tài khoản người dùng được lưu trữ trong package `server` theo cấu trúc mỗi người dùng một thư mục mang tên `username`:
```
src/server/accounts/
├── admin/
│   └── account.txt
├── alice/
│   └── account.txt
├── bob/
│   └── account.txt
└── charlie/
    └── account.txt
```

Nội dung mỗi file `account.txt` chứa đầy đủ các trường thông tin:
```yaml
username: alice
password: 123456
createdAt: 2026-10-09 14:18:56
lastLoginTime: 2026-10-09 14:28:40
lastLogoutTime: 2026-10-09 14:29:10
```
- Khi người dùng đăng nhập thành công, Server tự động ghi nhận **thời gian vào (lastLoginTime)**.
- Khi người dùng đăng xuất hoặc ngắt kết nối, Server tự động ghi nhận **thời gian ra (lastLogoutTime)**.
- Thông tin được lưu kiên cố (persist) vào file `account.txt` tương ứng trong package `server`.

---

## 👥 Cấu trúc lưu trữ nhóm trò chuyện (Multicast Groups)

Toàn bộ thông tin các nhóm trò chuyện được lưu trữ kiên cố (persist) vào package `server` tại thư mục `src/server/groups/`, **được đặt tên theo số thứ tự `group_xxx` kèm theo tên nhóm**:
```
src/server/groups/
├── group_001_General/
│   └── group.txt
├── group_002_Nhom_Hoc_Tap/
│   └── group.txt
└── group_003_Gaming/
    └── group.txt
```

Nội dung mỗi file `group.txt`:
```yaml
groupId: group_001
groupName: General
multicastAddress: 230.0.0.1
multicastPort: 50000
members: alice, bob
```
- Khi tạo nhóm mới (từ Client hoặc Server UI), Server tự động tăng số thứ tự `group_xxx` và tạo thư mục mang tên `group_xxx_<tên_nhóm>` chứa file `group.txt`.
- Khi người dùng tham gia (`Join`) hoặc rời (`Leave`) nhóm, danh sách `members` trong `group.txt` lập tức được đồng bộ.
- Khi Server khởi động lại, toàn bộ danh sách nhóm, thông tin IP/Port Multicast và thành viên được nạp lại đầy đủ mà không bị mất.
- Khi giải tán nhóm, Server tự động dọn dẹp và xoá thư mục của nhóm tương ứng.

---

## 🚀 Hướng dẫn khởi chạy

### Cách 1: Chạy trực tiếp (Khuyên dùng)
- Chạy **[run_server.bat](run_server.bat)** để mở Giao diện Server Quản trị Ant Design.
- Chạy **[run_client.bat](run_client.bat)** (mở 2 hoặc nhiều lần để đăng nhập nhiều tài khoản chat thử).

### Cách 2: Chạy từ Terminal / Command Prompt
Biên dịch:
```powershell
javac -cp "lib/flatlaf-3.5.4.jar;src" -d bin -sourcepath src src/common/*.java src/server/*.java src/client/*.java
```

Khởi chạy Server:
```powershell
java -cp "bin;lib/flatlaf-3.5.4.jar" server.ServerUI
```

Khởi chạy Client:
```powershell
java -cp "bin;lib/flatlaf-3.5.4.jar" client.ClientUI
```

### Tài khoản

Ứng dụng nạp các account.txt đang có trong src/server/accounts; không tự tạo các tài khoản demo được liệt kê trong bản README cũ. Có thể tạo tài khoản từ Server UI hoặc Đăng ký ở Client UI.

## Kiểm tra phiên bản làm lại

Lab 1 lưu lịch sử chat bằng JDBC/MySQL. Tài khoản và nhóm vẫn lưu file TXT. File broadcast là server fan-out TCP tới mọi client, chat nhóm dùng UDP multicast thật. Đọc [báo cáo kiểm tra](docs/REVIEW.md). Build bằng `.\build.ps1`, kiểm thử `.\test.ps1`; test dùng thư mục riêng trong target, không sửa tài khoản/nhóm hiện có.

## Đọc code và nơi lưu dữ liệu

[Hướng dẫn TXT/MySQL và dòng comment để tắt ghi](docs/DATABASE_WRITES.md). Source được định dạng bằng tab width 4, có .editorconfig riêng.

## Đọc code từng bước

Đã tách các thao tác thành hàm có tên, dùng kiểu dữ liệu cụ thể, for/while và if/else. Xem [các hàm nên đọc trước và giải thích mẫu Java](docs/READ_CODE.md).

## Lưu chat vào MySQL

Bật MySQL Laragon và sửa config/database.properties trước khi mở server. Xem [cấu hình HeidiSQL và cách tắt ghi](docs/DATABASE_WRITES.md). Server tự tạo bảng chat_messages và nạp lại lịch sử khi mở lại. Chạy build.ps1 rồi run_server.bat/run_client.bat như trước. Nếu dùng IntelliJ, mở pom.xml của Lab 1 dưới dạng Maven project để nạp JDBC driver.
