# WMS Supply Chain - Hệ thống quản lý kho và chuỗi cung ứng

WMS Supply Chain là hệ thống quản lý kho và chuỗi cung ứng xây dựng theo kiến trúc microservice, giúp doanh nghiệp
kiểm soát hàng hóa từ lúc đặt mua, nhập kho, lưu kho, điều chuyển cho tới lúc bán và xuất kho.

Trạng thái hiện tại: **Sprint 1 (20/10 - 23/10/2026): khởi tạo** repository, khung backend, khung frontend,
Docker Compose, CI và tài liệu. Kế hoạch chi tiết xem [docs/SPRINT_PLAN.md](docs/SPRINT_PLAN.md).

## Bài toán

Nhiều doanh nghiệp vừa và nhỏ vẫn quản lý kho bằng giấy tờ và bảng tính, dẫn tới:

- **Lệch số liệu tồn kho** giữa sổ sách và thực tế, mỗi bộ phận giữ một con số khác nhau.
- **Cháy hàng và đọng vốn**: thiếu hàng khi khách cần mua, trong khi hàng chậm luân chuyển lại tồn quá nhiều.
- **Nhập, xuất, kiểm kê thủ công**: tốn thời gian, dễ nhầm lẫn và khó đối chiếu với đơn mua, đơn bán.
- **Khó truy vết**: không biết hàng nhập từ nhà cung cấp nào, xuất cho khách nào, ai điều chỉnh số liệu và vào lúc nào.

WMS Supply Chain số hóa toàn bộ chuỗi nghiệp vụ trên một hệ thống thống nhất: danh mục hàng hóa và barcode, kho và vị trí,
tồn kho theo thời gian thực, điều chuyển, mua hàng và nhập kho (PO, GRN), bán hàng và xuất kho (SO, GDN), kiểm kê,
báo cáo và dashboard, phân quyền người dùng, thông báo; mọi biến động tồn kho đều được ghi lại để truy vết.

## Thành viên và vai trò

| Thành viên | Vai trò | Phụ trách chính |
| --- | --- | --- |
| Lê Phú Toàn | TechLead, Core Inventory và Warehouse | Kiến trúc, chuẩn code và quy ước API, Git flow, review và merge PR; inventory-service: đa kho, vị trí Zone/Rack/Bin, tồn kho, điều chuyển, kiểm kê, cảnh báo tồn |
| Phạm Phương Anh | Leader, Scrum Master; DevOps và nền tảng microservice, bảo mật, QA, AI nâng cao | Config Server, Eureka, API Gateway, Docker và Docker Compose, CI/CD, logging, backup, kiểm thử tích hợp, quản lý board và tiến độ |
| Lê Phương Linh | Core Supply Chain và Master Data | product-service (SKU, đơn vị tính, barcode) và order-service (nhà cung cấp, khách hàng, PO và GRN, SO và GDN); tài liệu yêu cầu chức năng |
| Trần Đăng Việt | Auth và RBAC, Báo cáo và Dashboard, Notification | auth-service (JWT, RBAC) và notification-service (KPI, báo cáo, email cảnh báo); Executive Dashboard; tài liệu yêu cầu phi chức năng |

## Công nghệ

| Lớp | Công nghệ |
| --- | --- |
| Backend | Java 17, Spring Boot 3.3, Spring Cloud 2023.0 (API Gateway, Eureka, Config Server), Gradle multi-project |
| Dữ liệu và tích hợp | MySQL 8.0 (mỗi service một schema), Redis 7, RabbitMQ 3 |
| Frontend | React 18, Vite, TypeScript, Ant Design 5, Tailwind CSS, Recharts, Axios, React Router |
| Vận hành | Docker, Docker Compose, GitHub Actions |

## Kiến trúc tổng quan

