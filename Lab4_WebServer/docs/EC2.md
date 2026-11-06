# Public Lab 4 lên EC2

Mô hình: trình duyệt → IP công khai EC2:80 → WebServer Java → MySQL trên cùng EC2.
Server tự xử lý giao thức, không cần Apache/Nginx. Dùng chế độ dòng lệnh, không mở Swing trên EC2.
Các bước dưới dành cho **Ubuntu Server 24.04 LTS**, user SSH `ubuntu`.

## 1. Tạo EC2

Trong AWS Console → EC2 → Launch instance:

- Chọn Ubuntu Server 24.04 LTS, kiến trúc x86_64; để demo Java + MySQL nên chọn máy có RAM từ 2 GB.
- Tạo/chọn key pair và lưu file `.pem` trên Windows.
- Chọn subnet public, bật Auto-assign public IP. Subnet phải có route `0.0.0.0/0` đến Internet Gateway (default VPC thường có sẵn).
- Security Group inbound: SSH TCP 22 từ My IP; HTTP TCP 80 từ `0.0.0.0/0`. Giữ outbound mặc định.
- MySQL dùng nội bộ `127.0.0.1:3306`, không cần mở cổng database để website hoạt động.
- Sau khi instance Running, ghi Public IPv4 address. Ví dụ bên dưới dùng `YOUR_PUBLIC_IP`, phải thay bằng IP thật.

AWS hướng dẫn [quy tắc cho web server](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html). Public IP thông thường có thể đổi sau stop/start; có thể gán [Elastic IP](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/elastic-ip-addresses-eip.html) nếu cần địa chỉ cố định. EC2, dung lượng, truyền dữ liệu và public IPv4 có thể tính phí theo tài khoản; xem Billing khi demo xong.

## 2. Build và upload từ Windows

Mở PowerShell tại folder Lab4_WebServer trên máy bạn:

```powershell
.\build.ps1
$Ec2Ip = 'YOUR_PUBLIC_IP'
$Ec2Key = 'C:\duong-dan\lab4.pem'
ssh -i $Ec2Key "ubuntu@$Ec2Ip" 'mkdir -p /home/ubuntu/Lab4_WebServer'
scp -i $Ec2Key -r target public config sql deploy run-server.sh "ubuntu@${Ec2Ip}:/home/ubuntu/Lab4_WebServer/"
ssh -i $Ec2Key "ubuntu@$Ec2Ip"
```

`target` phải có `lab-web-1.0.jar` và `dependency/` sau build. Không chỉ chép riêng jar.
Build trên Windows chạy được trên Linux vì bytecode Java; script Linux dùng dấu `:` phân tách classpath.

## 3. Cài Java 21 và MySQL trên EC2

Các lệnh tiếp theo chạy trong SSH Ubuntu:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless mysql-server
sudo systemctl enable --now mysql
java -version
cd /home/ubuntu/Lab4_WebServer
sudo mysql < sql/schema.sql
sudo mysql
```

Trong dấu nhắc MySQL, tạo tài khoản riêng cho ứng dụng:

```sql
CREATE USER IF NOT EXISTS 'lab4'@'localhost' IDENTIFIED BY 'lab4demo';
ALTER USER 'lab4'@'localhost' IDENTIFIED BY 'lab4demo';
GRANT ALL PRIVILEGES ON ltm_web.* TO 'lab4'@'localhost';
EXIT;
```

MySQL root Ubuntu thường đăng nhập bằng `sudo mysql`, nên không dùng cấu hình root/mật khẩu trống từ Laragon.
Ghi cấu hình cho EC2 (chỉ sửa bản trên EC2):

```bash
cat > config/database.properties <<'EOF'
host=127.0.0.1
port=3306
database=ltm_web
user=lab4
password=lab4demo
EOF
```

Ubuntu có [OpenJDK 21 trên 24.04](https://documentation.ubuntu.com/ubuntu-for-developers/reference/availability/java/) và hướng dẫn [cài MySQL Server](https://ubuntu.com/server/docs/install-and-configure-a-mysql-server).

## 4. Thử chạy trên cổng 8080

```bash
bash run-server.sh 8080
```

Mở SSH thứ hai và thử:

```bash
curl -I http://127.0.0.1:8080/
curl http://127.0.0.1:8080/api/library
```

Nếu trả 200 và danh sách file, quay về SSH thứ nhất bấm Ctrl+C. Không cần mở 8080 ra Internet khi dùng cổng 80 ở bước sau.

## 5. Chạy tự khởi động trên cổng 80

```bash
cd /home/ubuntu/Lab4_WebServer
sudo cp deploy/lab4-web.service /etc/systemd/system/lab4-web.service
sudo systemctl daemon-reload
sudo systemctl enable --now lab4-web
sudo systemctl status lab4-web --no-pager
curl -I http://127.0.0.1/
```

Service cấp quyền bind cổng 80 cho tiến trình Java. Nếu dùng user hoặc thư mục khác, sửa `User`, `WorkingDirectory`, `ExecStart` trong service tương ứng.

Trên máy bạn mở **`http://YOUR_PUBLIC_IP/`**: thử Nghe nhạc, Xem video, Đọc tài liệu, Xem ảnh, Tải file. Gõ rõ `http://` vì server hiện chỉ hỗ trợ HTTP.

