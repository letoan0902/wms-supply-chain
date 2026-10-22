# WMS Supply Chain - Backend

Hệ thống quản lý kho và chuỗi cung ứng (Warehouse Management System). Thư mục `backend/` là monorepo
kiến trúc microservice dùng Spring Cloud. Hiện đang ở giai đoạn khởi tạo (Project Proposal): khung dự án
build được ngay, mỗi service có health endpoint, Dockerfile và cấu hình rõ ràng để nhóm bắt đầu Sprint 1.
Chưa có nghiệp vụ, mọi API hiện chỉ là khung.

## 1. Kiến trúc tổng quan

```
Frontend (http://localhost:5173)
        |
        v
   api-gateway :8080 ---- /api/** (lb://) ---> auth-service         :8081 -> auth_db
                                               product-service      :8082 -> product_db
                                               inventory-service    :8083 -> inventory_db
                                               order-service        :8084 -> order_db
                                               notification-service :8085 -> notification_db

 discovery-server :8761 (Eureka)             config-server :8888 (native, config-repo/)
   gateway và 5 service nghiệp vụ              gateway và 5 service nghiệp vụ
   đăng ký và tra cứu nhau qua đây             nhận cấu hình tập trung từ đây

Hạ tầng dùng chung: MySQL 8.0 (:3306)  Redis 7 (:6379)  RabbitMQ (:5672)
```

- Frontend chỉ gọi vào `api-gateway`; gateway định tuyến theo tiền tố đường dẫn và tìm service qua Eureka.
- Mỗi service nghiệp vụ có database riêng (không dùng chung bảng), giao tiếp với nhau qua REST (OpenFeign)
  hoặc sự kiện RabbitMQ.
- Mọi service nghiệp vụ trả về khung phản hồi thống nhất `ApiResponse` (xem mục 13).

## 2. Công nghệ

| Hạng mục | Chuẩn sử dụng |
|---|---|
| Ngôn ngữ / build | Java 17 (toolchain), Gradle 8.10 multi-project, Gradle Wrapper |
| Framework | Spring Boot 3.3.13 |
| Spring Cloud | 2023.0.6 (Config Server, Netflix Eureka, Gateway, OpenFeign) |
| Cơ sở dữ liệu | MySQL 8.0, Spring Data JPA, Flyway 10 |
| Cache / hàng đợi | Redis 7, RabbitMQ (Spring AMQP) |
| Bảo mật | Spring Security + JWT (jjwt 0.12.6) |
| Tài liệu API | springdoc-openapi 2.6.0 (`/swagger-ui.html`) |
| Giám sát | Spring Boot Actuator (`health`, `info`) |
| Tiện ích | Lombok (đã cấu hình annotationProcessor) |
| Container | Docker đa giai đoạn: `gradle:8.10-jdk17` build, `eclipse-temurin:17-jre-alpine` chạy |

## 3. Cấu trúc thư mục

```
backend/
  settings.gradle        # khai báo các module
  build.gradle           # plugin Spring Boot, BOM Spring Cloud, toolchain 17, cấu hình chung
  gradle.properties      # phiên bản thư viện (Spring Cloud, jjwt, springdoc) và tham số Gradle
  gradlew, gradlew.bat, gradle/wrapper/
  .dockerignore, .gitignore, .gitattributes
  common/                # thư viện dùng chung: ApiResponse, PageResponse, ApiException, GlobalExceptionHandler, Roles
  config-server/         # Spring Cloud Config (native), cấu hình tập trung nằm ở src/main/resources/config-repo/
  discovery-server/      # Eureka Server
  api-gateway/           # Spring Cloud Gateway (WebFlux): route, CORS, filter JWT
  auth-service/          # tài khoản, JWT, RBAC
  product-service/       # SKU, danh mục, đơn vị tính, barcode
  inventory-service/     # kho, zone/rack/bin, tồn kho, điều chuyển, kiểm kê
  order-service/         # nhà cung cấp, khách hàng, PO/GRN, SO/GDN
  notification-service/  # nhận sự kiện RabbitMQ, gửi email cảnh báo
```

Mỗi module nghiệp vụ có cùng bố cục: `src/main/java/vn/wms/<tên>/{config,controller,entity,dto,repository,service}`,
`src/main/resources/application.yml`, `src/main/resources/db/migration/V1__init.sql`, `Dockerfile` ở thư mục module.

## 4. Bảng cổng

