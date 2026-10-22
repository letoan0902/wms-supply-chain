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
- **Báo cáo và Dashboard (FR23 đến FR25)**: notification-service tổng hợp số liệu tồn kho, nhập xuất tồn, đơn chờ xử lý và công nợ
  để hiển thị thẻ KPI, biểu đồ và bảng giao dịch gần nhất.

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
