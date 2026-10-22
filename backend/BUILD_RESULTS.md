# Kết quả build và kiểm thử backend

Kiểm chứng ngày 2026-10-23, thực hiện hoàn toàn trong Docker (máy không có JDK 21/Gradle cục bộ).
Mọi số liệu dưới đây là của lần chạy cuối cùng trên mã nguồn hiện tại.

## 1. Môi trường

| Thành phần | Giá trị |
|---|---|
| Hệ điều hành máy chủ | Windows 11, Docker Desktop (Docker Engine 29.7.2, WSL2, 16 CPU, 15,5 GB RAM) |
| Image build | `gradle:8.10-jdk17` (Gradle 8.10.2, JDK Eclipse Adoptium 17.0.13+11) |
| Spring Boot / Spring Cloud | 3.3.13 / 2023.0.6 |
| Gradle Wrapper | 8.10 (có `distributionSha256Sum`), kiểm tra bằng `./gradlew --version` và `./gradlew projects` trong Docker: đạt, liệt kê đủ 9 module |

## 2. Build toàn bộ module: `gradle build -x test`

Lệnh (từ thư mục `backend/`, Git Bash). Chạy sau khi xóa sạch `build/` và `.gradle/`, tắt build cache để biên dịch lại từ đầu;
volume `wms-gradle-cache` chỉ lưu thư viện đã tải để khỏi tải lại.

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/home/gradle/project" -v wms-gradle-cache:/home/gradle/.gradle \
  -w /home/gradle/project gradle:8.10-jdk17 gradle build -x test --no-daemon --no-build-cache --console=plain
```

Output rút gọn:

```
> Task :common:jar
> Task :config-server:bootJar
> Task :discovery-server:bootJar
> Task :api-gateway:bootJar
> Task :product-service:bootJar
> Task :inventory-service:bootJar
> Task :auth-service:bootJar
> Task :order-service:bootJar
> Task :notification-service:bootJar

BUILD SUCCESSFUL in 36s
43 actionable tasks: 43 executed
```

Không có cảnh báo biên dịch hay cảnh báo deprecation của Gradle. Kích thước file jar:

| Module | Jar | Kích thước |
|---|---|---|
| common | `common-0.1.0-SNAPSHOT.jar` (thư viện) | nhỏ (vài chục KB) |
| config-server | `config-server.jar` | 41,1 MB |
| discovery-server | `discovery-server.jar` | 55,1 MB |
| api-gateway | `api-gateway.jar` | 51,4 MB |
| auth-service | `auth-service.jar` | 92,8 MB |
| product-service | `product-service.jar` | 92,2 MB |
| inventory-service | `inventory-service.jar` | 92,7 MB |
| order-service | `order-service.jar` | 92,7 MB |
| notification-service | `notification-service.jar` | 92,9 MB |

## 3. Chạy test: `gradle test`

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/home/gradle/project" -v wms-gradle-cache:/home/gradle/.gradle \
  -w /home/gradle/project gradle:8.10-jdk17 gradle test --no-daemon --no-build-cache --console=plain
```

Output rút gọn:

```
> Task :common:test
ApiResponseTest > okWrapsDataAndMarksSuccess() PASSED
GlobalExceptionHandlerTest > apiExceptionIsMappedToItsHttpStatusAndErrorCode() PASSED
...
AuthServiceApplicationTest > loginWithDevAccountReturnsValidJwt() PASSED
JwtServiceTest > expiredTokenIsRejected() PASSED

BUILD SUCCESSFUL in 3m 41s
51 actionable tasks: 24 executed, 27 up-to-date
```

Kết quả: 72 test đạt, 0 lỗi, 0 bỏ qua.

| Module | Lớp test | Số test | Nội dung chính |
|---|---|---|---|
| common | `ApiResponseTest`, `PageResponseTest`, `RolesTest`, `GlobalExceptionHandlerTest`, `WmsCommonAutoConfigurationTest`, `RequestHeaderAuditorAwareTest` | 22 | khung phản hồi, phân trang, vai trò, xử lý lỗi (404/400/405/500, thông báo tiếng Việt), tự cấu hình, người thực hiện audit |
| config-server | `ConfigServerApplicationTest` | 3 | khởi động thật, phục vụ `config-repo` (chung và theo service), health |
| discovery-server | `DiscoveryServerApplicationTest` | 2 | khởi động Eureka thật, health, dashboard |
| api-gateway | `ApiGatewayApplicationTest`, `JwtAuthenticationGlobalFilterTest` | 10 | health, 5 route, CORS preflight, filter JWT (token đúng/sai/hết hạn/thiếu, đường dẫn công khai) |
| auth-service | `AuthServiceApplicationTest`, `JwtServiceTest` | 14 | đăng nhập giả lập, JWT hợp lệ, `/me`, 401/400, tạo/kiểm tra/giả mạo/hết hạn token |
| product-service | `ProductServiceApplicationTest` | 3 | context + H2, danh sách rỗng, ánh xạ sản phẩm kèm danh mục/đơn vị tính, cột audit |
| inventory-service | `InventoryServiceApplicationTest` | 4 | context + H2, Feign client, danh sách kho |
| order-service | `OrderServiceApplicationTest` | 4 | context + H2, Feign client, đơn mua phân trang |
| notification-service | `NotificationServiceApplicationTest`, `RabbitMqConfigTest`, `MailSenderCredentialsPostProcessorTest` | 10 | context + H2, health-info, gửi email SENT/FAILED, binding và bộ chuyển đổi JSON, chuẩn hóa tài khoản SMTP |

