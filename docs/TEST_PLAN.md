# Kế hoạch kiểm thử đầu cuối, cách ly doanh nghiệp và tải đồng thời

Tài liệu gồm bảng test case của bốn task QA, phần kiểm tra thủ công và bảng truy vết yêu cầu. Bộ kiểm thử tự động nằm ở
`qa/`, cách dựng stack và chạy ở [qa/README.md](../qa/README.md).

| Task | Jira | Nội dung | Tự động hóa |
| --- | --- | --- | --- |
| WMS-20.1 | TASK-123 | Luồng mua và nhập kho | `qa/postman/00-setup`, `qa/postman/10-inbound` |
| WMS-20.2 | TASK-124 | Bán, xuất kho, điều chuyển, kiểm kê, sổ kho | `qa/postman/20-outbound-transfer-stocktake` |
| WMS-22.5 | TASK-139 | Cách ly doanh nghiệp, giới hạn gói, hết hạn, API key | `qa/postman/30-isolation-plan-expiry` |
| WMS-19.2 | TASK-52 | Tải đồng thời, bất biến tồn kho, chống nộp trùng | `qa/load/concurrency.mjs` |

## 1. Quy ước

- Mỗi dòng của bảng là một request (hoặc một chế độ của kịch bản tải) có test tự động, cột "Tự động hóa" ghi tên file
  collection và tên request trong file. Thư mục trong collection trùng mã nhóm test case (`TC-IN-05`, `TC-ISO-12`...).
- Dữ liệu không cố định ID: mỗi lượt chạy có một `runId`, doanh nghiệp A, B (do `00-setup` đăng ký), C (do `30` đăng
  ký) và doanh nghiệp của kịch bản tải đều mới. Mật khẩu kiểm thử là chuỗi giả `QaTest-<runId>-Mk`.
- Mọi request có thêm test chung "Không có lỗi máy chủ (HTTP 5xx)".
- Cột "Kết quả" ghi kết quả lượt chạy ngày 12/11/2026 (mục 2). "chưa chạy" là ca chưa được thực hiện; lượt chạy sau ghi đè "đạt" hoặc "không đạt" kèm mã lỗi hoặc dòng log của Postman CLI.
- Số lượng request hiện có: `00-setup.postman_collection.json`: 20 request, `10-inbound.postman_collection.json`: 102 request, `20-outbound-transfer-stocktake.postman_collection.json`: 137 request, `30-isolation-plan-expiry.postman_collection.json`: 219 request.

## 2. Kết quả lượt chạy ngày 12/11/2026 và các lỗi đã tìm thấy

### 2.1. Môi trường

Hệ thống thật dựng bằng Docker trên một máy phát triển: 13 container (MySQL 8, Redis, RabbitMQ, config-server,
discovery-server, api-gateway, sáu service nghiệp vụ, frontend), mọi migration Flyway chạy trên MySQL với
`ddl-auto: validate`, thêm cấu hình `qa/docker-compose.qa.yml` (bật JWT, tắt kích hoạt qua email, bật công cụ demo, PayOS
và AI ở chế độ giả lập, tắt giới hạn tốc độ theo IP). Công cụ bắn request chạy trên cùng máy. Chưa chạy trên máy chủ
triển khai.

### 2.2. Bộ Postman

| Collection | Request đã gửi | Kiểm tra | Không đạt |
| --- | --- | --- | --- |
| `00-setup` | 20 | 70 | 0 |
| `10-inbound` | 102 | 277 | 0 |
| `20-outbound-transfer-stocktake` | 137 | 369 | 0 |
| `30-isolation-plan-expiry` | 278 (219 request trong file, phần còn lại là các lần hỏi lại chờ sự kiện) | 542 | 0 |
| Tổng | 537 | 1.258 | 0 |

Lượt chạy đầu tiên trong ngày có 32 kiểm tra không đạt; nguyên nhân ở mục 2.4, sau khi sửa chạy lại đạt toàn bộ như bảng
trên.

### 2.3. Kịch bản tải (`qa/load/concurrency.mjs`)

Mọi bất biến I1 đến I9 và G1 đến G5 đạt ở cả ba mức tải, không có HTTP 5xx, lỗi mạng, deadlock hay chờ khóa quá hạn. Ở
40 thao tác mỗi giây: 1.200 thao tác trong 30,3 giây, 376 đơn bán, 150 phiếu xuất, xuất đúng 300 trên 300 đã nhập, tồn
và giữ chỗ về 0, sổ kho khớp.

Độ trễ các API ghi của order-service (ms), mọi thao tác dồn vào một mặt hàng ở một kho của một doanh nghiệp:

| Tải | `POST /api/orders/sales` p50 / p95 | `POST .../sales/{id}/confirm` p50 / p95 | `POST /api/orders/goods-delivery-notes` p50 / p95 | So với 500 ms |
| --- | --- | --- | --- | --- |
| 10 thao tác/giây | 65 / 99 | 66 / 107 | 101 / 159 | đạt |
| 20 thao tác/giây | 52 / 122 | 59 / 132 | 80 / 150 | đạt |
| 40 thao tác/giây | 1.220 / 2.480 | 1.410 / 2.563 | 2.005 / 2.540 | vượt |

Các API đọc của inventory-service giữ p95 dưới 60 ms ở cả ba mức. Ở 40 thao tác mỗi giây hai API danh sách của
order-service cũng chậm theo (p95 1,6 đến 1,8 giây). Kết luận: trên máy này hệ thống đáp ứng 500 ms tới khoảng 20 thao
tác mỗi giây dồn vào một mặt hàng; ở 40 thao tác mỗi giây kết quả vẫn đúng nhưng request phải xếp hàng. Chưa đo để biết
phần chờ nằm ở khóa dòng tồn kho, ở nhóm kết nối hay ở CPU của máy (13 container và công cụ bắn tải chung một máy), và
chưa đo với tải rải trên nhiều mặt hàng.

### 2.4. Lỗi tìm thấy