| Module | Cổng | Vai trò | Database | Health |
|---|---|---|---|---|
| api-gateway | 8080 | Cổng vào duy nhất, CORS, kiểm tra JWT | không | http://localhost:8080/actuator/health |
| auth-service | 8081 | Tài khoản, JWT, RBAC | `auth_db` | http://localhost:8081/actuator/health |
| product-service | 8082 | SKU, danh mục, đơn vị tính, barcode | `product_db` | http://localhost:8082/actuator/health |
| inventory-service | 8083 | Kho, vị trí, tồn kho, điều chuyển, kiểm kê | `inventory_db` | http://localhost:8083/actuator/health |
| order-service | 8084 | Nhà cung cấp, khách hàng, PO/GRN, SO/GDN | `order_db` | http://localhost:8084/actuator/health |
| notification-service | 8085 | RabbitMQ consumer, email cảnh báo | `notification_db` | http://localhost:8085/actuator/health |
| discovery-server | 8761 | Eureka (dashboard tại `/`) | không | http://localhost:8761/actuator/health |
| config-server | 8888 | Cấu hình tập trung | không | http://localhost:8888/actuator/health |

Mỗi service còn có `/actuator/health/liveness` (không phụ thuộc MySQL/Redis/RabbitMQ, dùng cho Docker HEALTHCHECK)
và `/actuator/info`. Service nghiệp vụ có Swagger UI tại `/swagger-ui.html` (ví dụ http://localhost:8082/swagger-ui.html).

Endpoint khung hiện có (gọi qua gateway `:8080` hoặc trực tiếp vào service):

| Endpoint | Service | Mô tả |
|---|---|---|
| `POST /api/auth/login` | auth-service | Đăng nhập giả lập, trả JWT hợp lệ |
| `GET /api/auth/me` | auth-service | Thông tin người dùng từ JWT (cần token) |
| `GET /api/products` | product-service | Danh sách sản phẩm, phân trang (`page`, `size`, `sort`) |
| `GET /api/inventory/warehouses` | inventory-service | Danh sách kho |
| `GET /api/orders/purchase` | order-service | Danh sách đơn mua hàng, phân trang |
| `GET /api/notifications/health-info` | notification-service | Cấu hình RabbitMQ / email đang chạy |

## 5. Yêu cầu môi trường

Có hai cách làm việc, chọn một:

- Chỉ cần Docker Desktop (không cần cài JDK hay Gradle): build và test trong container `gradle:8.10-jdk17`.
- Cài JDK 17 và dùng Gradle Wrapper (`./gradlew`). Nếu máy chưa có JDK 17, Gradle tự tải qua toolchain
  (plugin `foojay-resolver`) ở lần chạy đầu tiên.

Để chạy các service nghiệp vụ cần thêm MySQL 8.0, Redis 7 và RabbitMQ (xem mục 7.1, có thể chạy bằng Docker).

## 6. Build và test

### 6.1. Dùng Gradle Wrapper (máy có JDK)

```bash
cd backend
./gradlew build -x test        # build tất cả module, bỏ qua test
./gradlew test                 # chạy toàn bộ test (dùng H2, không cần MySQL)
./gradlew build                # build + test
./gradlew :auth-service:bootJar    # chỉ đóng gói một service: auth-service/build/libs/auth-service.jar
```

### 6.2. Chỉ dùng Docker (không cần JDK)

Git Bash trên Windows (tham số `MSYS_NO_PATHCONV=1` để Git Bash không đổi sai đường dẫn):

```bash
cd backend
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/home/gradle/project" -w /home/gradle/project \
  gradle:8.10-jdk17 gradle build -x test --no-daemon
```

Linux / macOS dùng `-v "$(pwd):/home/gradle/project"`; PowerShell dùng `-v "${PWD}:/home/gradle/project"`.
Thêm `-v wms-gradle-cache:/home/gradle/.gradle` để giữ cache thư viện giữa các lần chạy (nhanh hơn nhiều).
Đổi `build -x test` thành `test` để chạy test.

Ghi chú: `common` là thư viện cho các service servlet (Spring MVC); `api-gateway` chạy WebFlux nên cố ý
không phụ thuộc `common`.

## 7. Chạy trên máy phát triển

### 7.1. Hạ tầng (MySQL, Redis, RabbitMQ)

```bash
docker run -d --name wms-mysql    -p 3306:3306 -e MYSQL_ROOT_PASSWORD=change_me mysql:8.0
docker run -d --name wms-redis    -p 6379:6379 redis:7-alpine
docker run -d --name wms-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management-alpine
# Tùy chọn: hộp thư giả để xem email cảnh báo của notification-service (SMTP :1025, giao diện :8025)
docker run -d --name wms-mailpit  -p 1025:1025 -p 8025:8025 axllent/mailpit
```

Không cần tạo database thủ công: JDBC URL mặc định có `createDatabaseIfNotExist=true` nên mỗi service tự tạo
database của nó (`auth_db`, `product_db`, `inventory_db`, `order_db`, `notification_db`) rồi Flyway tạo bảng.
Nếu cổng 3306 của máy đã bị chiếm, đổi cổng map và đặt `DB_URL` tương ứng.

### 7.2. Thứ tự khởi động

Mỗi lệnh chạy trong một terminal riêng, từ thư mục `backend/`:

```bash
./gradlew :config-server:bootRun       # 1. Cấu hình tập trung  (8888)
./gradlew :discovery-server:bootRun    # 2. Eureka              (8761)
./gradlew :auth-service:bootRun        # 3. Các service nghiệp vụ, thứ tự tùy ý
./gradlew :product-service:bootRun
./gradlew :inventory-service:bootRun
./gradlew :order-service:bootRun
./gradlew :notification-service:bootRun
./gradlew :api-gateway:bootRun         # 4. Gateway             (8080)
```

Sau khi một service khởi động, gateway cần khoảng 5-10 giây để nhận ra nó qua Eureka; trong thời gian đó gọi qua gateway
có thể nhận HTTP 503 (không có instance), chờ rồi thử lại là được.

Mọi service đều khởi động được khi Config Server chưa chạy (import cấu hình ở dạng `optional:`, dùng cấu hình
cục bộ trong `application.yml`). Chỉ cần chạy các service bạn đang làm cùng hạ tầng tối thiểu của chúng.

### 7.3. Kiểm tra nhanh

```bash
curl http://localhost:8080/actuator/health
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"change_me"}'
curl http://localhost:8080/api/products
curl http://localhost:8080/api/inventory/warehouses
curl http://localhost:8080/api/orders/purchase
curl http://localhost:8080/api/notifications/health-info
```

Eureka dashboard: http://localhost:8761 . Swagger UI từng service: `http://localhost:<cổng>/swagger-ui.html`.

## 8. Docker image

Mỗi module có `Dockerfile` riêng, build đa giai đoạn; build context là thư mục `backend/` (để dùng chung `common`).
Chạy từ thư mục gốc của repo:

```bash
docker build -f backend/inventory-service/Dockerfile -t wms-inventory:dev backend/
docker build -f backend/discovery-server/Dockerfile  -t wms-discovery:dev backend/
```

| Giai đoạn | Image | Nội dung |
|---|---|---|
| build | `gradle:8.10-jdk17` | chạy `gradle :<service>:bootJar -x test --no-daemon` |
| runtime | `eclipse-temurin:17-jre-alpine` | chỉ có JRE và file `app.jar`, chạy bằng user không phải root (`wms`) |

Đặc điểm của image: `EXPOSE` đúng cổng của service; `HEALTHCHECK` gọi `/actuator/health/liveness`;
tham số JVM đặt qua biến `JAVA_OPTS` (mặc định `-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8`).
Trong Docker Compose, địa chỉ các thành phần dùng tên service thay cho `localhost`, ví dụ:
`CONFIG_SERVER_URL=http://config-server:8888`, `EUREKA_URL=http://discovery-server:8761/eureka/`,
`DB_URL=jdbc:mysql://mysql:3306/auth_db?createDatabaseIfNotExist=true`, `REDIS_HOST=redis`, `RABBITMQ_HOST=rabbitmq`.

## 9. Biến môi trường

Mọi giá trị nhạy cảm chỉ là mặc định cho môi trường dev (`change_me`) và luôn đọc từ biến môi trường.
Ngoài các biến dưới đây, mọi thuộc tính Spring đều ghi đè được bằng biến môi trường theo quy tắc relaxed binding
(ví dụ `SPRING_DATA_REDIS_PASSWORD`). Không commit file `.env` chứa giá trị thật.

**Dùng chung cho các service nghiệp vụ** (auth, product, inventory, order, notification):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `SERVER_PORT` | theo bảng cổng | Cổng HTTP của service |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Địa chỉ Config Server |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Địa chỉ Eureka |
| `DB_URL` | `jdbc:mysql://localhost:3306/<tên>_db?createDatabaseIfNotExist=true` | JDBC URL (mỗi service một database) |
| `DB_USERNAME` | `root` | Tài khoản MySQL |
| `DB_PASSWORD` | `change_me` | Mật khẩu MySQL |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | Tài khoản RabbitMQ |
| `HEALTH_SHOW_DETAILS` | `always` | Hiện chi tiết ở `/actuator/health`; production đặt `never` |
| `LOG_LEVEL` | `INFO` | Mức log gói `vn.wms.<service>` (đặt qua `config-repo`) |

**Riêng từng module:**

| Module | Biến | Mặc định | Ý nghĩa |
|---|---|---|---|
| auth-service | `JWT_SECRET` | `change_me_dev_only_jwt_secret_key_min_32_bytes` | Khóa ký HS256, tối thiểu 32 byte, phải trùng với gateway |
| auth-service | `JWT_EXPIRATION_MINUTES` | `15` | Thời gian sống của access token (đặc tả: 15 phút; khi dev có thể tăng để đỡ phải đăng nhập lại) |
| auth-service | `AUTH_DEV_LOGIN_ENABLED` | `true` | Bật đăng nhập giả lập (tắt khi có đăng nhập thật) |
| auth-service | `AUTH_DEV_PASSWORD` | `change_me` | Mật khẩu dùng chung của các tài khoản dev |
| api-gateway | `JWT_SECRET` | như trên | Khóa kiểm tra chữ ký token |
| api-gateway | `GATEWAY_JWT_ENABLED` | `false` | Bật kiểm tra JWT tại gateway |
| api-gateway | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origin frontend được phép (phân tách bằng dấu phẩy) |
| notification-service | `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | Máy chủ SMTP |
| notification-service | `MAIL_USERNAME` / `MAIL_PASSWORD` | rỗng | Tài khoản SMTP; rỗng thì gửi không xác thực. Có giá trị thì tự bật xác thực, cổng 587 tự bật STARTTLS, cổng 465 tự bật SSL |
| notification-service | `MAIL_FROM` | `no-reply@wms.local` | Địa chỉ người gửi |
| notification-service | `NOTIFICATION_EXCHANGE` | `wms.events` | Exchange (topic) nhận sự kiện |
| notification-service | `NOTIFICATION_QUEUE` | `notification.alerts` | Queue của service |
| notification-service | `NOTIFICATION_ROUTING_KEY` | `alert.#` | Routing key binding |
| discovery-server | `EUREKA_HOSTNAME` | `localhost` | Hostname của Eureka |
| discovery-server | `EUREKA_SELF_PRESERVATION` | `false` | Dev tắt để instance chết bị loại nhanh; production nên bật |
| config-server | `CONFIG_REPO_LOCATIONS` | `classpath:/config-repo/` | Nơi đọc cấu hình, ví dụ `file:/config-repo/` |
| mọi module | `JAVA_OPTS` | xem mục 8 | Tham số JVM (chỉ trong image Docker) |

## 10. Cấu hình tập trung (Config Server)

- `config-server` dùng backend `native` và đọc thư mục `config-server/src/main/resources/config-repo/`
  (được đóng gói sẵn trong jar, nên image không cần mount thêm). Muốn sửa cấu hình mà không build lại,
  trỏ `CONFIG_REPO_LOCATIONS` ra thư mục ngoài, ví dụ `file:/config-repo/`.
- `config-repo/application.yml` là cấu hình dùng chung: địa chỉ Eureka, tài khoản MySQL, Redis, RabbitMQ.
  `config-repo/<tên-service>.yml` là cấu hình riêng của từng service (hiện chỉ chỉnh mức log).
- Mỗi service có `spring.config.import: optional:configserver:${CONFIG_SERVER_URL}`: nếu Config Server chưa chạy thì
  vẫn khởi động bằng `application.yml` cục bộ. `discovery-server` và `config-server` không import cấu hình.
- Lưu ý độ ưu tiên: giá trị từ Config Server GHI ĐÈ `application.yml` cục bộ. Vì vậy các khóa xuất hiện ở cả hai nơi
  phải giữ nguyên biểu thức biến môi trường. Không đặt `spring.datasource.url` trong `config-repo` (mỗi service một DB riêng).
- Khi chạy test (profile `test`) import Config Server bị bỏ qua và `spring.cloud.config.enabled=false`, nên test không gọi mạng.

## 11. Bảo mật và JWT

Trạng thái hiện tại là khung, có chủ đích để nhóm phát triển không bị chặn:

- `auth-service` dùng Spring Security không trạng thái. Cho phép `/api/auth/**`, `/actuator/**`, Swagger;
  riêng `GET /api/auth/me` yêu cầu token. Mọi đường dẫn khác cần xác thực.
- Đăng nhập là GIẢ LẬP nhưng JWT là thật (jjwt, HS256, ký bằng `JWT_SECRET`, có `sub`, `roles`, `iss`, `iat`, `exp`).
  Mật khẩu chung là `change_me` (`AUTH_DEV_PASSWORD`), tài khoản dev ứng với 6 vai trò:

  | Tên đăng nhập | Vai trò |
  |---|---|
  | `admin` | `ADMIN` |
  | `manager` | `WAREHOUSE_MANAGER` |
  | `staff` | `WAREHOUSE_STAFF` |
  | `purchasing` | `PURCHASING` |
  | `sales` | `SALES` |
  | `accountant` | `ACCOUNTANT` |

- Access token mặc định sống 15 phút (`JWT_EXPIRATION_MINUTES`, đúng đặc tả). Khung chưa có Refresh Token nên khi dev
  có thể tăng giá trị này (ví dụ 480) để đỡ phải đăng nhập lại.
- `api-gateway` có sẵn `JwtAuthenticationGlobalFilter`: khi `GATEWAY_JWT_ENABLED=true` yêu cầu header Authorization dạng Bearer
  với token hợp lệ (trừ `/api/auth/login`, `/actuator/**` và request preflight CORS), thêm header `X-User-Name`, `X-User-Roles`
  cho service phía sau và luôn gỡ hai header này nếu client tự gửi. Mặc định TẮT.
- Các service còn lại tạm `permitAll` (xem `config/SecurityConfig` trong mỗi service, có ghi TODO).
- Hằng số vai trò dùng chung: `vn.wms.common.constant.Roles` (`ADMIN`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF`,
  `PURCHASING`, `SALES`, `ACCOUNTANT`).
- Trước khi triển khai thật: đặt `JWT_SECRET` mạnh và giống nhau ở auth-service và gateway, đặt
  `AUTH_DEV_LOGIN_ENABLED=false`, đổi mật khẩu MySQL/RabbitMQ, đặt `HEALTH_SHOW_DETAILS=never`.

## 12. Cơ sở dữ liệu và Flyway

- Mỗi service dùng database riêng: `auth_db`, `product_db`, `inventory_db`, `order_db`, `notification_db`.
- Schema do Flyway quản lý (`src/main/resources/db/migration/V1__init.sql`). Hibernate chạy ở chế độ
  `ddl-auto: validate`: entity không khớp schema thì service không khởi động, nhờ đó phát hiện lệch sớm.
- Thay đổi schema: thêm file mới `V2__mo_ta.sql`, tuyệt đối không sửa file `V1` đã chạy (Flyway sẽ báo sai checksum).
- `stock_items.product_id` (inventory) chỉ là tham chiếu logic tới product-service, không có khóa ngoại vì khác database.
- Trạng thái đơn hàng và thông báo lưu dạng chuỗi VARCHAR (không dùng kiểu ENUM native của MySQL).
- Mỗi bảng thực thể có 4 cột audit `created_at`, `updated_at` (UTC), `created_by`, `updated_by`. Entity chỉ cần
  `extends AuditableEntity` (module `common`); auditing tự bật qua auto-configuration. `created_by` / `updated_by`
  lấy từ header `X-User-Name` do gateway chuyển tiếp, không có thì ghi `system` (consumer RabbitMQ, job định kỳ, dev
  khi gateway chưa bật kiểm tra JWT).
- Migration không khai báo `DEFAULT CHARSET`: bảng dùng charset/collation mặc định của database (compose đặt
  `utf8mb4_unicode_ci` trong `docker/mysql/init.sql`). Giá trị mặc định thời gian dùng `UTC_TIMESTAMP(6)`.

## 13. Quy ước phát triển

- Mọi API trả về `ApiResponse<T>` (`success`, `code`, `message`, `data`, `timestamp`); danh sách phân trang trả
  `ApiResponse<PageResponse<T>>` với `page` tính từ 0. Lỗi nghiệp vụ ném `ApiException` (ví dụ
  `ApiException.notFound` kèm thông báo), `GlobalExceptionHandler` trong `common` tự đổi thành phản hồi JSON chuẩn
  kèm HTTP status. Service chỉ cần `implementation project(':common')`, bean được đăng ký qua auto-configuration.
- Cấu trúc gói: `vn.wms.<service>.{config,controller,entity,dto,repository,service}`; entity ở `entity`, DTO là `record`
  (hậu tố `Request` / `Response`). Chi tiết quy ước chung của nhóm nằm ở `docs/CONVENTIONS.md` ở thư mục gốc repo.
- Thêm service mới: tạo module, thêm `include` vào `settings.gradle`, sao chép `build.gradle`, `application.yml`,
  `Dockerfile` của một service nghiệp vụ rồi đổi tên/cổng/DB, thêm route trong `api-gateway/src/main/resources/application.yml`
  và file `config-repo/<tên>.yml`.
- Gọi service khác bằng OpenFeign (đã có mẫu `ProductClient` trong inventory-service và order-service).
- Comment và tài liệu bằng tiếng Việt có dấu; định danh trong code bằng tiếng Anh.

## 14. Kiểm thử

- Mỗi service có test khởi động context thật (`@SpringBootTest`) với profile `test`: H2 trong bộ nhớ,
  Hibernate `create-drop`, tắt Flyway / Eureka / Config Server, không cần MySQL, Redis hay RabbitMQ.
- `common` có test đơn vị cho `ApiResponse`, `PageResponse`, `Roles`, `GlobalExceptionHandler` và auto-configuration.
- Tên lớp test có hậu tố `Test` (ví dụ `ProductServiceApplicationTest`); test mới cần dùng `@ActiveProfiles("test")`.
- Kết quả build/test lần kiểm chứng gần nhất nằm trong `BUILD_RESULTS.md`.

## 15. Lưu ý khi dùng Git trên Windows

- File `gradlew` cần quyền thực thi trong Git, nếu không CI/Linux báo `Permission denied`. Sau khi thêm file lần đầu chạy:

  ```bash
  git update-index --chmod=+x backend/gradlew
  ```

- Phải commit cả `backend/gradle/wrapper/gradle-wrapper.jar` (kiểm tra `.gitignore` ở thư mục gốc không loại trừ file `*.jar`).
- `backend/.gitattributes` ép xuống dòng LF cho mã nguồn, Dockerfile và `gradlew` (`gradlew.bat` giữ CRLF) để Docker/Linux
  chạy được dù clone trên Windows.

## 16. Việc cần làm tiếp theo (gợi ý cho Sprint 1)

- [ ] auth-service: đăng nhập thật dựa trên bảng `users` (BCrypt), API quản lý người dùng và vai trò, tắt `AUTH_DEV_LOGIN_ENABLED`;
  Refresh Token trong HttpOnly cookie, khóa tài khoản tạm thời khi đăng nhập sai nhiều lần (theo `docs/ARCHITECTURE.md`).
- [ ] api-gateway: bật `GATEWAY_JWT_ENABLED=true`, thêm kiểm tra quyền theo vai trò ở mức route và giới hạn tốc độ
  (rate limiting, dùng Redis qua `spring-boot-starter-data-redis-reactive`).
- [ ] Các service nghiệp vụ: thay `permitAll` bằng phân quyền theo vai trò (đọc `X-User-Name` / `X-User-Roles` từ gateway hoặc kiểm tra JWT).
- [ ] product-service: CRUD sản phẩm, danh mục, đơn vị tính; tra cứu theo SKU và barcode (để `ProductClient` dùng).
- [ ] inventory-service: nhập/xuất/điều chuyển/kiểm kê, phát sự kiện cảnh báo tồn kho thấp lên RabbitMQ (routing key `alert.*`).
- [ ] order-service: dòng hàng của PO/SO, phiếu nhập GRN và phiếu xuất GDN.
- [ ] notification-service: mẫu email HTML, cơ chế gửi lại các thông báo `FAILED`.
- [ ] common: xử lý `DataIntegrityViolationException` thành 409, bổ sung mã lỗi nghiệp vụ.
- [ ] Hạ tầng: Docker Compose chạy cả stack, CI chạy `./gradlew build`, quản lý bí mật (không dùng giá trị mặc định `change_me`).