Test của các service nghiệp vụ dùng profile `test`: H2 trong bộ nhớ, `ddl-auto: create-drop`, tắt Flyway, Eureka và Config Server.
Nên không cần MySQL, Redis hay RabbitMQ khi chạy `gradle test`.

## 4. Docker image

Build cả 8 image từ thư mục gốc repo (build context là `backend/`), mỗi lệnh dạng:

```bash
docker build -f backend/inventory-service/Dockerfile -t wms-inventory:dev backend/
docker build -f backend/discovery-server/Dockerfile  -t wms-discovery:dev backend/
```

Cả 8 image build thành công. Lần build nguội (bốn image song song) mất 70-113 giây mỗi image, gồm tải thư viện và biên dịch trong
container build; build lại khi lớp đã có trong cache chỉ vài giây.

| Image | Cổng EXPOSE | Kích thước (`docker images`) | Nội dung nén (`docker image inspect`) |
|---|---|---|---|
| `wms-config:dev` | 8888 | 338 MB | 101 MB |
| `wms-discovery:dev` | 8761 | 365 MB | 113 MB |
| `wms-gateway:dev` | 8080 | 358 MB | 110 MB |
| `wms-auth:dev` | 8081 | 441 MB | 148 MB |
| `wms-product:dev` | 8082 | 440 MB | 147 MB |
| `wms-inventory:dev` | 8083 | 441 MB | 148 MB |
| `wms-order:dev` | 8084 | 441 MB | 148 MB |
| `wms-notification:dev` | 8085 | 441 MB | 148 MB |

Phần lớn dung lượng là `eclipse-temurin:17-jre-alpine` (JRE 17 khoảng 141 MB cộng gói hệ thống) và file jar của service (41-93 MB).
Đã xác nhận khi chạy: tiến trình chạy bằng user `wms` (không phải root), `HEALTHCHECK` chuyển sang `healthy` ở cả 8 container.
Sau kiểm chứng đã xóa toàn bộ 8 image `wms-*:dev`.

## 5. Kiểm thử tích hợp trên MySQL thật

### 5.1. Compose tạm (không thuộc repo)

Dựng cả 8 image cùng MySQL 8.0, Redis 7, RabbitMQ (management) và Mailpit trên một mạng Docker riêng, không publish cổng cố định.
Bộ kiểm tra tự động gồm 103 điểm, kết quả 103 đạt, 0 lỗi:

- Eureka có đủ 6 ứng dụng (gateway và 5 service nghiệp vụ); Config Server phục vụ `config-repo` và các service nhận cấu hình
  (log: "Located environment: name=auth-service").
- Flyway chạy `V1__init.sql` thành công trên cả 5 database; Hibernate `ddl-auto: validate` khớp schema thật (kiểu boolean, decimal,
  datetime, varchar cho enum, lớp cha `AuditableEntity`); `auth_db.roles` có đủ 6 vai trò; 6 bảng mẫu có đủ 4 cột audit.
- Gateway định tuyến (`lb://`) tới cả 5 service: login (`expiresIn` = 900 giây), `/me`, `GET /api/products`,
  `/api/inventory/warehouses`, `/api/orders/purchase`, `/api/notifications/health-info`; đường dẫn lạ trả 404;
  CORS cho phép `http://localhost:5173`, chặn origin lạ.
- Mỗi service nghiệp vụ: `/actuator/health` UP kèm `db`, `redis`, `rabbit` UP; `/actuator/health/liveness`; `/actuator/info` có build-info;
  `/v3/api-docs` và `/swagger-ui/index.html` hoạt động.