Nếu UFW đang bật (`sudo ufw status`), mở thêm `sudo ufw allow 80/tcp`; Security Group và firewall hệ điều hành là hai lớp độc lập.

## 6. Thêm file / cập nhật

Đặt file ngay trong các thư mục, không lồng thư mục con:

| Dịch vụ | Folder trên EC2 | File hỗ trợ |
|---|---|---|
| Nghe nhạc | public/media/music | mp3, wav, ogg, m4a |
| Xem video | public/media/videos | mp4, webm, ogv |
| Đọc tài liệu | public/media/documents | pdf |
| Xem ảnh | public/media/photos | png, jpg, jpeg, gif, webp, svg |
| Tải file | public/media/downloads | mọi loại file |

Ví dụ upload file từ PowerShell Windows:

```powershell
scp -i $Ec2Key 'C:\Video\demo.mp4' "ubuntu@${Ec2Ip}:/home/ubuntu/Lab4_WebServer/public/media/videos/"
scp -i $Ec2Key 'C:\TaiLieu\baitap.zip' "ubuntu@${Ec2Ip}:/home/ubuntu/Lab4_WebServer/public/media/downloads/"
```

Bấm Làm mới trên website, không cần restart khi thêm/xóa media. Định dạng và codec thực tế phải được trình duyệt hỗ trợ. Với client Java nhúng, ưu tiên WebM/OGG như nội dung thật đi kèm; một số bản Chromium không hỗ trợ H.264/AAC. Không tự phát nhạc/video khi vừa mở trang.

Chỉ client Java trên máy người dùng cần bộ browser-runtime. Server EC2 chạy headless, không upload bộ native Windows/browser-cache và không cài Chromium trên EC2. Client Java nhập Public IPv4 của EC2, cổng 80; cấu hình MySQL phía client dùng database lưu history mà client truy cập được.

Khi cập nhật code: build lại trên Windows, dừng `sudo systemctl stop lab4-web`, upload jar/dependency, rồi `sudo systemctl start lab4-web`. Không ghi đè config EC2 bằng config Laragon.

## 7. Nhật ký, dữ liệu và lỗi thường gặp

```bash
sudo journalctl -u lab4-web -n 80 --no-pager
sudo ss -ltnp | grep ':80 '
sudo mysql -e "SELECT method,target,remote_ip,status,bytes FROM ltm_web.requests ORDER BY id DESC LIMIT 20;"
```

- Không vào được từ Internet nhưng curl localhost được: kiểm tra Public IPv4, Security Group cổng 80, subnet/Internet Gateway và UFW.
- Access denied / Communications link failure: kiểm tra `config/database.properties`, MySQL running và user `lab4`.
- Address already in use: cổng 80 đang được ứng dụng khác dùng; xem `ss` trước khi đổi cổng/dừng ứng dụng đó.
- Không thấy media: chép đúng folder, đúng đuôi file, bấm Làm mới. Không chỉ chép source mà bỏ quên `public`.
- UnsupportedClassVersionError: Java chạy phải là 21 trở lên; kiểm tra `java -version`.
- Không hiện PDF trong trình duyệt: dùng nút Tải xuống để đọc bằng ứng dụng trên máy; một số trình duyệt di động không nhúng PDF.

Request kể cả xem/tua/tải media được ghi bảng `ltm_web.requests`; file media nằm trên ổ đĩa EC2. Xem video có thể tạo nhiều request 206 do trình duyệt tải từng đoạn.
Không cần kết nối MySQL về Laragon để website chạy. Client Swing vẫn dùng DB riêng cho history theo cấu hình của client.

## 8. Khi demo xong

```bash
sudo systemctl stop lab4-web
sudo systemctl disable lab4-web
```

Dừng service chỉ dừng website; muốn ngừng tài nguyên tính phí, quản lý stop/terminate instance, EBS và Elastic IP trong AWS Console theo nhu cầu giữ dữ liệu.

Hướng dẫn đã đối chiếu tài liệu AWS/Ubuntu; chưa triển khai vào tài khoản AWS của bạn.