| Lỗi | Phát hiện bằng | Trạng thái |
| --- | --- | --- |
| Phiếu nhập vào vị trí đang kiểm kê vẫn được ghi (tồn không tăng nhưng phiếu nhập, hóa đơn mua, công nợ đã ghi) | Đọc mã khi viết TC-STK-01.10 | Đã sửa (PR #72): order-service hỏi inventory-service trước khi lưu phiếu, trả 409 |
| Nộp trùng một phiếu nhập từng phần cộng tồn hai lần | Đọc mã khi viết TC-LOAD-02.2 | Đã sửa (PR #72): header `Idempotency-Key`, lần gửi lại trả đúng phiếu đã lập |
| Hai lần xác nhận đồng thời một đơn bán cùng thành công, phát hai sự kiện | Đọc mã khi viết TC-LOAD-02.3 | Đã sửa (PR #72): khóa dòng đơn bán khi xác nhận |
| Xác nhận đơn bán đồng thời có thể bán vượt tồn; phiếu xuất hoàn tất nhưng tồn không giảm | Đọc mã khi viết TC-LOAD-01.5, TC-LOAD-01.6 | Đã sửa (PR #72): giữ chỗ đồng bộ khi xác nhận, trừ tồn đồng bộ khi xuất, khóa theo mã phiếu xuất |
| Giới hạn gói không chặn, doanh nghiệp hết hạn vẫn ghi được, API key nào cũng bị 401 | Lượt chạy đầu của collection 30 (32 kiểm tra không đạt) | Đã sửa (PR #77): auth-service ghi hash trên Redis với tên trường tuần tự hóa kiểu JDK nên gateway và các service khác không đọc được. Test cũ giả lập Redis nên không bắt được; đã thêm test kiểm tra byte thật |
| order-service ngừng trả lời khi nhiều request tạo chứng từ cùng lúc | Kịch bản tải 40 thao tác/giây (604 trên 637 lần tạo đơn bán hết thời gian chờ) | Đã sửa: việc cấp số chứng từ mở transaction thứ hai trên cùng nhóm kết nối; chuyển sang nhóm kết nối riêng. Chi tiết ở `docs/PERFORMANCE.md` mục 5 |
| Thời điểm thu hồi API key ở phản hồi đầu và lần đọc lại lệch nhau phần nano giây | TC-KEY-01.14 | Đã sửa (PR #77): cắt về micro giây |
| Hai ca kiểm thử viết sai (mã đơn vị tính chữ thường, đọc danh sách khu sai cú pháp) | Lượt chạy đầu của collection 10 | Đã sửa ca kiểm thử (PR #77), không phải lỗi của hệ thống |

### 2.5. Ghi nhận khi đọc mã, chưa có test khẳng định

Các điểm dưới đây ghi lúc viết bộ kiểm thử (11/11/2026), chưa rà lại sau các bản sửa ở mục 2.4 và yêu cầu chưa chốt:

- Hóa đơn mua tự sinh từ phiếu nhập lấy `acceptedQuantity x unitPrice`, không cộng VAT của đơn mua, nên công nợ phải trả nhỏ hơn tổng đơn mua (TC-IN-08 kiểm tra đúng theo cách tính hiện có; cần nhóm chốt yêu cầu).
- order-service không biết cờ `manageByLot`, `manageByExpiration` của hàng hóa: phiếu nhập hàng quản lý theo lô mà không có số lô hoặc hạn dùng vẫn được nhận (lô tự đặt `LOT-<số phiếu nhập>`).
- Một tài khoản PURCHASING lập và tự duyệt được đơn mua của chính mình (chưa tách người lập và người duyệt).
- Phiếu xuất được lập cho đơn ở trạng thái BACKORDER (chỉ chặn DRAFT, CANCELLED, COMPLETED), và không kiểm tra kho của nhân viên kho với kho của đơn.
- Danh mục, đơn vị tính chưa có API sửa, xóa; notification-service chưa có API danh sách thông báo: phần cách ly của các nhóm này kiểm tra bằng cách dùng ID của A khi tạo mới (danh mục, đơn vị tính) và qua KPI (thông báo).

## 3. WMS-20.1 Luồng mua và nhập kho

### 3.1. Chuẩn bị (00-setup)

#### TC-SET-01 Kiểm tra hệ thống sẵn sàng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-SET-01.1 | Gateway trả trạng thái sức khỏe | Khách gọi `GET /actuator/health` | 200, status = UP | `00-setup` / TC-SET-01.1 Gateway trả trạng thái sức khỏe | đạt |
| TC-SET-01.2 | Danh sách gói dịch vụ công khai | Khách gọi `GET /api/auth/plans` | 200, có ba gói FREE, TRIAL, PRO; FREE: 3 người dùng, 1 kho, 200 SKU, 20 lượt AI, không có API key | `00-setup` / TC-SET-01.2 Danh sách gói dịch vụ công khai | đạt |

#### TC-SET-02 Đăng ký doanh nghiệp A và B

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-SET-02.1 | Đăng ký doanh nghiệp A | Gọi `POST /api/auth/companies/register` với tên, mã số thuế, email, tên đăng nhập ngẫu nhiên theo runId cho doanh nghiệp A | 201; activationRequired = false (stack kiểm thử tắt kích hoạt qua email); company.status = ACTIVE; subscription.plan = TRIAL | `00-setup` / TC-SET-02.1 Đăng ký doanh nghiệp A | đạt |
| TC-SET-02.2 | Đăng ký doanh nghiệp B | Gọi `POST /api/auth/companies/register` với tên, mã số thuế, email, tên đăng nhập ngẫu nhiên theo runId cho doanh nghiệp B | 201; activationRequired = false (stack kiểm thử tắt kích hoạt qua email); company.status = ACTIVE; subscription.plan = TRIAL | `00-setup` / TC-SET-02.2 Đăng ký doanh nghiệp B | đạt |
| TC-SET-02.3 | Đăng ký lại cùng tên đăng nhập bị từ chối | Gọi `POST /api/auth/companies/register` với tên đăng nhập của ADMIN A đã có | 409 CONFLICT, không tạo doanh nghiệp mới | `00-setup` / TC-SET-02.3 Đăng ký lại cùng tên đăng nhập bị từ chối | đạt |

#### TC-SET-03 Đăng nhập ADMIN và thuê bao dùng thử

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-SET-03.1 | Đăng nhập ADMIN của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-03.1 Đăng nhập ADMIN của công ty A | đạt |
| TC-SET-03.2 | Đăng nhập ADMIN của công ty B | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty B (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp B | `00-setup` / TC-SET-03.2 Đăng nhập ADMIN của công ty B | đạt |
| TC-SET-03.3 | Thuê bao của A sau đăng ký | ADMIN của A gọi `GET /api/auth/subscription` | 200, plan TRIAL, status ACTIVE, daysRemaining 30, readOnly false, usage.users = 1 | `00-setup` / TC-SET-03.3 Thuê bao của A sau đăng ký | đạt |

#### TC-SET-04 Tạo tài khoản các vai trò của A

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-SET-04.1 | ADMIN của A tạo tài khoản WAREHOUSE_MANAGER | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [WAREHOUSE_MANAGER], companyId = A | `00-setup` / TC-SET-04.1 ADMIN của A tạo tài khoản WAREHOUSE_MANAGER | đạt |
| TC-SET-04.2 | ADMIN của A tạo tài khoản WAREHOUSE_STAFF | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [WAREHOUSE_STAFF], companyId = A | `00-setup` / TC-SET-04.2 ADMIN của A tạo tài khoản WAREHOUSE_STAFF | đạt |
| TC-SET-04.3 | ADMIN của A tạo tài khoản PURCHASING | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [PURCHASING], companyId = A | `00-setup` / TC-SET-04.3 ADMIN của A tạo tài khoản PURCHASING | đạt |
| TC-SET-04.4 | ADMIN của A tạo tài khoản SALES | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [SALES], companyId = A | `00-setup` / TC-SET-04.4 ADMIN của A tạo tài khoản SALES | đạt |
| TC-SET-04.5 | ADMIN của A tạo tài khoản ACCOUNTANT | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [ACCOUNTANT], companyId = A | `00-setup` / TC-SET-04.5 ADMIN của A tạo tài khoản ACCOUNTANT | đạt |

#### TC-SET-05 Đăng nhập các vai trò của A

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-SET-05.1 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.1 Đăng nhập WAREHOUSE_MANAGER của công ty A | đạt |
| TC-SET-05.2 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.2 Đăng nhập WAREHOUSE_STAFF của công ty A | đạt |
| TC-SET-05.3 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.3 Đăng nhập PURCHASING của công ty A | đạt |
| TC-SET-05.4 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.4 Đăng nhập SALES của công ty A | đạt |
| TC-SET-05.5 | Đăng nhập ACCOUNTANT của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ACCOUNTANT của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ACCOUNTANT; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.5 Đăng nhập ACCOUNTANT của công ty A | đạt |
| TC-SET-05.6 | Kế toán không được tạo tài khoản | Kế toán của A gọi `POST /api/auth/users` | 403 FORBIDDEN (chỉ ADMIN quản lý người dùng) | `00-setup` / TC-SET-05.6 Kế toán không được tạo tài khoản | đạt |
| TC-SET-05.7 | Tra ID tài khoản WAREHOUSE_MANAGER của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_MANAGER vừa tạo | `00-setup` / TC-SET-05.7 Tra ID tài khoản WAREHOUSE_MANAGER của A | đạt |

### 3.2. Mua và nhập kho (10-inbound)

#### TC-IN-00 Đăng nhập

Đăng nhập lại các tài khoản của A tạo ở 00-setup.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-00.1 | Kiểm tra runId của lượt chạy | Kiểm tra biến môi trường `runId` đã được truyền (`--env-var runId=...`), rồi gọi `GET /api/auth/plans` | runId có giá trị (cùng runId đã dùng cho 00-setup); API công khai trả 200 | `10-inbound` / TC-IN-00.1 Kiểm tra runId của lượt chạy | đạt |
| TC-IN-00.2 | Đăng nhập ADMIN của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.2 Đăng nhập ADMIN của công ty A | đạt |
| TC-IN-00.3 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.3 Đăng nhập WAREHOUSE_MANAGER của công ty A | đạt |
| TC-IN-00.4 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.4 Đăng nhập WAREHOUSE_STAFF của công ty A | đạt |
| TC-IN-00.5 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.5 Đăng nhập PURCHASING của công ty A | đạt |
| TC-IN-00.6 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.6 Đăng nhập SALES của công ty A | đạt |
| TC-IN-00.7 | Đăng nhập ACCOUNTANT của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ACCOUNTANT của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ACCOUNTANT; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.7 Đăng nhập ACCOUNTANT của công ty A | đạt |

#### TC-IN-01 Đơn vị tính và danh mục

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-01.1 | Tạo đơn vị tính Cái | ADMIN của A gọi `POST /api/products/uoms` | 201, code = CAI | `10-inbound` / TC-IN-01.1 Tạo đơn vị tính Cái | đạt |
| TC-IN-01.2 | Tạo đơn vị tính Thùng (Mua hàng) | Mua hàng của A gọi `POST /api/products/uoms` | 201 (vai trò Mua hàng được tạo đơn vị tính) | `10-inbound` / TC-IN-01.2 Tạo đơn vị tính Thùng (Mua hàng) | đạt |
| TC-IN-01.3 | Trùng mã đơn vị tính bị từ chối | ADMIN của A gọi `POST /api/products/uoms` | 409 CONFLICT (mã chuẩn hóa thành CAI đã có) | `10-inbound` / TC-IN-01.3 Trùng mã đơn vị tính bị từ chối | đạt |
| TC-IN-01.4 | Mã đơn vị tính sai mẫu | ADMIN của A gọi `POST /api/products/uoms` | 400 VALIDATION_ERROR, data.code có lỗi | `10-inbound` / TC-IN-01.4 Mã đơn vị tính sai mẫu | đạt |
| TC-IN-01.5 | Bán hàng không được tạo đơn vị tính | Bán hàng của A gọi `POST /api/products/uoms` | 403 FORBIDDEN | `10-inbound` / TC-IN-01.5 Bán hàng không được tạo đơn vị tính | đạt |
| TC-IN-01.6 | Tạo danh mục Sữa | ADMIN của A gọi `POST /api/products/categories` | 201, code = QA-SUA | `10-inbound` / TC-IN-01.6 Tạo danh mục Sữa | đạt |
| TC-IN-01.7 | Danh mục thiếu tên | ADMIN của A gọi `POST /api/products/categories` | 400 VALIDATION_ERROR, data.name | `10-inbound` / TC-IN-01.7 Danh mục thiếu tên | đạt |
| TC-IN-01.8 | Kế toán không được tạo danh mục | Kế toán của A gọi `POST /api/products/categories` | 403 FORBIDDEN | `10-inbound` / TC-IN-01.8 Kế toán không được tạo danh mục | đạt |
| TC-IN-01.9 | Nhân viên kho xem danh sách đơn vị tính | Nhân viên kho của A gọi `GET /api/products/uoms` | 200, có CAI và THUNG, chỉ của doanh nghiệp A (doanh nghiệp mới bắt đầu rỗng) | `10-inbound` / TC-IN-01.9 Nhân viên kho xem danh sách đơn vị tính | đạt |

#### TC-IN-02 Hàng hóa

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-02.1 | Tạo hàng hóa quản lý theo lô và hạn dùng | Mua hàng của A gọi `POST /api/products` | 201, manageByLot = true, manageByExpiration = true, uomCode = THUNG | `10-inbound` / TC-IN-02.1 Tạo hàng hóa quản lý theo lô và hạn dùng | đạt |
| TC-IN-02.2 | Tạo hàng hóa không theo lô | Nhân viên kho của A gọi `POST /api/products` | 201 (nhân viên kho được thêm hàng hóa), manageByLot = false | `10-inbound` / TC-IN-02.2 Tạo hàng hóa không theo lô | đạt |
| TC-IN-02.3 | Trùng SKU trong doanh nghiệp | ADMIN của A gọi `POST /api/products` | 409 CONFLICT | `10-inbound` / TC-IN-02.3 Trùng SKU trong doanh nghiệp | đạt |
| TC-IN-02.4 | SKU chứa ký tự không hợp lệ | ADMIN của A gọi `POST /api/products` | 400 VALIDATION_ERROR, data.sku | `10-inbound` / TC-IN-02.4 SKU chứa ký tự không hợp lệ | đạt |
| TC-IN-02.5 | Hàng hóa thiếu đơn vị tính | ADMIN của A gọi `POST /api/products` | 400 VALIDATION_ERROR, data.uomId | `10-inbound` / TC-IN-02.5 Hàng hóa thiếu đơn vị tính | đạt |
| TC-IN-02.6 | Giá nhập âm | ADMIN của A gọi `POST /api/products` | 400 VALIDATION_ERROR, data.purchasePrice | `10-inbound` / TC-IN-02.6 Giá nhập âm | đạt |
| TC-IN-02.7 | Bán hàng không được tạo hàng hóa | Bán hàng của A gọi `POST /api/products` | 403 FORBIDDEN | `10-inbound` / TC-IN-02.7 Bán hàng không được tạo hàng hóa | đạt |
| TC-IN-02.8 | Nhân viên kho không được ngưng hàng hóa | Nhân viên kho của A gọi `DELETE /api/products/{{in_prodPlain}}` | 403 FORBIDDEN (chỉ ADMIN, Quản lý kho) | `10-inbound` / TC-IN-02.8 Nhân viên kho không được ngưng hàng hóa | đạt |
| TC-IN-02.9 | Tra hàng hóa theo SKU | Bán hàng của A gọi `GET /api/products/sku/QA-SUA-01` | 200, đúng ID vừa tạo, đang hoạt động | `10-inbound` / TC-IN-02.9 Tra hàng hóa theo SKU | đạt |

#### TC-IN-03 Kho, khu và vị trí

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-03.1 | Tạo kho QA-WH1 | ADMIN của A gọi `POST /api/inventory/warehouses` | 201, code = QA-WH1, active = true | `10-inbound` / TC-IN-03.1 Tạo kho QA-WH1 | đạt |
| TC-IN-03.2 | Trùng mã kho | ADMIN của A gọi `POST /api/inventory/warehouses` | 409 CONFLICT | `10-inbound` / TC-IN-03.2 Trùng mã kho | đạt |
| TC-IN-03.3 | Mã kho sai mẫu | ADMIN của A gọi `POST /api/inventory/warehouses` | 400 VALIDATION_ERROR, data.code | `10-inbound` / TC-IN-03.3 Mã kho sai mẫu | đạt |
| TC-IN-03.4 | Quản lý kho không được tạo kho | Quản lý kho của A gọi `POST /api/inventory/warehouses` | 403 FORBIDDEN | `10-inbound` / TC-IN-03.4 Quản lý kho không được tạo kho | đạt |
| TC-IN-03.5 | Tạo hàng loạt vị trí khu A | ADMIN của A gọi `POST /api/inventory/locations/bulk` | 201, ba vị trí QA-WH1-A-R01-B01 đến B03 | `10-inbound` / TC-IN-03.5 Tạo hàng loạt vị trí khu A | đạt |
| TC-IN-03.6 | Tạo vị trí khu B | ADMIN của A gọi `POST /api/inventory/locations` | 201, code = QA-WH1-B-R01-B01 (viết hoa), status EMPTY | `10-inbound` / TC-IN-03.6 Tạo vị trí khu B | đạt |
| TC-IN-03.7 | Trùng vị trí | ADMIN của A gọi `POST /api/inventory/locations` | 409 CONFLICT | `10-inbound` / TC-IN-03.7 Trùng vị trí | đạt |
| TC-IN-03.8 | Vị trí thiếu khu | ADMIN của A gọi `POST /api/inventory/locations` | 400 VALIDATION_ERROR, data.zone | `10-inbound` / TC-IN-03.8 Vị trí thiếu khu | đạt |
| TC-IN-03.9 | Nhân viên kho không được tạo vị trí | Nhân viên kho của A gọi `POST /api/inventory/locations` | 403 FORBIDDEN | `10-inbound` / TC-IN-03.9 Nhân viên kho không được tạo vị trí | đạt |
| TC-IN-03.10 | Danh sách khu của kho | ADMIN của A gọi `GET /api/inventory/locations/zones?warehouseId={{in_wh}}` | 200, [A, B] | `10-inbound` / TC-IN-03.10 Danh sách khu của kho | đạt |
| TC-IN-03.11 | Tra ID tài khoản WAREHOUSE_MANAGER của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_MANAGER vừa tạo | `10-inbound` / TC-IN-03.11 Tra ID tài khoản WAREHOUSE_MANAGER của A | đạt |
| TC-IN-03.12 | Gán kho QA-WH1 cho WAREHOUSE_MANAGER | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_MANAGER}}/warehouses` | 200, warehouseIds = [QA-WH1] | `10-inbound` / TC-IN-03.12 Gán kho QA-WH1 cho WAREHOUSE_MANAGER | đạt |
| TC-IN-03.13 | Tra ID tài khoản WAREHOUSE_STAFF của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_STAFF vừa tạo | `10-inbound` / TC-IN-03.13 Tra ID tài khoản WAREHOUSE_STAFF của A | đạt |
| TC-IN-03.14 | Gán kho QA-WH1 cho WAREHOUSE_STAFF | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_STAFF}}/warehouses` | 200, warehouseIds = [QA-WH1] | `10-inbound` / TC-IN-03.14 Gán kho QA-WH1 cho WAREHOUSE_STAFF | đạt |
| TC-IN-03.15 | Tra ID tài khoản PURCHASING của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản PURCHASING vừa tạo | `10-inbound` / TC-IN-03.15 Tra ID tài khoản PURCHASING của A | đạt |
| TC-IN-03.16 | Gán kho QA-WH1 cho PURCHASING | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_PURCHASING}}/warehouses` | 200, warehouseIds = [QA-WH1] | `10-inbound` / TC-IN-03.16 Gán kho QA-WH1 cho PURCHASING | đạt |
| TC-IN-03.17 | Tra ID tài khoản SALES của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản SALES vừa tạo | `10-inbound` / TC-IN-03.17 Tra ID tài khoản SALES của A | đạt |
| TC-IN-03.18 | Gán kho QA-WH1 cho SALES | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_SALES}}/warehouses` | 200, warehouseIds = [QA-WH1] | `10-inbound` / TC-IN-03.18 Gán kho QA-WH1 cho SALES | đạt |
| TC-IN-03.19 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-03.19 Đăng nhập WAREHOUSE_MANAGER của công ty A | đạt |
| TC-IN-03.20 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-03.20 Đăng nhập WAREHOUSE_STAFF của công ty A | đạt |
| TC-IN-03.21 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-03.21 Đăng nhập PURCHASING của công ty A | đạt |
| TC-IN-03.22 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-03.22 Đăng nhập SALES của công ty A | đạt |

#### TC-IN-04 Nhà cung cấp

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-04.1 | Mua hàng tạo nhà cung cấp | Mua hàng của A gọi `POST /api/orders/suppliers` | 201, currentDebt = 0, active = true | `10-inbound` / TC-IN-04.1 Mua hàng tạo nhà cung cấp | đạt |
| TC-IN-04.2 | Trùng mã nhà cung cấp | ADMIN của A gọi `POST /api/orders/suppliers` | 409 CONFLICT | `10-inbound` / TC-IN-04.2 Trùng mã nhà cung cấp | đạt |
| TC-IN-04.3 | Nhà cung cấp thiếu tên | ADMIN của A gọi `POST /api/orders/suppliers` | 400 VALIDATION_ERROR, data.name | `10-inbound` / TC-IN-04.3 Nhà cung cấp thiếu tên | đạt |
| TC-IN-04.4 | Email nhà cung cấp sai định dạng | ADMIN của A gọi `POST /api/orders/suppliers` | 400 VALIDATION_ERROR, data.email | `10-inbound` / TC-IN-04.4 Email nhà cung cấp sai định dạng | đạt |
| TC-IN-04.5 | Nhân viên kho không được tạo nhà cung cấp | Nhân viên kho của A gọi `POST /api/orders/suppliers` | 403 FORBIDDEN | `10-inbound` / TC-IN-04.5 Nhân viên kho không được tạo nhà cung cấp | đạt |
| TC-IN-04.6 | Bán hàng không được xem nhà cung cấp | Bán hàng của A gọi `GET /api/orders/suppliers/{{in_sup}}` | 403 FORBIDDEN | `10-inbound` / TC-IN-04.6 Bán hàng không được xem nhà cung cấp | đạt |

#### TC-IN-05 Đơn mua và duyệt

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-05.1 | Mua hàng lập đơn mua hai dòng | Mua hàng của A gọi `POST /api/orders/purchase` | 201, status PENDING, số PO-yyyyMMdd-NNNN, subtotal 15.800.000, thuế 1.200.000, tổng 17.000.000 | `10-inbound` / TC-IN-05.1 Mua hàng lập đơn mua hai dòng | đạt |
| TC-IN-05.2 | Đơn mua không có dòng hàng | Mua hàng của A gọi `POST /api/orders/purchase` | 400 VALIDATION_ERROR, data.items | `10-inbound` / TC-IN-05.2 Đơn mua không có dòng hàng | đạt |
| TC-IN-05.3 | Dòng đơn mua số lượng bằng 0 | Mua hàng của A gọi `POST /api/orders/purchase` | 400 VALIDATION_ERROR, data["items[0].quantity"] | `10-inbound` / TC-IN-05.3 Dòng đơn mua số lượng bằng 0 | đạt |
| TC-IN-05.4 | Đơn mua với nhà cung cấp không tồn tại | Mua hàng của A gọi `POST /api/orders/purchase` | 404 NOT_FOUND | `10-inbound` / TC-IN-05.4 Đơn mua với nhà cung cấp không tồn tại | đạt |
| TC-IN-05.5 | Quản lý kho không được lập đơn mua | Quản lý kho của A gọi `POST /api/orders/purchase` | 403 FORBIDDEN | `10-inbound` / TC-IN-05.5 Quản lý kho không được lập đơn mua | đạt |
| TC-IN-05.6 | Chưa duyệt thì chưa được lập phiếu nhập | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST (đơn đang PENDING) | `10-inbound` / TC-IN-05.6 Chưa duyệt thì chưa được lập phiếu nhập | đạt |
| TC-IN-05.7 | Nhân viên kho không được duyệt đơn mua | Nhân viên kho của A gọi `POST /api/orders/purchase/{{in_po}}/approve` | 403 FORBIDDEN | `10-inbound` / TC-IN-05.7 Nhân viên kho không được duyệt đơn mua | đạt |
| TC-IN-05.8 | Bán hàng không được duyệt đơn mua | Bán hàng của A gọi `POST /api/orders/purchase/{{in_po}}/approve` | 403 FORBIDDEN | `10-inbound` / TC-IN-05.8 Bán hàng không được duyệt đơn mua | đạt |
| TC-IN-05.9 | Quản lý kho duyệt đơn mua | Quản lý kho của A gọi `POST /api/orders/purchase/{{in_po}}/approve` | 200, status APPROVED, approvedBy = tài khoản quản lý kho | `10-inbound` / TC-IN-05.9 Quản lý kho duyệt đơn mua | đạt |
| TC-IN-05.10 | Duyệt lại đơn đã duyệt | ADMIN của A gọi `POST /api/orders/purchase/{{in_po}}/approve` | 400 BAD_REQUEST | `10-inbound` / TC-IN-05.10 Duyệt lại đơn đã duyệt | đạt |
| TC-IN-05.11 | Hàng đang về tăng sau khi duyệt | Mua hàng của A gọi `GET /api/inventory/incoming-stocks/by-product/{{in_prodLot}}?warehouseId={{in_wh}}`; hỏi lại tới khi sự kiện duyệt đơn được xử lý | 200, incomingQuantity = 100 (sự kiện duyệt đơn tăng Incoming) | `10-inbound` / TC-IN-05.11 Hàng đang về tăng sau khi duyệt | đạt |
| TC-IN-05.12 | Danh sách hàng đang về của kho | Nhân viên kho của A gọi `GET /api/inventory/incoming-stocks?warehouseId={{in_wh}}` | 200, có hai dòng của đơn mua, trạng thái INCOMING | `10-inbound` / TC-IN-05.12 Danh sách hàng đang về của kho | đạt |

#### TC-IN-06 Nhập kho từng phần theo lô

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-06.1 | Nhân viên kho nhập 60 thùng lô LOT-A1 và từ chối 5 | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 201, status COMPLETED, totalAcceptedQuantity 60, totalRejectedQuantity 5, totalAmount 9.000.000 | `10-inbound` / TC-IN-06.1 Nhân viên kho nhập 60 thùng lô LOT-A1 và từ chối 5 | đạt |
| TC-IN-06.2 | Đơn mua chuyển nhập dở dang | Mua hàng của A gọi `GET /api/orders/purchase/{{in_po}}` | 200, status PARTIALLY_RECEIVED | `10-inbound` / TC-IN-06.2 Đơn mua chuyển nhập dở dang | đạt |
| TC-IN-06.3 | Nhập vượt số còn lại | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST (còn lại 40) | `10-inbound` / TC-IN-06.3 Nhập vượt số còn lại | đạt |
| TC-IN-06.4 | Nhập lô đã hết hạn | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST (hạn dùng không sau ngày nhận) | `10-inbound` / TC-IN-06.4 Nhập lô đã hết hạn | đạt |
| TC-IN-06.5 | Hạn dùng trước ngày sản xuất | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST | `10-inbound` / TC-IN-06.5 Hạn dùng trước ngày sản xuất | đạt |
| TC-IN-06.6 | Phiếu nhập thiếu đơn mua | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 VALIDATION_ERROR, data.poId | `10-inbound` / TC-IN-06.6 Phiếu nhập thiếu đơn mua | đạt |
| TC-IN-06.7 | Dòng không thuộc đơn mua | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST | `10-inbound` / TC-IN-06.7 Dòng không thuộc đơn mua | đạt |
| TC-IN-06.8 | Mua hàng không được lập phiếu nhập | Mua hàng của A gọi `POST /api/orders/goods-receipt-notes` | 403 FORBIDDEN | `10-inbound` / TC-IN-06.8 Mua hàng không được lập phiếu nhập | đạt |
| TC-IN-06.9 | Tồn thực tế tăng 60 sau phiếu nhập | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodLot}}&warehouseId={{in_wh}}`; hỏi lại tới khi inventory-service xử lý sự kiện nhập kho | 200, onHandQuantity 60, reservedQuantity 0, atpQuantity 60 | `10-inbound` / TC-IN-06.9 Tồn thực tế tăng 60 sau phiếu nhập | đạt |
| TC-IN-06.10 | Hàng đang về giảm còn 40 | Mua hàng của A gọi `GET /api/inventory/incoming-stocks/by-product/{{in_prodLot}}?warehouseId={{in_wh}}` | 200, incomingQuantity 40 | `10-inbound` / TC-IN-06.10 Hàng đang về giảm còn 40 | đạt |
| TC-IN-06.11 | Lô LOT-A1 nằm đúng vị trí với đủ hạn dùng | Quản lý kho của A gọi `GET /api/inventory/lots?productId={{in_prodLot}}&warehouseId={{in_wh}}&size=50` | 200, một lô LOT-A1 số lượng 60 tại QA-WH1-A-R01-B01, expDate đúng | `10-inbound` / TC-IN-06.11 Lô LOT-A1 nằm đúng vị trí với đủ hạn dùng | đạt |
| TC-IN-06.12 | Xác nhận lại phiếu nhập đã hoàn tất | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes/{{in_grn1}}/confirm` | 400 BAD_REQUEST (đã xác nhận), không phát sự kiện nhập lần hai | `10-inbound` / TC-IN-06.12 Xác nhận lại phiếu nhập đã hoàn tất | đạt |
| TC-IN-06.13 | Tồn không tăng thêm sau khi bấm lại | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodLot}}&warehouseId={{in_wh}}`; chờ 4 giây rồi đọc lại | 200, onHandQuantity vẫn 60 | `10-inbound` / TC-IN-06.13 Tồn không tăng thêm sau khi bấm lại | đạt |

#### TC-IN-07 Nhập đủ và nộp trùng phiếu

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-07.1 | Nhập nốt 40 thùng lô LOT-A2 và 40 khay | Quản lý kho của A gọi `POST /api/orders/goods-receipt-notes` | 201, totalAcceptedQuantity 80, totalAmount 6.800.000 | `10-inbound` / TC-IN-07.1 Nhập nốt 40 thùng lô LOT-A2 và 40 khay | đạt |
| TC-IN-07.2 | Nộp trùng phiếu nhập vừa gửi | Quản lý kho của A gọi `POST /api/orders/goods-receipt-notes`; gửi lại nguyên body của TC-IN-07.1 | 400 BAD_REQUEST (đơn đã COMPLETED), không sinh phiếu thứ hai | `10-inbound` / TC-IN-07.2 Nộp trùng phiếu nhập vừa gửi | đạt |
| TC-IN-07.3 | Đơn mua hoàn tất | Mua hàng của A gọi `GET /api/orders/goods-receipt-notes/po/{{in_po}}/progress` | 200, status COMPLETED, isFullyReceived true, totalReceivedQuantity 140, totalRemainingQuantity 0 | `10-inbound` / TC-IN-07.3 Đơn mua hoàn tất | đạt |
| TC-IN-07.4 | Chỉ có hai phiếu nhập cho đơn | Kế toán của A gọi `GET /api/orders/goods-receipt-notes/po/{{in_po}}` | 200, đúng hai phiếu | `10-inbound` / TC-IN-07.4 Chỉ có hai phiếu nhập cho đơn | đạt |
| TC-IN-07.5 | Tồn hàng theo lô bằng 100 | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodLot}}&warehouseId={{in_wh}}` | 200, onHandQuantity 100 | `10-inbound` / TC-IN-07.5 Tồn hàng theo lô bằng 100 | đạt |
| TC-IN-07.6 | Tồn hàng không theo lô bằng 40 | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodPlain}}&warehouseId={{in_wh}}` | 200, onHandQuantity 40 | `10-inbound` / TC-IN-07.6 Tồn hàng không theo lô bằng 40 | đạt |
| TC-IN-07.7 | Tồn không tăng thêm sau khi nộp trùng | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodLot}}&warehouseId={{in_wh}}` | 200, onHandQuantity vẫn 100 | `10-inbound` / TC-IN-07.7 Tồn không tăng thêm sau khi nộp trùng | đạt |
| TC-IN-07.8 | Hai lô với tổng 100 | Kế toán của A gọi `GET /api/inventory/lots?productId={{in_prodLot}}&warehouseId={{in_wh}}&size=50` | 200, LOT-A1 = 60 và LOT-A2 = 40, sắp theo hạn dùng tăng dần | `10-inbound` / TC-IN-07.8 Hai lô với tổng 100 | đạt |
| TC-IN-07.9 | Hàng đang về về 0 | Mua hàng của A gọi `GET /api/inventory/incoming-stocks/by-product/{{in_prodLot}}?warehouseId={{in_wh}}` | 200, incomingQuantity 0 | `10-inbound` / TC-IN-07.9 Hàng đang về về 0 | đạt |
| TC-IN-07.10 | Không lập phiếu nhập cho đơn đã hoàn tất | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST | `10-inbound` / TC-IN-07.10 Không lập phiếu nhập cho đơn đã hoàn tất | đạt |
| TC-IN-07.11 | Vị trí đã có hàng | Quản lý kho của A gọi `GET /api/inventory/locations?warehouseId={{in_wh}}&zone=A` | 200, A-R01-B01 có 60, A-R01-B02 có 40 | `10-inbound` / TC-IN-07.11 Vị trí đã có hàng | đạt |