- Notification đầu-cuối: publish JSON vào exchange `wms.events` (routing key `alert.low-stock`, kể cả khi header `__TypeId__` trỏ tới class
  không tồn tại) -> consumer nhận -> Mailpit nhận email -> `notification_logs` ghi `SENT`, `created_by` = `system`.
  Kiểm tra riêng (lần chạy trước): khi SMTP tắt ghi `FAILED`; tin nhắn JSON hỏng bị loại một lần, hàng đợi về 0, không xoay vòng.
- Bật `GATEWAY_JWT_ENABLED=true` (kiểm tra riêng): thiếu token trả 401 (JSON `ApiResponse`), token do auth-service cấp được chấp nhận,
  token giả bị từ chối, header `X-User-Roles` do client tự gửi bị gỡ, preflight CORS không cần token.
- Từ lúc service nghiệp vụ báo "Started" đến khi gateway route được: 2-4 giây; khởi động toàn stack khoảng 35 giây.

### 5.2. Chạy bằng `docker-compose.yml` ở gốc repo (do thành viên khác soạn, chỉ đọc)

Build và chạy phần backend của file compose gốc (MySQL user `wms` không phải root, `docker/mysql/init.sql` tạo 5 database,
`EUREKA_URL` không có dấu `/` cuối, `TZ=Asia/Ho_Chi_Minh`, healthcheck `curl ... || wget ...` vào `/actuator/health`), có file override
đặt ngoài repo để bỏ cổng cố định:

- 11/11 container `healthy` sau khoảng 1 phút (mysql, redis, rabbitmq, config-server, discovery-server, api-gateway và 5 service nghiệp vụ).
- Eureka có đủ 6 ứng dụng; login, `/me` và 4 endpoint khung trả 200 qua gateway; Flyway `V1` thành công trên 5 database;
  bảng dùng collation `utf8mb4_unicode_ci` kế thừa từ database; consumer RabbitMQ ghi `FAILED` khi SMTP không tới được.
- Cổng 8080 của gateway trong compose gốc trùng với container `campus-hub-backend` đang chạy trên máy này nên đã phải override khi thử.

## 6. Dọn dẹp sau kiểm chứng

- Đã xóa: `backend/build`, `backend/*/build`, `backend/.gradle` (giữ nguyên `gradlew`, `gradlew.bat`, `gradle/wrapper/*`);
  8 image `wms-*:dev` và 8 image do compose gốc build; container, mạng và volume của các compose thử nghiệm;
  container Gradle tạm và volume cache `wms-gradle-cache`.
- Không có bí mật thật trong mã nguồn: JWT secret, mật khẩu MySQL, tài khoản RabbitMQ chỉ là giá trị mặc định dev
  (`change_me...`, `guest`), luôn đọc từ biến môi trường.

## 7. Điểm chưa chắc và hạn chế

- Spring Boot 3.3.x đã hết thời hạn hỗ trợ mã nguồn mở (từ giữa năm 2025) nhưng vẫn dùng vì đặc tả của nhóm bắt buộc; bản dùng là 3.3.13,
  bản mới nhất của nhánh 3.3 trên Maven Central. Nên lên kế hoạch nâng cấp lên Boot 3.5.x trở lên kèm Spring Cloud tương ứng.
- Java toolchain tự tải JDK 17 (plugin foojay) chưa được thử thật vì container build đã có sẵn JDK 17.
- Chưa chạy `./gradlew test` trực tiếp trên máy Windows của nhóm (chỉ kiểm trong Docker/Linux).
- `JWT_SECRET` dùng thuật toán đối xứng HS256 chia sẻ giữa auth-service và gateway; chưa có refresh token, thu hồi token hay khóa bất đối xứng.
- `created_by` / `updated_by` lấy từ header `X-User-Name`, chỉ đáng tin khi service nhận request qua gateway (mạng nội bộ);
  khi `GATEWAY_JWT_ENABLED=false` (mặc định) cột này luôn là `system`.
- JDBC URL mặc định có thêm `?createDatabaseIfNotExist=true` (ngoài `jdbc:mysql://localhost:3306/<tên>_db` theo đặc tả) để chạy được ngay trên MySQL trống.
- Cấu hình dev tinh chỉnh cho nhanh (Eureka tắt self-preservation và cache chỉ-đọc, gateway kéo registry mỗi 5 giây,
  `HEALTH_SHOW_DETAILS=always`): cần đảo lại khi triển khai thật.
- Config Server chỉ dùng backend native đọc từ trong jar; chưa có backend Git, mã hóa giá trị nhạy cảm (`{cipher}`) hay refresh cấu hình lúc chạy.
- Chưa thử gửi email thật qua Gmail (cổng 587 có xác thực); logic chuẩn hóa tài khoản SMTP chỉ được kiểm bằng test đơn vị.
