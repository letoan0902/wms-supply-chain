# Triển khai lên VPS (production)

Hướng dẫn đưa WMS Supply Chain lên một VPS Ubuntu 24.04 mới, chạy bằng Docker Compose và truy cập qua `https://wms.toanmarket.me`.
Caddy làm reverse proxy, tự xin và gia hạn chứng chỉ Let's Encrypt. Cách chạy trên máy phát triển xem [README.md](../README.md) ở thư mục gốc.

Quy trình tóm tắt: chuẩn bị VPS (Docker, firewall) -> trỏ DNS -> lấy mã nguồn và tạo `.env` -> chạy `deploy/deploy.sh` -> kiểm tra.
Các bản cập nhật về sau chỉ cần chạy lại `deploy/deploy.sh`.

## 1. Tổng quan

```text
                       Internet (người dùng)
                                |
                     80 (HTTP), 443 (HTTPS, HTTP/3)
                                v
                  +---------------------------+
                  |  caddy (wms-caddy)        |  TLS, chứng chỉ Let's Encrypt tự động,
                  |  caddy:2-alpine           |  chuyển HTTP sang HTTPS, nén, log JSON
                  +-------------+-------------+
                                |  HTTP trong mạng nội bộ wms-network
                                v
                  +---------------------------+
                  |  frontend (Nginx :80)     |  phục vụ giao diện React,
                  |                           |  chuyển /api/ tới api-gateway
                  +-------------+-------------+
                                |  /api/*
                                v
                  +---------------------------+
                  |  api-gateway (:8080)      |  JWT, giới hạn tốc độ, CORS
                  +-------------+-------------+
                                |  định tuyến qua Eureka
                                v
     auth / product / inventory / order / notification  ->  MySQL, Redis, RabbitMQ
```

Chỉ cổng 22 (SSH), 80 và 443 nhận kết nối từ Internet. Mọi cổng còn lại (api-gateway 8080, MySQL, Redis, RabbitMQ, Eureka,
Config Server, các service nghiệp vụ) chỉ mở trên `127.0.0.1` của máy chủ.

Các tệp trong thư mục `deploy/`:

| Tệp | Vai trò |
| --- | --- |
| [docker-compose.prod.yml](docker-compose.prod.yml) | Ghép chồng lên `docker-compose.yml` gốc: thêm service `caddy`, bỏ cổng publish của `frontend`, đưa `api-gateway` về `127.0.0.1:8080`, truyền `CORS_ALLOWED_ORIGINS`, `GATEWAY_JWT_ENABLED` và các biến giới hạn tốc độ vào gateway |
| [Caddyfile](Caddyfile) | Cấu hình Caddy: HTTPS tự động, nén, header bảo mật, log JSON, proxy tới `frontend:80` |
| [.env.production.example](.env.production.example) | Mẫu file `.env` cho production, không chứa bí mật |
| [deploy.sh](deploy.sh) | Script chạy trên VPS: lấy mã nguồn, build, khởi động, chờ healthy, kiểm tra gateway, làm nóng vài request đầu, kiểm tra HTTPS và API qua tên miền, dọn image cũ |
| `.gitignore`, `.gitattributes` | Cho phép commit file mẫu `.env.production.example` (`.gitignore` gốc chặn mọi `.env.*`) và ép dùng LF cho script |

## 2. Yêu cầu

- VPS Ubuntu 24.04 LTS, quyền root (hoặc sudo). Cấu hình 12 vCPU, 47 GB RAM là dư dả: stack gồm 12 container, ước lượng tối thiểu khoảng 8 GB RAM.
- Ổ đĩa còn trống từ 20 GB trở lên (image Docker, bộ nhớ đệm build và dữ liệu MySQL).
- Tên miền `wms.toanmarket.me` và quyền chỉnh bản ghi DNS.
- Cổng 80 và 443 của máy chủ chưa bị chương trình khác chiếm, và không bị firewall của nhà cung cấp VPS chặn.
- Repository `https://github.com/letoan0902/wms-supply-chain.git` đọc được từ VPS. Nếu repository ở chế độ private, xem mục 5.1.

