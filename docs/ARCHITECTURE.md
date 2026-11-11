# Kiến trúc hệ thống

## 1. Tổng quan

WMS Supply Chain theo kiến trúc microservice trên nền Spring Cloud. Frontend React gọi API thông qua một điểm vào duy nhất
là API Gateway; Gateway định tuyến yêu cầu tới các service nghiệp vụ được đăng ký ở Discovery Server (Eureka). Cấu hình
của mọi service được cấp tập trung từ Config Server.

Nguyên tắc thiết kế:

- **Mỗi service sở hữu dữ liệu riêng** (database-per-service): một schema MySQL cho một service, không truy cập chéo schema.
  Muốn dùng dữ liệu của service khác phải gọi API hoặc nhận sự kiện.
- **Đồng bộ khi cần kết quả ngay** (REST qua tên service đã đăng ký ở Eureka), **bất đồng bộ khi cần tách rời**
  (sự kiện qua RabbitMQ), ví dụ cập nhật tồn kho sau khi xác nhận phiếu nhập hoặc xuất.
- **Mọi biến động tồn kho đều có chứng từ gốc** (GRN, GDN, phiếu điều chuyển, phiếu kiểm kê) và được ghi vào sổ biến động
  để truy vết. Số lượng tồn không bao giờ được sửa trực tiếp.
- **Xử lý sự kiện phải idempotent**: nhận trùng một sự kiện không được cộng hoặc trừ tồn hai lần.
- **Bảo mật tập trung**: xác thực JWT ở API Gateway, phân quyền (RBAC) theo vai trò do auth-service quản lý.

## 2. Sơ đồ thành phần

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

Ngoài sơ đồ: ai-service (:8086) gọi Claude, không có schema MySQL, chỉ dùng Redis (đếm lượt AI theo gói).

Dùng chung:
  Config Server (:8888)                   cấp cấu hình tập trung cho mọi service
  Redis 7 (:6379)                         cache
  RabbitMQ 3 (:5672, giao diện :15672)    sự kiện bất đồng bộ giữa các service
