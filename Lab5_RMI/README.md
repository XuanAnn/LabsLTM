# Lab 5 — RMI đồng bộ hai MySQL databases

## Chạy độc lập

Mỗi folder là một project riêng, không cần source/jar/config của lab khác. JDK 21 + Maven. UI và Controller tách riêng theo Lab 1; FlatLaf + bản sao AntDesignTheme là phần giao diện Java Swing, không có frontend web (trừ nội dung HTTP của Lab 4).

1. Bật MySQL trong Laragon.
2. Làm theo [MySQL + HeidiSQL](docs/MYSQL_HEIDISQL.md), chạy sql/schema.sql và sửa config/database.properties.
3. Trong folder này:

```powershell
.\build.ps1
.\check-db.ps1
.\run.ps1 server
```

Terminal thứ hai: `.\run.ps1 client`. Có thể bấm run_server.bat / run_client.bat để tự biên dịch rồi mở UI. Chế độ không GUI: `.\run.ps1 server-cli`.

Tài khoản MySQL root/password trong config **khác** tài khoản đăng nhập ứng dụng. Password trên GUI hiện chữ, tài khoản lưu đơn giản theo yêu cầu. Không có SQLite hoặc DB file dự phòng.

## UI và cấu hình hai node

Server dashboard theo Lab 1: thống kê, bảng key/value/version/origin/deleted, audit, tab Cấu hình MySQL. Client không cần tài khoản, nhập host/registry port, kết nối → bảng dữ liệu và editor Put/Delete, tự làm mới 2 giây.

| Thiết lập | Node A | Node B |
| --- | --- | --- |
| Node ID | A | B |
| Registry port | 1099 | 2099 |
| Object port | 1199 | 2199 |
| DB config | config/database-a.properties | config/database-b.properties |
| MySQL schema | ltm_rmi_a | ltm_rmi_b |
| Peer lúc thử localhost | 127.0.0.1:2099 | 127.0.0.1:1099 |

Trong tab cấu hình server, nhập file config tương ứng rồi Nạp file, nhập đúng Node ID, advertised IP và peer. Khi thử trên một máy mở 2 server UI, một cho A, một cho B. Hai node không dùng chung schema; nếu mở nhầm database của node khác sẽ báo lỗi.

## CLI trên một máy

Mỗi lệnh ở một terminal trong Lab5_RMI:

```powershell
.\run.ps1 server-cli A 1099 1199 127.0.0.1 config/database-a.properties 127.0.0.1:2099
.\run.ps1 server-cli B 2099 2199 127.0.0.1 config/database-b.properties 127.0.0.1:1099
.\run.ps1 client localhost 1099
```

## Hai máy thật, mỗi máy chạy server và client

Không cần máy ảo cho mô hình này. Máy A chạy Server A + Client A + MySQL A; máy B chạy Server B + Client B + MySQL B. Hai server đồng bộ qua LAN; mỗi client kết nối server trên chính máy của nó.

| Thiết lập | Máy A | Máy B |
|---|---|---|
| IPv4 ví dụ | 192.168.1.10 | 192.168.1.11 |
| Registry / object port | 1099 / 1199 | 2099 / 2199 |
| Node ID | A | B |
| MySQL host | 127.0.0.1 | 127.0.0.1 |
| Database | ltm_rmi_a | ltm_rmi_b |
| Client kết nối | 127.0.0.1:1099 | 127.0.0.1:2099 |

Chép riêng Lab5_RMI lên mỗi máy, bật MySQL trong Laragon, sửa tài khoản MySQL trong config/database-a.properties trên A và config/database-b.properties trên B. Mỗi máy chạy build.ps1. Dùng ipconfig để lấy IPv4 của card Wi-Fi/Ethernet đang dùng; thay các IP ví dụ dưới đây.

Máy A, terminal thứ nhất:

```powershell
.\run-lan.ps1 -Node A -App server -LocalIp 192.168.1.10 -PeerIp 192.168.1.11
```

Máy B, terminal thứ nhất:

```powershell
.\run-lan.ps1 -Node B -App server -LocalIp 192.168.1.11 -PeerIp 192.168.1.10
```

Script điền sẵn Node ID, file MySQL, cổng, IP quảng bá và peer trong server UI. Kiểm tra cấu hình rồi bấm **Khởi động Server** trên cả hai máy.

Mỗi máy mở thêm terminal để chạy client:

```powershell
# Trên máy A
.\run-lan.ps1 -Node A -App client
# Trên máy B
.\run-lan.ps1 -Node B -App client
```

Bấm Kết nối trong mỗi client. Server và client là hai tiến trình riêng; có thể đặt hai cửa sổ cạnh nhau. Client không truy cập MySQL trực tiếp. IP quảng bá của server phải là IPv4 LAN thật dù client trên máy đó nhập 127.0.0.1.

Hai máy cần truy cập được cả registry và object port. Nếu Windows Firewall chặn, cho phép Java trên mạng LAN hoặc tạo inbound rule TCP: 1099,1199 trên A; 2099,2199 trên B. Không cần mở MySQL ra LAN.

Kiểm tra từ A: Test-NetConnection 192.168.1.11 -Port 2099 và -Port 2199. Từ B kiểm tra 192.168.1.10 với 1099 và 1199. Hai máy phải có route tới nhau; Wi-Fi có client isolation có thể chặn dù cùng tên mạng.

## Demo và cơ chế đồng bộ

Client trên máy A ghi A, client trên máy B đọc B; sửa từ B rồi đọc A. Tắt B, ghi A, bật B cùng ID/database để catch-up. Xóa dùng tombstone để bản cũ không sống lại. Thử ghi đồng thời khi mất mạng rồi nối lại; quan sát hội tụ trong hai cửa sổ HeidiSQL.

Store extends Remote với snapshot/put/merge/info; Value Serializable. Phiên bản (Lamport counter, node ID), counter=max đã biết+1; hòa counter thì so node ID. Không phụ thuộc đồng hồ hệ điều hành, không cam kết thứ tự ghi theo thời gian thật khi partition. Chu kỳ 2s pull snapshot, merge transaction MySQL rồi push. Không giữ lock local trong lúc gọi peer. Put trả khi local commit, eventual consistency chứ không phải distributed transaction/consensus. Chưa delta sync và dọn tombstone.

## Tài liệu và kiểm thử

- [Kiến trúc](docs/ARCHITECTURE.md)
- [Laragon + MySQL + HeidiSQL](docs/MYSQL_HEIDISQL.md)
- [Kết quả kiểm thử](docs/VALIDATION.md)
- [Server UI](docs/server.png) · [Login UI](docs/client.png) · [Workspace](docs/workspace.png)

Chạy `.\test.ps1` khi MySQL đang bật; test tạo schema tên ltm_test_* riêng, dọn lại sau khi chạy. `.\preview.ps1` render giao diện chưa kết nối; `.\preview-workspace.ps1` chạy client với server và schema thử thật để render workspace.

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