Các lệnh trong tài liệu chạy bằng root (`sudo -i`). Nếu dùng tài khoản thường, thêm `sudo` trước từng lệnh.

## 3. Chuẩn bị máy chủ

### 3.1. Cập nhật hệ thống và cài công cụ cơ bản

```bash
apt update && apt upgrade -y
apt install -y git curl ca-certificates ufw
```

### 3.2. Cài Docker Engine và Docker Compose (kho apt chính thức của Docker)

```bash
apt update
apt install -y ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Kiểm tra:

```bash
docker --version
docker compose version       # cần v2.24.4 trở lên (file deploy/docker-compose.prod.yml dùng thẻ !reset và !override)
systemctl is-active docker   # active
```

Docker tự chạy khi máy khởi động, và các container có `restart: unless-stopped` sẽ tự chạy lại sau khi VPS khởi động lại.

### 3.3. Giới hạn dung lượng log của Docker

Mặc định Docker không xoay vòng log của container nên log có thể làm đầy ổ đĩa theo thời gian. Cấu hình trước lần chạy đầu tiên
(cấu hình chỉ áp dụng cho container tạo sau đó):

```bash
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "5"
  }
}
EOF
systemctl restart docker
```

Nếu `/etc/docker/daemon.json` đã tồn tại, hãy gộp các khóa trên vào file hiện có thay vì ghi đè.

### 3.4. Mở cổng firewall (ufw)

```bash
ufw allow 22/tcp      # SSH. Nếu SSH dùng cổng khác, đổi thành cổng đó TRƯỚC khi bật ufw
ufw allow 80/tcp      # HTTP: Let's Encrypt xác thực tên miền và chuyển hướng sang HTTPS
ufw allow 443/tcp     # HTTPS
ufw allow 443/udp     # HTTP/3 (tùy chọn)
ufw enable            # nhập y để xác nhận
ufw status verbose
```

Hai lưu ý:

- ufw KHÔNG chặn được cổng do Docker publish: Docker thêm quy tắc iptables riêng và bỏ qua ufw. Vì vậy `docker-compose.yml` chỉ publish các cổng
  nội bộ trên `127.0.0.1`, và `deploy/docker-compose.prod.yml` đưa cả cổng 8080 của gateway về `127.0.0.1`. Đừng đổi các cổng này sang `0.0.0.0`.
- Nếu nhà cung cấp VPS có firewall hoặc security group riêng, mở cổng 22, 80, 443 (TCP) và 443 (UDP) ở đó nữa.

## 4. Trỏ DNS

Tạo bản ghi tại nơi quản lý DNS của `toanmarket.me` (ví dụ Cloudflare):

| Trường | Giá trị |
| --- | --- |
| Loại | `A` |
| Tên | `wms` (tức `wms.toanmarket.me`) |
| Nội dung | địa chỉ IPv4 công khai của VPS |
| TTL | Auto |
| Proxy status | DNS only (đám mây xám), KHÔNG bật proxy Cloudflare |

Caddy xác thực tên miền với Let's Encrypt qua cổng 80 hoặc 443 nên yêu cầu phải đi thẳng tới máy chủ. Bật proxy Cloudflare (đám mây cam) dễ làm việc
cấp chứng chỉ tự động thất bại hoặc gây vòng lặp chuyển hướng. Nếu VPS không có IPv6 đang hoạt động thì không tạo bản ghi `AAAA` (hoặc xóa nếu đã có).

Kiểm tra trên VPS (kết quả phải là IP của VPS):

```bash
getent ahosts wms.toanmarket.me
```

Nên trỏ DNS trước khi chạy `deploy.sh`: nếu chưa trỏ, ứng dụng vẫn chạy nhưng Caddy chưa xin được chứng chỉ (xem mục 10.2).

## 5. Triển khai lần đầu

### 5.1. Lấy mã nguồn

Thư mục `deploy/` phải có trên nhánh được triển khai. Nhánh mặc định là `develop`, nên cần merge Pull Request chứa `deploy/` vào `develop` trước.
Muốn thử trên nhánh khác, thay `develop` bằng tên nhánh ở lệnh clone dưới đây và chạy `deploy.sh` với `BRANCH=<tên-nhánh>`.

```bash
git clone --branch develop https://github.com/letoan0902/wms-supply-chain.git /opt/wms-supply-chain
cd /opt/wms-supply-chain
```

Nếu repository ở chế độ private, dùng deploy key (chỉ đọc) thay cho HTTPS:

```bash
ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519      # khóa không đặt passphrase để deploy.sh chạy không cần nhập
cat ~/.ssh/id_ed25519.pub                             # dán vào GitHub: Settings > Deploy keys > Add deploy key (không tick quyền ghi)
ssh-keyscan github.com >> ~/.ssh/known_hosts
git clone --branch develop git@github.com:letoan0902/wms-supply-chain.git /opt/wms-supply-chain
cd /opt/wms-supply-chain
```

Địa chỉ remote được lưu trong `.git/config` nên các lần chạy `deploy.sh` sau không cần đặt lại `REPO_URL`.

### 5.2. Tạo file .env

```bash
cp deploy/.env.production.example .env
chmod 600 .env