#### TC-IN-08 Hóa đơn mua, thanh toán và công nợ nhà cung cấp

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-08.1 | Hai hóa đơn mua tự sinh từ phiếu nhập | Kế toán của A gọi `GET /api/orders/purchase-bills/po/{{in_po}}` | 200, hai hóa đơn UNPAID: 9.000.000 (GRN 1) và 6.800.000 (GRN 2) | `10-inbound` / TC-IN-08.1 Hai hóa đơn mua tự sinh từ phiếu nhập | đạt |
| TC-IN-08.2 | Công nợ nhà cung cấp tăng theo hóa đơn | Kế toán của A gọi `GET /api/orders/suppliers/{{in_sup}}` | 200, currentDebt 15.800.000 | `10-inbound` / TC-IN-08.2 Công nợ nhà cung cấp tăng theo hóa đơn | đạt |
| TC-IN-08.3 | Lịch sử công nợ có hai hóa đơn | Mua hàng của A gọi `GET /api/orders/suppliers/{{in_sup}}/transactions` | 200, hai giao dịch PURCHASE_INVOICE, balanceAfter mới nhất 15.800.000 | `10-inbound` / TC-IN-08.3 Lịch sử công nợ có hai hóa đơn | đạt |
| TC-IN-08.4 | Kế toán trả một phần hóa đơn 1 | Kế toán của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 200, status PARTIALLY_PAID, paidAmount 5.000.000, remainingAmount 4.000.000 | `10-inbound` / TC-IN-08.4 Kế toán trả một phần hóa đơn 1 | đạt |
| TC-IN-08.5 | Trả vượt số còn nợ | Kế toán của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 400 BAD_REQUEST | `10-inbound` / TC-IN-08.5 Trả vượt số còn nợ | đạt |
| TC-IN-08.6 | Số tiền thanh toán bằng 0 | Kế toán của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 400 VALIDATION_ERROR, data.amount | `10-inbound` / TC-IN-08.6 Số tiền thanh toán bằng 0 | đạt |
| TC-IN-08.7 | Mua hàng không được ghi thanh toán | Mua hàng của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 403 FORBIDDEN | `10-inbound` / TC-IN-08.7 Mua hàng không được ghi thanh toán | đạt |
| TC-IN-08.8 | Nhân viên kho không được xem hóa đơn mua | Nhân viên kho của A gọi `GET /api/orders/purchase-bills/{{in_bill1}}` | 403 FORBIDDEN | `10-inbound` / TC-IN-08.8 Nhân viên kho không được xem hóa đơn mua | đạt |
| TC-IN-08.9 | Trả nốt hóa đơn 1 | ADMIN của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 200, status PAID, remainingAmount 0 | `10-inbound` / TC-IN-08.9 Trả nốt hóa đơn 1 | đạt |
| TC-IN-08.10 | Không thanh toán thêm hóa đơn đã trả đủ | Kế toán của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 400 BAD_REQUEST | `10-inbound` / TC-IN-08.10 Không thanh toán thêm hóa đơn đã trả đủ | đạt |
| TC-IN-08.11 | Công nợ còn lại bằng hóa đơn 2 | Mua hàng của A gọi `GET /api/orders/suppliers/{{in_sup}}` | 200, currentDebt 6.800.000 | `10-inbound` / TC-IN-08.11 Công nợ còn lại bằng hóa đơn 2 | đạt |
| TC-IN-08.12 | Tổng công nợ phải trả của doanh nghiệp | Kế toán của A gọi `GET /api/orders/suppliers/summary`; chỉ khi A mới có một nhà cung cấp của 10-inbound | 200, totalDebt 6.800.000 (bằng tổng nợ các nhà cung cấp của A) | `10-inbound` / TC-IN-08.12 Tổng công nợ phải trả của doanh nghiệp | đạt |
| TC-IN-08.13 | Báo cáo nhập của kho QA-WH1 | Kế toán của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{in_wh}}&fromDate={{d_m10}}&toDate={{d_today}}&groupBy=PRODUCT_WAREHOUSE` | 200, totals: openingQuantity 0, quantityIn 140, quantityOut 0, closingQuantity 140 | `10-inbound` / TC-IN-08.13 Báo cáo nhập của kho QA-WH1 | đạt |

## 4. WMS-20.2 Bán, xuất kho, điều chuyển và kiểm kê (20-outbound-transfer-stocktake)

Trình tự số liệu của hàng `QA-FEFO-01` ở kho nguồn: nhập 85 (L-EXP 5 hết hạn hôm qua, L-LOCK 10 bị khóa, L-EARLY 20 hạn
+20 ngày, L-LATE 50 hạn +90 ngày), khả dụng 70; đơn bán 30 giữ L-EARLY 20 và L-LATE 10; xuất 10 rồi 20; điều chuyển 15
sang kho đích (nhận 12, hỏng 2, thiếu 1); điều chuyển ngược 2 vào vị trí kiểm kê. Hàng `QA-STK-01`: nhập 40, kiểm kê đếm
37, điều chỉnh giảm 3.

#### TC-OUT-00 Đăng nhập

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-00.1 | Kiểm tra runId của lượt chạy | Kiểm tra biến môi trường `runId` đã được truyền (`--env-var runId=...`), rồi gọi `GET /api/auth/plans` | runId có giá trị (cùng runId đã dùng cho 00-setup); API công khai trả 200 | `20-outbound-transfer-stocktake` / TC-OUT-00.1 Kiểm tra runId của lượt chạy | đạt |
| TC-OUT-00.2 | Đăng nhập ADMIN của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.2 Đăng nhập ADMIN của công ty A | đạt |
| TC-OUT-00.3 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.3 Đăng nhập WAREHOUSE_MANAGER của công ty A | đạt |
| TC-OUT-00.4 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.4 Đăng nhập WAREHOUSE_STAFF của công ty A | đạt |
| TC-OUT-00.5 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.5 Đăng nhập PURCHASING của công ty A | đạt |
| TC-OUT-00.6 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.6 Đăng nhập SALES của công ty A | đạt |
| TC-OUT-00.7 | Đăng nhập ACCOUNTANT của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ACCOUNTANT của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ACCOUNTANT; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.7 Đăng nhập ACCOUNTANT của công ty A | đạt |

#### TC-PREP-20 Chuẩn bị dữ liệu riêng của collection 20

Kho, vị trí, hàng hóa, tồn theo lô dùng cho các bước sau; không phụ thuộc 10-inbound.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PREP-20.1 | Tạo đơn vị tính Hộp | ADMIN của A gọi `POST /api/products/uoms` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.1 Tạo đơn vị tính Hộp | đạt |
| TC-PREP-20.2 | Tạo hàng hóa QA-FEFO-01 | ADMIN của A gọi `POST /api/products` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.2 Tạo hàng hóa QA-FEFO-01 | đạt |
| TC-PREP-20.3 | Tạo hàng hóa QA-STK-01 | ADMIN của A gọi `POST /api/products` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.3 Tạo hàng hóa QA-STK-01 | đạt |
| TC-PREP-20.4 | Tạo hàng hóa QA-FRZ-01 | ADMIN của A gọi `POST /api/products` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.4 Tạo hàng hóa QA-FRZ-01 | đạt |
| TC-PREP-20.5 | Tạo kho QA-WHS | ADMIN của A gọi `POST /api/inventory/warehouses` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.5 Tạo kho QA-WHS | đạt |
| TC-PREP-20.6 | Tạo kho QA-WHD | ADMIN của A gọi `POST /api/inventory/warehouses` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.6 Tạo kho QA-WHD | đạt |
| TC-PREP-20.7 | Tạo vị trí khu A kho nguồn | ADMIN của A gọi `POST /api/inventory/locations/bulk` | 201, QA-WHS-A-R01-B01 và B02 | `20-outbound-transfer-stocktake` / TC-PREP-20.7 Tạo vị trí khu A kho nguồn | đạt |
| TC-PREP-20.8 | Tạo vị trí kiểm kê khu K kho nguồn | ADMIN của A gọi `POST /api/inventory/locations` | 201, QA-WHS-K-R01-B01 | `20-outbound-transfer-stocktake` / TC-PREP-20.8 Tạo vị trí kiểm kê khu K kho nguồn | đạt |
| TC-PREP-20.9 | Tạo vị trí kho đích | ADMIN của A gọi `POST /api/inventory/locations` | 201, QA-WHD-A-R01-B01 | `20-outbound-transfer-stocktake` / TC-PREP-20.9 Tạo vị trí kho đích | đạt |
| TC-PREP-20.10 | Lấy toàn bộ kho của A để gán quyền | ADMIN của A gọi `GET /api/inventory/warehouses` | 200, có QA-WHS và QA-WHD | `20-outbound-transfer-stocktake` / TC-PREP-20.10 Lấy toàn bộ kho của A để gán quyền | đạt |
| TC-PREP-20.11 | Tra ID tài khoản WAREHOUSE_MANAGER của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_MANAGER vừa tạo | `20-outbound-transfer-stocktake` / TC-PREP-20.11 Tra ID tài khoản WAREHOUSE_MANAGER của A | đạt |
| TC-PREP-20.12 | Gán mọi kho của A cho WAREHOUSE_MANAGER | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_MANAGER}}/warehouses` | 200 | `20-outbound-transfer-stocktake` / TC-PREP-20.12 Gán mọi kho của A cho WAREHOUSE_MANAGER | đạt |
| TC-PREP-20.13 | Tra ID tài khoản WAREHOUSE_STAFF của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_STAFF vừa tạo | `20-outbound-transfer-stocktake` / TC-PREP-20.13 Tra ID tài khoản WAREHOUSE_STAFF của A | đạt |
| TC-PREP-20.14 | Gán mọi kho của A cho WAREHOUSE_STAFF | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_STAFF}}/warehouses` | 200 | `20-outbound-transfer-stocktake` / TC-PREP-20.14 Gán mọi kho của A cho WAREHOUSE_STAFF | đạt |
| TC-PREP-20.15 | Tra ID tài khoản PURCHASING của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản PURCHASING vừa tạo | `20-outbound-transfer-stocktake` / TC-PREP-20.15 Tra ID tài khoản PURCHASING của A | đạt |
| TC-PREP-20.16 | Gán mọi kho của A cho PURCHASING | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_PURCHASING}}/warehouses` | 200 | `20-outbound-transfer-stocktake` / TC-PREP-20.16 Gán mọi kho của A cho PURCHASING | đạt |
| TC-PREP-20.17 | Tra ID tài khoản SALES của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản SALES vừa tạo | `20-outbound-transfer-stocktake` / TC-PREP-20.17 Tra ID tài khoản SALES của A | đạt |
| TC-PREP-20.18 | Gán mọi kho của A cho SALES | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_SALES}}/warehouses` | 200 | `20-outbound-transfer-stocktake` / TC-PREP-20.18 Gán mọi kho của A cho SALES | đạt |
| TC-PREP-20.19 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-PREP-20.19 Đăng nhập WAREHOUSE_MANAGER của công ty A | đạt |
| TC-PREP-20.20 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-PREP-20.20 Đăng nhập WAREHOUSE_STAFF của công ty A | đạt |
| TC-PREP-20.21 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-PREP-20.21 Đăng nhập PURCHASING của công ty A | đạt |
| TC-PREP-20.22 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-PREP-20.22 Đăng nhập SALES của công ty A | đạt |
| TC-PREP-20.23 | Tạo nhà cung cấp NCC-QA-20 | ADMIN của A gọi `POST /api/orders/suppliers` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.23 Tạo nhà cung cấp NCC-QA-20 | đạt |
| TC-PREP-20.24 | Lập đơn mua ba mặt hàng | ADMIN của A gọi `POST /api/orders/purchase` | 201, PENDING | `20-outbound-transfer-stocktake` / TC-PREP-20.24 Lập đơn mua ba mặt hàng | đạt |
| TC-PREP-20.25 | Duyệt đơn mua | ADMIN của A gọi `POST /api/orders/purchase/{{o_po}}/approve` | 200, APPROVED | `20-outbound-transfer-stocktake` / TC-PREP-20.25 Duyệt đơn mua | đạt |
| TC-PREP-20.26 | Nhập lô L-EXP ghi lùi ngày (lô đã hết hạn hôm qua) | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes`; receiptDate = hôm nay - 10, expDate = hôm qua | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.26 Nhập lô L-EXP ghi lùi ngày (lô đã hết hạn hôm qua) | đạt |
| TC-PREP-20.27 | Nhập ba lô FEFO và lô kiểm kê | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 201, totalAcceptedQuantity 120 | `20-outbound-transfer-stocktake` / TC-PREP-20.27 Nhập ba lô FEFO và lô kiểm kê | đạt |
| TC-PREP-20.28 | Tồn FEFO khả dụng 80 (lô hết hạn không tính) | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 80 (10 + 20 + 50; lô L-EXP hết hạn bị loại khỏi tồn khả dụng) | `20-outbound-transfer-stocktake` / TC-PREP-20.28 Tồn FEFO khả dụng 80 (lô hết hạn không tính) | đạt |
| TC-PREP-20.29 | Tồn hàng kiểm kê 40 | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodStk}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40 | `20-outbound-transfer-stocktake` / TC-PREP-20.29 Tồn hàng kiểm kê 40 | đạt |
| TC-PREP-20.30 | Lô hết hạn hiển thị trạng thái EXPIRED | Quản lý kho của A gọi `GET /api/inventory/lots?productId={{o_prodFefo}}&warehouseId={{o_whS}}&size=50` | 200, bốn lô; L-EXP có expiryStatus EXPIRED; lưu ID lô L-LOCK | `20-outbound-transfer-stocktake` / TC-PREP-20.30 Lô hết hạn hiển thị trạng thái EXPIRED | đạt |
| TC-PREP-20.31 | Bán hàng không được khóa lô | Bán hàng của A gọi `PUT /api/inventory/lots/{{o_lotLock}}/lock` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-PREP-20.31 Bán hàng không được khóa lô | đạt |
| TC-PREP-20.32 | Quản lý kho khóa lô L-LOCK | Quản lý kho của A gọi `PUT /api/inventory/lots/{{o_lotLock}}/lock` | 200, locked = true, lockedReason lưu lại | `20-outbound-transfer-stocktake` / TC-PREP-20.32 Quản lý kho khóa lô L-LOCK | đạt |
| TC-PREP-20.33 | Tồn FEFO khả dụng còn 70 sau khi khóa lô | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 70, reservedQuantity 0 | `20-outbound-transfer-stocktake` / TC-PREP-20.33 Tồn FEFO khả dụng còn 70 sau khi khóa lô | đạt |

#### TC-OUT-01 Khách hàng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-01.1 | Bán hàng tạo khách hàng bán sỉ | Bán hàng của A gọi `POST /api/orders/customers` | 201, customerType WHOLESALE, currentDebt 0 | `20-outbound-transfer-stocktake` / TC-OUT-01.1 Bán hàng tạo khách hàng bán sỉ | đạt |
| TC-OUT-01.2 | Khách hàng thiếu loại | Bán hàng của A gọi `POST /api/orders/customers` | 400 VALIDATION_ERROR, data.customerType | `20-outbound-transfer-stocktake` / TC-OUT-01.2 Khách hàng thiếu loại | đạt |
| TC-OUT-01.3 | Loại khách hàng không hợp lệ | Bán hàng của A gọi `POST /api/orders/customers` | 400 BAD_REQUEST (giá trị enum không đọc được) | `20-outbound-transfer-stocktake` / TC-OUT-01.3 Loại khách hàng không hợp lệ | đạt |
| TC-OUT-01.4 | Trùng mã khách hàng | ADMIN của A gọi `POST /api/orders/customers` | 409 CONFLICT | `20-outbound-transfer-stocktake` / TC-OUT-01.4 Trùng mã khách hàng | đạt |
| TC-OUT-01.5 | Nhân viên kho không được tạo khách hàng | Nhân viên kho của A gọi `POST /api/orders/customers` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-01.5 Nhân viên kho không được tạo khách hàng | đạt |

#### TC-OUT-02 Đơn bán, xác nhận và giữ chỗ

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-02.1 | Bán hàng lập đơn nháp 30 hộp | Bán hàng của A gọi `POST /api/orders/sales` | 201, status DRAFT, tổng 2.592.000 (30 x 80.000 + VAT 8%) | `20-outbound-transfer-stocktake` / TC-OUT-02.1 Bán hàng lập đơn nháp 30 hộp | đạt |
| TC-OUT-02.2 | Đơn nháp chưa giữ hàng | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, reservedQuantity 0, onHandQuantity 70 | `20-outbound-transfer-stocktake` / TC-OUT-02.2 Đơn nháp chưa giữ hàng | đạt |
| TC-OUT-02.3 | Nhân viên kho không được xác nhận đơn bán | Nhân viên kho của A gọi `POST /api/orders/sales/{{o_so}}/confirm` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-02.3 Nhân viên kho không được xác nhận đơn bán | đạt |
| TC-OUT-02.4 | Kế toán không được xác nhận đơn bán | Kế toán của A gọi `POST /api/orders/sales/{{o_so}}/confirm` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-02.4 Kế toán không được xác nhận đơn bán | đạt |
| TC-OUT-02.5 | Bán hàng xác nhận đơn | Bán hàng của A gọi `POST /api/orders/sales/{{o_so}}/confirm` | 200, status CONFIRMED, confirmedBy = tài khoản bán hàng | `20-outbound-transfer-stocktake` / TC-OUT-02.5 Bán hàng xác nhận đơn | đạt |
| TC-OUT-02.6 | Giữ chỗ làm giảm khả dụng, không đổi tồn thực tế | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 70, reservedQuantity 30, atpQuantity 40 | `20-outbound-transfer-stocktake` / TC-OUT-02.6 Giữ chỗ làm giảm khả dụng, không đổi tồn thực tế | đạt |
| TC-OUT-02.7 | Giữ chỗ theo FEFO trên lô | Quản lý kho của A gọi `GET /api/inventory/lots?productId={{o_prodFefo}}&warehouseId={{o_whS}}&size=50` | 200, L-EARLY giữ 20, L-LATE giữ 10, L-LOCK và L-EXP không bị giữ | `20-outbound-transfer-stocktake` / TC-OUT-02.7 Giữ chỗ theo FEFO trên lô | đạt |
| TC-OUT-02.8 | Xác nhận lại đơn đã xác nhận | Bán hàng của A gọi `POST /api/orders/sales/{{o_so}}/confirm` | 400 BAD_REQUEST, không giữ thêm | `20-outbound-transfer-stocktake` / TC-OUT-02.8 Xác nhận lại đơn đã xác nhận | đạt |
| TC-OUT-02.9 | Lập đơn nháp vượt khả dụng | Bán hàng của A gọi `POST /api/orders/sales` | 201, DRAFT | `20-outbound-transfer-stocktake` / TC-OUT-02.9 Lập đơn nháp vượt khả dụng | đạt |
| TC-OUT-02.10 | Không xác nhận được đơn vượt khả dụng | Bán hàng của A gọi `POST /api/orders/sales/{{o_so2}}/confirm` | 400 BAD_REQUEST (khả dụng 40 < 41) | `20-outbound-transfer-stocktake` / TC-OUT-02.10 Không xác nhận được đơn vượt khả dụng | đạt |
| TC-OUT-02.11 | Đơn không cho backorder bị chặn khi thiếu hàng | Bán hàng của A gọi `POST /api/orders/sales` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-OUT-02.11 Đơn không cho backorder bị chặn khi thiếu hàng | đạt |
| TC-OUT-02.12 | Xóa đơn nháp thừa | Bán hàng của A gọi `DELETE /api/orders/sales/{{o_so2}}` | 200 | `20-outbound-transfer-stocktake` / TC-OUT-02.12 Xóa đơn nháp thừa | đạt |
| TC-OUT-02.13 | Khả dụng không đổi sau các lần bị từ chối | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, reservedQuantity 30, atpQuantity 40 | `20-outbound-transfer-stocktake` / TC-OUT-02.13 Khả dụng không đổi sau các lần bị từ chối | đạt |