```

## 3. Các service và trách nhiệm

| Service | Cổng | Schema | Trách nhiệm | Phụ trách |
| --- | --- | --- | --- | --- |
| config-server | 8888 | không có | Cấp cấu hình tập trung cho các service | Phạm Phương Anh |
| discovery-server | 8761 | không có | Eureka: đăng ký và tra cứu service | Phạm Phương Anh |
| api-gateway | 8080 | không có | Điểm vào duy nhất `/api/**`: định tuyến, kiểm tra JWT tập trung, rate limiting, CORS | Phạm Phương Anh |
| auth-service | 8081 | `auth_db` | Đăng nhập (Access Token và Refresh Token), băm mật khẩu BCrypt, khóa tài khoản khi sai nhiều lần, RBAC 6 vai trò, gán người dùng theo kho | Trần Đăng Việt |
| product-service | 8082 | `product_db` | Danh mục hàng hóa và SKU, nhóm hàng, đơn vị tính quy đổi đa cấp, giá nhập và bán, VAT, sinh Barcode và QR, import và export Excel | Lê Phương Linh |
| inventory-service | 8083 | `inventory_db` | Đa kho, vị trí Zone/Rack/Bin, tồn kho và thẻ kho, biến động tồn, điều chuyển, kiểm kê và điều chỉnh, cảnh báo tồn an toàn | Lê Phú Toàn |
| order-service | 8084 | `order_db` | Nhà cung cấp và khách hàng kèm công nợ, PO, GRN, SO, Picking, GDN, thông tin lô và hạn dùng, hóa đơn mua và bán | Lê Phương Linh |
| notification-service | 8085 | `notification_db` | Notification và Report: thẻ KPI, báo cáo Xuất-Nhập-Tồn, doanh thu, công nợ, xuất Excel, email cảnh báo, job hằng ngày | Trần Đăng Việt |
| ai-service | 8086 | không có (Redis) | Gọi Claude qua SDK Java chính thức của Anthropic: hỏi đáp, trích xuất tài liệu, trợ lý; kiểm tra và đếm lượt AI theo gói; chế độ giả lập khi chưa có khóa | Lê Phú Toàn |

Báo cáo và Dashboard do notification-service tổng hợp từ dữ liệu của inventory-service và order-service; frontend hiển thị
ở trang Dashboard và Báo cáo.

## 4. Hạ tầng dùng chung

| Thành phần | Vai trò |
| --- | --- |
| MySQL 8.0 | Lưu dữ liệu nghiệp vụ, 5 schema độc lập; tài khoản ứng dụng `wms` chỉ có quyền trên 5 schema này (xem `docker/mysql/init.sql`) |
| Redis 7 | Cache dữ liệu đọc nhiều, đặc biệt là dữ liệu tồn kho của inventory-service |
| RabbitMQ 3 | Truyền sự kiện bất đồng bộ giữa các service (nhập kho, xuất kho, cảnh báo tồn) |
| Eureka | Đăng ký và tra cứu service theo tên, cho phép Gateway và các service gọi nhau mà không cần địa chỉ cố định |
| Config Server | Cấu hình tập trung theo từng service; bí mật được truyền qua biến môi trường |

Vận hành (Phạm Phương Anh): logging hai lớp gồm audit log nghiệp vụ (ai làm gì trên chứng từ nào) và error log kỹ thuật;
backup MySQL định kỳ kèm kiểm tra khôi phục.

## 5. Luồng dữ liệu chính

Tên sự kiện và trạng thái chi tiết sẽ được chốt khi triển khai ở Sprint 3. Mã FRxx tham chiếu
[docs/SPRINT_PLAN.md](SPRINT_PLAN.md).

### 5.1. Mua hàng: PO, GRN, tồn kho

```text
PO (chờ duyệt) -> APPROVED -> GRN (nhận hàng, có thể nhiều lần) -> sự kiện nhập kho -> inventory-service tăng tồn
                      |                                                                          |
                      +-> tăng Incoming Stock                  giảm Incoming, ghi thẻ kho (loại nhập)
```

1. Nhân viên mua hàng tạo PO (FR10) trong order-service: chọn nhà cung cấp, kho, SKU (từ product-service), số lượng, giá,
   chiết khấu, VAT. PO được phê duyệt (APPROVED) hoặc từ chối (REJECTED); khi duyệt, Incoming Stock của kho tăng.
2. Hàng về kho, thủ kho lập GRN (FR11) theo PO đã duyệt, cho phép nhập từng phần: quét mã vạch, ghi số lượng đạt và không đạt,
   số lô, ngày sản xuất, hạn dùng (FR12) và vị trí lưu.
3. Khi GRN được xác nhận, order-service phát sự kiện nhập kho lên RabbitMQ và cập nhật PO; PO đóng khi nhập đủ.
4. inventory-service nhận sự kiện, tăng tồn thực tế (OnHand) theo kho, vị trí và lô, giảm Incoming, ghi một dòng thẻ kho
   tham chiếu số GRN. Nhận trùng sự kiện thì bỏ qua (idempotent).

### 5.2. Bán hàng: SO, GDN, trừ tồn

```text
SO -> kiểm tra ATP -> CONFIRMED (giữ hàng mềm) -> Picking List -> GDN (xuất kho) -> sự kiện xuất kho
                                                                                            |
                                              inventory-service: trừ OnHand, giải phóng Reserved, ghi thẻ kho
```

1. Nhân viên bán hàng tạo SO (FR15) cho khách hàng. order-service gọi đồng bộ inventory-service để kiểm tra tồn khả dụng
   ATP = OnHand - Reserved. Đủ hàng thì xác nhận; thiếu hàng thì báo thiếu hoặc ghi nhận Backorder.
2. Xác nhận SO là giữ hàng mềm (FR16): Reserved tăng, ATP giảm, trạng thái CONFIRMED.
3. Xuất kho (FR17): hệ thống tạo Picking List, gợi ý vị trí lấy hàng theo FIFO (FEFO ở mức nâng cao); thủ kho quét mã khi lấy
   hàng và lập GDN.
4. Khi GDN được xác nhận, order-service phát sự kiện xuất kho; inventory-service trừ OnHand, giải phóng Reserved và ghi thẻ kho.
5. Sau mỗi lần xuất, inventory-service kiểm tra ngưỡng cảnh báo tồn (mục 5.3).

### 5.3. Các luồng khác

- **Điều chuyển (FR08)**: phiếu điều chuyển giữa hai kho đi qua các trạng thái DRAFT, IN_TRANSIT, COMPLETED hoặc PARTIAL_RECEIVED.
  Khi duyệt, tồn kho nguồn bị trừ và hàng được ghi nhận là đang đi đường; kho đích quét mã, xác nhận nhận hàng và ghi biên bản
  thiếu hỏng nếu có.
- **Cảnh báo tồn an toàn (FR21)**: sau mỗi lần xuất, nếu tồn nhỏ hơn hoặc bằng Reorder Point thì cảnh báo vàng và đề xuất lập PO;
  nếu nhỏ hơn hoặc bằng Safety Stock thì cảnh báo đỏ. inventory-service phát sự kiện, notification-service gửi email và
  Dashboard hiển thị nổi bật.
- **Kiểm kê và điều chỉnh (FR19, FR20)**: đóng băng vị trí đang kiểm, chụp snapshot tồn, đếm không hiển thị số hệ thống,
  tính chênh lệch và giá trị. Điều chỉnh kho chỉ có hiệu lực sau khi Quản lý kho và Kế toán phê duyệt, đồng thời lưu audit log.
- **Chống âm kho**: mọi thao tác thay đổi tồn dùng khóa bi quan (`PESSIMISTIC_WRITE`) trên bản ghi tồn để không bị âm kho khi
  nhiều giao dịch xảy ra đồng thời.
- **Báo cáo và Dashboard (FR23 đến FR25)**: notification-service tổng hợp số liệu tồn kho, đơn chờ xử lý và công nợ
  để hiển thị thẻ KPI, biểu đồ và bảng giao dịch gần nhất. Báo cáo Xuất - Nhập - Tồn, thẻ kho chi tiết và truy vết theo
  số lô do inventory-service phục vụ (`/api/inventory/reports/**`), tính từ sổ kho `stock_movements`: mọi thao tác đổi
  tồn (nhập GRN, xuất bán, xuất và nhận điều chuyển, sau này là điều chỉnh kiểm kê) ghi đúng một dòng sổ trong cùng
  transaction. Sổ kho bắt đầu từ migration V13 với một dòng số dư mở sổ cho mỗi dòng tồn đang có; kỳ báo cáo bắt đầu
  trước mốc này trả tồn đầu, nhập, xuất là null kèm giải thích. Tồn kho chưa có giá vốn nên các cột giá trị tiền là null.

### 5.4. API kiểm kê (inventory-service, `/api/inventory/stocktakes`)

Bảng `stocktake_sessions`, `stocktake_locations`, `stocktake_lines` (Flyway V11, đều có `company_id`, mã đợt
`ST-yyyyMM-NN` duy nhất theo `(company_id, code)`). Mọi API bắt buộc đăng nhập; phản hồi theo `ApiResponse`,
danh sách theo `PageResponse`.

| Method, đường dẫn | Vai trò | Việc |
|---|---|---|
| `POST /` (201) | ADMIN, WAREHOUSE_MANAGER của kho | Lập đợt DRAFT: `warehouseId`, `scopeType` (WAREHOUSE, ZONES, LOCATIONS), `zones`, `binLocationIds`, `productIds`, `plannedDate`, `notes` |
| `GET /`, `GET /{id}` | ADMIN, ACCOUNTANT, WAREHOUSE_MANAGER, WAREHOUSE_STAFF (theo kho) | Danh sách lọc `warehouseId`, `status`, `fromDate`, `toDate` (theo `plannedDate`), `keyword`; chi tiết kèm `locations` |
| `POST /{id}/start` | ADMIN, WAREHOUSE_MANAGER của kho | DRAFT sang COUNTING: chốt vị trí, đóng băng, chụp tồn theo (vị trí, sản phẩm, lô) gồm tồn và giữ chỗ |
| `POST /{id}/counts` | ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF của kho | Đếm mù: `lineId` hoặc (`binLocationId`/`binLocationCode`, `productId`, `lotNo`), `quantity`, `mode` ADD (cộng dồn khi quét) hoặc SET; không khớp dòng nào thì tạo dòng phát sinh; phản hồi không có tồn hệ thống |
| `GET /{id}/count-lines` | như `GET /{id}` | Dòng cần đếm (không có tồn hệ thống), lọc `counted`, `binCode`, `productId` |
| `POST /{id}/cancel` | ADMIN, WAREHOUSE_MANAGER của kho | Hủy khi DRAFT, COUNTING, COUNTED: mở đóng băng, tồn không đổi |
| `POST /{id}/finish` | ADMIN, WAREHOUSE_MANAGER của kho | COUNTING sang COUNTED, chênh lệch = số đếm - tồn chụp; còn dòng chưa đếm thì 409 (`data.uncountedLines`) trừ khi gửi `confirmUncountedAsZero=true` |
| `GET /{id}/variances` | ADMIN, ACCOUNTANT, WAREHOUSE_MANAGER của kho | `summary` (số dòng, thừa, thiếu, ròng, số dòng xung đột giữ chỗ) và `lines` có tồn hệ thống; lọc `onlyDifferences`, `binCode`, `productId` |
| `POST /{id}/recount` | ADMIN, WAREHOUSE_MANAGER của kho | `lineIds`: xóa số đếm các dòng, quay lại COUNTING |
| `PUT /{id}/lines/{lineId}/reason` | ADMIN, WAREHOUSE_MANAGER của kho | Ghi lý do chênh lệch (COUNTED) |
| `POST /{id}/approve` | ADMIN, WAREHOUSE_MANAGER của kho | Cấp Quản lý kho: không lệch thì COMPLETED và mở đóng băng; có lệch thì sinh phiếu `ADJ-yyyyMM-NN`, đợt sang PENDING_ACCOUNTING |
| `POST /{id}/adjustment/approve` | ACCOUNTANT, ADMIN (khác người duyệt cấp Quản lý) | Cấp Kế toán: một transaction mở đóng băng, khóa bi quan dòng tồn, tồn mới = tồn hiện tại + chênh lệch, đồng bộ `inventory_lots`, ghi tồn trước và sau vào dòng phiếu, đợt COMPLETED, phát `inventory.updated` (`reason = ADJUSTMENT`) |
| `POST /{id}/reject` | COUNTED: ADMIN, WAREHOUSE_MANAGER; PENDING_ACCOUNTING: thêm ACCOUNTANT | Bắt buộc `reason`; mở đóng băng, phiếu (nếu có) REJECTED, tồn không đổi |
| `GET /api/inventory/stock-adjustments`, `GET /api/inventory/stock-adjustments/{id}` | ADMIN, ACCOUNTANT, WAREHOUSE_MANAGER (theo kho) | Phiếu điều chỉnh (bảng `stock_adjustments`, `stock_adjustment_lines`, Flyway V12, mỗi đợt tối đa một phiếu theo `(company_id, session_id)`) |

Quy tắc điều chỉnh: tồn mới không được âm và không được nhỏ hơn số đang giữ chỗ cho đơn; vi phạm thì 409 kèm danh sách
dòng trong `data` (cần hủy hoặc chuyển giữ chỗ của đơn, hoặc đếm lại), giữ chỗ không bị điều chỉnh tự động. Mọi thao tác
đổi trạng thái khóa bi quan dòng đợt kiểm kê nên duyệt hai lần hoặc đồng thời chỉ áp dụng một lần. Giá trị tiền của chênh
lệch chưa tính vì inventory-service chưa có giá vốn sản phẩm.

Đóng băng: mỗi vị trí chỉ thuộc một đợt đang kiểm (ràng buộc duy nhất `(company_id, frozen_bin_location_id)`).
`StocktakeFreezeGuard` là chốt chặn dùng chung: nhập vào ô, xuất chỉ định ô, duyệt xuất điều chuyển từ ô, nhận hàng
điều chuyển vào ô đang đóng băng bị từ chối 409 `CONFLICT` với thông báo nêu mã vị trí và mã đợt, `data` gồm
`sessionId`, `sessionCode`, `binLocationId`, `binLocationCode`, `productId`; giữ chỗ đơn mới và xuất không chỉ định ô
bỏ qua ô đang đóng băng. Đợt có `productIds` chỉ đóng băng các sản phẩm đó.

## 6. Bảo mật

- Người dùng đăng nhập qua `POST /api/auth/login`. Access Token (JWT, thời hạn 15 phút) gửi trong body; Refresh Token nằm
  trong HttpOnly cookie. Mọi request sau đó gửi `Authorization: Bearer <accessToken>`.
- Mật khẩu băm bằng BCrypt; tài khoản bị khóa tạm thời khi đăng nhập sai quá số lần cho phép.
- API Gateway kiểm tra JWT tập trung và giới hạn tốc độ (rate limiting) trước khi chuyển tiếp; từng service kiểm tra quyền
  theo vai trò (RBAC) cho từng endpoint, dữ liệu được lọc theo kho mà người dùng được cấp quyền.
- Vai trò hệ thống (định nghĩa trong module `common`): `ADMIN` (quản trị hệ thống), `WAREHOUSE_MANAGER` (quản lý kho),
  `WAREHOUSE_STAFF` (nhân viên kho), `PURCHASING` (nhân viên mua hàng), `SALES` (nhân viên bán hàng), `ACCOUNTANT` (kế toán).
  Danh sách vai trò được lưu trong claim `roles` của JWT.
- Khóa ký JWT (`JWT_SECRET`), mật khẩu CSDL và tài khoản email chỉ truyền qua biến môi trường, không commit lên Git
  (file `.env` đã được `.gitignore` loại trừ).
- Các cổng hạ tầng (MySQL, Redis, RabbitMQ) trong `docker-compose.yml` chỉ phục vụ môi trường phát triển và chỉ mở trên
  `127.0.0.1`; môi trường triển khai thật không được mở ra ngoài.

## 8. Mô hình SaaS đa doanh nghiệp (từ 04/11/2026)

Hệ thống mở rộng để nhiều doanh nghiệp tự đăng ký và dùng chung một bản triển khai. Chi tiết kế hoạch, task và lịch ở
[SAAS_PLAN.md](SAAS_PLAN.md).

- **Tách dữ liệu theo doanh nghiệp**: mọi bảng nghiệp vụ có cột `company_id`; Hibernate `@TenantId` tự thêm điều kiện lọc vào
  mọi truy vấn JPA nên một công ty không bao giờ đọc hay ghi được dữ liệu của công ty khác. JWT mang claim `companyId`,
  API Gateway chuyển tiếp header `X-Company-Id` cho service; sự kiện RabbitMQ mang `companyId` trong message. Ràng buộc duy
  nhất đổi thành `(company_id, mã)`. Module `common` cung cấp `TenantContext`, `TenantEntity` và test cách ly mẫu.
- **Đăng ký và gói dịch vụ**: auth-service có bảng `companies`, `subscriptions`, `plans`, `payments`; đăng ký tạo công ty,
  admin đầu tiên và 30 ngày dùng thử, kích hoạt qua email. Gói Free, Trial, Pro với giới hạn số user, kho, SKU và lượt AI;
  `PlanLimitChecker` trong `common` trả mã lỗi `PLAN_LIMIT_EXCEEDED` hoặc `SUBSCRIPTION_EXPIRED` (HTTP 403), hết hạn thì
  hệ thống chỉ đọc. Gia hạn thanh toán qua PayOS: backend tạo link thanh toán có chữ ký, PayOS gọi webhook về
  `/api/auth/payments/payos/webhook`, kiểm tra chữ ký và số tiền rồi mới kích hoạt gói; khóa PayOS chỉ nằm trong `.env`.
- **ai-service (cổng 8086)**: gọi Claude qua SDK Java chính thức của Anthropic (`com.anthropic:anthropic-java`, không dùng
  Spring AI vì bản 1.x cần Spring Boot 3.4+), chạy giả lập khi chưa có khóa; trích xuất tài liệu (Excel, CSV, PDF, ảnh) thành hàng hóa, nhà cung cấp,
  khách hàng để người dùng xem trước rồi nhập hàng loạt qua API của product-service và order-service; trợ lý AI hướng dẫn
  sử dụng và tra cứu tồn kho, đơn hàng của chính công ty; tóm tắt báo cáo. Mỗi lượt gọi trừ hạn mức của gói.
- **Tích hợp ngoài**: API key theo doanh nghiệp (bảng `api_keys`), gateway chấp nhận header `X-Api-Key` với vai trò
  INTEGRATION chỉ đọc, giới hạn tốc độ theo key, Swagger công khai.