# Sinh mật khẩu ngẫu nhiên cho MySQL và khóa JWT (không in ra màn hình)
sed -i "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=$(openssl rand -base64 32)|" .env
sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -base64 32)|" .env
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" .env

nano .env     # điền ACME_EMAIL, kiểm tra DOMAIN và CORS_ALLOWED_ORIGINS
```

| Biến | Bắt buộc | Ý nghĩa |
| --- | --- | --- |
| `DOMAIN` | Không (mặc định `wms.toanmarket.me`) | Tên miền Caddy xin chứng chỉ |
| `ACME_EMAIL` | Có | Email đăng ký tài khoản Let's Encrypt |
| `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD` | Có | Mật khẩu MySQL. Đặt đúng ngay từ lần chạy đầu vì MySQL chỉ đọc mật khẩu khi khởi tạo volume |
| `DB_USERNAME` | Không (mặc định `wms`) | Tài khoản ứng dụng, giữ `wms` để khớp `docker/mysql/init.sql` |
| `JWT_SECRET` | Có | Khóa ký JWT, tối thiểu 32 ký tự, dùng chung cho api-gateway và auth-service |
| `CORS_ALLOWED_ORIGINS` | Không (mặc định `https://<DOMAIN>`) | Origin của giao diện. Sai giá trị thì đăng nhập bị gateway từ chối (403) |
| `GATEWAY_JWT_ENABLED` | Không (mặc định `true`) | Gateway bắt buộc Bearer token hợp lệ cho mọi API trừ `/api/auth/login` |
| `RATE_LIMIT_ENABLED`, `RATE_LIMIT_REPLENISH_RATE`, `RATE_LIMIT_BURST_CAPACITY` | Không | Giới hạn tốc độ tại gateway (mặc định bật, 20 request/giây, bùng nổ tối đa 40) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Không | SMTP gửi email cảnh báo tồn kho |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | Nên đặt | Tài khoản RabbitMQ riêng cho các service và container `rabbitmq-init` (không dùng `guest`). Volume mới: broker tự tạo tài khoản này. Volume đã có dữ liệu: tạo trước bằng `rabbitmqctl` theo mục 6 của [docs/MESSAGING.md](../docs/MESSAGING.md) rồi mới ghi vào `.env` |
| `COMPOSE_PATH_SEPARATOR`, `COMPOSE_FILE` | Nên giữ | Để lệnh `docker compose` trong thư mục này tự dùng cả hai file compose |

Hai dòng `COMPOSE_*` giúp mọi lệnh `docker compose ...` chạy trong `/opt/wms-supply-chain` tự dùng cả `docker-compose.yml` lẫn `deploy/docker-compose.prod.yml`
nên các lệnh trong tài liệu này viết ngắn. Nếu bạn xóa hai dòng đó, thêm `-f docker-compose.yml -f deploy/docker-compose.prod.yml` ngay sau `docker compose`.
Chạy riêng `docker-compose.yml` gốc trên VPS là sai: cổng 80 và 8080 bị publish lại và `caddy` bị coi là container thừa.