```text
                             Trình duyệt người dùng
                                        |
                                        v
                          +---------------------------+
                          |  Frontend (Nginx, :80)    |
                          |  React SPA                |
                          +-------------+-------------+
                                        |  /api/*
                                        v
                          +---------------------------+        +---------------------------+
                          |  API Gateway (:8080)      |------->|  Discovery Server (:8761) |
                          |  định tuyến, kiểm tra JWT |        |  Eureka                   |
                          +-------------+-------------+        +---------------------------+
                                        |                      mọi service đều đăng ký ở đây
        +-------------+-----------------+-----------------+------------------+
        v             v                 v                 v                  v
  +-----------+ +-----------+ +-------------------+ +-----------+ +---------------------+
  |   auth-   | | product-  | |    inventory-     | |  order-   | |    notification-    |
  |  service  | |  service  | |      service      | |  service  | |       service       |
  |   :8081   | |   :8082   | |       :8083       | |   :8084   | |        :8085        |
  +-----+-----+ +-----+-----+ +---------+---------+ +-----+-----+ +----------+----------+
        |             |                 |                 |                  |
     auth_db     product_db       inventory_db        order_db        notification_db
        |             |                 |                 |                  |
        +-------------+-----------------+-----------------+------------------+
                                        |
                               MySQL 8.0  (:3306)

Dùng chung:
  Config Server (:8888)                   cấp cấu hình tập trung cho mọi service
  Redis 7 (:6379)                         cache
  RabbitMQ 3 (:5672, giao diện :15672)    sự kiện bất đồng bộ giữa các service
```