#### TC-OUT-03 Danh sách lấy hàng FEFO

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-03.1 | Danh sách lấy hàng theo FEFO, bỏ lô khóa và hết hạn | Nhân viên kho của A gọi `GET /api/orders/sales/{{o_so}}/picking-list` | 200, hai bước: L-EARLY 20 rồi L-LATE 10; không có L-LOCK, L-EXP; totalQuantity 30 | `20-outbound-transfer-stocktake` / TC-OUT-03.1 Danh sách lấy hàng theo FEFO, bỏ lô khóa và hết hạn | đạt |
| TC-OUT-03.2 | Mua hàng không xem được danh sách lấy hàng | Mua hàng của A gọi `GET /api/orders/sales/{{o_so}}/picking-list` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-03.2 Mua hàng không xem được danh sách lấy hàng | đạt |

#### TC-OUT-04 Phiếu xuất từng phần rồi đủ

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-04.1 | Bán hàng không được lập phiếu xuất | Bán hàng của A gọi `POST /api/orders/goods-delivery-notes` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-04.1 Bán hàng không được lập phiếu xuất | đạt |
| TC-OUT-04.2 | Nhân viên kho xuất 10 hộp | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes` | 201, status COMPLETED, totalQuantity 10 | `20-outbound-transfer-stocktake` / TC-OUT-04.2 Nhân viên kho xuất 10 hộp | đạt |
| TC-OUT-04.3 | Tồn giảm 10, giữ chỗ giảm 10 | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 60, reservedQuantity 20 | `20-outbound-transfer-stocktake` / TC-OUT-04.3 Tồn giảm 10, giữ chỗ giảm 10 | đạt |
| TC-OUT-04.4 | Đơn bán vẫn đang xác nhận | Bán hàng của A gọi `GET /api/orders/sales/{{o_so}}` | 200, status CONFIRMED | `20-outbound-transfer-stocktake` / TC-OUT-04.4 Đơn bán vẫn đang xác nhận | đạt |
| TC-OUT-04.5 | Xuất vượt số còn phải giao | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes` | 400 BAD_REQUEST (còn 20) | `20-outbound-transfer-stocktake` / TC-OUT-04.5 Xuất vượt số còn phải giao | đạt |
| TC-OUT-04.6 | Số lượng xuất bằng 0 | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes` | 400 VALIDATION_ERROR | `20-outbound-transfer-stocktake` / TC-OUT-04.6 Số lượng xuất bằng 0 | đạt |
| TC-OUT-04.7 | Xuất nốt 20 hộp | Quản lý kho của A gọi `POST /api/orders/goods-delivery-notes` | 201, COMPLETED, totalQuantity 20 | `20-outbound-transfer-stocktake` / TC-OUT-04.7 Xuất nốt 20 hộp | đạt |
| TC-OUT-04.8 | Đơn bán hoàn tất | Bán hàng của A gọi `GET /api/orders/sales/{{o_so}}` | 200, status COMPLETED | `20-outbound-transfer-stocktake` / TC-OUT-04.8 Đơn bán hoàn tất | đạt |
| TC-OUT-04.9 | Tồn còn 40, giữ chỗ về 0 | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40, reservedQuantity 0 | `20-outbound-transfer-stocktake` / TC-OUT-04.9 Tồn còn 40, giữ chỗ về 0 | đạt |
| TC-OUT-04.10 | Tồn không bị trừ thêm | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity vẫn 40 sau 4 giây | `20-outbound-transfer-stocktake` / TC-OUT-04.10 Tồn không bị trừ thêm | đạt |
| TC-OUT-04.11 | Không xuất thêm cho đơn đã hoàn tất | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-OUT-04.11 Không xuất thêm cho đơn đã hoàn tất | đạt |
| TC-OUT-04.12 | Hai phiếu xuất của đơn | Kế toán của A gọi `GET /api/orders/goods-delivery-notes/so/{{o_so}}` | 200, hai phiếu, tổng 30 | `20-outbound-transfer-stocktake` / TC-OUT-04.12 Hai phiếu xuất của đơn | đạt |
| TC-OUT-04.13 | Xác nhận xuất lại phiếu đã hoàn tất | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes/{{o_gdn1}}/dispatch` | 200, trả nguyên phiếu COMPLETED, không phát sự kiện trừ tồn lần hai | `20-outbound-transfer-stocktake` / TC-OUT-04.13 Xác nhận xuất lại phiếu đã hoàn tất | đạt |
| TC-OUT-04.14 | Tồn vẫn 40 sau khi bấm xuất lại | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40 | `20-outbound-transfer-stocktake` / TC-OUT-04.14 Tồn vẫn 40 sau khi bấm xuất lại | đạt |

#### TC-TRF-01 Điều chuyển giữa hai kho có hàng thiếu, hỏng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-TRF-01.1 | Kho nguồn trùng kho đích | Quản lý kho của A gọi `POST /api/inventory/transfers` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.1 Kho nguồn trùng kho đích | đạt |
| TC-TRF-01.2 | Bán hàng không được lập lệnh điều chuyển | Bán hàng của A gọi `POST /api/inventory/transfers` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-TRF-01.2 Bán hàng không được lập lệnh điều chuyển | đạt |
| TC-TRF-01.3 | Quản lý kho lập lệnh điều chuyển 15 hộp | Quản lý kho của A gọi `POST /api/inventory/transfers` | 201, status DRAFT, totalQuantity 15 | `20-outbound-transfer-stocktake` / TC-TRF-01.3 Quản lý kho lập lệnh điều chuyển 15 hộp | đạt |
| TC-TRF-01.4 | Lập lệnh chưa trừ tồn kho nguồn | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40 | `20-outbound-transfer-stocktake` / TC-TRF-01.4 Lập lệnh chưa trừ tồn kho nguồn | đạt |
| TC-TRF-01.5 | Nhân viên kho không được xuất điều chuyển | Nhân viên kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/dispatch` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-TRF-01.5 Nhân viên kho không được xuất điều chuyển | đạt |
| TC-TRF-01.6 | Quản lý kho xuất điều chuyển | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/dispatch` | 200, status IN_TRANSIT, totalTransferredQuantity 15 | `20-outbound-transfer-stocktake` / TC-TRF-01.6 Quản lý kho xuất điều chuyển | đạt |
| TC-TRF-01.7 | Tồn kho nguồn giảm 15 | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 25 | `20-outbound-transfer-stocktake` / TC-TRF-01.7 Tồn kho nguồn giảm 15 | đạt |
| TC-TRF-01.8 | Xuất điều chuyển lần hai | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/dispatch` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.8 Xuất điều chuyển lần hai | đạt |
| TC-TRF-01.9 | Tổng nhận và hỏng vượt số đã xuất | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 400 BAD_REQUEST (12 + 4 > 15) | `20-outbound-transfer-stocktake` / TC-TRF-01.9 Tổng nhận và hỏng vượt số đã xuất | đạt |
| TC-TRF-01.10 | Nhận hàng thiếu vị trí đích | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.10 Nhận hàng thiếu vị trí đích | đạt |
| TC-TRF-01.11 | Vị trí đích không thuộc kho đích | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.11 Vị trí đích không thuộc kho đích | đạt |
| TC-TRF-01.12 | Nhận 12, hỏng 2, thiếu 1 | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 200, status PARTIAL_RECEIVED, receivedQuantity 12, damagedQuantity 2 | `20-outbound-transfer-stocktake` / TC-TRF-01.12 Nhận 12, hỏng 2, thiếu 1 | đạt |
| TC-TRF-01.13 | Tồn kho đích bằng số thực nhận | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whD}}` | 200, onHandQuantity 12 | `20-outbound-transfer-stocktake` / TC-TRF-01.13 Tồn kho đích bằng số thực nhận | đạt |
| TC-TRF-01.14 | Nhận lại lệnh đã nhận | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.14 Nhận lại lệnh đã nhận | đạt |

#### TC-STK-01 Kiểm kê: đóng băng, đếm mù, duyệt hai cấp, điều chỉnh tồn