File `.env` chứa mật khẩu: không commit lên Git, và lưu một bản ở nơi an toàn (ví dụ trình quản lý mật khẩu). Mất `MYSQL_ROOT_PASSWORD` hoặc `DB_PASSWORD`
đồng nghĩa không kết nối lại được vào dữ liệu MySQL đã có.

### 5.3. Chạy deploy.sh

```bash
bash deploy/deploy.sh
```

Script thực hiện lần lượt: kiểm tra Docker và Compose, `git fetch` rồi `git reset --hard origin/develop` (in ra commit đang triển khai), kiểm tra `.env`
(thiếu biến, còn giá trị `change_me`, JWT quá ngắn), `docker compose config`, `build --pull`, `up -d --remove-orphans`, chờ mọi container healthy
(tối đa 600 giây), kiểm tra `http://127.0.0.1:8080/actuator/health` và `https://wms.toanmarket.me/`, rồi `docker image prune -f`.
Lần đầu việc build 8 service Java và frontend mất vài phút đến vài chục phút tùy tốc độ mạng và máy.

Nếu kiểm tra HTTPS chưa đạt (DNS chưa trỏ hoặc Caddy đang xin chứng chỉ), script chỉ cảnh báo chứ không dừng. Nếu `.env` chưa có, script dừng và in hướng dẫn tạo file.
Có thể chạy lại script nhiều lần. Vì lần build đầu khá lâu, nên chạy trong `tmux` (`apt install -y tmux`) để không mất tiến trình khi phiên SSH bị ngắt;
nếu vẫn bị ngắt giữa chừng, chỉ cần chạy lại `bash deploy/deploy.sh`.

Tùy chọn của script:

```bash
bash deploy/deploy.sh --help
bash deploy/deploy.sh --no-build        # bỏ qua bước build, dùng lại image đã có
BRANCH=main bash deploy/deploy.sh       # triển khai nhánh khác
WAIT_TIMEOUT=900 bash deploy/deploy.sh  # chờ container healthy lâu hơn
```

## 6. Kiểm tra sau triển khai

```bash
cd /opt/wms-supply-chain
docker compose ps                                  # mọi service Up, các service có healthcheck ở trạng thái healthy
curl -I https://wms.toanmarket.me/                 # HTTP/2 200
curl -I http://wms.toanmarket.me/                  # 308, chuyển hướng sang https
curl -fsS http://127.0.0.1:8080/actuator/health    # {"status":"UP",...}
ss -tlnp | grep -E ':(80|443|8080|3306|6379|5672|15672|8761|8888|808[1-5])\b'
```

Kết quả `ss` đúng khi chỉ cổng 80 và 443 nghe trên `0.0.0.0` (và `[::]`); các cổng còn lại đều nghe trên `127.0.0.1`.
Từ một máy khác, thử mở `http://<IP-VPS>:8080` và kết nối tới cổng 3306: cả hai phải không kết nối được.

Mở `https://wms.toanmarket.me` trong trình duyệt và đăng nhập bằng tài khoản ở mục 7.

Kiểm tra tự động bằng script `deploy/smoke-test.sh` (đăng nhập, gọi API có token, kiểm tra chặn request không token): xem hướng dẫn chi tiết tại [SMOKE_TEST.md](SMOKE_TEST.md).

```bash
SMOKE_PASSWORD='<mật khẩu tài khoản admin>' bash deploy/smoke-test.sh https://wms.toanmarket.me
```

## 7. Tài khoản đăng nhập mẫu

Lần đầu auth-service khởi động, Flyway chạy `V2__seed_default_accounts.sql` (`backend/auth-service/src/main/resources/db/migration/`) và tạo 6 tài khoản nghiệp vụ mẫu,
mỗi vai trò một tài khoản. Migration `V4__remove_demo_account.sql` xóa tài khoản `demo` (từng có quyền `ADMIN` với mật khẩu công khai), nên hệ thống không còn
tài khoản thử nghiệm nào và trang đăng nhập cũng không hiển thị gợi ý tài khoản:

| Tên đăng nhập | Vai trò | Email |
| --- | --- | --- |
| `admin` | `ADMIN` | admin@wms.vn |
| `manager` | `WAREHOUSE_MANAGER` | manager@wms.vn |
| `staff` | `WAREHOUSE_STAFF` | staff@wms.vn |
| `purchasing` | `PURCHASING` | purchasing@wms.vn |
| `sales` | `SALES` | sales@wms.vn |
| `accountant` | `ACCOUNTANT` | accountant@wms.vn |

Mật khẩu ban đầu của 6 tài khoản này nằm công khai trong repository (chú thích đầu file V2), nên ngay sau lần triển khai đầu, và sau mỗi lần khôi phục CSDL
từ bản sao lưu cũ, phải đổi toàn bộ bằng script có sẵn:

```bash
cd /opt/wms-supply-chain
bash deploy/rotate-seed-passwords.sh            # đổi cả 6 tài khoản
bash deploy/rotate-seed-passwords.sh admin      # hoặc chỉ một số tài khoản
```

Script sinh mật khẩu ngẫu nhiên 20 ký tự cho từng tài khoản, băm BCrypt bằng `caddy hash-password` trong container caddy, ghi vào CSDL `auth_db` và lưu danh sách
mật khẩu mới tại `/root/wms-seed-accounts.txt` (quyền 600, không in ra màn hình, không truyền mật khẩu MySQL trên dòng lệnh). Đọc file bằng
`cat /root/wms-seed-accounts.txt`, lưu vào trình quản lý mật khẩu rồi xóa file. Sau đó mỗi người tự đổi mật khẩu qua API `POST /api/auth/users/change-password`
(gửi mật khẩu hiện tại và mật khẩu mới kèm token) hoặc màn hình đổi mật khẩu khi giao diện có; tài khoản không dùng tới thì vô hiệu hóa trong trang quản trị người dùng.

## 8. Vận hành hằng ngày

Chạy trong `/opt/wms-supply-chain`.

| Việc cần làm | Lệnh |
| --- | --- |
| Xem trạng thái | `docker compose ps` |
| Xem log theo thời gian thực | `docker compose logs -f --tail 100 caddy` (thay `caddy` bằng `api-gateway`, `auth-service`, `inventory-service`...) |
| Xem log theo khoảng thời gian | `docker compose logs --since 1h auth-service` |
| Khởi động lại một service | `docker compose restart auth-service` |
| Dừng và chạy lại toàn bộ, giữ dữ liệu | `docker compose stop` rồi `docker compose start` |
| Xem mức dùng tài nguyên | `docker stats --no-stream` và `docker system df` |
| Cập nhật phiên bản mới | `bash deploy/deploy.sh` |
| Đổi cấu hình trong `.env` | Sửa `.env` rồi `docker compose up -d` (Compose tạo lại những service có biến thay đổi) |
| Sửa Caddyfile | `docker compose restart caddy` (`deploy.sh` tự làm khi Caddyfile thay đổi sau khi cập nhật mã nguồn) |

Không dùng `docker compose down -v`: lệnh này xóa volume `mysql_data` (toàn bộ dữ liệu) và `caddy_data` (chứng chỉ TLS). `docker compose down` không kèm `-v` thì giữ nguyên dữ liệu.

### 8.1. Cập nhật phiên bản mới

```bash
cd /opt/wms-supply-chain
bash deploy/deploy.sh
```

`deploy.sh` kéo mã nguồn mới của nhánh `develop`, build lại các lớp Docker có thay đổi, rồi `up -d` chỉ tạo lại những service có image hoặc cấu hình mới. File `.env` được giữ nguyên.
Vì dùng `git reset --hard`, mọi chỉnh sửa trực tiếp trên VPS vào file đã được Git theo dõi (ví dụ sửa `deploy/Caddyfile`) sẽ bị xóa ở lần chạy sau: hãy sửa qua Git hoặc qua `.env`.
Migration CSDL (Flyway) tự chạy khi service tương ứng khởi động. Service Java mất khoảng một đến hai phút để khởi động lại nên người dùng có thể gặp gián đoạn ngắn.