Chi tiết trách nhiệm từng service và các luồng dữ liệu chính xem [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Bảng cổng

| Thành phần | Cổng | Ghi chú |
| --- | --- | --- |
| Frontend | 80 (Docker), 5173 (chạy dev) | Biến `FRONTEND_PORT` đổi cổng khi chạy Docker |
| API Gateway | 8080 | Điểm vào duy nhất của API, tiền tố `/api` |
| auth-service | 8081 | Schema `auth_db` |
| product-service | 8082 | Schema `product_db` |
| inventory-service | 8083 | Schema `inventory_db` |
| order-service | 8084 | Schema `order_db` |
| notification-service | 8085 | Schema `notification_db` |
| Discovery Server (Eureka) | 8761 | Giao diện http://localhost:8761 |
| Config Server | 8888 | |
| MySQL | 3306 | Tài khoản ứng dụng `wms` |
| Redis | 6379 | |
| RabbitMQ | 5672 | Giao diện quản trị 15672 (guest/guest, chỉ dùng khi dev) |

Khi chạy bằng Docker Compose, chỉ frontend (80) và API Gateway (8080) được mở ra mạng; các cổng còn lại (hạ tầng, Config Server,
Discovery Server, service nghiệp vụ) chỉ mở trên `127.0.0.1` của máy host để không lộ ra mạng LAN.

## Cấu trúc thư mục

```text
wms-supply-chain/
  backend/              Gradle multi-project (Spring Cloud): 8 service
  frontend/             Giao diện React + Vite + TypeScript
  docker/mysql/         Script khởi tạo MySQL (init.sql: 5 schema và user wms)
  docs/                 Tài liệu kỹ thuật
  .github/              CI (GitHub Actions), template PR và issue, CODEOWNERS
  docker-compose.yml    Chạy toàn bộ stack
  .env.example          Mẫu biến môi trường (sao chép thành .env)
  CONTRIBUTING.md       Quy ước làm việc nhóm
```

## Chạy dự án

Yêu cầu: Git, Docker Desktop (có Docker Compose v2), JDK 17 (khi chạy service trực tiếp trên máy),
Node.js 20.19 trở lên (khi chạy frontend trực tiếp trên máy).

### Cách 1: chạy toàn bộ bằng Docker Compose

```bash
cp .env.example .env            # đổi MYSQL_ROOT_PASSWORD, DB_PASSWORD, JWT_SECRET
docker compose up -d            # lần đầu tự build image nên mất vài phút (thêm --build để build lại sau khi sửa code)
docker compose ps               # chờ các service chuyển sang trạng thái healthy
```

| Địa chỉ | Thành phần |
| --- | --- |
| http://localhost | Giao diện web (đổi cổng bằng biến `FRONTEND_PORT` trong `.env`) |
| http://localhost:8080 | API Gateway |
| http://localhost:8761 | Eureka Dashboard |
| http://localhost:15672 | RabbitMQ Management (guest/guest) |

Một số lệnh thường dùng:

```bash
docker compose logs -f inventory-service    # xem log một service
docker compose up -d --build auth-service   # build lại và chạy lại một service
docker compose down                         # dừng toàn bộ, giữ dữ liệu
docker compose down -v                      # dừng và xóa cả dữ liệu (init.sql sẽ chạy lại lần sau)
```

Thứ tự khởi động do `depends_on` kèm healthcheck bảo đảm: MySQL, Redis, RabbitMQ, Config Server, Discovery Server,
rồi mới tới các service nghiệp vụ, API Gateway và cuối cùng là frontend.

### Cách 2: chạy khi phát triển (hạ tầng bằng Docker, service chạy trên máy)

```bash
# 1. Hạ tầng
docker compose up -d mysql redis rabbitmq

# 2. Backend: chạy lần lượt Config Server, Discovery Server, rồi các service còn lại
#    (mỗi lệnh một cửa sổ terminal, hoặc chạy trong IDE). Tên project Gradle trùng tên thư mục.
cd backend
./gradlew :config-server:bootRun
./gradlew :discovery-server:bootRun
./gradlew :api-gateway:bootRun
./gradlew :inventory-service:bootRun

# 3. Frontend (http://localhost:5173, tự động proxy /api tới http://localhost:8080)
cd ../frontend
npm install
npm run dev
```

Theo cấu hình mặc định trong `application.yml`, các service đã trỏ sẵn tới `localhost` (MySQL 3306, Redis 6379, RabbitMQ 5672,
Eureka 8761, Config Server 8888). Chỉ cần đặt `DB_USERNAME=wms` và `DB_PASSWORD` trùng với giá trị trong `.env` (trong cấu hình chạy
của IDE hoặc bằng `export` trước khi chạy). `JWT_SECRET` phải giống nhau ở api-gateway và auth-service. Danh sách đầy đủ các biến môi trường
xem trong `docker-compose.yml`; chi tiết về frontend xem [frontend/README.md](frontend/README.md).

## Tài liệu

| Tài liệu | Nội dung |
| --- | --- |
| [docs/README.md](docs/README.md) | Mục lục tài liệu |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Kiến trúc microservice, trách nhiệm từng service, luồng dữ liệu PO, GRN, SO, GDN |
| [docs/SPRINT_PLAN.md](docs/SPRINT_PLAN.md) | Kế hoạch bốn sprint và mục tiêu theo phân hệ |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | Quy ước API, mã lỗi, định dạng response, chuẩn code Java và TypeScript |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Git flow, commit, Pull Request, board, Definition of Done |

## Quy ước Git (tóm tắt)

- Nhánh: `main` là bản phát hành ổn định, `develop` là nhánh tích hợp; làm việc trên `feat/<mã-task>-<mô-tả>`,
  `fix/<mã-task>-<mô-tả>`, `chore/<mã-task>-<mô-tả>`.
- Commit theo Conventional Commits: `feat`, `fix`, `docs`, `refactor`, `chore`, `test`, `ci`.
- Pull Request luôn vào `develop`, cần ít nhất 1 người review và approve, CI xanh, mô tả theo template, squash merge.
- Công việc đi qua board: Backlog, To Do, In Progress, Review, Done. Daily Standup lúc 20:30 qua Google Meet.

Chi tiết xem [CONTRIBUTING.md](CONTRIBUTING.md).

## Kiểm tra tự động (CI)

Workflow `.github/workflows/ci.yml` chạy khi push mọi nhánh và khi mở Pull Request:

- **Backend**: `./gradlew build` với JDK 17 (kèm test), tải báo cáo test lên nếu có.
- **Frontend**: `npm ci`, `npm run lint`, `npm run build` với Node.js 20.
- **Docker** (chỉ khi push, sau khi hai job trên xanh): build thử image api-gateway, inventory-service và frontend
  (không push) để phát hiện sớm lỗi Dockerfile.