Đợt kiểm kê theo vị trí QA-WHS-K-R01-B01 (hàng QA-STK-01 lô STK-1, hệ thống 40, đếm được 37).

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-STK-01.1 | Chuẩn bị lệnh điều chuyển ngược 2 hộp sẽ nhận vào vị trí kiểm kê | Quản lý kho của A gọi `POST /api/inventory/transfers` | 201, DRAFT | `20-outbound-transfer-stocktake` / TC-STK-01.1 Chuẩn bị lệnh điều chuyển ngược 2 hộp sẽ nhận vào vị trí kiểm kê | đạt |
| TC-STK-01.2 | Xuất lệnh điều chuyển ngược | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf2}}/dispatch` | 200, IN_TRANSIT | `20-outbound-transfer-stocktake` / TC-STK-01.2 Xuất lệnh điều chuyển ngược | đạt |
| TC-STK-01.3 | Phạm vi theo vị trí thiếu danh sách vị trí | Quản lý kho của A gọi `POST /api/inventory/stocktakes` | 400 (VALIDATION_ERROR hoặc BAD_REQUEST) | `20-outbound-transfer-stocktake` / TC-STK-01.3 Phạm vi theo vị trí thiếu danh sách vị trí | đạt |
| TC-STK-01.4 | Nhân viên kho không được lập đợt kiểm kê | Nhân viên kho của A gọi `POST /api/inventory/stocktakes` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-STK-01.4 Nhân viên kho không được lập đợt kiểm kê | đạt |
| TC-STK-01.5 | Quản lý kho lập đợt kiểm kê vị trí K | Quản lý kho của A gọi `POST /api/inventory/stocktakes` | 201, status DRAFT, mã ST-yyyyMM-NN | `20-outbound-transfer-stocktake` / TC-STK-01.5 Quản lý kho lập đợt kiểm kê vị trí K | đạt |
| TC-STK-01.6 | Bắt đầu kiểm kê đóng băng vị trí | Quản lý kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/start` | 200, status COUNTING, totalLines 1 | `20-outbound-transfer-stocktake` / TC-STK-01.6 Bắt đầu kiểm kê đóng băng vị trí | đạt |
| TC-STK-01.7 | Vị trí hiển thị đang đóng băng | Nhân viên kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}` | 200, locations[0].frozen = true | `20-outbound-transfer-stocktake` / TC-STK-01.7 Vị trí hiển thị đang đóng băng | đạt |
| TC-STK-01.8 | Trừ tồn tại vị trí đóng băng bị chặn | ADMIN của A gọi `POST /api/inventory/stock/deduct` | 409 CONFLICT, data.sessionCode = mã đợt | `20-outbound-transfer-stocktake` / TC-STK-01.8 Trừ tồn tại vị trí đóng băng bị chặn | đạt |
| TC-STK-01.9 | Nhận điều chuyển vào vị trí đóng băng bị chặn | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf2}}/receive` | 409 CONFLICT | `20-outbound-transfer-stocktake` / TC-STK-01.9 Nhận điều chuyển vào vị trí đóng băng bị chặn | đạt |
| TC-STK-01.10 | Phiếu nhập vào vị trí đóng băng phải bị chặn | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 409 CONFLICT theo yêu cầu (SRS UC-09 1a): order-service hỏi inventory-service trước khi lưu, không ghi phiếu nhập và hóa đơn mua | `20-outbound-transfer-stocktake` / TC-STK-01.10 Phiếu nhập vào vị trí đóng băng phải bị chặn | đạt |
| TC-STK-01.11 | Dòng đếm không lộ số hệ thống | Nhân viên kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}/count-lines` | 200, một dòng STK-1, không có trường systemQuantity | `20-outbound-transfer-stocktake` / TC-STK-01.11 Dòng đếm không lộ số hệ thống | đạt |
| TC-STK-01.12 | Số đếm âm bị từ chối | Nhân viên kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/counts` | 400 VALIDATION_ERROR | `20-outbound-transfer-stocktake` / TC-STK-01.12 Số đếm âm bị từ chối | đạt |
| TC-STK-01.13 | Nhân viên kho đếm được 37 | Nhân viên kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/counts` | 200, countedQuantity 37, không có systemQuantity | `20-outbound-transfer-stocktake` / TC-STK-01.13 Nhân viên kho đếm được 37 | đạt |
| TC-STK-01.14 | Nhân viên kho không xem được chênh lệch | Nhân viên kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}/variances` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-STK-01.14 Nhân viên kho không xem được chênh lệch | đạt |
| TC-STK-01.15 | Chưa kết thúc đếm thì chưa có chênh lệch | Quản lý kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}/variances` | 409 CONFLICT | `20-outbound-transfer-stocktake` / TC-STK-01.15 Chưa kết thúc đếm thì chưa có chênh lệch | đạt |
| TC-STK-01.16 | Kết thúc đếm | Quản lý kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/finish` | 200, status COUNTED, uncountedLines 0 | `20-outbound-transfer-stocktake` / TC-STK-01.16 Kết thúc đếm | đạt |
| TC-STK-01.17 | Chênh lệch giảm 3 | Quản lý kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}/variances?onlyDifferences=true` | 200, linesWithVariance 1, totalDecreaseQuantity 3, netVarianceQuantity -3; dòng: systemQuantity 40, countedQuantity 37, varianceQuantity -3 | `20-outbound-transfer-stocktake` / TC-STK-01.17 Chênh lệch giảm 3 | đạt |
| TC-STK-01.18 | Ghi lý do chênh lệch | Quản lý kho của A gọi `PUT /api/inventory/stocktakes/{{o_stk}}/lines/{{o_stkLine}}/reason` | 200, reason được lưu | `20-outbound-transfer-stocktake` / TC-STK-01.18 Ghi lý do chênh lệch | đạt |
| TC-STK-01.19 | Quản lý kho duyệt cấp một | Quản lý kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/approve` | 200, status PENDING_ACCOUNTING, adjustmentStatus PENDING_APPROVAL | `20-outbound-transfer-stocktake` / TC-STK-01.19 Quản lý kho duyệt cấp một | đạt |
| TC-STK-01.20 | Chưa duyệt cấp kế toán thì tồn chưa đổi | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodStk}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40 | `20-outbound-transfer-stocktake` / TC-STK-01.20 Chưa duyệt cấp kế toán thì tồn chưa đổi | đạt |
| TC-STK-01.21 | Quản lý kho không được duyệt cấp kế toán | Quản lý kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/adjustment/approve` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-STK-01.21 Quản lý kho không được duyệt cấp kế toán | đạt |
| TC-STK-01.22 | Kế toán duyệt phiếu điều chỉnh | Kế toán của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/adjustment/approve` | 200, status COMPLETED, adjustmentStatus APPROVED | `20-outbound-transfer-stocktake` / TC-STK-01.22 Kế toán duyệt phiếu điều chỉnh | đạt |
| TC-STK-01.23 | Tồn được điều chỉnh về 37 | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodStk}}&warehouseId={{o_whS}}` | 200, onHandQuantity 37 | `20-outbound-transfer-stocktake` / TC-STK-01.23 Tồn được điều chỉnh về 37 | đạt |
| TC-STK-01.24 | Duyệt lại phiếu đã duyệt | Kế toán của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/adjustment/approve` | 409 CONFLICT | `20-outbound-transfer-stocktake` / TC-STK-01.24 Duyệt lại phiếu đã duyệt | đạt |
| TC-STK-01.25 | Tồn chỉ điều chỉnh một lần | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodStk}}&warehouseId={{o_whS}}` | 200, onHandQuantity vẫn 37 | `20-outbound-transfer-stocktake` / TC-STK-01.25 Tồn chỉ điều chỉnh một lần | đạt |
| TC-STK-01.26 | Phiếu điều chỉnh ghi tồn trước và sau | Kế toán của A gọi `GET /api/inventory/stock-adjustments/{{o_adj}}` | 200, status APPROVED, dòng: differenceQuantity -3, quantityBefore 40, quantityAfter 37, approvedBy = kế toán | `20-outbound-transfer-stocktake` / TC-STK-01.26 Phiếu điều chỉnh ghi tồn trước và sau | đạt |
| TC-STK-01.27 | Vị trí được mở đóng băng | Quản lý kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}` | 200, locations[0].frozen = false | `20-outbound-transfer-stocktake` / TC-STK-01.27 Vị trí được mở đóng băng | đạt |
| TC-STK-01.28 | Nhận điều chuyển vào vị trí sau kiểm kê | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf2}}/receive` | 200, status COMPLETED | `20-outbound-transfer-stocktake` / TC-STK-01.28 Nhận điều chuyển vào vị trí sau kiểm kê | đạt |

#### TC-STK-02 Kiểm kê: hai cấp duyệt phải khác người, từ chối không đổi tồn

Đợt kiểm kê vị trí QA-WHD-A-R01-B01 do ADMIN thực hiện cả cấp quản lý.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-STK-02.1 | ADMIN lập đợt kiểm kê kho đích | ADMIN của A gọi `POST /api/inventory/stocktakes` | 201, DRAFT | `20-outbound-transfer-stocktake` / TC-STK-02.1 ADMIN lập đợt kiểm kê kho đích | đạt |
| TC-STK-02.2 | ADMIN bắt đầu đếm | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/start` | 200, COUNTING | `20-outbound-transfer-stocktake` / TC-STK-02.2 ADMIN bắt đầu đếm | đạt |
| TC-STK-02.3 | Lấy dòng cần đếm | ADMIN của A gọi `GET /api/inventory/stocktakes/{{o_stk2}}/count-lines` | 200, một dòng hàng QA-FEFO-01 không có số lô (hàng nhận điều chuyển) | `20-outbound-transfer-stocktake` / TC-STK-02.3 Lấy dòng cần đếm | đạt |
| TC-STK-02.4 | Đếm được 9 | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/counts` | 200 | `20-outbound-transfer-stocktake` / TC-STK-02.4 Đếm được 9 | đạt |
| TC-STK-02.5 | Kết thúc đếm | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/finish` | 200, COUNTED | `20-outbound-transfer-stocktake` / TC-STK-02.5 Kết thúc đếm | đạt |
| TC-STK-02.6 | ADMIN duyệt cấp quản lý | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/approve` | 200, PENDING_ACCOUNTING | `20-outbound-transfer-stocktake` / TC-STK-02.6 ADMIN duyệt cấp quản lý | đạt |
| TC-STK-02.7 | Cùng người không được duyệt cấp kế toán | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/adjustment/approve` | 403 FORBIDDEN (người duyệt cấp kế toán phải khác người duyệt cấp quản lý) | `20-outbound-transfer-stocktake` / TC-STK-02.7 Cùng người không được duyệt cấp kế toán | đạt |
| TC-STK-02.8 | Từ chối không có lý do | Kế toán của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/reject` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-STK-02.8 Từ chối không có lý do | đạt |
| TC-STK-02.9 | Kế toán từ chối phiếu điều chỉnh | Kế toán của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/reject` | 200, status REJECTED | `20-outbound-transfer-stocktake` / TC-STK-02.9 Kế toán từ chối phiếu điều chỉnh | đạt |
| TC-STK-02.10 | Từ chối thì tồn giữ nguyên | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whD}}` | 200, onHandQuantity 10 (12 nhận - 2 điều chuyển ngược) | `20-outbound-transfer-stocktake` / TC-STK-02.10 Từ chối thì tồn giữ nguyên | đạt |

#### TC-RPT-01 Sổ kho khớp tồn sau toàn bộ luồng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-RPT-01.1 | Lấy tồn khả dụng và các lô của hàng FEFO ở kho nguồn | Kế toán của A gọi `GET /api/inventory/lots?productId={{o_prodFefo}}&warehouseId={{o_whS}}&size=50` | 200; lô khóa và lô hết hạn cộng lại 15 (L-LOCK 10, L-EXP 5); L-LATE còn 25, L-EARLY 0 | `20-outbound-transfer-stocktake` / TC-RPT-01.1 Lấy tồn khả dụng và các lô của hàng FEFO ở kho nguồn | đạt |
| TC-RPT-01.2 | Tồn khả dụng hàng FEFO ở kho nguồn | Kế toán của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 27 (L-LATE 25 + 2 nhận điều chuyển ngược) | `20-outbound-transfer-stocktake` / TC-RPT-01.2 Tồn khả dụng hàng FEFO ở kho nguồn | đạt |
| TC-RPT-01.3 | Xuất nhập tồn hàng FEFO kho nguồn | Kế toán của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{o_whS}}&productId={{o_prodFefo}}&fromDate={{d_m60}}&toDate={{d_today}}` | 200; opening 0, nhập 87 (85 phiếu nhập + 2 điều chuyển), xuất 45 (30 bán + 15 điều chuyển), tồn cuối 42 = tồn khả dụng + lô khóa, hết hạn | `20-outbound-transfer-stocktake` / TC-RPT-01.3 Xuất nhập tồn hàng FEFO kho nguồn | đạt |
| TC-RPT-01.4 | Thẻ kho hàng FEFO kho nguồn | Quản lý kho của A gọi `GET /api/inventory/reports/stock-card?productId={{o_prodFefo}}&warehouseId={{o_whS}}&fromDate={{d_m60}}&toDate={{d_today}}&size=200` | 200; totalIn 87, totalOut 45, closingQuantity 42; dòng cuối balanceAfter 42; có RECEIPT, SALES_DISPATCH, TRANSFER_OUT, TRANSFER_IN | `20-outbound-transfer-stocktake` / TC-RPT-01.4 Thẻ kho hàng FEFO kho nguồn | đạt |
| TC-RPT-01.5 | Xuất nhập tồn hàng kiểm kê | Kế toán của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{o_whS}}&productId={{o_prodStk}}&fromDate={{d_m60}}&toDate={{d_today}}` | 200; nhập 40, xuất 3 (điều chỉnh giảm), tồn cuối 37 = tồn khả dụng | `20-outbound-transfer-stocktake` / TC-RPT-01.5 Xuất nhập tồn hàng kiểm kê | đạt |
| TC-RPT-01.6 | Xuất nhập tồn kho đích | Kế toán của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{o_whD}}&productId={{o_prodFefo}}&fromDate={{d_m60}}&toDate={{d_today}}` | 200; nhập 12, xuất 2, tồn cuối 10 (đợt kiểm kê bị từ chối không ghi sổ) | `20-outbound-transfer-stocktake` / TC-RPT-01.6 Xuất nhập tồn kho đích | đạt |
| TC-RPT-01.7 | Bán hàng không xem được báo cáo kho | Bán hàng của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{o_whS}}&productId={{o_prodFefo}}&fromDate={{d_m60}}&toDate={{d_today}}` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-RPT-01.7 Bán hàng không xem được báo cáo kho | đạt |
| TC-RPT-01.8 | Truy vết lô L-LATE | Quản lý kho của A gọi `GET /api/inventory/reports/lot-trace?lotNo=L-LATE&productId={{o_prodFefo}}` | 200; nhận 50, xuất bán 10, điều chuyển 15, tồn 25; nguồn gốc là phiếu nhập; nơi đi gồm đơn bán và lệnh điều chuyển | `20-outbound-transfer-stocktake` / TC-RPT-01.8 Truy vết lô L-LATE | đạt |

#### TC-RPT-02 Truy vết lô nhận ở collection 10

Cần chạy 10-inbound trước với cùng runId.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-RPT-02.1 | Tra hàng hóa QA-SUA-01 của collection 10 | ADMIN của A gọi `GET /api/products/sku/QA-SUA-01` | 200 (10-inbound đã chạy) | `20-outbound-transfer-stocktake` / TC-RPT-02.1 Tra hàng hóa QA-SUA-01 của collection 10 | đạt |
| TC-RPT-02.2 | Truy vết lô LOT-A1 | Kế toán của A gọi `GET /api/inventory/reports/lot-trace?lotNo=LOT-A1&productId={{o_prod10}}` | 200; nguồn gốc RECEIPT từ phiếu nhập lần 1 của đơn mua ở collection 10; totalReceived 60; currentOnHand 60; cây có nút GOODS_RECEIPT, PURCHASE_ORDER, SUPPLIER | `20-outbound-transfer-stocktake` / TC-RPT-02.2 Truy vết lô LOT-A1 | đạt |
| TC-RPT-02.3 | Bán hàng không truy vết được lô | Bán hàng của A gọi `GET /api/inventory/reports/lot-trace?lotNo=LOT-A1` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-RPT-02.3 Bán hàng không truy vết được lô | đạt |

## 5. WMS-22.5 Cách ly doanh nghiệp, giới hạn gói, hết hạn và API key (30-isolation-plan-expiry)

Phần cách ly lấy ID dữ liệu của A bằng API danh sách nên cần chạy 10 và 20 trước với cùng `runId`. Doanh nghiệp C được
đăng ký ngay trong collection: chuyển TRIAL về FREE để thử giới hạn, đặt hết hạn bằng công cụ demo, thanh toán giả lập
lên PRO rồi thử API key.

#### TC-ISO-00 Đăng nhập và lấy ID dữ liệu của A

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-00.1 | Kiểm tra runId của lượt chạy | Kiểm tra biến môi trường `runId` đã được truyền (`--env-var runId=...`), rồi gọi `GET /api/auth/plans` | runId có giá trị (cùng runId đã dùng cho 00-setup); API công khai trả 200 | `30-isolation-plan-expiry` / TC-ISO-00.1 Kiểm tra runId của lượt chạy | đạt |
| TC-ISO-00.2 | Đăng nhập ADMIN của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp A | `30-isolation-plan-expiry` / TC-ISO-00.2 Đăng nhập ADMIN của công ty A | đạt |
| TC-ISO-00.3 | Đăng nhập ADMIN của công ty B | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty B (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp B | `30-isolation-plan-expiry` / TC-ISO-00.3 Đăng nhập ADMIN của công ty B | đạt |
| TC-ISO-00.4 | Tra ID tài khoản WAREHOUSE_MANAGER của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_MANAGER vừa tạo | `30-isolation-plan-expiry` / TC-ISO-00.4 Tra ID tài khoản WAREHOUSE_MANAGER của A | đạt |
| TC-ISO-00.5 | Kho của A | ADMIN của A gọi `GET /api/inventory/warehouses` | 200, A có ít nhất một kho | `30-isolation-plan-expiry` / TC-ISO-00.5 Kho của A | đạt |
| TC-ISO-00.6 | Vị trí của A | ADMIN của A gọi `GET /api/inventory/locations?warehouseId={{A_wh}}` | 200, A có ít nhất một vị trí | `30-isolation-plan-expiry` / TC-ISO-00.6 Vị trí của A | đạt |
| TC-ISO-00.7 | Hàng hóa của A | ADMIN của A gọi `GET /api/products?size=5` | 200, A có ít nhất một hàng hóa | `30-isolation-plan-expiry` / TC-ISO-00.7 Hàng hóa của A | đạt |
| TC-ISO-00.8 | Danh mục của A | ADMIN của A gọi `GET /api/products/categories` | 200, A có ít nhất một danh mục | `30-isolation-plan-expiry` / TC-ISO-00.8 Danh mục của A | đạt |
| TC-ISO-00.9 | Đơn vị tính của A | ADMIN của A gọi `GET /api/products/uoms` | 200, A có ít nhất một đơn vị tính | `30-isolation-plan-expiry` / TC-ISO-00.9 Đơn vị tính của A | đạt |
| TC-ISO-00.10 | Nhà cung cấp của A | ADMIN của A gọi `GET /api/orders/suppliers` | 200, A có ít nhất một nhà cung cấp | `30-isolation-plan-expiry` / TC-ISO-00.10 Nhà cung cấp của A | đạt |
| TC-ISO-00.11 | Khách hàng của A | ADMIN của A gọi `GET /api/orders/customers` | 200, A có ít nhất một khách hàng | `30-isolation-plan-expiry` / TC-ISO-00.11 Khách hàng của A | đạt |
| TC-ISO-00.12 | Đơn mua của A | ADMIN của A gọi `GET /api/orders/purchase` | 200, A có ít nhất một đơn mua | `30-isolation-plan-expiry` / TC-ISO-00.12 Đơn mua của A | đạt |
| TC-ISO-00.13 | Đơn bán của A | ADMIN của A gọi `GET /api/orders/sales` | 200, A có ít nhất một đơn bán | `30-isolation-plan-expiry` / TC-ISO-00.13 Đơn bán của A | đạt |
| TC-ISO-00.14 | Phiếu nhập của A | ADMIN của A gọi `GET /api/orders/goods-receipt-notes` | 200, A có ít nhất một phiếu nhập | `30-isolation-plan-expiry` / TC-ISO-00.14 Phiếu nhập của A | đạt |
| TC-ISO-00.15 | Phiếu xuất của A | ADMIN của A gọi `GET /api/orders/goods-delivery-notes` | 200, A có ít nhất một phiếu xuất | `30-isolation-plan-expiry` / TC-ISO-00.15 Phiếu xuất của A | đạt |
| TC-ISO-00.16 | Hóa đơn mua của A | ADMIN của A gọi `GET /api/orders/purchase-bills` | 200, A có ít nhất một hóa đơn mua | `30-isolation-plan-expiry` / TC-ISO-00.16 Hóa đơn mua của A | đạt |
| TC-ISO-00.17 | Lệnh điều chuyển của A | ADMIN của A gọi `GET /api/inventory/transfers` | 200, A có ít nhất một lệnh điều chuyển | `30-isolation-plan-expiry` / TC-ISO-00.17 Lệnh điều chuyển của A | đạt |
| TC-ISO-00.18 | Đợt kiểm kê của A | ADMIN của A gọi `GET /api/inventory/stocktakes` | 200, A có ít nhất một đợt kiểm kê | `30-isolation-plan-expiry` / TC-ISO-00.18 Đợt kiểm kê của A | đạt |
| TC-ISO-00.19 | Phiếu điều chỉnh của A | ADMIN của A gọi `GET /api/inventory/stock-adjustments` | 200, A có ít nhất một phiếu điều chỉnh | `30-isolation-plan-expiry` / TC-ISO-00.19 Phiếu điều chỉnh của A | đạt |
| TC-ISO-00.20 | Lô còn hàng, chưa khóa của A | ADMIN của A gọi `GET /api/inventory/lots?hasStock=true&locked=false&size=5` | 200, A có ít nhất một lô | `30-isolation-plan-expiry` / TC-ISO-00.20 Lô còn hàng, chưa khóa của A | đạt |
| TC-ISO-00.21 | Tồn của hàng hóa A | ADMIN của A gọi `GET /api/inventory/stock/atp?productId={{A_lotProd}}&warehouseId={{A_lotWh}}` | 200, onHandQuantity > 0 | `30-isolation-plan-expiry` / TC-ISO-00.21 Tồn của hàng hóa A | đạt |
| TC-ISO-00.22 | A tạo API key | ADMIN của A gọi `POST /api/auth/api-keys` | 201 (gói TRIAL có quyền API) | `30-isolation-plan-expiry` / TC-ISO-00.22 A tạo API key | đạt |
| TC-ISO-00.23 | A tạo giao dịch thanh toán chờ | ADMIN của A gọi `POST /api/auth/payments` | 201, status PENDING, amount 499000 | `30-isolation-plan-expiry` / TC-ISO-00.23 A tạo giao dịch thanh toán chờ | đạt |
| TC-ISO-00.24 | A trích xuất file CSV bằng AI | ADMIN của A gọi `POST /api/ai/extractions`; multipart `file` = qa/postman/files/hang-hoa-qa.csv, `dataType` = PRODUCT (chạy với --working-dir qa/postman) | 200, extractionId, method HEADER_MATCHING khi AI_MOCK=true, 2 dòng | `30-isolation-plan-expiry` / TC-ISO-00.24 A trích xuất file CSV bằng AI | đạt |
| TC-ISO-00.25 | A hỏi trợ lý về tồn của hàng hóa | ADMIN của A gọi `POST /api/ai/chat` | 200, có conversationId | `30-isolation-plan-expiry` / TC-ISO-00.25 A hỏi trợ lý về tồn của hàng hóa | đạt |
| TC-ISO-00.26 | KPI thời gian thực của A | ADMIN của A gọi `GET /api/notifications/realtime/kpi` | 200 (ghi lại để so sánh) | `30-isolation-plan-expiry` / TC-ISO-00.26 KPI thời gian thực của A | đạt |

#### TC-ISO-01 Cách ly: người dùng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-01.1 | B xem danh sách người dùng | ADMIN của B gọi `GET /api/auth/users?size=100` | 200, chỉ có người dùng của B | `30-isolation-plan-expiry` / TC-ISO-01.1 B xem danh sách người dùng | đạt |
| TC-ISO-01.2 | B xem người dùng của A theo ID | ADMIN của B gọi `GET /api/auth/users/{{uid_A_MANAGER}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-01.2 B xem người dùng của A theo ID | đạt |
| TC-ISO-01.3 | B sửa người dùng của A | ADMIN của B gọi `PUT /api/auth/users/{{uid_A_MANAGER}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-01.3 B sửa người dùng của A | đạt |
| TC-ISO-01.4 | B khóa người dùng của A | ADMIN của B gọi `PATCH /api/auth/users/{{uid_A_MANAGER}}/status` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-01.4 B khóa người dùng của A | đạt |
| TC-ISO-01.5 | B gán kho cho người dùng của A | ADMIN của B gọi `PUT /api/auth/users/{{uid_A_MANAGER}}/warehouses` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-01.5 B gán kho cho người dùng của A | đạt |
| TC-ISO-01.6 | Người dùng của A không đổi | ADMIN của A gọi `GET /api/auth/users/{{uid_A_MANAGER}}` | 200, vẫn bật, vai trò WAREHOUSE_MANAGER | `30-isolation-plan-expiry` / TC-ISO-01.6 Người dùng của A không đổi | đạt |

#### TC-ISO-02 Cách ly: kho

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-02.1 | B xem danh sách kho | ADMIN của B gọi `GET /api/inventory/warehouses` | 200, không có kho của A | `30-isolation-plan-expiry` / TC-ISO-02.1 B xem danh sách kho | đạt |
| TC-ISO-02.2 | B xem kho của A | ADMIN của B gọi `GET /api/inventory/warehouses/{{A_wh}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-02.2 B xem kho của A | đạt |
| TC-ISO-02.3 | B sửa kho của A | ADMIN của B gọi `PUT /api/inventory/warehouses/{{A_wh}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-02.3 B sửa kho của A | đạt |
| TC-ISO-02.4 | B ngưng kho của A | ADMIN của B gọi `PATCH /api/inventory/warehouses/{{A_wh}}/status` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-02.4 B ngưng kho của A | đạt |
| TC-ISO-02.5 | B xóa kho của A | ADMIN của B gọi `DELETE /api/inventory/warehouses/{{A_wh}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-02.5 B xóa kho của A | đạt |
| TC-ISO-02.6 | Kho của A không đổi | ADMIN của A gọi `GET /api/inventory/warehouses/{{A_wh}}` | 200, tên và trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-02.6 Kho của A không đổi | đạt |

#### TC-ISO-03 Cách ly: vị trí

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-03.1 | B lọc vị trí theo kho của A | ADMIN của B gọi `GET /api/inventory/locations?warehouseId={{A_wh}}` | 200 rỗng hoặc 404 NOT_FOUND, không lộ vị trí của A | `30-isolation-plan-expiry` / TC-ISO-03.1 B lọc vị trí theo kho của A | đạt |
| TC-ISO-03.2 | B xem vị trí của A | ADMIN của B gọi `GET /api/inventory/locations/{{A_loc}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-03.2 B xem vị trí của A | đạt |
| TC-ISO-03.3 | B sửa vị trí của A | ADMIN của B gọi `PUT /api/inventory/locations/{{A_loc}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-03.3 B sửa vị trí của A | đạt |
| TC-ISO-03.4 | B xóa vị trí của A | ADMIN của B gọi `DELETE /api/inventory/locations/{{A_loc}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-03.4 B xóa vị trí của A | đạt |
| TC-ISO-03.5 | Vị trí của A không đổi | ADMIN của A gọi `GET /api/inventory/locations/{{A_loc}}` | 200, vẫn hoạt động, đúng mã | `30-isolation-plan-expiry` / TC-ISO-03.5 Vị trí của A không đổi | đạt |

#### TC-ISO-04 Cách ly: tồn kho

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-04.1 | B đọc tồn của hàng hóa A ở kho A | ADMIN của B gọi `GET /api/inventory/stock/atp?productId={{A_lotProd}}&warehouseId={{A_lotWh}}` | 200 với onHandQuantity 0 hoặc 404; không lộ số tồn của A | `30-isolation-plan-expiry` / TC-ISO-04.1 B đọc tồn của hàng hóa A ở kho A | đạt |
| TC-ISO-04.2 | B đọc tồn theo lô sản phẩm của A | ADMIN của B gọi `POST /api/inventory/stock/atp/batch` | 200, tồn bằng 0 | `30-isolation-plan-expiry` / TC-ISO-04.2 B đọc tồn theo lô sản phẩm của A | đạt |
| TC-ISO-04.3 | B trừ tồn ở kho của A | ADMIN của B gọi `POST /api/inventory/stock/deduct` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-04.3 B trừ tồn ở kho của A | đạt |
| TC-ISO-04.4 | Tồn của A không đổi | ADMIN của A gọi `GET /api/inventory/stock/atp?productId={{A_lotProd}}&warehouseId={{A_lotWh}}` | 200, onHandQuantity như trước | `30-isolation-plan-expiry` / TC-ISO-04.4 Tồn của A không đổi | đạt |

#### TC-ISO-05 Cách ly: lô hàng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-05.1 | B lọc lô theo hàng hóa của A | ADMIN của B gọi `GET /api/inventory/lots?productId={{A_lotProd}}` | 200, totalElements 0 | `30-isolation-plan-expiry` / TC-ISO-05.1 B lọc lô theo hàng hóa của A | đạt |
| TC-ISO-05.2 | B tìm lô theo số lô của A | ADMIN của B gọi `GET /api/inventory/lots?lotNo={{A_lotNo}}` | 200, không có lô của A | `30-isolation-plan-expiry` / TC-ISO-05.2 B tìm lô theo số lô của A | đạt |
| TC-ISO-05.3 | B khóa lô của A | ADMIN của B gọi `PUT /api/inventory/lots/{{A_lot}}/lock` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-05.3 B khóa lô của A | đạt |
| TC-ISO-05.4 | Lô của A vẫn chưa khóa | ADMIN của A gọi `GET /api/inventory/lots?productId={{A_lotProd}}&lotNo={{A_lotNo}}` | 200, lô locked = false | `30-isolation-plan-expiry` / TC-ISO-05.4 Lô của A vẫn chưa khóa | đạt |

#### TC-ISO-06 Cách ly: hàng hóa

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-06.1 | B xem danh sách hàng hóa | ADMIN của B gọi `GET /api/products?size=100` | 200, không có hàng của A | `30-isolation-plan-expiry` / TC-ISO-06.1 B xem danh sách hàng hóa | đạt |
| TC-ISO-06.2 | B xem hàng hóa của A | ADMIN của B gọi `GET /api/products/{{A_prod}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-06.2 B xem hàng hóa của A | đạt |
| TC-ISO-06.3 | B tra SKU của A | ADMIN của B gọi `GET /api/products/sku/{{A_prodSku}}` | 404 NOT_FOUND (B chưa có SKU này) | `30-isolation-plan-expiry` / TC-ISO-06.3 B tra SKU của A | đạt |
| TC-ISO-06.4 | B sửa hàng hóa của A | ADMIN của B gọi `PUT /api/products/{{A_prod}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-06.4 B sửa hàng hóa của A | đạt |
| TC-ISO-06.5 | B ngưng hàng hóa của A | ADMIN của B gọi `DELETE /api/products/{{A_prod}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-06.5 B ngưng hàng hóa của A | đạt |
| TC-ISO-06.6 | Hàng hóa của A không đổi | ADMIN của A gọi `GET /api/products/{{A_prod}}` | 200, tên, SKU, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-06.6 Hàng hóa của A không đổi | đạt |

#### TC-ISO-07 Cách ly: danh mục

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-07.1 | B xem danh mục | ADMIN của B gọi `GET /api/products/categories` | 200, không có danh mục của A | `30-isolation-plan-expiry` / TC-ISO-07.1 B xem danh mục | đạt |
| TC-ISO-07.2 | B xem danh mục của A | ADMIN của B gọi `GET /api/products/categories/{{A_cat}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-07.2 B xem danh mục của A | đạt |
| TC-ISO-07.3 | B gắn danh mục của A vào hàng hóa mới | ADMIN của B gọi `POST /api/products`; danh mục không có API sửa, xóa: thử dùng ID danh mục của A khi tạo hàng hóa | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-07.3 B gắn danh mục của A vào hàng hóa mới | đạt |

#### TC-ISO-08 Cách ly: đơn vị tính

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-08.1 | B xem đơn vị tính | ADMIN của B gọi `GET /api/products/uoms` | 200, không có đơn vị tính của A | `30-isolation-plan-expiry` / TC-ISO-08.1 B xem đơn vị tính | đạt |
| TC-ISO-08.2 | B xem đơn vị tính của A | ADMIN của B gọi `GET /api/products/uoms/{{A_uom}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-08.2 B xem đơn vị tính của A | đạt |
| TC-ISO-08.3 | B dùng đơn vị tính của A | ADMIN của B gọi `POST /api/products`; đơn vị tính không có API sửa, xóa: thử dùng ID của A khi tạo hàng hóa | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-08.3 B dùng đơn vị tính của A | đạt |

#### TC-ISO-09 Cách ly: nhà cung cấp

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-09.1 | B xem danh sách nhà cung cấp | ADMIN của B gọi `GET /api/orders/suppliers?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-09.1 B xem danh sách nhà cung cấp | đạt |
| TC-ISO-09.2 | B xem nhà cung cấp của A | ADMIN của B gọi `GET /api/orders/suppliers/{{A_sup}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.2 B xem nhà cung cấp của A | đạt |
| TC-ISO-09.3 | B xem công nợ nhà cung cấp của A | ADMIN của B gọi `GET /api/orders/suppliers/{{A_sup}}/transactions` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.3 B xem công nợ nhà cung cấp của A | đạt |
| TC-ISO-09.4 | B sửa nhà cung cấp của A | ADMIN của B gọi `PUT /api/orders/suppliers/{{A_sup}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.4 B sửa nhà cung cấp của A | đạt |
| TC-ISO-09.5 | B xóa nhà cung cấp của A | ADMIN của B gọi `DELETE /api/orders/suppliers/{{A_sup}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.5 B xóa nhà cung cấp của A | đạt |
| TC-ISO-09.6 | B ghi công nợ nhà cung cấp của A | ADMIN của B gọi `POST /api/orders/suppliers/{{A_sup}}/transactions` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.6 B ghi công nợ nhà cung cấp của A | đạt |
| TC-ISO-09.7 | Nhà cung cấp của A không đổi | ADMIN của A gọi `GET /api/orders/suppliers/{{A_sup}}` | 200, tên giữ nguyên, còn hoạt động | `30-isolation-plan-expiry` / TC-ISO-09.7 Nhà cung cấp của A không đổi | đạt |

#### TC-ISO-10 Cách ly: khách hàng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-10.1 | B xem danh sách khách hàng | ADMIN của B gọi `GET /api/orders/customers?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-10.1 B xem danh sách khách hàng | đạt |
| TC-ISO-10.2 | B xem khách hàng của A | ADMIN của B gọi `GET /api/orders/customers/{{A_cus}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-10.2 B xem khách hàng của A | đạt |
| TC-ISO-10.3 | B kiểm tra hạn mức khách hàng của A | ADMIN của B gọi `GET /api/orders/customers/{{A_cus}}/credit-check?orderAmount=1000` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-10.3 B kiểm tra hạn mức khách hàng của A | đạt |
| TC-ISO-10.4 | B sửa khách hàng của A | ADMIN của B gọi `PUT /api/orders/customers/{{A_cus}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-10.4 B sửa khách hàng của A | đạt |
| TC-ISO-10.5 | B xóa khách hàng của A | ADMIN của B gọi `DELETE /api/orders/customers/{{A_cus}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-10.5 B xóa khách hàng của A | đạt |
| TC-ISO-10.6 | Khách hàng của A không đổi | ADMIN của A gọi `GET /api/orders/customers/{{A_cus}}` | 200, tên giữ nguyên, không bị khóa nợ | `30-isolation-plan-expiry` / TC-ISO-10.6 Khách hàng của A không đổi | đạt |

#### TC-ISO-11 Cách ly: đơn mua

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-11.1 | B xem danh sách đơn mua | ADMIN của B gọi `GET /api/orders/purchase?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-11.1 B xem danh sách đơn mua | đạt |
| TC-ISO-11.2 | B xem đơn mua của A | ADMIN của B gọi `GET /api/orders/purchase/{{A_po}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.2 B xem đơn mua của A | đạt |
| TC-ISO-11.3 | B tra số đơn mua của A | ADMIN của B gọi `GET /api/orders/purchase/number/{{A_poNumber}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.3 B tra số đơn mua của A | đạt |
| TC-ISO-11.4 | B duyệt đơn mua của A | ADMIN của B gọi `POST /api/orders/purchase/{{A_po}}/approve` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.4 B duyệt đơn mua của A | đạt |
| TC-ISO-11.5 | B hủy đơn mua của A | ADMIN của B gọi `POST /api/orders/purchase/{{A_po}}/cancel` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.5 B hủy đơn mua của A | đạt |
| TC-ISO-11.6 | B xóa đơn mua của A | ADMIN của B gọi `DELETE /api/orders/purchase/{{A_po}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.6 B xóa đơn mua của A | đạt |
| TC-ISO-11.7 | Đơn mua của A không đổi | ADMIN của A gọi `GET /api/orders/purchase/{{A_po}}` | 200, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-11.7 Đơn mua của A không đổi | đạt |

#### TC-ISO-12 Cách ly: đơn bán

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-12.1 | B xem danh sách đơn bán | ADMIN của B gọi `GET /api/orders/sales?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-12.1 B xem danh sách đơn bán | đạt |
| TC-ISO-12.2 | B xem đơn bán của A | ADMIN của B gọi `GET /api/orders/sales/{{A_so}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.2 B xem đơn bán của A | đạt |
| TC-ISO-12.3 | B tra số đơn bán của A | ADMIN của B gọi `GET /api/orders/sales/number/{{A_soNumber}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.3 B tra số đơn bán của A | đạt |
| TC-ISO-12.4 | B xem danh sách lấy hàng của đơn A | ADMIN của B gọi `GET /api/orders/sales/{{A_so}}/picking-list` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.4 B xem danh sách lấy hàng của đơn A | đạt |
| TC-ISO-12.5 | B xác nhận đơn bán của A | ADMIN của B gọi `POST /api/orders/sales/{{A_so}}/confirm` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.5 B xác nhận đơn bán của A | đạt |
| TC-ISO-12.6 | B hủy đơn bán của A | ADMIN của B gọi `POST /api/orders/sales/{{A_so}}/cancel` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.6 B hủy đơn bán của A | đạt |
| TC-ISO-12.7 | B xóa đơn bán của A | ADMIN của B gọi `DELETE /api/orders/sales/{{A_so}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.7 B xóa đơn bán của A | đạt |
| TC-ISO-12.8 | B lập phiếu xuất cho đơn bán của A | ADMIN của B gọi `POST /api/orders/goods-delivery-notes` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.8 B lập phiếu xuất cho đơn bán của A | đạt |
| TC-ISO-12.9 | Đơn bán của A không đổi | ADMIN của A gọi `GET /api/orders/sales/{{A_so}}` | 200, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-12.9 Đơn bán của A không đổi | đạt |

#### TC-ISO-13 Cách ly: phiếu nhập

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-13.1 | B xem danh sách phiếu nhập | ADMIN của B gọi `GET /api/orders/goods-receipt-notes?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-13.1 B xem danh sách phiếu nhập | đạt |
| TC-ISO-13.2 | B xem phiếu nhập của A | ADMIN của B gọi `GET /api/orders/goods-receipt-notes/{{A_grn}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-13.2 B xem phiếu nhập của A | đạt |
| TC-ISO-13.3 | B tra số phiếu nhập của A | ADMIN của B gọi `GET /api/orders/goods-receipt-notes/number/{{A_grnNumber}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-13.3 B tra số phiếu nhập của A | đạt |
| TC-ISO-13.4 | B xác nhận phiếu nhập của A | ADMIN của B gọi `POST /api/orders/goods-receipt-notes/{{A_grn}}/confirm` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-13.4 B xác nhận phiếu nhập của A | đạt |
| TC-ISO-13.5 | B lập phiếu nhập cho đơn mua của A | ADMIN của B gọi `POST /api/orders/goods-receipt-notes` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-13.5 B lập phiếu nhập cho đơn mua của A | đạt |

#### TC-ISO-14 Cách ly: phiếu xuất

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-14.1 | B xem danh sách phiếu xuất | ADMIN của B gọi `GET /api/orders/goods-delivery-notes?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-14.1 B xem danh sách phiếu xuất | đạt |
| TC-ISO-14.2 | B xem phiếu xuất của A | ADMIN của B gọi `GET /api/orders/goods-delivery-notes/{{A_gdn}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-14.2 B xem phiếu xuất của A | đạt |
| TC-ISO-14.3 | B tra số phiếu xuất của A | ADMIN của B gọi `GET /api/orders/goods-delivery-notes/number/{{A_gdnNumber}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-14.3 B tra số phiếu xuất của A | đạt |
| TC-ISO-14.4 | B xác nhận xuất phiếu của A | ADMIN của B gọi `POST /api/orders/goods-delivery-notes/{{A_gdn}}/dispatch` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-14.4 B xác nhận xuất phiếu của A | đạt |

#### TC-ISO-15 Cách ly: hóa đơn mua và thanh toán nhà cung cấp

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-15.1 | B xem danh sách hóa đơn mua | ADMIN của B gọi `GET /api/orders/purchase-bills?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-15.1 B xem danh sách hóa đơn mua | đạt |
| TC-ISO-15.2 | B xem hóa đơn mua của A | ADMIN của B gọi `GET /api/orders/purchase-bills/{{A_bill}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-15.2 B xem hóa đơn mua của A | đạt |
| TC-ISO-15.3 | B ghi thanh toán hóa đơn của A | ADMIN của B gọi `POST /api/orders/purchase-bills/{{A_bill}}/payments` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-15.3 B ghi thanh toán hóa đơn của A | đạt |
| TC-ISO-15.4 | Hóa đơn của A không đổi | ADMIN của A gọi `GET /api/orders/purchase-bills/{{A_bill}}` | 200, paidAmount giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-15.4 Hóa đơn của A không đổi | đạt |

#### TC-ISO-16 Cách ly: điều chuyển

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-16.1 | B xem danh sách điều chuyển | ADMIN của B gọi `GET /api/inventory/transfers?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-16.1 B xem danh sách điều chuyển | đạt |
| TC-ISO-16.2 | B xem lệnh điều chuyển của A | ADMIN của B gọi `GET /api/inventory/transfers/{{A_trf}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.2 B xem lệnh điều chuyển của A | đạt |
| TC-ISO-16.3 | B sửa lệnh điều chuyển của A | ADMIN của B gọi `PUT /api/inventory/transfers/{{A_trf}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.3 B sửa lệnh điều chuyển của A | đạt |
| TC-ISO-16.4 | B hủy lệnh điều chuyển của A | ADMIN của B gọi `DELETE /api/inventory/transfers/{{A_trf}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.4 B hủy lệnh điều chuyển của A | đạt |
| TC-ISO-16.5 | B xuất lệnh điều chuyển của A | ADMIN của B gọi `POST /api/inventory/transfers/{{A_trf}}/dispatch` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.5 B xuất lệnh điều chuyển của A | đạt |
| TC-ISO-16.6 | B nhận lệnh điều chuyển của A | ADMIN của B gọi `POST /api/inventory/transfers/{{A_trf}}/receive` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.6 B nhận lệnh điều chuyển của A | đạt |
| TC-ISO-16.7 | Lệnh điều chuyển của A không đổi | ADMIN của A gọi `GET /api/inventory/transfers/{{A_trf}}` | 200, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-16.7 Lệnh điều chuyển của A không đổi | đạt |

#### TC-ISO-17 Cách ly: kiểm kê và phiếu điều chỉnh

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-17.1 | B xem danh sách đợt kiểm kê | ADMIN của B gọi `GET /api/inventory/stocktakes?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-17.1 B xem danh sách đợt kiểm kê | đạt |
| TC-ISO-17.2 | B xem đợt kiểm kê của A | ADMIN của B gọi `GET /api/inventory/stocktakes/{{A_stk}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-17.2 B xem đợt kiểm kê của A | đạt |
| TC-ISO-17.3 | B xem dòng đếm của A | ADMIN của B gọi `GET /api/inventory/stocktakes/{{A_stk}}/count-lines` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-17.3 B xem dòng đếm của A | đạt |
| TC-ISO-17.4 | B hủy đợt kiểm kê của A | ADMIN của B gọi `POST /api/inventory/stocktakes/{{A_stk}}/cancel` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-17.4 B hủy đợt kiểm kê của A | đạt |
| TC-ISO-17.5 | B xem danh sách phiếu điều chỉnh | ADMIN của B gọi `GET /api/inventory/stock-adjustments?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-17.5 B xem danh sách phiếu điều chỉnh | đạt |
| TC-ISO-17.6 | B xem phiếu điều chỉnh của A | ADMIN của B gọi `GET /api/inventory/stock-adjustments/{{A_adj}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-17.6 B xem phiếu điều chỉnh của A | đạt |
| TC-ISO-17.7 | Đợt kiểm kê của A không đổi | ADMIN của A gọi `GET /api/inventory/stocktakes/{{A_stk}}` | 200, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-17.7 Đợt kiểm kê của A không đổi | đạt |

#### TC-ISO-18 Cách ly: báo cáo kho

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-18.1 | B xem báo cáo xuất nhập tồn của mình | ADMIN của B gọi `GET /api/inventory/reports/in-out-balance?fromDate={{d_m60}}&toDate={{d_today}}&size=200` | 200, không có dòng nào của sản phẩm A | `30-isolation-plan-expiry` / TC-ISO-18.1 B xem báo cáo xuất nhập tồn của mình | đạt |
| TC-ISO-18.2 | B xem báo cáo theo kho của A | ADMIN của B gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{A_lotWh}}&fromDate={{d_m60}}&toDate={{d_today}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-18.2 B xem báo cáo theo kho của A | đạt |
| TC-ISO-18.3 | B xem thẻ kho của A | ADMIN của B gọi `GET /api/inventory/reports/stock-card?productId={{A_lotProd}}&warehouseId={{A_lotWh}}&fromDate={{d_m60}}&toDate={{d_today}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-18.3 B xem thẻ kho của A | đạt |
| TC-ISO-18.4 | B truy vết lô của A | ADMIN của B gọi `GET /api/inventory/reports/lot-trace?lotNo={{A_lotNo}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-18.4 B truy vết lô của A | đạt |

#### TC-ISO-19 Cách ly: cảnh báo tồn kho

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-19.1 | B xem cảnh báo của mình | ADMIN của B gọi `GET /api/inventory/alerts?size=100` | 200, không có cảnh báo của kho A | `30-isolation-plan-expiry` / TC-ISO-19.1 B xem cảnh báo của mình | đạt |
| TC-ISO-19.2 | B lọc cảnh báo theo kho của A | ADMIN của B gọi `GET /api/inventory/alerts?warehouseId={{A_lotWh}}` | 200 với totalElements 0 hoặc 404 | `30-isolation-plan-expiry` / TC-ISO-19.2 B lọc cảnh báo theo kho của A | đạt |
| TC-ISO-19.3 | B xem tổng hợp cảnh báo kho của A | ADMIN của B gọi `GET /api/inventory/alerts/summary?warehouseId={{A_lotWh}}` | 200 với total 0 hoặc 404 | `30-isolation-plan-expiry` / TC-ISO-19.3 B xem tổng hợp cảnh báo kho của A | đạt |

#### TC-ISO-20 Cách ly: thông báo và KPI

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-20.1 | KPI thời gian thực của B không đếm sự kiện của A | ADMIN của B gọi `GET /api/notifications/realtime/kpi`; B chưa lập đơn bán, chưa xuất kho; notification-service không có API danh sách thông báo nên kiểm tra qua KPI | 200, salesOrders 0, dispatchedOrders 0 | `30-isolation-plan-expiry` / TC-ISO-20.1 KPI thời gian thực của B không đếm sự kiện của A | đạt |
| TC-ISO-20.2 | B xem KPI dashboard với kho của A | ADMIN của B gọi `GET /api/notifications/dashboard/kpi-summary?warehouseId={{A_lotWh}}` | Không lỗi 5xx; nếu 200 thì không chứa mã, tên kho của A | `30-isolation-plan-expiry` / TC-ISO-20.2 B xem KPI dashboard với kho của A | đạt |

#### TC-ISO-21 Cách ly: API key

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-21.1 | B xem danh sách API key | ADMIN của B gọi `GET /api/auth/api-keys` | 200, không có key của A | `30-isolation-plan-expiry` / TC-ISO-21.1 B xem danh sách API key | đạt |
| TC-ISO-21.2 | B thu hồi API key của A | ADMIN của B gọi `DELETE /api/auth/api-keys/{{A_keyId}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-21.2 B thu hồi API key của A | đạt |
| TC-ISO-21.3 | Key của A vẫn hoạt động | ADMIN của A gọi `GET /api/auth/api-keys` | 200, key của A active = true | `30-isolation-plan-expiry` / TC-ISO-21.3 Key của A vẫn hoạt động | đạt |
| TC-ISO-21.4 | Key của A đọc được hàng hóa của chính A | Hệ thống ngoài (API key) gọi `GET /api/products?size=100` | 200, chỉ có hàng hóa của A (có SKU của A) | `30-isolation-plan-expiry` / TC-ISO-21.4 Key của A đọc được hàng hóa của chính A | đạt |
| TC-ISO-21.5 | A thu hồi key sau kiểm thử | ADMIN của A gọi `DELETE /api/auth/api-keys/{{A_keyId}}` | 200, active false | `30-isolation-plan-expiry` / TC-ISO-21.5 A thu hồi key sau kiểm thử | đạt |

#### TC-ISO-22 Cách ly: thanh toán

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-22.1 | B xem lịch sử thanh toán | ADMIN của B gọi `GET /api/auth/payments?size=100` | 200, không có giao dịch của A | `30-isolation-plan-expiry` / TC-ISO-22.1 B xem lịch sử thanh toán | đạt |
| TC-ISO-22.2 | B xem giao dịch của A | ADMIN của B gọi `GET /api/auth/payments/{{A_payOrder}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-22.2 B xem giao dịch của A | đạt |
| TC-ISO-22.3 | B xác nhận giả lập giao dịch của A | ADMIN của B gọi `POST /api/auth/payments/{{A_payOrder}}/mock-confirm` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-22.3 B xác nhận giả lập giao dịch của A | đạt |
| TC-ISO-22.4 | B hủy giao dịch của A | ADMIN của B gọi `POST /api/auth/payments/{{A_payOrder}}/cancel` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-22.4 B hủy giao dịch của A | đạt |
| TC-ISO-22.5 | Giao dịch của A vẫn chờ, thuê bao A không đổi | ADMIN của A gọi `GET /api/auth/payments/{{A_payOrder}}` | 200, status PENDING | `30-isolation-plan-expiry` / TC-ISO-22.5 Giao dịch của A vẫn chờ, thuê bao A không đổi | đạt |
| TC-ISO-22.6 | A hủy giao dịch sau kiểm thử | ADMIN của A gọi `POST /api/auth/payments/{{A_payOrder}}/cancel` | 200, CANCELLED | `30-isolation-plan-expiry` / TC-ISO-22.6 A hủy giao dịch sau kiểm thử | đạt |
| TC-ISO-22.7 | Thuê bao của B vẫn là dùng thử | ADMIN của B gọi `GET /api/auth/subscription` | 200, plan TRIAL | `30-isolation-plan-expiry` / TC-ISO-22.7 Thuê bao của B vẫn là dùng thử | đạt |

#### TC-ISO-23 Cách ly: trích xuất AI

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-23.1 | B xem kết quả trích xuất của A | ADMIN của B gọi `GET /api/ai/extractions/{{A_ext}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-23.1 B xem kết quả trích xuất của A | đạt |
| TC-ISO-23.2 | B nhập kết quả trích xuất của A | ADMIN của B gọi `POST /api/ai/extractions/{{A_ext}}/import` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-23.2 B nhập kết quả trích xuất của A | đạt |
| TC-ISO-23.3 | A vẫn xem được kết quả trích xuất | ADMIN của A gọi `GET /api/ai/extractions/{{A_ext}}` | 200 | `30-isolation-plan-expiry` / TC-ISO-23.3 A vẫn xem được kết quả trích xuất | đạt |

#### TC-ISO-24 Cách ly: trợ lý AI

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-24.1 | B dùng mã hội thoại của A | ADMIN của B gọi `POST /api/ai/chat` | 200, newConversation true, conversationId khác của A | `30-isolation-plan-expiry` / TC-ISO-24.1 B dùng mã hội thoại của A | đạt |
| TC-ISO-24.2 | B hỏi tồn của SKU chỉ có ở A | ADMIN của B gọi `POST /api/ai/chat` | 200, câu trả lời không chứa tên hàng của A (không tìm thấy trong dữ liệu của B) | `30-isolation-plan-expiry` / TC-ISO-24.2 B hỏi tồn của SKU chỉ có ở A | đạt |

#### TC-ISO-25 Hai doanh nghiệp dùng trùng mã

B tạo dữ liệu trùng mã với A: được phép (ràng buộc duy nhất theo doanh nghiệp).

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-25.1 | B tạo đơn vị tính trùng mã với A | ADMIN của B gọi `POST /api/products/uoms` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.1 B tạo đơn vị tính trùng mã với A | đạt |
| TC-ISO-25.2 | B tạo danh mục trùng mã với A | ADMIN của B gọi `POST /api/products/categories` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.2 B tạo danh mục trùng mã với A | đạt |
| TC-ISO-25.3 | B tạo hàng hóa trùng SKU với A | ADMIN của B gọi `POST /api/products` | 201, ID khác hàng của A | `30-isolation-plan-expiry` / TC-ISO-25.3 B tạo hàng hóa trùng SKU với A | đạt |
| TC-ISO-25.4 | Tra SKU trùng trả hàng của chính mình | ADMIN của A gọi `GET /api/products/sku/{{A_prodSku}}` | 200, A nhận hàng của A | `30-isolation-plan-expiry` / TC-ISO-25.4 Tra SKU trùng trả hàng của chính mình | đạt |
| TC-ISO-25.5 | B tạo kho trùng mã với A | ADMIN của B gọi `POST /api/inventory/warehouses` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.5 B tạo kho trùng mã với A | đạt |
| TC-ISO-25.6 | B tạo vị trí trùng mã với A | ADMIN của B gọi `POST /api/inventory/locations`; cùng khu, kệ, ô với vị trí đầu tiên của A trong kho trùng mã | 201, cùng mã vị trí với A | `30-isolation-plan-expiry` / TC-ISO-25.6 B tạo vị trí trùng mã với A | đạt |
| TC-ISO-25.7 | B tạo nhà cung cấp trùng mã với A | ADMIN của B gọi `POST /api/orders/suppliers` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.7 B tạo nhà cung cấp trùng mã với A | đạt |
| TC-ISO-25.8 | B tạo khách hàng trùng mã với A | ADMIN của B gọi `POST /api/orders/customers` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.8 B tạo khách hàng trùng mã với A | đạt |
| TC-ISO-25.9 | B lập đơn mua trùng số chứng từ với A | ADMIN của B gọi `POST /api/orders/purchase` | 201, poNumber trùng với A | `30-isolation-plan-expiry` / TC-ISO-25.9 B lập đơn mua trùng số chứng từ với A | đạt |
| TC-ISO-25.10 | B lập đơn bán trùng số chứng từ với A | ADMIN của B gọi `POST /api/orders/sales` | 201, soNumber trùng với A | `30-isolation-plan-expiry` / TC-ISO-25.10 B lập đơn bán trùng số chứng từ với A | đạt |
| TC-ISO-25.11 | Số đơn mua trùng: mỗi bên thấy đơn của mình | ADMIN của A gọi `GET /api/orders/purchase/number/{{A_poNumber}}` | 200, A nhận đơn của A | `30-isolation-plan-expiry` / TC-ISO-25.11 Số đơn mua trùng: mỗi bên thấy đơn của mình | đạt |
| TC-ISO-25.12 | Tên đăng nhập vẫn duy nhất toàn hệ thống | ADMIN của B gọi `POST /api/auth/users` | 409 CONFLICT (ngoại lệ mục 2.3.5: tên đăng nhập, email duy nhất toàn hệ thống) | `30-isolation-plan-expiry` / TC-ISO-25.12 Tên đăng nhập vẫn duy nhất toàn hệ thống | đạt |

#### TC-PLAN-01 Doanh nghiệp C chuyển về gói FREE

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-01.1 | Đăng ký doanh nghiệp C | Khách gọi `POST /api/auth/companies/register` | 201, TRIAL, activationRequired false | `30-isolation-plan-expiry` / TC-PLAN-01.1 Đăng ký doanh nghiệp C | đạt |
| TC-PLAN-01.2 | Đăng nhập ADMIN của công ty C | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty C (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp C | `30-isolation-plan-expiry` / TC-PLAN-01.2 Đăng nhập ADMIN của công ty C | đạt |
| TC-PLAN-01.3 | Doanh nghiệp mới nhận gói dùng thử | ADMIN của C gọi `GET /api/auth/subscription` | 200, plan TRIAL | `30-isolation-plan-expiry` / TC-PLAN-01.3 Doanh nghiệp mới nhận gói dùng thử | đạt |
| TC-PLAN-01.4 | Chuyển về gói Khởi Đầu | ADMIN của C gọi `POST /api/auth/subscription/downgrade` | 200 | `30-isolation-plan-expiry` / TC-PLAN-01.4 Chuyển về gói Khởi Đầu | đạt |
| TC-PLAN-01.5 | Thuê bao FREE với giới hạn đúng | ADMIN của C gọi `GET /api/auth/subscription` | 200, plan FREE, status ACTIVE, limits 3 / 1 / 200 / 20, apiAccess false | `30-isolation-plan-expiry` / TC-PLAN-01.5 Thuê bao FREE với giới hạn đúng | đạt |
| TC-PLAN-01.6 | Đang FREE không chuyển về FREE lần nữa | ADMIN của C gọi `POST /api/auth/subscription/downgrade` | 409 CONFLICT | `30-isolation-plan-expiry` / TC-PLAN-01.6 Đang FREE không chuyển về FREE lần nữa | đạt |

#### TC-PLAN-02 Giới hạn người dùng của gói FREE

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-02.1 | Tạo tài khoản thứ 2 (WAREHOUSE_MANAGER) | ADMIN của C gọi `POST /api/auth/users` | 201 | `30-isolation-plan-expiry` / TC-PLAN-02.1 Tạo tài khoản thứ 2 (WAREHOUSE_MANAGER) | đạt |
| TC-PLAN-02.2 | Tạo tài khoản thứ 3 (WAREHOUSE_STAFF) | ADMIN của C gọi `POST /api/auth/users` | 201 | `30-isolation-plan-expiry` / TC-PLAN-02.2 Tạo tài khoản thứ 3 (WAREHOUSE_STAFF) | đạt |
| TC-PLAN-02.3 | Tạo tài khoản thứ 4 bị chặn | ADMIN của C gọi `POST /api/auth/users` | 403 PLAN_LIMIT_EXCEEDED, data {limit USERS, current 3, max 3, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-02.3 Tạo tài khoản thứ 4 bị chặn | đạt |
| TC-PLAN-02.4 | Mức dùng người dùng là 3 | ADMIN của C gọi `GET /api/auth/subscription` | 200, usage.users 3 | `30-isolation-plan-expiry` / TC-PLAN-02.4 Mức dùng người dùng là 3 | đạt |
| TC-PLAN-02.5 | Đăng nhập WAREHOUSE_MANAGER của công ty C | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty C (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp C | `30-isolation-plan-expiry` / TC-PLAN-02.5 Đăng nhập WAREHOUSE_MANAGER của công ty C | đạt |

#### TC-PLAN-03 Giới hạn kho của gói FREE

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-03.1 | Tạo kho thứ nhất | ADMIN của C gọi `POST /api/inventory/warehouses` | 201 | `30-isolation-plan-expiry` / TC-PLAN-03.1 Tạo kho thứ nhất | đạt |
| TC-PLAN-03.2 | Tạo kho thứ hai bị chặn | ADMIN của C gọi `POST /api/inventory/warehouses` | 403 PLAN_LIMIT_EXCEEDED, data {limit WAREHOUSES, current 1, max 1, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-03.2 Tạo kho thứ hai bị chặn | đạt |

#### TC-PLAN-04 Giới hạn SKU của gói FREE

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-04.1 | Nhập hàng loạt 199 SKU | ADMIN của C gọi `POST /api/products/bulk`; body có 199 dòng QA-PL-001 đến QA-PL-199 | 200, created 199, failed 0 | `30-isolation-plan-expiry` / TC-PLAN-04.1 Nhập hàng loạt 199 SKU | đạt |
| TC-PLAN-04.2 | Lấy đơn vị tính tự tạo | ADMIN của C gọi `GET /api/products/uoms` | 200, có đơn vị Cái do nhập hàng loạt tạo | `30-isolation-plan-expiry` / TC-PLAN-04.2 Lấy đơn vị tính tự tạo | đạt |
| TC-PLAN-04.3 | Tạo SKU thứ 200 | ADMIN của C gọi `POST /api/products` | 201 | `30-isolation-plan-expiry` / TC-PLAN-04.3 Tạo SKU thứ 200 | đạt |
| TC-PLAN-04.4 | Tạo SKU thứ 201 bị chặn | ADMIN của C gọi `POST /api/products` | 403 PLAN_LIMIT_EXCEEDED, data {limit SKUS, current 200, max 200, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-04.4 Tạo SKU thứ 201 bị chặn | đạt |
| TC-PLAN-04.5 | Nhập hàng loạt vượt giới hạn SKU | ADMIN của C gọi `POST /api/products/bulk` | 200, failed 1, dòng FAILED kèm lý do vượt 200 SKU | `30-isolation-plan-expiry` / TC-PLAN-04.5 Nhập hàng loạt vượt giới hạn SKU | đạt |

#### TC-PLAN-05 Giới hạn lượt AI của gói FREE (AI_MOCK=true vẫn tính lượt)

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-05.1 | Mức dùng AI ban đầu | ADMIN của C gọi `GET /api/ai/usage` | 200, used 0, max 20, plan FREE | `30-isolation-plan-expiry` / TC-PLAN-05.1 Mức dùng AI ban đầu | đạt |
| TC-PLAN-05.2 | Hỏi trợ lý cho tới hết lượt | ADMIN của C gọi `POST /api/ai/ask`; lặp lại request tới khi usage.used = usage.max (tối đa 25 lần) | mỗi lần 200 và used tăng 1, dừng ở used = 20 | `30-isolation-plan-expiry` / TC-PLAN-05.2 Hỏi trợ lý cho tới hết lượt | đạt |
| TC-PLAN-05.3 | Lượt AI thứ 21 bị chặn | ADMIN của C gọi `POST /api/ai/ask` | 403 PLAN_LIMIT_EXCEEDED, data {limit AI_REQUESTS, current 20, max 20, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-05.3 Lượt AI thứ 21 bị chặn | đạt |
| TC-PLAN-05.4 | Trợ lý hội thoại cũng bị chặn khi hết lượt | ADMIN của C gọi `POST /api/ai/chat` | 403 PLAN_LIMIT_EXCEEDED, limit AI_REQUESTS | `30-isolation-plan-expiry` / TC-PLAN-05.4 Trợ lý hội thoại cũng bị chặn khi hết lượt | đạt |

#### TC-PLAN-06 Gói FREE không tạo được API key

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-06.1 | Tạo API key ở gói FREE | ADMIN của C gọi `POST /api/auth/api-keys` | 403 PLAN_LIMIT_EXCEEDED, data {limit API_ACCESS, current 0, max 0, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-06.1 Tạo API key ở gói FREE | đạt |

#### TC-EXP-01 Hết hạn: chỉ đọc

Cần APP_DEMO_TOOLS=true cho auth-service (qa/docker-compose.qa.yml).

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-EXP-01.1 | Đặt thuê bao của C hết hạn bằng công cụ demo | ADMIN của C gọi `POST /api/auth/subscription/demo/expire` | 200 (404 nghĩa là chưa bật APP_DEMO_TOOLS) | `30-isolation-plan-expiry` / TC-EXP-01.1 Đặt thuê bao của C hết hạn bằng công cụ demo | đạt |
| TC-EXP-01.2 | Thuê bao ở trạng thái hết hạn, chỉ đọc | ADMIN của C gọi `GET /api/auth/subscription` | 200, status EXPIRED, readOnly true | `30-isolation-plan-expiry` / TC-EXP-01.2 Thuê bao ở trạng thái hết hạn, chỉ đọc | đạt |
| TC-EXP-01.3 | Đọc hàng hóa vẫn được | ADMIN của C gọi `GET /api/products?size=5` | 200 | `30-isolation-plan-expiry` / TC-EXP-01.3 Đọc hàng hóa vẫn được | đạt |
| TC-EXP-01.4 | Đọc đơn vị tính vẫn được | Quản lý kho của C gọi `GET /api/products/uoms` | 200 | `30-isolation-plan-expiry` / TC-EXP-01.4 Đọc đơn vị tính vẫn được | đạt |
| TC-EXP-01.5 | Tạo hàng hóa khi hết hạn | ADMIN của C gọi `POST /api/products` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.5 Tạo hàng hóa khi hết hạn | đạt |
| TC-EXP-01.6 | Sửa kho khi hết hạn | ADMIN của C gọi `PUT /api/inventory/warehouses/{{C_wh1}}` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.6 Sửa kho khi hết hạn | đạt |
| TC-EXP-01.7 | Ngưng hàng hóa khi hết hạn | ADMIN của C gọi `DELETE /api/products/{{C_prod1}}` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.7 Ngưng hàng hóa khi hết hạn | đạt |
| TC-EXP-01.8 | Tạo nhà cung cấp khi hết hạn | Quản lý kho của C gọi `POST /api/orders/suppliers` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.8 Tạo nhà cung cấp khi hết hạn | đạt |
| TC-EXP-01.9 | Khóa tài khoản khi hết hạn | ADMIN của C gọi `PATCH /api/auth/users/{{uid_C_STAFF}}/status` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.9 Khóa tài khoản khi hết hạn | đạt |
| TC-EXP-01.10 | Hỏi AI khi hết hạn | ADMIN của C gọi `POST /api/ai/ask` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.10 Hỏi AI khi hết hạn | đạt |

#### TC-EXP-02 Thanh toán giả lập mở lại quyền ghi

Cần PAYOS_MOCK=true.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-EXP-02.1 | Tạo giao dịch gói PRO theo tháng | ADMIN của C gọi `POST /api/auth/payments` | 201, PENDING, amount 499000, checkoutUrl trỏ tới /payment/mock?orderCode= | `30-isolation-plan-expiry` / TC-EXP-02.1 Tạo giao dịch gói PRO theo tháng | đạt |
| TC-EXP-02.2 | Thiếu chu kỳ thanh toán | ADMIN của C gọi `POST /api/auth/payments` | 400 VALIDATION_ERROR, data.cycle | `30-isolation-plan-expiry` / TC-EXP-02.2 Thiếu chu kỳ thanh toán | đạt |
| TC-EXP-02.3 | Quản lý kho không được tạo thanh toán | Quản lý kho của C gọi `POST /api/auth/payments` | 403 FORBIDDEN | `30-isolation-plan-expiry` / TC-EXP-02.3 Quản lý kho không được tạo thanh toán | đạt |
| TC-EXP-02.4 | Giao dịch đang chờ | ADMIN của C gọi `GET /api/auth/payments/{{C_payOrder}}` | 200, PENDING | `30-isolation-plan-expiry` / TC-EXP-02.4 Giao dịch đang chờ | đạt |
| TC-EXP-02.5 | Xác nhận thanh toán giả lập | ADMIN của C gọi `POST /api/auth/payments/{{C_payOrder}}/mock-confirm` | 200, PAID, paidAt có giá trị, reference MOCK-<orderCode> | `30-isolation-plan-expiry` / TC-EXP-02.5 Xác nhận thanh toán giả lập | đạt |
| TC-EXP-02.6 | Gói PRO hoạt động 30 ngày | ADMIN của C gọi `GET /api/auth/subscription` | 200, plan PRO, status ACTIVE, readOnly false, daysRemaining 30, apiAccess true | `30-isolation-plan-expiry` / TC-EXP-02.6 Gói PRO hoạt động 30 ngày | đạt |
| TC-EXP-02.7 | Xác nhận lại không cộng thêm ngày | ADMIN của C gọi `POST /api/auth/payments/{{C_payOrder}}/mock-confirm` | 200, vẫn PAID | `30-isolation-plan-expiry` / TC-EXP-02.7 Xác nhận lại không cộng thêm ngày | đạt |
| TC-EXP-02.8 | Ngày hết hạn không đổi sau lần xác nhận thứ hai | ADMIN của C gọi `GET /api/auth/subscription` | 200, expiresAt bằng lần đọc trước | `30-isolation-plan-expiry` / TC-EXP-02.8 Ngày hết hạn không đổi sau lần xác nhận thứ hai | đạt |
| TC-EXP-02.9 | Không hủy được giao dịch đã trả | ADMIN của C gọi `POST /api/auth/payments/{{C_payOrder}}/cancel` | 409 CONFLICT | `30-isolation-plan-expiry` / TC-EXP-02.9 Không hủy được giao dịch đã trả | đạt |
| TC-EXP-02.10 | Ghi lại được ngay, không cần đăng nhập lại | ADMIN của C gọi `POST /api/inventory/warehouses`; dùng token cấp từ trước khi hết hạn | 201 (gói PRO cho 20 kho) | `30-isolation-plan-expiry` / TC-EXP-02.10 Ghi lại được ngay, không cần đăng nhập lại | đạt |
| TC-EXP-02.11 | Tạo SKU vượt 200 sau khi lên PRO | ADMIN của C gọi `POST /api/products` | 201 (PRO không giới hạn SKU) | `30-isolation-plan-expiry` / TC-EXP-02.11 Tạo SKU vượt 200 sau khi lên PRO | đạt |

#### TC-KEY-01 API key cho hệ thống ngoài (doanh nghiệp C gói PRO)

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-KEY-01.1 | ADMIN tạo API key | ADMIN của C gọi `POST /api/auth/api-keys` | 201, key dạng wms_ + 43 ký tự, keyPrefix là 8 ký tự đầu | `30-isolation-plan-expiry` / TC-KEY-01.1 ADMIN tạo API key | đạt |
| TC-KEY-01.2 | Tên key để trống | ADMIN của C gọi `POST /api/auth/api-keys` | 400 VALIDATION_ERROR | `30-isolation-plan-expiry` / TC-KEY-01.2 Tên key để trống | đạt |
| TC-KEY-01.3 | Quản lý kho không được tạo key | Quản lý kho của C gọi `POST /api/auth/api-keys` | 403 FORBIDDEN | `30-isolation-plan-expiry` / TC-KEY-01.3 Quản lý kho không được tạo key | đạt |
| TC-KEY-01.4 | Đọc tồn bằng API key | Hệ thống ngoài (API key) gọi `GET /api/inventory/stock/atp?productId={{C_prodPro}}` | 200, đúng productId | `30-isolation-plan-expiry` / TC-KEY-01.4 Đọc tồn bằng API key | đạt |
| TC-KEY-01.5 | Đọc danh mục hàng hóa bằng API key | Hệ thống ngoài (API key) gọi `GET /api/products?size=50` | 200, chỉ hàng của C (SKU QA-PL-...) | `30-isolation-plan-expiry` / TC-KEY-01.5 Đọc danh mục hàng hóa bằng API key | đạt |
| TC-KEY-01.6 | Đọc đơn bán bằng API key | Hệ thống ngoài (API key) gọi `GET /api/orders/sales` | 200 | `30-isolation-plan-expiry` / TC-KEY-01.6 Đọc đơn bán bằng API key | đạt |
| TC-KEY-01.7 | Key của C không đọc được hàng hóa của A | Hệ thống ngoài (API key) gọi `GET /api/products/{{A_prod}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-KEY-01.7 Key của C không đọc được hàng hóa của A | đạt |
| TC-KEY-01.8 | Ghi bằng API key bị từ chối | Hệ thống ngoài (API key) gọi `POST /api/products` | 403 FORBIDDEN | `30-isolation-plan-expiry` / TC-KEY-01.8 Ghi bằng API key bị từ chối | đạt |
| TC-KEY-01.9 | API key không gọi được API thuê bao | Hệ thống ngoài (API key) gọi `GET /api/auth/subscription` | 403 FORBIDDEN (ngoài /api/products, /api/inventory, /api/orders) | `30-isolation-plan-expiry` / TC-KEY-01.9 API key không gọi được API thuê bao | đạt |
| TC-KEY-01.10 | API key không gọi được AI | Hệ thống ngoài (API key) gọi `GET /api/ai/usage` | 403 FORBIDDEN | `30-isolation-plan-expiry` / TC-KEY-01.10 API key không gọi được AI | đạt |
| TC-KEY-01.11 | Key sai | Hệ thống ngoài (API key) gọi `GET /api/products` | 401 UNAUTHORIZED | `30-isolation-plan-expiry` / TC-KEY-01.11 Key sai | đạt |
| TC-KEY-01.12 | Danh sách key không lộ key đầy đủ | ADMIN của C gọi `GET /api/auth/api-keys` | 200, có key vừa tạo, active true, không có trường key | `30-isolation-plan-expiry` / TC-KEY-01.12 Danh sách key không lộ key đầy đủ | đạt |
| TC-KEY-01.13 | Thu hồi key | ADMIN của C gọi `DELETE /api/auth/api-keys/{{C_keyId}}` | 200, active false, revokedAt có giá trị | `30-isolation-plan-expiry` / TC-KEY-01.13 Thu hồi key | đạt |
| TC-KEY-01.14 | Thu hồi lần hai không đổi thời điểm | ADMIN của C gọi `DELETE /api/auth/api-keys/{{C_keyId}}` | 200, revokedAt giữ nguyên | `30-isolation-plan-expiry` / TC-KEY-01.14 Thu hồi lần hai không đổi thời điểm | đạt |
| TC-KEY-01.15 | Key đã thu hồi bị từ chối | Hệ thống ngoài (API key) gọi `GET /api/products` | 401 UNAUTHORIZED | `30-isolation-plan-expiry` / TC-KEY-01.15 Key đã thu hồi bị từ chối | đạt |
| TC-KEY-01.16 | Tạo key thứ hai để thử giới hạn tốc độ | ADMIN của C gọi `POST /api/auth/api-keys` | 201 | `30-isolation-plan-expiry` / TC-KEY-01.16 Tạo key thứ hai để thử giới hạn tốc độ | đạt |
| TC-KEY-01.17 | Gửi dồn 40 request trong một giây bằng một key | Hệ thống ngoài (API key) gọi `GET /api/products?size=1`; script gửi song song thêm 40 request cùng key bằng pm.sendRequest | ít nhất một phản hồi 429 TOO_MANY_REQUESTS kèm header Retry-After: 1 | `30-isolation-plan-expiry` / TC-KEY-01.17 Gửi dồn 40 request trong một giây bằng một key | đạt |
| TC-KEY-01.18 | Thu hồi key thứ hai | ADMIN của C gọi `DELETE /api/auth/api-keys/{{C_key2Id}}` | 200 | `30-isolation-plan-expiry` / TC-KEY-01.18 Thu hồi key thứ hai | đạt |

## 6. WMS-19.2 Tải đồng thời và bất biến tồn kho (qa/load/concurrency.mjs)

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-LOAD-01.1 | Chuẩn bị doanh nghiệp, hàng hóa và tồn đã biết | Đăng ký doanh nghiệp mới, tạo kho, vị trí, hàng hóa, nhà cung cấp, khách hàng; đơn mua `--initial-stock` (mặc định 300), duyệt, nhập đủ; chờ `GET /api/inventory/stock/atp` đạt số tồn | Tồn thực tế bằng số đã nhập, giữ chỗ 0 | `qa/load/concurrency.mjs` / phần `prepare()` | đạt |
| TC-LOAD-01.2 | Tải hỗn hợp 40 request/giây trong 30 giây | Bắn lịch đều `--rps` request/giây trong `--duration` giây, trộn: lập đơn bán nháp, xác nhận đơn (giữ cùng một tồn), lập phiếu xuất cho đơn đã xác nhận, đọc tồn, danh sách đơn bán, lô, phiếu xuất | Không có HTTP 5xx; từ chối nghiệp vụ là 400 (thiếu tồn khả dụng, xuất vượt) hoặc 409 | `qa/load/concurrency.mjs` / chế độ `load` | đạt: 1.200 thao tác trong 30,3 giây, không có HTTP 5xx, không có lỗi mạng |
| TC-LOAD-01.3 | Tồn thực tế không âm, giữ chỗ không vượt tồn | Sau khi tải xong, chờ sự kiện xử lý xong (giữ chỗ và tồn ổn định 3 lần đọc liên tiếp), đọc ATP và từng lô | onHand >= 0, reserved >= 0, reserved <= onHand ở tổng và ở từng lô | `qa/load/concurrency.mjs` / bất biến `I1`, `I2` | đạt |
| TC-LOAD-01.4 | Tổng đã xuất không vượt tồn đã nhập | Cộng `totalQuantity` của mọi phiếu xuất của kho | Tổng đã xuất <= tồn đã nhập | `qa/load/concurrency.mjs` / bất biến `I3` | đạt (xuất 300 trên 300 đã nhập) |
| TC-LOAD-01.5 | Mỗi phiếu xuất trừ tồn đúng một lần | So tồn hiện tại với tồn đã nhập trừ tổng đã xuất | onHand = tồn đã nhập - tổng đã xuất | `qa/load/concurrency.mjs` / bất biến `I4` | đạt |
| TC-LOAD-01.6 | Đơn đã xác nhận được giữ đủ hàng | Cộng số lượng các đơn bán đang CONFIRMED (chưa xuất), so với giữ chỗ và tồn | Tổng cam kết = giữ chỗ và <= tồn thực tế (không bán vượt tồn khi xác nhận đồng thời) | `qa/load/concurrency.mjs` / bất biến `I5`, `I6` | đạt |
| TC-LOAD-01.7 | Sổ kho khớp tồn sau tải | `GET /api/inventory/reports/in-out-balance` cho sản phẩm và kho trong ngày | Nhập = tồn đã nhập, xuất = tổng đã xuất, tồn cuối = onHand | `qa/load/concurrency.mjs` / bất biến `I7` | đạt |
| TC-LOAD-01.8 | Không lỗi máy chủ, không deadlock | Đếm phản hồi 5xx, lỗi mạng, phản hồi có chữ deadlock hoặc lock wait | Bằng 0 (xem thêm log: `docker compose logs inventory-service order-service \\| grep -i deadlock`) | `qa/load/concurrency.mjs` / bất biến `I8`, `I9` | đạt |
| TC-LOAD-01.9 | Độ trễ theo API | In p50, p95, p99, lớn nhất theo từng API | Báo cáo; SRS yêu cầu API nghiệp vụ trả lời trong 500 ms (cột NFR in "vượt" khi p95 > 500 ms, không làm thất bại lượt chạy trừ khi đặt `--max-p95-ms`) | `qa/load/concurrency.mjs` / bảng độ trễ | đạt ở 20 thao tác/giây (p95 cao nhất 150 ms); KHÔNG ĐẠT ở 40 thao tác/giây: p95 các API ghi của order-service khoảng 2,5 giây (mục 2.3) |
| TC-LOAD-02.1 | Nộp trùng song song phiếu nhập nhận đủ phần còn lại | Đơn mua 50, duyệt; gửi đồng thời hai `POST /api/orders/goods-receipt-notes` nhận đủ 50 | Đúng một 201, một 400; tồn tăng đúng 50 | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G1` | đạt |
| TC-LOAD-02.2 | Nộp trùng song song cùng một phiếu nhập từng phần | Đơn mua 100; gửi đồng thời hai lần cùng body và cùng header `Idempotency-Key` nhận 30 (như form nhập kho) | Chỉ một lần được ghi (một 201, lần còn lại 200 trả lại đúng phiếu đã lập), tồn tăng đúng 30 | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G2` | đạt |
| TC-LOAD-02.3 | Xác nhận đồng thời cùng một đơn bán | Đơn bán nháp 20; gửi đồng thời hai `POST /api/orders/sales/{id}/confirm` | Giữ chỗ tăng đúng 20, không lỗi 5xx (`G3`); chỉ một lần thành công, lần sau 400 (`G3b`) | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G3`, `G3b` | đạt |
| TC-LOAD-02.4 | Nộp trùng song song phiếu xuất đủ số lượng | Đơn bán 20 đã xác nhận; gửi đồng thời hai `POST /api/orders/goods-delivery-notes` xuất 20 | Một 201, một 400; tồn giảm đúng 20, giữ chỗ về 0 | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G4` | đạt |
| TC-LOAD-02.5 | Bấm xác nhận xuất hai lần cùng lúc | Phiếu xuất nháp (status DRAFT) 10; gửi đồng thời hai `POST /api/orders/goods-delivery-notes/{id}/dispatch` | Cả hai 200 nhưng tồn chỉ giảm 10 | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G5` | đạt |

## 7. Kiểm tra thủ công (không tự động hóa được ở đây)

| ID | Nội dung | Các bước | Kết quả mong đợi | Kết quả |
| --- | --- | --- | --- | --- |
| TC-MAN-01 | Đăng ký trên giao diện và kích hoạt qua email thật | Stack để mặc định `REGISTRATION_REQUIRE_ACTIVATION=true`, cấu hình SMTP thật (`MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`); đăng ký ở `/register`, mở email, bấm liên kết `/activate`, đăng nhập; bấm liên kết lần hai | Chưa kích hoạt thì đăng nhập nhận thông báo COMPANY_INACTIVE kèm gửi lại email; kích hoạt xong đăng nhập được; liên kết lần hai báo không dùng được | chưa chạy |
| TC-MAN-02 | Quên mật khẩu qua email thật | `/forgot-password` với email đúng và email không tồn tại; mở email, đặt mật khẩu mới | Hai trường hợp cùng thông báo; mật khẩu mới dùng được, mật khẩu cũ không | chưa chạy |
| TC-MAN-03 | Thanh toán PayOS thật | Trên máy chủ `PAYOS_MOCK=false` với khóa thật: hết hạn bằng công cụ demo, chọn gói PRO, quét QR chuyển khoản, chờ webhook | Trang `/payment/result` báo thành công, gói mở lại, giao dịch PAID; gửi lại webhook không cộng thêm ngày | chưa chạy |
| TC-MAN-04 | Giao diện chỉ đọc khi hết hạn | Đặt hết hạn bằng công cụ demo, mở các màn hình tạo, sửa, xóa | Dải thông báo chỉ đọc, nút ghi bị khóa, phản hồi SUBSCRIPTION_EXPIRED mở hộp thoại dẫn tới `/subscription` | chưa chạy |
| TC-MAN-05 | Hộp thoại vượt giới hạn gói | Ở gói FREE tạo kho thứ hai, tài khoản thứ tư, SKU thứ 201 trên giao diện | Hộp thoại nêu đúng giới hạn, đang dùng, tối đa, có nút nâng cấp | chưa chạy |
| TC-MAN-06 | Claude thật: trích xuất và trợ lý | `AI_MOCK=false` có `ANTHROPIC_API_KEY`: tải file Excel 50 dòng, PDF, ảnh ở `/ai-import`; hỏi trợ lý "tạo kho thế nào", hỏi tồn một SKU, người dùng B hỏi SKU chỉ có ở A | Trích xuất đúng trên 90% số ô dưới 30 giây; trợ lý trả lời đúng số tồn, B nhận "không tìm thấy" | chưa chạy |
| TC-MAN-07 | Kênh WebSocket theo doanh nghiệp | Đăng nhập A và B ở hai trình duyệt, A lập đơn bán; dùng công cụ STOMP đăng ký kênh `/topic/company/{id của A}/orders` bằng token của B | Chỉ A nhận thông báo; B bị từ chối khi đăng ký kênh của A | chưa chạy |
| TC-MAN-08 | Màn hình kiểm kê đếm mù và đối soát | Quét mã vạch bằng máy quét trên màn hình đếm, đối soát chênh lệch, duyệt hai cấp trên giao diện | Màn hình đếm không hiển thị số hệ thống; nút duyệt theo đúng vai trò | chưa chạy |
| TC-MAN-09 | Email cảnh báo tồn kho | Có SMTP thật, chạy quét cảnh báo (`POST /api/inventory/alerts/scan` bằng ADMIN) khi có lô cận hạn, tồn thấp | Chỉ người dùng của doanh nghiệp đó nhận email | chưa chạy |
| TC-MAN-10 | Khóa đăng nhập sau 5 lần sai | Nhập sai mật khẩu 5 lần cho một tài khoản thử (không dùng tài khoản của bộ Postman vì khóa theo IP 30 phút) | Lần thứ 6 báo bị khóa 30 phút | chưa chạy |
| TC-MAN-11 | Xuất Excel báo cáo, giao diện điện thoại | Xuất báo cáo xuất nhập tồn, thẻ kho; mở các màn hình chính ở bề rộng 375 px | File mở được, số liệu khớp màn hình; bố cục không vỡ | chưa chạy |
| TC-MAN-12 | Chuyển hết hạn bằng tác vụ định kỳ | Sửa `trial_ends_at` của một doanh nghiệp thử về quá khứ trong CSDL, chờ tối đa 5 phút không đăng nhập lại | Thuê bao chuyển EXPIRED, gateway chặn ghi | chưa chạy |

## 8. Truy vết yêu cầu

Mã FR theo danh sách yêu cầu chức năng ở `docs/PROPOSAL_PHAN_CONG_PHAM_VI.md` mục 2 (SRS `docs/project-management/srs`
dùng mã UC và tham chiếu các FR này); phần SaaS theo `docs/SAAS_SPEC.md`.

| Yêu cầu | Test case |
| --- | --- |
| FR01 Đăng nhập, token | TC-SET-03, TC-IN-00, TC-OUT-00, TC-ISO-00 |
| FR02 Sáu vai trò, gán kho, lọc theo kho | TC-SET-04, TC-SET-05, TC-IN-03.11 đến TC-IN-03.22, TC-PREP-20.11 đến TC-PREP-20.22; các ca từ chối 403 ở mọi nhóm (TC-IN-01.5, 01.8, 02.7, 02.8, 03.4, 03.9, 04.5, 04.6, 05.5, 05.7, 05.8, 06.8, 08.7, 08.8, TC-OUT-02.3, 02.4, 03.2, 04.1, TC-TRF-01.2, 01.5, TC-STK-01.4, 01.14, 01.21, TC-RPT-01.7, 02.3) |
| FR03 Quản lý đa kho | TC-IN-03.1 đến TC-IN-03.4, TC-PREP-20.5, TC-PREP-20.6 |
| FR04 Vị trí Kho-Khu-Kệ-Ô, mã duy nhất | TC-IN-03.5 đến TC-IN-03.10, TC-IN-07.11 |
| FR05 Danh mục hàng hóa, cờ lô và hạn dùng | TC-IN-02 |
| FR06 Đơn vị tính | TC-IN-01.1 đến TC-IN-01.5, TC-IN-01.9 |
| FR08 Điều chuyển nội bộ, thiếu hỏng | TC-TRF-01, TC-STK-01.1, TC-STK-01.2, TC-STK-01.28 |
| FR09 Hồ sơ nhà cung cấp | TC-IN-04 |
| FR10 Đơn mua, phê duyệt, tăng Incoming | TC-IN-05 |
| FR11 Phiếu nhập theo đơn đã duyệt, nhập từng phần, giảm Incoming, đóng đơn | TC-IN-06, TC-IN-07 |
| FR12 Lô, hạn dùng, FEFO, lô hết hạn | TC-IN-06.11, TC-IN-07.8, TC-PREP-20.30 đến TC-PREP-20.33, TC-OUT-02.7, TC-OUT-03.1 |
| FR13 Hóa đơn mua, công nợ phải trả, thanh toán | TC-IN-08 |
| FR14 Khách hàng, hạn mức công nợ | TC-OUT-01 |
| FR15 Đơn bán kiểm tra ATP | TC-OUT-02.1, TC-OUT-02.9 đến TC-OUT-02.11 |
| FR16 Giữ hàng mềm khi xác nhận | TC-OUT-02.2, TC-OUT-02.5 đến TC-OUT-02.8, TC-OUT-02.13, TC-LOAD-01.6, TC-LOAD-02.3 |
| FR17 Picking list, phiếu xuất trừ OnHand, giải phóng Reserved | TC-OUT-03, TC-OUT-04, TC-LOAD-01.5, TC-LOAD-02.4, TC-LOAD-02.5 |
| FR19 Kiểm kê: đóng băng, snapshot, đếm mù, chênh lệch | TC-STK-01.3 đến TC-STK-01.18, TC-STK-02.1 đến TC-STK-02.5 |
| FR20 Điều chỉnh, duyệt Quản lý kho và Kế toán, mở đóng băng | TC-STK-01.19 đến TC-STK-01.28, TC-STK-02.6 đến TC-STK-02.10 |
| FR21 Cảnh báo tồn kho (phần cách ly) | TC-ISO-19 |
| FR23 KPI (phần cách ly) | TC-ISO-20 |
| FR25 Xuất nhập tồn, thẻ kho, truy vết lô | TC-IN-08.13, TC-RPT-01, TC-RPT-02 |
| SRS UC-09 luồng 1a: nhập, xuất vào vị trí FROZEN bị từ chối | TC-STK-01.8, TC-STK-01.9, TC-STK-01.10 |
| SRS yêu cầu phi chức năng: API nghiệp vụ trả lời trong 500 ms, trừ tồn chịu 30 đến 50 request đồng thời | TC-LOAD-01, TC-LOAD-02 |
| SAAS_SPEC 2.3 Tách dữ liệu theo doanh nghiệp (SRS UC-11) | TC-ISO-01 đến TC-ISO-25 |
| SAAS_SPEC 2.5, WMS-23.2 Giới hạn gói | TC-SET-01.2, TC-PLAN-01 đến TC-PLAN-06 |
| SAAS_SPEC 2.6, WMS-23.1, WMS-23.2 Vòng đời thuê bao, chỉ đọc khi hết hạn | TC-SET-03.3, TC-PLAN-01, TC-EXP-01 |
| WMS-23.5a Thanh toán PayOS (giả lập) | TC-EXP-02, TC-ISO-22 |
| WMS-23.6 Đăng ký, kích hoạt (tắt trên stack kiểm thử) | TC-SET-02; luồng kích hoạt thật ở mục kiểm tra thủ công |
| WMS-24.1, WMS-24.2, WMS-24.4 AI (giả lập), lượt AI (SRS UC-12) | TC-ISO-23, TC-ISO-24, TC-PLAN-05 |
| WMS-25.1 API key (SRS UC-13) | TC-KEY-01, TC-ISO-21, TC-PLAN-06 |