Quay lại phiên bản trước (khi bản mới lỗi): `deploy.sh` luôn đưa mã nguồn về `origin/<BRANCH>`, nên để lùi về một commit cũ hãy thao tác tay:

```bash
cd /opt/wms-supply-chain
git log --oneline -10                  # tìm commit tốt
git reset --hard <commit>
docker compose up -d --build
```

Lần chạy `deploy.sh` kế tiếp sẽ đưa mã nguồn về lại bản mới nhất của nhánh.

### 8.2. Truy cập công cụ nội bộ qua SSH tunnel

Eureka (8761) và RabbitMQ Management (15672) chỉ nghe trên `127.0.0.1` của VPS. Mở tunnel từ máy cá nhân:

```bash
ssh -L 8761:127.0.0.1:8761 -L 15672:127.0.0.1:15672 root@wms.toanmarket.me
```

Sau đó mở `http://localhost:8761` (Eureka) và `http://localhost:15672` (RabbitMQ, tài khoản `guest`/`guest`) trên máy cá nhân.

## 9. Sao lưu và khôi phục MySQL

Mỗi service dùng một schema riêng: `auth_db`, `product_db`, `inventory_db`, `order_db`, `notification_db`. Redis và RabbitMQ chỉ giữ dữ liệu tạm nên không cần sao lưu thường xuyên.

### 9.1. Sao lưu thủ công

```bash
mkdir -p /opt/backups
cd /opt/wms-supply-chain
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --default-character-set=utf8mb4 --databases auth_db product_db inventory_db order_db notification_db' \
  | gzip > /opt/backups/wms-$(date +%F-%H%M).sql.gz
ls -lh /opt/backups
```

Biến `$MYSQL_ROOT_PASSWORD` nằm trong dấu nháy đơn nên do shell của container mysql đọc, mật khẩu không xuất hiện trên dòng lệnh của máy chủ. Cảnh báo
"Using a password on the command line interface can be insecure" từ mysqldump là bình thường.

### 9.2. Sao lưu tự động hằng ngày

```bash
cat > /usr/local/bin/wms-backup.sh <<'EOF'
#!/usr/bin/env bash
# Sao lưu toàn bộ schema WMS, giữ 14 ngày gần nhất
set -euo pipefail
cd /opt/wms-supply-chain
mkdir -p /opt/backups
file="/opt/backups/wms-$(date +%F-%H%M).sql.gz"
if ! docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --default-character-set=utf8mb4 --databases auth_db product_db inventory_db order_db notification_db' | gzip > "$file.tmp"; then
  rm -f "$file.tmp"
  echo "Sao lưu thất bại" >&2
  exit 1
fi
mv "$file.tmp" "$file"
find /opt/backups -name 'wms-*.sql.gz' -mtime +14 -delete
echo "Đã sao lưu: $file"
EOF
chmod +x /usr/local/bin/wms-backup.sh

# Chạy lúc 02:00 mỗi ngày, ghi kết quả vào /var/log/wms-backup.log
( crontab -l 2>/dev/null; echo '0 2 * * * /usr/local/bin/wms-backup.sh >> /var/log/wms-backup.log 2>&1' ) | crontab -
```

Bản sao lưu nằm cùng ổ đĩa với CSDL nên chưa phải sao lưu an toàn: định kỳ chép thư mục `/opt/backups` sang máy khác (scp, rsync hoặc kho lưu trữ đối tượng).
Nên thử khôi phục thử trên một máy khác để chắc chắn bản sao lưu dùng được.

### 9.3. Khôi phục

Lệnh khôi phục ghi đè các bảng hiện có. Dừng các service dùng CSDL trước, khôi phục rồi khởi động lại:

```bash
cd /opt/wms-supply-chain
docker compose stop api-gateway auth-service product-service inventory-service order-service notification-service

gunzip -c /opt/backups/wms-2026-11-23-0200.sql.gz \
  | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'

docker compose up -d
```

## 10. Khắc phục sự cố

### 10.1. Cổng 80 hoặc 443 bị chiếm

