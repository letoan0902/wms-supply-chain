# Hướng dẫn triển khai, vận hành và sao lưu phục hồi

Tài liệu cho người tiếp quản hệ thống. Các bước cài đặt từng lệnh nằm trong [deploy/README.md](../deploy/README.md) (không chép lại ở đây); tài liệu này tóm tắt luồng, tổng hợp biến môi trường, và bổ sung phần vận hành, kiểm tra sự cố, sao lưu và phục hồi. Mọi lệnh chạy trong thư mục mã nguồn trên máy chủ (`/opt/wms-supply-chain` theo mặc định) bằng quyền root.

## 1. Kiến trúc trong một đoạn

Trình duyệt vào Caddy (cổng 80 và 443, tự cấp chứng chỉ Let's Encrypt), Caddy chuyển cho Nginx của `frontend` (phục vụ giao diện React và chuyển `/api/`, `/ws-wms` tới gateway). `api-gateway` kiểm tra JWT hoặc API key, giới hạn tốc độ, gắn thông tin doanh nghiệp rồi định tuyến qua Eureka (`discovery-server`) tới sáu service: `auth-service`, `product-service`, `inventory-service`, `order-service`, `notification-service`, `ai-service`. Cấu hình chung lấy từ `config-server`. Năm service đầu mỗi service một schema MySQL 8 (`auth_db`, `product_db`, `inventory_db`, `order_db`, `notification_db`); `ai-service` chỉ dùng Redis. Redis giữ bộ nhớ đệm thuê bao, bộ đếm, khóa đăng nhập, refresh token; RabbitMQ chuyển sự kiện giữa các service. Chỉ cổng 22, 80, 443 mở ra Internet, mọi cổng khác chỉ nghe trên `127.0.0.1`. Chi tiết xem [ARCHITECTURE.md](ARCHITECTURE.md); sơ đồ triển khai và bảng cổng xem mục 1 của [deploy/README.md](../deploy/README.md).

## 2. Yêu cầu máy chủ

Theo mục 2 của [deploy/README.md](../deploy/README.md):

- VPS Ubuntu 24.04 LTS, quyền root. Ước lượng tối thiểu khoảng 8 GB RAM (máy đang chạy có 12 vCPU, 47 GB RAM). Stack gồm 14 container thường trực (kể cả Caddy) và một container chạy một lần (`rabbitmq-init`).
- Ổ đĩa trống từ 20 GB trở lên (image Docker, cache build, dữ liệu MySQL, bản sao lưu).
- Docker Engine và Docker Compose v2.24.4 trở lên (file `deploy/docker-compose.prod.yml` dùng thẻ `!reset` và `!override`).
- Tên miền trỏ bản ghi DNS `A` về IP máy chủ ở chế độ DNS only; cổng 80, 443 không bị chiếm hoặc chặn.
- Máy chủ đọc được repository GitHub (nếu private thì dùng deploy key, mục 5.1 của deploy/README.md).

## 3. Cài đặt lần đầu

Luồng thực tế trong repository: **máy chủ tự build image từ mã nguồn**, `docker-compose.yml` chưa khai báo `image:` kéo từ GHCR cho các service của dự án. CI đã đẩy image lên GHCR (tên và thẻ ở [CI_CD.md](CI_CD.md) mục 2) nhưng việc chuyển máy chủ sang dùng image đó mới là hướng phát triển (mục 12 của deploy/README.md). Vì vậy cài đặt lần đầu là:

1. Chuẩn bị máy chủ: cập nhật hệ thống, cài Docker, giới hạn log, mở firewall (mục 3 của deploy/README.md).
2. Trỏ DNS (mục 4).
3. Clone mã nguồn nhánh `develop` vào `/opt/wms-supply-chain` (mục 5.1).
4. Tạo `.env` từ `deploy/.env.production.example`, sinh mật khẩu và `JWT_SECRET` ngẫu nhiên, điền `ACME_EMAIL` (mục 5.2; bảng biến đầy đủ ở mục 4 dưới đây).
5. Chạy `bash deploy/deploy.sh` (mục 5.3). Script kiểm tra `.env`, build, khởi động, chờ healthy (tối đa 600 giây), kiểm tra gateway và HTTPS. Lần đầu mất vài phút đến vài chục phút; nên chạy trong `tmux`.
6. Kiểm tra (mục 6) và chạy `deploy/smoke-test.sh` ([SMOKE_TEST.md](../deploy/SMOKE_TEST.md)).
7. Đổi mật khẩu các tài khoản mẫu và vô hiệu hóa tài khoản `demo` (mục 7).

Các tệp: [deploy/docker-compose.prod.yml](../deploy/docker-compose.prod.yml) ghép chồng lên [docker-compose.yml](../docker-compose.yml); [deploy/deploy.sh](../deploy/deploy.sh) là script triển khai. Dòng `COMPOSE_FILE` trong `.env` giúp lệnh `docker compose` tự dùng cả hai file. Không chạy riêng `docker-compose.yml` gốc trên máy chủ.

## 4. Biến môi trường

Đặt trong file `.env` ở thư mục gốc mã nguồn (không commit, quyền `600`). Sau khi sửa: `docker compose up -d` để Compose tạo lại các service có biến thay đổi. Cột "Mặc định" là giá trị khi chạy theo `deploy/docker-compose.prod.yml`; khác biệt với máy phát triển được ghi chú. "Bí mật" là có thì không in ra log, không gửi qua chat, không commit.

### 4.1. Tên miền và HTTPS (Caddy)

| Biến | Ý nghĩa | Mặc định | Bí mật |
| --- | --- | --- | --- |
| `DOMAIN` | Tên miền Caddy xin chứng chỉ; cũng dùng suy ra `CORS_ALLOWED_ORIGINS`, `PAYOS_RETURN_URL`, `APP_FRONTEND_URL` | `wms.toanmarket.me` | Không |
| `ACME_EMAIL` | Email đăng ký Let's Encrypt, bắt buộc | không có | Không |

### 4.2. Cơ sở dữ liệu (MySQL)

| Biến | Ý nghĩa | Mặc định | Bí mật |
| --- | --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | Mật khẩu root MySQL (healthcheck, sao lưu); bắt buộc | không có | Có |
| `DB_USERNAME` | Tài khoản ứng dụng dùng chung, giữ `wms` để khớp `docker/mysql/init.sql` | `wms` | Không |
| `DB_PASSWORD` | Mật khẩu tài khoản ứng dụng; bắt buộc | không có | Có |
| `MYSQL_PORT` | Cổng publish MySQL trên `127.0.0.1` (chỉ máy phát triển) | `3306` | Không |

MySQL chỉ đọc hai mật khẩu khi khởi tạo volume `mysql_data` lần đầu. Đổi trong `.env` về sau không đổi mật khẩu trong MySQL (xem mục 9.3). `DB_URL` của từng service do `docker-compose.yml` đặt sẵn, không đặt trong `.env`.

### 4.3. JWT

| Biến | Ý nghĩa | Mặc định | Bí mật |
| --- | --- | --- | --- |
| `JWT_SECRET` | Khóa ký JWT (HS256), tối thiểu 32 ký tự, dùng chung api-gateway, auth-service, notification-service; bắt buộc | không có | Có |
| `JWT_EXPIRATION_MINUTES` | Thời gian sống access token (phút); có trong `auth-service/application.yml`, compose không truyền | `15` | Không |

### 4.4. RabbitMQ và Redis

| Biến | Ý nghĩa | Mặc định | Bí mật |
| --- | --- | --- | --- |
| `RABBITMQ_USERNAME` | Tài khoản broker cho các service và `rabbitmq-init` | `guest` | Không |
| `RABBITMQ_PASSWORD` | Mật khẩu broker | `guest` | Có |
| `RABBITMQ_PORT`, `RABBITMQ_MANAGEMENT_PORT` | Cổng publish trên `127.0.0.1` (chỉ máy phát triển) | `5672`, `15672` | Không |
| `REDIS_PORT` | Cổng publish Redis trên `127.0.0.1` (chỉ máy phát triển) | `6379` | Không |

Nên đặt tài khoản RabbitMQ riêng; với volume đã có dữ liệu phải tạo tài khoản trước bằng `rabbitmqctl` (mục 6 của [MESSAGING.md](MESSAGING.md)). Redis không đặt mật khẩu, chỉ truy cập được trong mạng Docker và từ `127.0.0.1`.

### 4.5. Doanh nghiệp (multi-tenant) và công tắc gateway

| Biến | Ý nghĩa | Mặc định | Bí mật |
| --- | --- | --- | --- |
| `TENANT_DEFAULT_COMPANY_ID` | Doanh nghiệp mặc định khi request thiếu `X-Company-Id`. Máy phát triển là `1`; file production ép rỗng để request không rõ doanh nghiệp bị từ chối `403 COMPANY_REQUIRED` | rỗng | Không |
| `GATEWAY_JWT_ENABLED` | Gateway bắt buộc JWT hợp lệ (trừ đường dẫn công khai); cũng bắt buộc JWT ở WebSocket `/ws-wms`. Máy phát triển mặc định `false` | `true` | Không |
| `GATEWAY_COMPANY_REQUIRED` | Token không có `companyId` bị `401` | `true` | Không |
| `CORS_ALLOWED_ORIGINS` | Origin của giao diện, cách nhau bằng dấu phẩy; sai thì đăng nhập bị `403` | `https://<DOMAIN>` | Không |
| `RATE_LIMIT_ENABLED` | Bật giới hạn tốc độ chung của gateway (Redis, token bucket) | `true` | Không |
| `RATE_LIMIT_REPLENISH_RATE` | Số request mỗi giây được nạp lại | `20` | Không |
| `RATE_LIMIT_BURST_CAPACITY` | Sức chứa bùng nổ tối đa | `40` | Không |
| `GATEWAY_API_KEY_ENABLED` | Bật xác thực bằng `X-Api-Key` cho hệ thống ngoài | `true` | Không |
| `API_KEY_RATE_LIMIT_PER_SECOND` | Số request tối đa mỗi giây của một API key, vượt thì `429` ([INTEGRATION.md](INTEGRATION.md)) | `20` | Không |
| `GATEWAY_SUBSCRIPTION_ENABLED` | Chặn thao tác ghi khi thuê bao hết hạn; có trong `api-gateway/application.yml`, compose không truyền | `true` | Không |
| `SWAGGER_ENABLED` | Giao diện OpenAPI tổng hợp tại gateway. Máy chủ mặc định tắt, máy phát triển mặc định bật | `false` | Không |
| `FRONTEND_PORT` | Cổng publish của frontend (chỉ máy phát triển; production do Caddy giữ cổng 80) | `80` | Không |

### 4.6. Thanh toán PayOS (auth-service)

| Biến | Ý nghĩa | Mặc định | Bí mật |
| --- | --- | --- | --- |
| `PAYOS_MOCK` | `true` là thanh toán giả lập, không có tiền thật | `true` | Không |
| `PAYOS_CLIENT_ID` | Client ID của kênh PayOS | rỗng | Có |
| `PAYOS_API_KEY` | API Key của kênh PayOS | rỗng | Có |
| `PAYOS_CHECKSUM_KEY` | Checksum Key, dùng kiểm tra chữ ký webhook (cần cả khi còn giả lập) | rỗng | Có |
| `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL` | Địa chỉ quay về và hủy | `https://<DOMAIN>/payment/result` | Không |
| `PAYOS_BASE_URL` | Địa chỉ API PayOS; có trong `docker-compose.yml`, không có trong file mẫu `.env` | `https://api-merchant.payos.vn` | Không |

### 4.7. AI (ai-service)

| Biến | Ý nghĩa | Mặc định | Bí mật |
| --- | --- | --- | --- |
| `AI_MOCK` | `true` là trả câu trả lời mẫu, không gọi Claude | `true` | Không |
| `ANTHROPIC_API_KEY` | Khóa API Anthropic; thiếu khóa thì ai-service tự chạy giả lập | rỗng | Có |
| `AI_MODEL` | Mô hình Claude | `claude-sonnet-5-5` | Không |
| `AI_EFFORT` | Mức suy nghĩ: `low`, `medium`, `high` | `low` | Không |
| `AI_SERVER_FALLBACK` | Tham số `fallbacks` (beta); đặt `false` nếu API từ chối, mọi câu hỏi trả `503` | `true` | Không |

### 4.8. Email

| Biến | Ý nghĩa | Mặc định | Bí mật |
| --- | --- | --- | --- |
| `MAIL_HOST`, `MAIL_PORT` | Máy chủ SMTP của notification-service | `smtp.gmail.com`, `587` | Không |
| `MAIL_USERNAME` | Tài khoản SMTP; để trống thì không gửi mà ghi liên kết ra log | rỗng | Có |
| `MAIL_PASSWORD` | Mật khẩu SMTP (Gmail: Mật khẩu ứng dụng) | rỗng | Có |
| `APP_FRONTEND_URL` | Địa chỉ gốc trong liên kết email kích hoạt và đặt lại mật khẩu | `https://<DOMAIN>` | Không |
| `REGISTRATION_REQUIRE_ACTIVATION` | Doanh nghiệp mới phải bấm liên kết kích hoạt trong email; để trống là `true`. Chỉ máy phát triển không có mail mới đặt `false` | `true` | Không |

### 4.9. Docker Compose, script triển khai và biến ít dùng

| Biến | Ý nghĩa | Mặc định | Bí mật |
| --- | --- | --- | --- |
| `COMPOSE_PATH_SEPARATOR`, `COMPOSE_FILE` | Để `docker compose` tự dùng cả hai file compose | `:`, `docker-compose.yml:deploy/docker-compose.prod.yml` | Không |
| `APP_DIR`, `BRANCH`, `REPO_URL`, `WAIT_TIMEOUT` | Biến của `deploy.sh` (truyền khi chạy, không nằm trong `.env`) | `/opt/wms-supply-chain`, `develop`, URL repository, `600` | Không |
| `HEALTH_SHOW_DETAILS` | `/actuator/health` hiện chi tiết hay không; có trong `application.yml`, compose không truyền nên luôn `always` | `always` | Không |
| `LOG_LEVEL` | Mức log của từng service (`config-repo/*.yml`); compose không truyền | `INFO` | Không |

Các biến sau có trong `application.yml` nhưng `docker-compose.yml` không truyền vào container, nên muốn dùng phải thêm vào mục `environment` của service: `AI_MAX_TOKENS` (4096), `AI_MOCK_COUNTS_USAGE` (true), `MAIL_FROM` (`no-reply@wms.local`), `GATEWAY_PUBLIC_URL` (`http://localhost:8080`, địa chỉ hiện trong mô tả Swagger), `APP_DEMO_TOOLS` (false), `AUTH_DEV_LOGIN_ENABLED` (true), `AUTH_DEV_PASSWORD`.

## 5. Cập nhật phiên bản và quay lại bản cũ

Cập nhật (mục 8.1 của deploy/README.md):

```bash
cd /opt/wms-supply-chain
/usr/local/bin/wms-backup.sh        # sao lưu trước (script ở mục 9.2 của deploy/README.md, xem mục 7 dưới đây)
bash deploy/deploy.sh               # lấy mã nguồn mới nhất của origin/develop, build, up -d, chờ healthy
```

`deploy.sh` dùng `git reset --hard` nên mọi sửa đổi tay vào file đã được Git theo dõi sẽ mất; `.env` được giữ. Migration Flyway tự chạy khi service khởi động. Service Java mất một đến hai phút để lên lại nên có gián đoạn ngắn.

Quay lại bản trước: máy chủ build từ mã nguồn nên quay lại là đưa mã nguồn về một commit cũ rồi build lại (không có "thẻ image" để chọn):

```bash
cd /opt/wms-supply-chain
git log --oneline -10               # tìm commit tốt (deploy.sh in commit đang triển khai ở mỗi lần chạy)
git reset --hard <commit>
docker compose up -d --build
```

Lần chạy `deploy.sh` kế tiếp sẽ đưa mã nguồn về lại bản mới nhất của nhánh, nên chỉ lùi tạm trong lúc chờ sửa lỗi.

Lưu ý quan trọng về cơ sở dữ liệu: lùi mã nguồn không lùi migration đã chạy. Nếu bản mới đã thêm cột hoặc bảng, bản cũ chạy trên cơ sở dữ liệu mới hơn có thể hoạt động bình thường hoặc lỗi tùy thay đổi; kiểm tra log ngay sau khi lùi. Cách chắc chắn nhất khi bản mới đã chạy migration lỗi hoặc làm sai dữ liệu: dừng service, khôi phục bản sao lưu lấy ngay trước khi cập nhật (mục 7), rồi lùi mã nguồn như trên.

Khi máy chủ chuyển sang dùng image từ GHCR (hướng phát triển, [CI_CD.md](CI_CD.md) mục 2): tên image `ghcr.io/letoan0902/wms-supply-chain/<service>`, thẻ `develop`, `latest` và `sha-<7 ký tự commit>`; quay lại bản cũ là khai báo thẻ `sha-...` của commit tốt cho từng service rồi `docker compose pull && docker compose up -d`. Việc này chưa được cấu hình trong repository.

## 6. Kiểm tra sức khỏe và tìm lỗi

### 6.1. Kiểm tra nhanh

```bash
cd /opt/wms-supply-chain
docker compose ps                                  # mọi service Up; service có healthcheck ở trạng thái healthy; rabbitmq-init ở trạng thái Exited (0)
curl -fsS http://127.0.0.1:8080/actuator/health    # gateway: {"status":"UP",...}
curl -fsS http://127.0.0.1:8081/actuator/health    # 8081 auth, 8082 product, 8083 inventory, 8084 order, 8085 notification, 8086 ai
curl -I https://<DOMAIN>/                          # HTTP/2 200
```

`/actuator/health` chỉ truy cập được từ chính máy chủ (cổng `127.0.0.1`); Nginx của frontend không chuyển `/actuator` ra ngoài. Script `deploy/smoke-test.sh` kiểm tra đăng nhập và API qua tên miền ([SMOKE_TEST.md](../deploy/SMOKE_TEST.md)).

### 6.2. Log

```bash
docker compose logs --tail 100 <tên-service>       # caddy, frontend, api-gateway, auth-service, product-service, inventory-service, order-service, notification-service, ai-service, config-server, discovery-server, mysql, redis, rabbitmq
docker compose logs -f --tail 100 api-gateway
docker compose logs --since 1h auth-service
docker compose logs rabbitmq-init                  # nạp topology RabbitMQ
```

Thứ tự khởi động: `mysql`, `redis`, `rabbitmq` (và `rabbitmq-init`), `config-server`, `discovery-server`, rồi các service nghiệp vụ và `api-gateway`, cuối cùng `frontend`, `caddy`. Service nào chưa lên thì xem log của service đứng trước nó. Các lỗi thường gặp và cách xử lý nằm ở mục 10 của [deploy/README.md](../deploy/README.md).

### 6.3. RabbitMQ và hàng đợi dead-letter

Hàng đợi nghiệp vụ: `inventory.order-events`, `notification.alerts`, `notification.auth-emails`, `notification.inventory-events`, `notification.order-events`. Tin bị từ chối sau khi thử lại 3 lần được chuyển sang `wms.dead-letter` (giữ 7 ngày, tối đa 10000 tin, không có consumer tự động); chi tiết ở mục 4 của [MESSAGING.md](MESSAGING.md).

```bash
docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers
docker compose exec rabbitmq rabbitmqctl list_policies
```

`messages` của `wms.dead-letter` lớn hơn 0 nghĩa là có sự kiện chưa xử lý được. Xem nội dung (Get messages, có header `x-death`) qua giao diện quản trị bằng SSH tunnel (mục 8.2 của deploy/README.md): `ssh -L 15672:127.0.0.1:15672 root@<DOMAIN>` rồi mở `http://localhost:15672`. Sửa nguyên nhân rồi publish lại vào exchange `wms.events` với routing key cũ.

### 6.4. Redis

Khóa chính (xem [SAAS_SPEC.md](SAAS_SPEC.md) mục 2.7 và mục WMS-25.1):

| Khóa | Nội dung |
| --- | --- |
| `wms:subscription:{companyId}` | Gói, trạng thái, hạn của thuê bao (auth-service ghi mỗi lần đăng nhập và định kỳ 5 phút); gateway đọc để gắn `X-Plan` và chặn khi hết hạn |
| `wms:usage:{companyId}`, `wms:usage:ai:{companyId}:{yyyyMM}` | Mức dùng người dùng, kho, SKU; lượt AI trong tháng |
| `wms:apikey:{sha256}`, `wms:apikey:last-used`, `wms:apikey:rate:{keyId}:{giây}` | API key đang hoạt động, lần dùng gần nhất, bộ đếm tốc độ |
| `login:lock:user:{tên}`, `login:lock:ip:{ip}` | Khóa đăng nhập tạm (5 lần sai, 30 phút) |
| `refresh:{tên đăng nhập}` | Refresh token đang hợp lệ |

```bash
docker compose exec redis redis-cli --scan --pattern 'wms:*' | head
docker compose exec redis redis-cli HGETALL wms:subscription:<companyId>
docker compose exec redis redis-cli DEL login:lock:user:<tên-đăng-nhập>      # mở khóa đăng nhập
```

Thiếu khóa thuê bao hoặc Redis lỗi thì gateway cho qua (không chặn). Xóa Redis không mất dữ liệu nghiệp vụ: người dùng phải đăng nhập lại, bộ nhớ đệm thuê bao và API key được ghi lại trong khoảng 5 phút.

### 6.5. Flyway: migration lỗi

Mỗi service có bảng `flyway_schema_history` trong schema của mình. Migration chạy khi service khởi động; `ddl-auto` là `validate` nên entity phải khớp schema.

- Migration lỗi: service không lên, container khởi động lại liên tục, log (`docker compose logs <service>`) có lỗi của Flyway kèm tên migration (`V<số>__...sql`). MySQL không hoàn tác được DDL, nên migration chạy dở có thể để lại bảng hoặc cột một phần và một dòng `success = 0` trong lịch sử. Kiểm tra:

  ```bash
  docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5" auth_db'
  ```

  Cách xử lý an toàn: dừng service, khôi phục bản sao lưu trước khi cập nhật (mục 7), lùi mã nguồn (mục 5), sửa lỗi bằng một migration mới có số lớn hơn rồi triển khai lại.
- Không bao giờ sửa file migration đã phát hành (`backend/<service>/src/main/resources/db/migration/V*.sql`). Flyway lưu checksum của từng migration; sửa nội dung file đã chạy làm service báo lỗi checksum không khớp khi khởi động. Muốn thay đổi schema, thêm file migration mới với số kế tiếp.
- Lỗi `Schema-validation` (thiếu cột, sai kiểu) nghĩa là entity và schema lệch nhau: cũng xử lý bằng migration mới, không sửa tay trong MySQL.

## 7. Sao lưu và phục hồi cơ sở dữ liệu MySQL 8

Cơ sở dữ liệu có năm schema, tạo bởi [docker/mysql/init.sql](../docker/mysql/init.sql): `auth_db`, `product_db`, `inventory_db`, `order_db`, `notification_db` (utf8mb4). `ai-service` không có schema. Tài khoản ứng dụng `wms` có toàn quyền trên năm schema. Dữ liệu nằm trong volume Docker `wms-supply-chain_mysql_data`. Script sao lưu tự động đã có ở mục 9 của [deploy/README.md](../deploy/README.md); phần dưới là các lệnh để dùng và kiểm chứng.

### 7.1. Sao lưu một lần

`--single-transaction` cho bản chụp nhất quán các bảng InnoDB mà không khóa ghi; một lệnh với `--databases` cho một bản chụp chung của cả năm schema. Mật khẩu root do shell trong container đọc từ biến `MYSQL_ROOT_PASSWORD`, không xuất hiện trên dòng lệnh của máy chủ.

```bash
mkdir -p /opt/backups
cd /opt/wms-supply-chain
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --default-character-set=utf8mb4 --databases auth_db product_db inventory_db order_db notification_db' \
  | gzip > /opt/backups/wms-$(date +%F-%H%M).sql.gz
ls -lh /opt/backups
```

Sao lưu riêng từng schema (khôi phục lẻ từng schema được):

```bash
for db in auth_db product_db inventory_db order_db notification_db; do
  docker compose exec -T mysql sh -c "exec mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --routines --triggers --events --default-character-set=utf8mb4 $db" \
    | gzip > /opt/backups/wms-$db-$(date +%F-%H%M).sql.gz
done
```

Nơi lưu: `/opt/backups` trên máy chủ chỉ là bản gần; luôn có thêm một bản ở máy khác. Ví dụ chép sang máy sao lưu:

```bash
rsync -av /opt/backups/ <user>@<máy-sao-lưu>:<thư-mục-sao-lưu>/
```

### 7.2. Sao lưu tự động hằng ngày

Cài script `/usr/local/bin/wms-backup.sh` theo mục 9.2 của [deploy/README.md](../deploy/README.md) (giữ 14 ngày gần nhất), rồi thêm dòng cron chạy 02:00 mỗi ngày:

```cron
0 2 * * * /usr/local/bin/wms-backup.sh >> /var/log/wms-backup.log 2>&1
```

Kiểm tra định kỳ: `tail /var/log/wms-backup.log` có dòng "Đã sao lưu" và `ls -lh /opt/backups` có file mới, dung lượng hợp lý.

### 7.3. Khôi phục

Khôi phục ghi đè dữ liệu hiện có. Dừng các service dùng CSDL, khôi phục, rồi chạy lại:

```bash
cd /opt/wms-supply-chain
docker compose stop api-gateway auth-service product-service inventory-service order-service notification-service

# Bản sao lưu dạng --databases (có CREATE DATABASE): không chỉ định schema
gunzip -c /opt/backups/<file>.sql.gz \
  | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'

# Bản sao lưu của một schema (dạng từng schema ở mục 7.1): chỉ định schema đích
gunzip -c /opt/backups/wms-auth_db-<thời-gian>.sql.gz \
  | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" auth_db'

docker compose up -d
```

Bản sao lưu gồm cả `flyway_schema_history`, nên sau khi khôi phục Flyway biết schema đang ở phiên bản nào. Khôi phục bản cũ rồi chạy mã nguồn mới thì Flyway chạy tiếp các migration còn thiếu; khôi phục bản mới hơn mã nguồn đang chạy thì phải đưa mã nguồn về phiên bản tương ứng (mục 5). Sau khi khôi phục, người dùng phải đăng nhập lại nếu Redis cũng bị xóa; bộ nhớ đệm thuê bao tự ghi lại khi đăng nhập và định kỳ 5 phút.

### 7.4. Thử khôi phục trên máy dự phòng

Làm định kỳ (ví dụ mỗi tháng) để chắc bản sao lưu dùng được. Máy dự phòng cần Docker và Compose, không cần domain hay chứng chỉ.

```bash
# 1. Lấy mã nguồn và chép bản sao lưu sang máy dự phòng
git clone --branch develop https://github.com/letoan0902/wms-supply-chain.git
cd wms-supply-chain
cp .env.example .env
# sửa .env: đặt MYSQL_ROOT_PASSWORD, DB_PASSWORD, JWT_SECRET (giá trị ngẫu nhiên, không dùng giá trị của máy chủ thật)
scp <user>@<máy-chủ>:/opt/backups/<file>.sql.gz .

# 2. Chỉ chạy MySQL bằng docker-compose.yml gốc (không có COMPOSE_FILE của production trong .env này)
docker compose up -d mysql
docker compose ps mysql              # chờ healthy

# 3. Khôi phục
gunzip -c <file>.sql.gz | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'

# 4. Kiểm tra: lịch sử migration mỗi schema không có dòng lỗi và số bản ghi hợp lý
for db in auth_db product_db inventory_db order_db notification_db; do
  docker compose exec -T mysql sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -e \"SELECT '$db', COUNT(*), SUM(success = 0) FROM flyway_schema_history\" $db"
done
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) AS users FROM users" auth_db'
```

Cột thứ ba của kết quả (số migration lỗi) phải là `0`. Muốn kiểm tra sâu hơn, chạy cả stack (`docker compose up -d`) rồi đăng nhập bằng một tài khoản có trong bản sao lưu và mở vài màn hình dữ liệu. Xong thì xóa máy hoặc chạy `docker compose down -v` (chỉ trên máy dự phòng).

### 7.5. Sao lưu `.env` và volume

- `.env` chứa mật khẩu, khóa JWT, khóa PayOS và AI; mất `MYSQL_ROOT_PASSWORD` hoặc `DB_PASSWORD` là không vào lại được dữ liệu MySQL đã có. Lưu một bản ở nơi an toàn (trình quản lý mật khẩu hoặc kho lưu trữ mã hóa), không đặt cạnh bản sao lưu dữ liệu ở dạng rõ, không commit.
- `mysql_data`: dùng `mysqldump` ở trên; không chép thô thư mục dữ liệu khi MySQL đang chạy.
- `caddy_data`: chứng chỉ TLS và tài khoản ACME. Mất thì Caddy tự xin lại (chịu giới hạn của Let's Encrypt, mục 10.2 của deploy/README.md), nên sao lưu là tùy chọn:

  ```bash
  docker run --rm -v wms-supply-chain_caddy_data:/data:ro -v /opt/backups:/backup alpine \
    tar czf /backup/caddy-data-$(date +%F).tgz -C /data .
  ```

- `redis_data`: bộ nhớ đệm, bộ đếm, khóa đăng nhập, refresh token; không cần sao lưu (mất thì người dùng đăng nhập lại).
- `rabbitmq_data`: topology được nạp lại mỗi lần `docker compose up` bởi `rabbitmq-init`; tin đang chờ là dữ liệu tạm, không cần sao lưu thường xuyên.

### 7.6. Dọn dẹp

Không chạy `docker compose down -v` trên máy chủ: lệnh xóa volume `mysql_data` (toàn bộ dữ liệu) và `caddy_data` (chứng chỉ).

## 8. Công việc định kỳ

### 8.1. Xoay `JWT_SECRET`

```bash
cd /opt/wms-supply-chain
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" .env
docker compose up -d        # tạo lại các service dùng khóa này (gateway, auth, product, inventory, order, notification, ai)
```

Hệ quả: mọi access token và refresh token đang có không còn hợp lệ nên toàn bộ người dùng bị đăng xuất và phải đăng nhập lại; kết nối WebSocket đang mở bị ngắt. API key của hệ thống ngoài không bị ảnh hưởng (key được kiểm tra bằng giá trị băm, không dùng khóa JWT). Mọi service đọc cùng biến `JWT_SECRET` (khối `x-backend-env` của `docker-compose.yml`) nên chỉ cần đổi một lần trong `.env`. Làm ngoài giờ cao điểm.

### 8.2. Đổi mật khẩu MySQL

Đổi trong `.env` không đổi mật khẩu bên trong MySQL. Đổi trong MySQL trước, rồi sửa `.env` và tạo lại service:

```bash
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' <<'SQL'
ALTER USER 'wms'@'%' IDENTIFIED BY '<mật-khẩu-mới>';
SQL
# sửa DB_PASSWORD trong .env cho khớp, rồi
docker compose up -d
```

Lệnh trên để mật khẩu mới trong lịch sử shell nếu gõ trực tiếp; nên dùng phiên shell không lưu lịch sử. Đổi mật khẩu root MySQL phức tạp hơn (container đọc `MYSQL_ROOT_PASSWORD` cho healthcheck và sao lưu), cần `ALTER USER 'root'@'localhost'` rồi cập nhật `.env`; làm ngoài giờ và thử kỹ.

### 8.3. Thêm khóa PayOS và khóa AI, tắt chế độ giả lập

Các bước đầy đủ (gồm đăng ký webhook PayOS và kiểm tra) ở mục 8.3 và 8.4 của [deploy/README.md](../deploy/README.md). Tóm tắt:

- PayOS: điền `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` vào `.env`, chạy `docker compose up -d auth-service`, đăng ký webhook `https://<DOMAIN>/api/auth/payments/payos/webhook`, rồi đặt `PAYOS_MOCK=false` và chạy lại `docker compose up -d auth-service`. Thiếu khóa mà `PAYOS_MOCK=false` thì tạo thanh toán trả `503`.
- AI: điền `ANTHROPIC_API_KEY`, đặt `AI_MOCK=false`, chạy `docker compose up -d ai-service`. Kiểm tra: `curl -s -H 'X-Company-Id: 1' http://127.0.0.1:8086/api/ai/health` trả `"mode":"LIVE"`. Thiếu khóa thì ai-service tự chạy giả lập.

### 8.4. Cấu hình SMTP

Điền `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` (Gmail dùng Mật khẩu ứng dụng) vào `.env`, rồi `docker compose up -d notification-service`. Khi chưa có SMTP, liên kết kích hoạt và đặt lại mật khẩu được ghi ra log: `docker compose logs notification-service | grep "liên kết"`. `REGISTRATION_REQUIRE_ACTIVATION` giữ trống (luôn bắt buộc kích hoạt) ở production. Chi tiết mục 8.5 của deploy/README.md.

### 8.5. Bật Swagger tạm thời

```bash
# thêm vào .env: SWAGGER_ENABLED=true
docker compose up -d api-gateway
# từ máy cá nhân, mở tunnel tới cổng 8080 của gateway (cổng chỉ nghe trên 127.0.0.1 của máy chủ)
ssh -L 8080:127.0.0.1:8080 root@<DOMAIN>
# rồi mở http://localhost:8080/swagger-ui.html
```

Tên miền công khai chỉ chuyển `/api/` tới gateway (Nginx của frontend), nên `/swagger-ui.html` qua tên miền trả giao diện web chứ không phải Swagger; dùng tunnel như trên. Xem xong đặt lại `SWAGGER_ENABLED=false` (hoặc xóa dòng) và chạy lại `docker compose up -d api-gateway`.

### 8.6. Tài khoản mẫu

Mật khẩu `Demopassword` của các tài khoản mẫu nằm công khai trong repository. Đổi mật khẩu và vô hiệu hóa tài khoản không dùng, đặc biệt `demo` (mục 7 của deploy/README.md).

## 9. Sau khi kết thúc khóa học

- Xóa `ANTHROPIC_API_KEY` khỏi `.env`, đặt `AI_MOCK=true`, chạy `docker compose up -d ai-service`, thu hồi khóa trên Claude Console.
- Xóa ba khóa PayOS khỏi `.env`, đặt `PAYOS_MOCK=true`, chạy `docker compose up -d auth-service`, hủy webhook hoặc thu hồi khóa ở my.payos.vn.
- Thu hồi Mật khẩu ứng dụng SMTP và xóa `MAIL_USERNAME`, `MAIL_PASSWORD`.
- Thu hồi mọi API key của hệ thống ngoài ở `/settings/api-keys`.
- Xóa deploy key đã thêm trên GitHub (nếu có) và xóa bản sao `.env`, bản sao lưu ở các máy cá nhân.
- Nếu ngừng dịch vụ: xóa bản ghi DNS, hủy hoặc xóa máy chủ sau khi đã lưu bản sao lưu cuối.

## 10. Danh sách kiểm tra bàn giao

- [ ] Người tiếp quản đăng nhập được vào máy chủ bằng SSH khóa, mở được `https://<DOMAIN>`.
- [ ] Có bản `.env` đầy đủ ở nơi an toàn; biết biến nào là bí mật (mục 4).
- [ ] `docker compose ps` mọi service healthy; `smoke-test.sh` đạt.
- [ ] Đã đổi mật khẩu tài khoản mẫu, `demo` đã vô hiệu hóa.
- [ ] Sao lưu tự động chạy (cron, log có dòng "Đã sao lưu"), có bản chép sang máy khác.
- [ ] Đã thử khôi phục trên máy dự phòng thành công (mục 7.4), ghi lại ngày thử.
- [ ] Biết cập nhật (`bash deploy/deploy.sh`) và lùi phiên bản (mục 5).
- [ ] Biết cách xem log, hàng đợi dead-letter, khóa Redis (mục 6).
- [ ] Hiểu quy tắc Flyway: không sửa migration đã phát hành.
- [ ] Biết tắt chế độ giả lập PayOS và AI, cấu hình SMTP (mục 8).
- [ ] Đã thu hồi các khóa không còn dùng (mục 9).
- [ ] Tài khoản GitHub, tên miền, nhà cung cấp VPS, Jira đã được chuyển quyền hoặc chia sẻ.