Triệu chứng: `deploy.sh` dừng ở bước khởi động với lỗi dạng `Bind for 0.0.0.0:80 failed: port is already allocated` hoặc `address already in use`.

```bash
ss -tulpn | grep -E ':(80|443)\b'      # xem tiến trình đang giữ cổng
systemctl disable --now nginx apache2  # nếu VPS cài sẵn nginx hoặc apache (bỏ qua thông báo không tìm thấy unit)
bash deploy/deploy.sh
```

Nếu cổng do một container khác giữ, dừng container đó (`docker ps`, `docker stop <tên>`).

### 10.2. Chứng chỉ chưa được cấp (DNS chưa trỏ hoặc bị chặn)

Triệu chứng: trình duyệt báo lỗi chứng chỉ hoặc không kết nối được `https://wms.toanmarket.me`; `deploy.sh` cảnh báo "Chưa truy cập được".

1. Kiểm tra DNS: `getent ahosts wms.toanmarket.me` phải trả về IP của VPS. Bản ghi `A` đúng, không còn bản ghi `AAAA` sai, proxy Cloudflare đang tắt (DNS only).
2. Kiểm tra cổng 80 và 443 mở ở cả ufw (`ufw status`) lẫn firewall của nhà cung cấp VPS. Từ máy khác: `curl -I http://wms.toanmarket.me/` phải nhận được phản hồi.
3. Xem log Caddy tìm lỗi cấp chứng chỉ:

   ```bash
   docker compose logs --tail 200 caddy | grep -iE 'error|challenge|acme|tls'
   ```

4. Sau khi sửa DNS hoặc firewall, khởi động lại Caddy để xin chứng chỉ ngay thay vì chờ lần thử lại tiếp theo: `docker compose restart caddy`.

Let's Encrypt giới hạn số lần xác thực thất bại (khoảng 5 lần mỗi giờ cho mỗi tên miền) và số chứng chỉ trùng lặp (khoảng 5 mỗi tuần), vì vậy đừng khởi động lại Caddy liên tục khi chưa sửa xong nguyên nhân.
Khi cần thử nhiều lần, bỏ dấu `#` ở dòng `acme_ca` trong `deploy/Caddyfile` để dùng môi trường staging (chứng chỉ staging không được trình duyệt tin cậy).
Khi chuyển lại sang CA thật mà Caddy vẫn dùng chứng chỉ staging, xóa volume chứng chỉ để Caddy xin lại (chỉ làm khi cần, vì chứng chỉ thật cũng nằm trong volume này):

```bash
docker compose rm -sf caddy
docker volume rm wms-supply-chain_caddy_data
docker compose up -d caddy
```

### 10.3. Service chưa healthy

```bash
docker compose ps                              # service nào Restarting hoặc unhealthy
docker compose logs --tail 100 <tên-service>   # nguyên nhân thường nằm ở vài dòng cuối
```

- Các service Spring Boot cần thời gian khởi động: healthcheck cho phép khoảng 3,5 phút (start_period 60 giây cộng 10 lần thử cách nhau 15 giây), `deploy.sh` chờ tối đa 600 giây.
- `config-server` hoặc `discovery-server` chưa healthy thì các service còn lại chưa được khởi động (theo `depends_on`). Xem log hai service này trước.
- Lỗi `Access denied for user 'wms'` ở các service nghiệp vụ: mật khẩu trong `.env` khác mật khẩu đã dùng khi MySQL khởi tạo volume. Đặt lại đúng giá trị cũ trong `.env`.
  Nếu CSDL chưa có dữ liệu cần giữ, có thể xóa volume để khởi tạo lại (MẤT TOÀN BỘ dữ liệu MySQL): `docker compose down && docker volume rm wms-supply-chain_mysql_data && bash deploy/deploy.sh`.
- Thiếu bộ nhớ: `free -h`, `docker stats --no-stream`, `dmesg | grep -i 'out of memory'`. Container Java chưa bị giới hạn bộ nhớ và JVM mặc định dùng tối đa 75% RAM của máy chủ cho mỗi tiến trình;
  nếu một service ngốn RAM, đặt `mem_limit` cho service đó trong `deploy/docker-compose.prod.yml`.

### 10.4. Các lỗi thường gặp khác

| Triệu chứng | Nguyên nhân và cách xử lý |
| --- | --- |
| Trang trả về `502 Bad Gateway` | Caddy chạy nhưng `frontend` chưa healthy. Xem `docker compose ps frontend` và `docker compose logs frontend api-gateway` |
| Đăng nhập báo lỗi 403 `Invalid CORS request` | `CORS_ALLOWED_ORIGINS` trong `.env` không đúng `https://<DOMAIN>`. Sửa rồi chạy `docker compose up -d api-gateway` |
| Mọi API trả 401 sau một thời gian | Access token hết hạn (gateway đang kiểm tra JWT). Đăng nhập lại. Chỉ đặt `GATEWAY_JWT_ENABLED=false` để chẩn đoán |
| Lỗi 429 `Too Many Requests` | Chạm giới hạn tốc độ của gateway. Tăng `RATE_LIMIT_REPLENISH_RATE` và `RATE_LIMIT_BURST_CAPACITY` trong `.env` rồi `docker compose up -d api-gateway` |
| Tài khoản báo bị khóa tạm thời sau 5 lần đăng nhập sai | Khóa 30 phút lưu trong Redis. Mở khóa: `docker compose exec redis redis-cli DEL login:lock:user:<tên-đăng-nhập>` (khóa theo IP: `login:lock:ip:<địa-chỉ-IP>`) |
| `deploy.sh`: `$'\r': command not found` hoặc `bad interpreter` | File bị đổi sang CRLF khi chỉnh sửa trên Windows. Chạy `sed -i 's/\r$//' deploy/deploy.sh`. Thư mục `deploy/` đã có `.gitattributes` ép LF |
| Lỗi YAML nhắc tới `!reset` hoặc `!override`, hoặc cổng 80 và 8080 vẫn bị publish ngoài ý muốn | Docker Compose cũ hơn v2.24.4 không hiểu hai thẻ này (`deploy.sh` từ chối chạy khi phiên bản quá cũ). Nâng cấp theo mục 3.2 |
| `git fetch` báo `Authentication failed` hoặc `Permission denied (publickey)` | Repository private: dùng deploy key theo mục 5.1 |
| Hết dung lượng ổ đĩa | `docker system df` để xem; dọn bằng `docker builder prune -f` và `docker image prune -f` |

## 11. Lưu ý bảo mật

- Giữ `.env` ở quyền `600`, không commit, không gửi qua kênh chat.
- Chạy `deploy/rotate-seed-passwords.sh` ngay sau lần triển khai đầu để đổi mật khẩu 6 tài khoản mẫu (mục 7); tài khoản `demo` đã bị migration V4 xóa.
- RabbitMQ dùng tài khoản riêng đặt qua `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` trong `.env` (máy chủ hiện tại đã tạo tài khoản `wms` ngày 04/11/2026). Sau khi các service đã chạy bằng tài khoản mới, xóa tài khoản mặc định: `docker compose exec rabbitmq rabbitmqctl delete_user guest`. Cổng 5672 và 15672 không mở ra ngoài, không đổi sang `0.0.0.0`.
- Chỉ cho phép đăng nhập SSH bằng khóa (đặt `PasswordAuthentication no` trong cấu hình sshd), cân nhắc cài `fail2ban`, và bật cập nhật bảo mật tự động (`unattended-upgrades`, thường có sẵn trên Ubuntu).
- Bật HSTS trong `deploy/Caddyfile` (dòng `Strict-Transport-Security` đang để dạng chú thích) khi HTTPS đã chạy ổn định.

## 12. Hướng phát triển tiếp

Hiện `deploy.sh` build image ngay trên VPS từ mã nguồn. Khi CI đã publish image lên GHCR (xem [docs/CI_CD.md](../docs/CI_CD.md)), có thể bỏ bước build tại chỗ bằng cách khai báo
`image: ghcr.io/letoan0902/wms-supply-chain/<service>:<thẻ>` cho các service trong `deploy/docker-compose.prod.yml` rồi chạy `docker compose pull && docker compose up -d`.
