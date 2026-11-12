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
- Cột "Kết quả" để "chưa chạy"; người chạy ghi "đạt" hoặc "không đạt" kèm mã lỗi, ảnh hoặc dòng log của Postman CLI.
- Số lượng request hiện có: `00-setup.postman_collection.json`: 20 request, `10-inbound.postman_collection.json`: 102 request, `20-outbound-transfer-stocktake.postman_collection.json`: 137 request, `30-isolation-plan-expiry.postman_collection.json`: 219 request.

## 2. Hành vi dự kiến không đạt (lỗi cần sửa trước)

Đọc mã nguồn cho thấy các yêu cầu sau chưa được đáp ứng; test tương ứng được giữ nguyên theo yêu cầu nên sẽ báo không đạt
cho tới khi sửa:

| Test case | Hành vi | Nguyên nhân trong mã và hướng sửa |
| --- | --- | --- |
| TC-STK-01.10 | Lập phiếu nhập vào vị trí đang kiểm kê trả 201 thay vì 409 | order-service `GoodsReceiptService.create` không hỏi inventory-service; `StockReceiptService.processOrderReceived` mới kiểm tra đóng băng ở luồng nền, ném 409 trong listener, message thử lại 3 lần rồi vào dead-letter. Hậu quả: phiếu nhập, hóa đơn mua, công nợ nhà cung cấp đã ghi nhưng tồn không tăng (lệch sổ). Sửa: order-service gọi inventory kiểm tra vị trí trước khi lưu phiếu, hoặc lập phiếu ở trạng thái chờ và chỉ hoàn tất khi inventory xác nhận. |
| TC-LOAD-02.2 | Nộp trùng cùng một phiếu nhập từng phần cộng tồn hai lần | API lập phiếu nhập không có khóa chống trùng (idempotency key); hai request giống nhau với số lượng còn trong phần còn lại của đơn đều được ghi (khóa bi quan trên đơn mua chỉ chặn được khi tổng vượt số còn lại). Sửa: nhận header `Idempotency-Key` hoặc mã phiếu do client sinh, ràng buộc duy nhất theo doanh nghiệp. |
| TC-LOAD-02.3 | Hai lần xác nhận cùng một đơn bán có thể cùng thành công (G3b) | `SalesOrderService.confirm` đọc đơn bằng `findById` (không `findByIdForUpdate`, entity không có `@Version`): hai request đồng thời cùng thấy DRAFT, cùng trả 200 và phát hai sự kiện `order.created`. Giữ chỗ vẫn một lần (`reserveStock` bỏ qua đơn đã có giữ chỗ ACTIVE) nhưng KPI đơn bán có thể đếm hai lần. Sửa: khóa dòng đơn bán khi xác nhận như `GoodsDeliveryService` đang làm. |
| TC-LOAD-01.6 | Xác nhận đơn bán đồng thời có thể bán vượt tồn | `SalesOrderService.confirm` kiểm tra ATP đồng bộ nhưng giữ chỗ chạy sau qua sự kiện `order.created`; hai lần xác nhận đồng thời cùng đọc một ATP. `StockReservationService.reserveStock` giữ phần còn lại được và chỉ ghi cảnh báo khi thiếu, đơn vẫn CONFIRMED. Dự kiến thấy tổng cam kết lớn hơn giữ chỗ khi tải 40 request/giây. |
| TC-LOAD-01.5 | Phiếu xuất không kiểm tra tồn, trừ tồn chạy nền | `GoodsDeliveryService.create` lưu phiếu COMPLETED rồi phát `order.dispatched`; nếu khi xử lý không đủ tồn (do bán vượt ở dòng trên), `StockDeductionService` ném lỗi, message vào dead-letter, phiếu xuất vẫn COMPLETED nhưng tồn không giảm. Chỉ xảy ra khi TC-LOAD-01.6 đã sai. |

Các điểm khác ghi nhận khi đọc mã (không có test khẳng định vì yêu cầu chưa chốt):

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
| TC-SET-01.1 | Gateway trả trạng thái sức khỏe | Khách gọi `GET /actuator/health` | 200, status = UP | `00-setup` / TC-SET-01.1 Gateway trả trạng thái sức khỏe | chưa chạy |
| TC-SET-01.2 | Danh sách gói dịch vụ công khai | Khách gọi `GET /api/auth/plans` | 200, có ba gói FREE, TRIAL, PRO; FREE: 3 người dùng, 1 kho, 200 SKU, 20 lượt AI, không có API key | `00-setup` / TC-SET-01.2 Danh sách gói dịch vụ công khai | chưa chạy |

#### TC-SET-02 Đăng ký doanh nghiệp A và B

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-SET-02.1 | Đăng ký doanh nghiệp A | Gọi `POST /api/auth/companies/register` với tên, mã số thuế, email, tên đăng nhập ngẫu nhiên theo runId cho doanh nghiệp A | 201; activationRequired = false (stack kiểm thử tắt kích hoạt qua email); company.status = ACTIVE; subscription.plan = TRIAL | `00-setup` / TC-SET-02.1 Đăng ký doanh nghiệp A | chưa chạy |
| TC-SET-02.2 | Đăng ký doanh nghiệp B | Gọi `POST /api/auth/companies/register` với tên, mã số thuế, email, tên đăng nhập ngẫu nhiên theo runId cho doanh nghiệp B | 201; activationRequired = false (stack kiểm thử tắt kích hoạt qua email); company.status = ACTIVE; subscription.plan = TRIAL | `00-setup` / TC-SET-02.2 Đăng ký doanh nghiệp B | chưa chạy |
| TC-SET-02.3 | Đăng ký lại cùng tên đăng nhập bị từ chối | Gọi `POST /api/auth/companies/register` với tên đăng nhập của ADMIN A đã có | 409 CONFLICT, không tạo doanh nghiệp mới | `00-setup` / TC-SET-02.3 Đăng ký lại cùng tên đăng nhập bị từ chối | chưa chạy |

#### TC-SET-03 Đăng nhập ADMIN và thuê bao dùng thử

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-SET-03.1 | Đăng nhập ADMIN của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-03.1 Đăng nhập ADMIN của công ty A | chưa chạy |
| TC-SET-03.2 | Đăng nhập ADMIN của công ty B | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty B (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp B | `00-setup` / TC-SET-03.2 Đăng nhập ADMIN của công ty B | chưa chạy |
| TC-SET-03.3 | Thuê bao của A sau đăng ký | ADMIN của A gọi `GET /api/auth/subscription` | 200, plan TRIAL, status ACTIVE, daysRemaining 30, readOnly false, usage.users = 1 | `00-setup` / TC-SET-03.3 Thuê bao của A sau đăng ký | chưa chạy |

#### TC-SET-04 Tạo tài khoản các vai trò của A

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-SET-04.1 | ADMIN của A tạo tài khoản WAREHOUSE_MANAGER | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [WAREHOUSE_MANAGER], companyId = A | `00-setup` / TC-SET-04.1 ADMIN của A tạo tài khoản WAREHOUSE_MANAGER | chưa chạy |
| TC-SET-04.2 | ADMIN của A tạo tài khoản WAREHOUSE_STAFF | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [WAREHOUSE_STAFF], companyId = A | `00-setup` / TC-SET-04.2 ADMIN của A tạo tài khoản WAREHOUSE_STAFF | chưa chạy |
| TC-SET-04.3 | ADMIN của A tạo tài khoản PURCHASING | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [PURCHASING], companyId = A | `00-setup` / TC-SET-04.3 ADMIN của A tạo tài khoản PURCHASING | chưa chạy |
| TC-SET-04.4 | ADMIN của A tạo tài khoản SALES | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [SALES], companyId = A | `00-setup` / TC-SET-04.4 ADMIN của A tạo tài khoản SALES | chưa chạy |
| TC-SET-04.5 | ADMIN của A tạo tài khoản ACCOUNTANT | ADMIN của A gọi `POST /api/auth/users` | 201, tài khoản bật, roles = [ACCOUNTANT], companyId = A | `00-setup` / TC-SET-04.5 ADMIN của A tạo tài khoản ACCOUNTANT | chưa chạy |

#### TC-SET-05 Đăng nhập các vai trò của A

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-SET-05.1 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.1 Đăng nhập WAREHOUSE_MANAGER của công ty A | chưa chạy |
| TC-SET-05.2 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.2 Đăng nhập WAREHOUSE_STAFF của công ty A | chưa chạy |
| TC-SET-05.3 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.3 Đăng nhập PURCHASING của công ty A | chưa chạy |
| TC-SET-05.4 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.4 Đăng nhập SALES của công ty A | chưa chạy |
| TC-SET-05.5 | Đăng nhập ACCOUNTANT của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ACCOUNTANT của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ACCOUNTANT; company.id đúng doanh nghiệp A | `00-setup` / TC-SET-05.5 Đăng nhập ACCOUNTANT của công ty A | chưa chạy |
| TC-SET-05.6 | Kế toán không được tạo tài khoản | Kế toán của A gọi `POST /api/auth/users` | 403 FORBIDDEN (chỉ ADMIN quản lý người dùng) | `00-setup` / TC-SET-05.6 Kế toán không được tạo tài khoản | chưa chạy |
| TC-SET-05.7 | Tra ID tài khoản WAREHOUSE_MANAGER của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_MANAGER vừa tạo | `00-setup` / TC-SET-05.7 Tra ID tài khoản WAREHOUSE_MANAGER của A | chưa chạy |

### 3.2. Mua và nhập kho (10-inbound)

#### TC-IN-00 Đăng nhập

Đăng nhập lại các tài khoản của A tạo ở 00-setup.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-00.1 | Kiểm tra runId của lượt chạy | Kiểm tra biến môi trường `runId` đã được truyền (`--env-var runId=...`), rồi gọi `GET /api/auth/plans` | runId có giá trị (cùng runId đã dùng cho 00-setup); API công khai trả 200 | `10-inbound` / TC-IN-00.1 Kiểm tra runId của lượt chạy | chưa chạy |
| TC-IN-00.2 | Đăng nhập ADMIN của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.2 Đăng nhập ADMIN của công ty A | chưa chạy |
| TC-IN-00.3 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.3 Đăng nhập WAREHOUSE_MANAGER của công ty A | chưa chạy |
| TC-IN-00.4 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.4 Đăng nhập WAREHOUSE_STAFF của công ty A | chưa chạy |
| TC-IN-00.5 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.5 Đăng nhập PURCHASING của công ty A | chưa chạy |
| TC-IN-00.6 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.6 Đăng nhập SALES của công ty A | chưa chạy |
| TC-IN-00.7 | Đăng nhập ACCOUNTANT của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ACCOUNTANT của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ACCOUNTANT; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-00.7 Đăng nhập ACCOUNTANT của công ty A | chưa chạy |

#### TC-IN-01 Đơn vị tính và danh mục

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-01.1 | Tạo đơn vị tính Cái | ADMIN của A gọi `POST /api/products/uoms` | 201, code = CAI | `10-inbound` / TC-IN-01.1 Tạo đơn vị tính Cái | chưa chạy |
| TC-IN-01.2 | Tạo đơn vị tính Thùng (Mua hàng) | Mua hàng của A gọi `POST /api/products/uoms` | 201 (vai trò Mua hàng được tạo đơn vị tính) | `10-inbound` / TC-IN-01.2 Tạo đơn vị tính Thùng (Mua hàng) | chưa chạy |
| TC-IN-01.3 | Trùng mã đơn vị tính bị từ chối | ADMIN của A gọi `POST /api/products/uoms` | 409 CONFLICT (mã chuẩn hóa thành CAI đã có) | `10-inbound` / TC-IN-01.3 Trùng mã đơn vị tính bị từ chối | chưa chạy |
| TC-IN-01.4 | Mã đơn vị tính sai mẫu | ADMIN của A gọi `POST /api/products/uoms` | 400 VALIDATION_ERROR, data.code có lỗi | `10-inbound` / TC-IN-01.4 Mã đơn vị tính sai mẫu | chưa chạy |
| TC-IN-01.5 | Bán hàng không được tạo đơn vị tính | Bán hàng của A gọi `POST /api/products/uoms` | 403 FORBIDDEN | `10-inbound` / TC-IN-01.5 Bán hàng không được tạo đơn vị tính | chưa chạy |
| TC-IN-01.6 | Tạo danh mục Sữa | ADMIN của A gọi `POST /api/products/categories` | 201, code = QA-SUA | `10-inbound` / TC-IN-01.6 Tạo danh mục Sữa | chưa chạy |
| TC-IN-01.7 | Danh mục thiếu tên | ADMIN của A gọi `POST /api/products/categories` | 400 VALIDATION_ERROR, data.name | `10-inbound` / TC-IN-01.7 Danh mục thiếu tên | chưa chạy |
| TC-IN-01.8 | Kế toán không được tạo danh mục | Kế toán của A gọi `POST /api/products/categories` | 403 FORBIDDEN | `10-inbound` / TC-IN-01.8 Kế toán không được tạo danh mục | chưa chạy |
| TC-IN-01.9 | Nhân viên kho xem danh sách đơn vị tính | Nhân viên kho của A gọi `GET /api/products/uoms` | 200, có CAI và THUNG, chỉ của doanh nghiệp A (doanh nghiệp mới bắt đầu rỗng) | `10-inbound` / TC-IN-01.9 Nhân viên kho xem danh sách đơn vị tính | chưa chạy |

#### TC-IN-02 Hàng hóa

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-02.1 | Tạo hàng hóa quản lý theo lô và hạn dùng | Mua hàng của A gọi `POST /api/products` | 201, manageByLot = true, manageByExpiration = true, uomCode = THUNG | `10-inbound` / TC-IN-02.1 Tạo hàng hóa quản lý theo lô và hạn dùng | chưa chạy |
| TC-IN-02.2 | Tạo hàng hóa không theo lô | Nhân viên kho của A gọi `POST /api/products` | 201 (nhân viên kho được thêm hàng hóa), manageByLot = false | `10-inbound` / TC-IN-02.2 Tạo hàng hóa không theo lô | chưa chạy |
| TC-IN-02.3 | Trùng SKU trong doanh nghiệp | ADMIN của A gọi `POST /api/products` | 409 CONFLICT | `10-inbound` / TC-IN-02.3 Trùng SKU trong doanh nghiệp | chưa chạy |
| TC-IN-02.4 | SKU chứa ký tự không hợp lệ | ADMIN của A gọi `POST /api/products` | 400 VALIDATION_ERROR, data.sku | `10-inbound` / TC-IN-02.4 SKU chứa ký tự không hợp lệ | chưa chạy |
| TC-IN-02.5 | Hàng hóa thiếu đơn vị tính | ADMIN của A gọi `POST /api/products` | 400 VALIDATION_ERROR, data.uomId | `10-inbound` / TC-IN-02.5 Hàng hóa thiếu đơn vị tính | chưa chạy |
| TC-IN-02.6 | Giá nhập âm | ADMIN của A gọi `POST /api/products` | 400 VALIDATION_ERROR, data.purchasePrice | `10-inbound` / TC-IN-02.6 Giá nhập âm | chưa chạy |
| TC-IN-02.7 | Bán hàng không được tạo hàng hóa | Bán hàng của A gọi `POST /api/products` | 403 FORBIDDEN | `10-inbound` / TC-IN-02.7 Bán hàng không được tạo hàng hóa | chưa chạy |
| TC-IN-02.8 | Nhân viên kho không được ngưng hàng hóa | Nhân viên kho của A gọi `DELETE /api/products/{{in_prodPlain}}` | 403 FORBIDDEN (chỉ ADMIN, Quản lý kho) | `10-inbound` / TC-IN-02.8 Nhân viên kho không được ngưng hàng hóa | chưa chạy |
| TC-IN-02.9 | Tra hàng hóa theo SKU | Bán hàng của A gọi `GET /api/products/sku/QA-SUA-01` | 200, đúng ID vừa tạo, đang hoạt động | `10-inbound` / TC-IN-02.9 Tra hàng hóa theo SKU | chưa chạy |

#### TC-IN-03 Kho, khu và vị trí

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-03.1 | Tạo kho QA-WH1 | ADMIN của A gọi `POST /api/inventory/warehouses` | 201, code = QA-WH1, active = true | `10-inbound` / TC-IN-03.1 Tạo kho QA-WH1 | chưa chạy |
| TC-IN-03.2 | Trùng mã kho | ADMIN của A gọi `POST /api/inventory/warehouses` | 409 CONFLICT | `10-inbound` / TC-IN-03.2 Trùng mã kho | chưa chạy |
| TC-IN-03.3 | Mã kho sai mẫu | ADMIN của A gọi `POST /api/inventory/warehouses` | 400 VALIDATION_ERROR, data.code | `10-inbound` / TC-IN-03.3 Mã kho sai mẫu | chưa chạy |
| TC-IN-03.4 | Quản lý kho không được tạo kho | Quản lý kho của A gọi `POST /api/inventory/warehouses` | 403 FORBIDDEN | `10-inbound` / TC-IN-03.4 Quản lý kho không được tạo kho | chưa chạy |
| TC-IN-03.5 | Tạo hàng loạt vị trí khu A | ADMIN của A gọi `POST /api/inventory/locations/bulk` | 201, ba vị trí QA-WH1-A-R01-B01 đến B03 | `10-inbound` / TC-IN-03.5 Tạo hàng loạt vị trí khu A | chưa chạy |
| TC-IN-03.6 | Tạo vị trí khu B | ADMIN của A gọi `POST /api/inventory/locations` | 201, code = QA-WH1-B-R01-B01 (viết hoa), status EMPTY | `10-inbound` / TC-IN-03.6 Tạo vị trí khu B | chưa chạy |
| TC-IN-03.7 | Trùng vị trí | ADMIN của A gọi `POST /api/inventory/locations` | 409 CONFLICT | `10-inbound` / TC-IN-03.7 Trùng vị trí | chưa chạy |
| TC-IN-03.8 | Vị trí thiếu khu | ADMIN của A gọi `POST /api/inventory/locations` | 400 VALIDATION_ERROR, data.zone | `10-inbound` / TC-IN-03.8 Vị trí thiếu khu | chưa chạy |
| TC-IN-03.9 | Nhân viên kho không được tạo vị trí | Nhân viên kho của A gọi `POST /api/inventory/locations` | 403 FORBIDDEN | `10-inbound` / TC-IN-03.9 Nhân viên kho không được tạo vị trí | chưa chạy |
| TC-IN-03.10 | Danh sách khu của kho | ADMIN của A gọi `GET /api/inventory/locations/zones?warehouseId={{in_wh}}` | 200, [A, B] | `10-inbound` / TC-IN-03.10 Danh sách khu của kho | chưa chạy |
| TC-IN-03.11 | Tra ID tài khoản WAREHOUSE_MANAGER của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_MANAGER vừa tạo | `10-inbound` / TC-IN-03.11 Tra ID tài khoản WAREHOUSE_MANAGER của A | chưa chạy |
| TC-IN-03.12 | Gán kho QA-WH1 cho WAREHOUSE_MANAGER | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_MANAGER}}/warehouses` | 200, warehouseIds = [QA-WH1] | `10-inbound` / TC-IN-03.12 Gán kho QA-WH1 cho WAREHOUSE_MANAGER | chưa chạy |
| TC-IN-03.13 | Tra ID tài khoản WAREHOUSE_STAFF của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_STAFF vừa tạo | `10-inbound` / TC-IN-03.13 Tra ID tài khoản WAREHOUSE_STAFF của A | chưa chạy |
| TC-IN-03.14 | Gán kho QA-WH1 cho WAREHOUSE_STAFF | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_STAFF}}/warehouses` | 200, warehouseIds = [QA-WH1] | `10-inbound` / TC-IN-03.14 Gán kho QA-WH1 cho WAREHOUSE_STAFF | chưa chạy |
| TC-IN-03.15 | Tra ID tài khoản PURCHASING của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản PURCHASING vừa tạo | `10-inbound` / TC-IN-03.15 Tra ID tài khoản PURCHASING của A | chưa chạy |
| TC-IN-03.16 | Gán kho QA-WH1 cho PURCHASING | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_PURCHASING}}/warehouses` | 200, warehouseIds = [QA-WH1] | `10-inbound` / TC-IN-03.16 Gán kho QA-WH1 cho PURCHASING | chưa chạy |
| TC-IN-03.17 | Tra ID tài khoản SALES của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản SALES vừa tạo | `10-inbound` / TC-IN-03.17 Tra ID tài khoản SALES của A | chưa chạy |
| TC-IN-03.18 | Gán kho QA-WH1 cho SALES | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_SALES}}/warehouses` | 200, warehouseIds = [QA-WH1] | `10-inbound` / TC-IN-03.18 Gán kho QA-WH1 cho SALES | chưa chạy |
| TC-IN-03.19 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-03.19 Đăng nhập WAREHOUSE_MANAGER của công ty A | chưa chạy |
| TC-IN-03.20 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-03.20 Đăng nhập WAREHOUSE_STAFF của công ty A | chưa chạy |
| TC-IN-03.21 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-03.21 Đăng nhập PURCHASING của công ty A | chưa chạy |
| TC-IN-03.22 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `10-inbound` / TC-IN-03.22 Đăng nhập SALES của công ty A | chưa chạy |

#### TC-IN-04 Nhà cung cấp

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-04.1 | Mua hàng tạo nhà cung cấp | Mua hàng của A gọi `POST /api/orders/suppliers` | 201, currentDebt = 0, active = true | `10-inbound` / TC-IN-04.1 Mua hàng tạo nhà cung cấp | chưa chạy |
| TC-IN-04.2 | Trùng mã nhà cung cấp | ADMIN của A gọi `POST /api/orders/suppliers` | 409 CONFLICT | `10-inbound` / TC-IN-04.2 Trùng mã nhà cung cấp | chưa chạy |
| TC-IN-04.3 | Nhà cung cấp thiếu tên | ADMIN của A gọi `POST /api/orders/suppliers` | 400 VALIDATION_ERROR, data.name | `10-inbound` / TC-IN-04.3 Nhà cung cấp thiếu tên | chưa chạy |
| TC-IN-04.4 | Email nhà cung cấp sai định dạng | ADMIN của A gọi `POST /api/orders/suppliers` | 400 VALIDATION_ERROR, data.email | `10-inbound` / TC-IN-04.4 Email nhà cung cấp sai định dạng | chưa chạy |
| TC-IN-04.5 | Nhân viên kho không được tạo nhà cung cấp | Nhân viên kho của A gọi `POST /api/orders/suppliers` | 403 FORBIDDEN | `10-inbound` / TC-IN-04.5 Nhân viên kho không được tạo nhà cung cấp | chưa chạy |
| TC-IN-04.6 | Bán hàng không được xem nhà cung cấp | Bán hàng của A gọi `GET /api/orders/suppliers/{{in_sup}}` | 403 FORBIDDEN | `10-inbound` / TC-IN-04.6 Bán hàng không được xem nhà cung cấp | chưa chạy |

#### TC-IN-05 Đơn mua và duyệt

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-05.1 | Mua hàng lập đơn mua hai dòng | Mua hàng của A gọi `POST /api/orders/purchase` | 201, status PENDING, số PO-yyyyMMdd-NNNN, subtotal 15.800.000, thuế 1.200.000, tổng 17.000.000 | `10-inbound` / TC-IN-05.1 Mua hàng lập đơn mua hai dòng | chưa chạy |
| TC-IN-05.2 | Đơn mua không có dòng hàng | Mua hàng của A gọi `POST /api/orders/purchase` | 400 VALIDATION_ERROR, data.items | `10-inbound` / TC-IN-05.2 Đơn mua không có dòng hàng | chưa chạy |
| TC-IN-05.3 | Dòng đơn mua số lượng bằng 0 | Mua hàng của A gọi `POST /api/orders/purchase` | 400 VALIDATION_ERROR, data["items[0].quantity"] | `10-inbound` / TC-IN-05.3 Dòng đơn mua số lượng bằng 0 | chưa chạy |
| TC-IN-05.4 | Đơn mua với nhà cung cấp không tồn tại | Mua hàng của A gọi `POST /api/orders/purchase` | 404 NOT_FOUND | `10-inbound` / TC-IN-05.4 Đơn mua với nhà cung cấp không tồn tại | chưa chạy |
| TC-IN-05.5 | Quản lý kho không được lập đơn mua | Quản lý kho của A gọi `POST /api/orders/purchase` | 403 FORBIDDEN | `10-inbound` / TC-IN-05.5 Quản lý kho không được lập đơn mua | chưa chạy |
| TC-IN-05.6 | Chưa duyệt thì chưa được lập phiếu nhập | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST (đơn đang PENDING) | `10-inbound` / TC-IN-05.6 Chưa duyệt thì chưa được lập phiếu nhập | chưa chạy |
| TC-IN-05.7 | Nhân viên kho không được duyệt đơn mua | Nhân viên kho của A gọi `POST /api/orders/purchase/{{in_po}}/approve` | 403 FORBIDDEN | `10-inbound` / TC-IN-05.7 Nhân viên kho không được duyệt đơn mua | chưa chạy |
| TC-IN-05.8 | Bán hàng không được duyệt đơn mua | Bán hàng của A gọi `POST /api/orders/purchase/{{in_po}}/approve` | 403 FORBIDDEN | `10-inbound` / TC-IN-05.8 Bán hàng không được duyệt đơn mua | chưa chạy |
| TC-IN-05.9 | Quản lý kho duyệt đơn mua | Quản lý kho của A gọi `POST /api/orders/purchase/{{in_po}}/approve` | 200, status APPROVED, approvedBy = tài khoản quản lý kho | `10-inbound` / TC-IN-05.9 Quản lý kho duyệt đơn mua | chưa chạy |
| TC-IN-05.10 | Duyệt lại đơn đã duyệt | ADMIN của A gọi `POST /api/orders/purchase/{{in_po}}/approve` | 400 BAD_REQUEST | `10-inbound` / TC-IN-05.10 Duyệt lại đơn đã duyệt | chưa chạy |
| TC-IN-05.11 | Hàng đang về tăng sau khi duyệt | Mua hàng của A gọi `GET /api/inventory/incoming-stocks/by-product/{{in_prodLot}}?warehouseId={{in_wh}}`; hỏi lại tới khi sự kiện duyệt đơn được xử lý | 200, incomingQuantity = 100 (sự kiện duyệt đơn tăng Incoming) | `10-inbound` / TC-IN-05.11 Hàng đang về tăng sau khi duyệt | chưa chạy |
| TC-IN-05.12 | Danh sách hàng đang về của kho | Nhân viên kho của A gọi `GET /api/inventory/incoming-stocks?warehouseId={{in_wh}}` | 200, có hai dòng của đơn mua, trạng thái INCOMING | `10-inbound` / TC-IN-05.12 Danh sách hàng đang về của kho | chưa chạy |

#### TC-IN-06 Nhập kho từng phần theo lô

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-06.1 | Nhân viên kho nhập 60 thùng lô LOT-A1 và từ chối 5 | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 201, status COMPLETED, totalAcceptedQuantity 60, totalRejectedQuantity 5, totalAmount 9.000.000 | `10-inbound` / TC-IN-06.1 Nhân viên kho nhập 60 thùng lô LOT-A1 và từ chối 5 | chưa chạy |
| TC-IN-06.2 | Đơn mua chuyển nhập dở dang | Mua hàng của A gọi `GET /api/orders/purchase/{{in_po}}` | 200, status PARTIALLY_RECEIVED | `10-inbound` / TC-IN-06.2 Đơn mua chuyển nhập dở dang | chưa chạy |
| TC-IN-06.3 | Nhập vượt số còn lại | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST (còn lại 40) | `10-inbound` / TC-IN-06.3 Nhập vượt số còn lại | chưa chạy |
| TC-IN-06.4 | Nhập lô đã hết hạn | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST (hạn dùng không sau ngày nhận) | `10-inbound` / TC-IN-06.4 Nhập lô đã hết hạn | chưa chạy |
| TC-IN-06.5 | Hạn dùng trước ngày sản xuất | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST | `10-inbound` / TC-IN-06.5 Hạn dùng trước ngày sản xuất | chưa chạy |
| TC-IN-06.6 | Phiếu nhập thiếu đơn mua | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 VALIDATION_ERROR, data.poId | `10-inbound` / TC-IN-06.6 Phiếu nhập thiếu đơn mua | chưa chạy |
| TC-IN-06.7 | Dòng không thuộc đơn mua | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST | `10-inbound` / TC-IN-06.7 Dòng không thuộc đơn mua | chưa chạy |
| TC-IN-06.8 | Mua hàng không được lập phiếu nhập | Mua hàng của A gọi `POST /api/orders/goods-receipt-notes` | 403 FORBIDDEN | `10-inbound` / TC-IN-06.8 Mua hàng không được lập phiếu nhập | chưa chạy |
| TC-IN-06.9 | Tồn thực tế tăng 60 sau phiếu nhập | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodLot}}&warehouseId={{in_wh}}`; hỏi lại tới khi inventory-service xử lý sự kiện nhập kho | 200, onHandQuantity 60, reservedQuantity 0, atpQuantity 60 | `10-inbound` / TC-IN-06.9 Tồn thực tế tăng 60 sau phiếu nhập | chưa chạy |
| TC-IN-06.10 | Hàng đang về giảm còn 40 | Mua hàng của A gọi `GET /api/inventory/incoming-stocks/by-product/{{in_prodLot}}?warehouseId={{in_wh}}` | 200, incomingQuantity 40 | `10-inbound` / TC-IN-06.10 Hàng đang về giảm còn 40 | chưa chạy |
| TC-IN-06.11 | Lô LOT-A1 nằm đúng vị trí với đủ hạn dùng | Quản lý kho của A gọi `GET /api/inventory/lots?productId={{in_prodLot}}&warehouseId={{in_wh}}&size=50` | 200, một lô LOT-A1 số lượng 60 tại QA-WH1-A-R01-B01, expDate đúng | `10-inbound` / TC-IN-06.11 Lô LOT-A1 nằm đúng vị trí với đủ hạn dùng | chưa chạy |
| TC-IN-06.12 | Xác nhận lại phiếu nhập đã hoàn tất | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes/{{in_grn1}}/confirm` | 400 BAD_REQUEST (đã xác nhận), không phát sự kiện nhập lần hai | `10-inbound` / TC-IN-06.12 Xác nhận lại phiếu nhập đã hoàn tất | chưa chạy |
| TC-IN-06.13 | Tồn không tăng thêm sau khi bấm lại | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodLot}}&warehouseId={{in_wh}}`; chờ 4 giây rồi đọc lại | 200, onHandQuantity vẫn 60 | `10-inbound` / TC-IN-06.13 Tồn không tăng thêm sau khi bấm lại | chưa chạy |

#### TC-IN-07 Nhập đủ và nộp trùng phiếu

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-07.1 | Nhập nốt 40 thùng lô LOT-A2 và 40 khay | Quản lý kho của A gọi `POST /api/orders/goods-receipt-notes` | 201, totalAcceptedQuantity 80, totalAmount 6.800.000 | `10-inbound` / TC-IN-07.1 Nhập nốt 40 thùng lô LOT-A2 và 40 khay | chưa chạy |
| TC-IN-07.2 | Nộp trùng phiếu nhập vừa gửi | Quản lý kho của A gọi `POST /api/orders/goods-receipt-notes`; gửi lại nguyên body của TC-IN-07.1 | 400 BAD_REQUEST (đơn đã COMPLETED), không sinh phiếu thứ hai | `10-inbound` / TC-IN-07.2 Nộp trùng phiếu nhập vừa gửi | chưa chạy |
| TC-IN-07.3 | Đơn mua hoàn tất | Mua hàng của A gọi `GET /api/orders/goods-receipt-notes/po/{{in_po}}/progress` | 200, status COMPLETED, isFullyReceived true, totalReceivedQuantity 140, totalRemainingQuantity 0 | `10-inbound` / TC-IN-07.3 Đơn mua hoàn tất | chưa chạy |
| TC-IN-07.4 | Chỉ có hai phiếu nhập cho đơn | Kế toán của A gọi `GET /api/orders/goods-receipt-notes/po/{{in_po}}` | 200, đúng hai phiếu | `10-inbound` / TC-IN-07.4 Chỉ có hai phiếu nhập cho đơn | chưa chạy |
| TC-IN-07.5 | Tồn hàng theo lô bằng 100 | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodLot}}&warehouseId={{in_wh}}` | 200, onHandQuantity 100 | `10-inbound` / TC-IN-07.5 Tồn hàng theo lô bằng 100 | chưa chạy |
| TC-IN-07.6 | Tồn hàng không theo lô bằng 40 | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodPlain}}&warehouseId={{in_wh}}` | 200, onHandQuantity 40 | `10-inbound` / TC-IN-07.6 Tồn hàng không theo lô bằng 40 | chưa chạy |
| TC-IN-07.7 | Tồn không tăng thêm sau khi nộp trùng | Nhân viên kho của A gọi `GET /api/inventory/stock/atp?productId={{in_prodLot}}&warehouseId={{in_wh}}` | 200, onHandQuantity vẫn 100 | `10-inbound` / TC-IN-07.7 Tồn không tăng thêm sau khi nộp trùng | chưa chạy |
| TC-IN-07.8 | Hai lô với tổng 100 | Kế toán của A gọi `GET /api/inventory/lots?productId={{in_prodLot}}&warehouseId={{in_wh}}&size=50` | 200, LOT-A1 = 60 và LOT-A2 = 40, sắp theo hạn dùng tăng dần | `10-inbound` / TC-IN-07.8 Hai lô với tổng 100 | chưa chạy |
| TC-IN-07.9 | Hàng đang về về 0 | Mua hàng của A gọi `GET /api/inventory/incoming-stocks/by-product/{{in_prodLot}}?warehouseId={{in_wh}}` | 200, incomingQuantity 0 | `10-inbound` / TC-IN-07.9 Hàng đang về về 0 | chưa chạy |
| TC-IN-07.10 | Không lập phiếu nhập cho đơn đã hoàn tất | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 400 BAD_REQUEST | `10-inbound` / TC-IN-07.10 Không lập phiếu nhập cho đơn đã hoàn tất | chưa chạy |
| TC-IN-07.11 | Vị trí đã có hàng | Quản lý kho của A gọi `GET /api/inventory/locations?warehouseId={{in_wh}}&zone=A` | 200, A-R01-B01 có 60, A-R01-B02 có 40 | `10-inbound` / TC-IN-07.11 Vị trí đã có hàng | chưa chạy |

#### TC-IN-08 Hóa đơn mua, thanh toán và công nợ nhà cung cấp

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-IN-08.1 | Hai hóa đơn mua tự sinh từ phiếu nhập | Kế toán của A gọi `GET /api/orders/purchase-bills/po/{{in_po}}` | 200, hai hóa đơn UNPAID: 9.000.000 (GRN 1) và 6.800.000 (GRN 2) | `10-inbound` / TC-IN-08.1 Hai hóa đơn mua tự sinh từ phiếu nhập | chưa chạy |
| TC-IN-08.2 | Công nợ nhà cung cấp tăng theo hóa đơn | Kế toán của A gọi `GET /api/orders/suppliers/{{in_sup}}` | 200, currentDebt 15.800.000 | `10-inbound` / TC-IN-08.2 Công nợ nhà cung cấp tăng theo hóa đơn | chưa chạy |
| TC-IN-08.3 | Lịch sử công nợ có hai hóa đơn | Mua hàng của A gọi `GET /api/orders/suppliers/{{in_sup}}/transactions` | 200, hai giao dịch PURCHASE_INVOICE, balanceAfter mới nhất 15.800.000 | `10-inbound` / TC-IN-08.3 Lịch sử công nợ có hai hóa đơn | chưa chạy |
| TC-IN-08.4 | Kế toán trả một phần hóa đơn 1 | Kế toán của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 200, status PARTIALLY_PAID, paidAmount 5.000.000, remainingAmount 4.000.000 | `10-inbound` / TC-IN-08.4 Kế toán trả một phần hóa đơn 1 | chưa chạy |
| TC-IN-08.5 | Trả vượt số còn nợ | Kế toán của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 400 BAD_REQUEST | `10-inbound` / TC-IN-08.5 Trả vượt số còn nợ | chưa chạy |
| TC-IN-08.6 | Số tiền thanh toán bằng 0 | Kế toán của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 400 VALIDATION_ERROR, data.amount | `10-inbound` / TC-IN-08.6 Số tiền thanh toán bằng 0 | chưa chạy |
| TC-IN-08.7 | Mua hàng không được ghi thanh toán | Mua hàng của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 403 FORBIDDEN | `10-inbound` / TC-IN-08.7 Mua hàng không được ghi thanh toán | chưa chạy |
| TC-IN-08.8 | Nhân viên kho không được xem hóa đơn mua | Nhân viên kho của A gọi `GET /api/orders/purchase-bills/{{in_bill1}}` | 403 FORBIDDEN | `10-inbound` / TC-IN-08.8 Nhân viên kho không được xem hóa đơn mua | chưa chạy |
| TC-IN-08.9 | Trả nốt hóa đơn 1 | ADMIN của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 200, status PAID, remainingAmount 0 | `10-inbound` / TC-IN-08.9 Trả nốt hóa đơn 1 | chưa chạy |
| TC-IN-08.10 | Không thanh toán thêm hóa đơn đã trả đủ | Kế toán của A gọi `POST /api/orders/purchase-bills/{{in_bill1}}/payments` | 400 BAD_REQUEST | `10-inbound` / TC-IN-08.10 Không thanh toán thêm hóa đơn đã trả đủ | chưa chạy |
| TC-IN-08.11 | Công nợ còn lại bằng hóa đơn 2 | Mua hàng của A gọi `GET /api/orders/suppliers/{{in_sup}}` | 200, currentDebt 6.800.000 | `10-inbound` / TC-IN-08.11 Công nợ còn lại bằng hóa đơn 2 | chưa chạy |
| TC-IN-08.12 | Tổng công nợ phải trả của doanh nghiệp | Kế toán của A gọi `GET /api/orders/suppliers/summary`; chỉ khi A mới có một nhà cung cấp của 10-inbound | 200, totalDebt 6.800.000 (bằng tổng nợ các nhà cung cấp của A) | `10-inbound` / TC-IN-08.12 Tổng công nợ phải trả của doanh nghiệp | chưa chạy |
| TC-IN-08.13 | Báo cáo nhập của kho QA-WH1 | Kế toán của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{in_wh}}&fromDate={{d_m10}}&toDate={{d_today}}&groupBy=PRODUCT_WAREHOUSE` | 200, totals: openingQuantity 0, quantityIn 140, quantityOut 0, closingQuantity 140 | `10-inbound` / TC-IN-08.13 Báo cáo nhập của kho QA-WH1 | chưa chạy |

## 4. WMS-20.2 Bán, xuất kho, điều chuyển và kiểm kê (20-outbound-transfer-stocktake)

Trình tự số liệu của hàng `QA-FEFO-01` ở kho nguồn: nhập 85 (L-EXP 5 hết hạn hôm qua, L-LOCK 10 bị khóa, L-EARLY 20 hạn
+20 ngày, L-LATE 50 hạn +90 ngày), khả dụng 70; đơn bán 30 giữ L-EARLY 20 và L-LATE 10; xuất 10 rồi 20; điều chuyển 15
sang kho đích (nhận 12, hỏng 2, thiếu 1); điều chuyển ngược 2 vào vị trí kiểm kê. Hàng `QA-STK-01`: nhập 40, kiểm kê đếm
37, điều chỉnh giảm 3.

#### TC-OUT-00 Đăng nhập

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-00.1 | Kiểm tra runId của lượt chạy | Kiểm tra biến môi trường `runId` đã được truyền (`--env-var runId=...`), rồi gọi `GET /api/auth/plans` | runId có giá trị (cùng runId đã dùng cho 00-setup); API công khai trả 200 | `20-outbound-transfer-stocktake` / TC-OUT-00.1 Kiểm tra runId của lượt chạy | chưa chạy |
| TC-OUT-00.2 | Đăng nhập ADMIN của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.2 Đăng nhập ADMIN của công ty A | chưa chạy |
| TC-OUT-00.3 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.3 Đăng nhập WAREHOUSE_MANAGER của công ty A | chưa chạy |
| TC-OUT-00.4 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.4 Đăng nhập WAREHOUSE_STAFF của công ty A | chưa chạy |
| TC-OUT-00.5 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.5 Đăng nhập PURCHASING của công ty A | chưa chạy |
| TC-OUT-00.6 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.6 Đăng nhập SALES của công ty A | chưa chạy |
| TC-OUT-00.7 | Đăng nhập ACCOUNTANT của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ACCOUNTANT của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ACCOUNTANT; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-OUT-00.7 Đăng nhập ACCOUNTANT của công ty A | chưa chạy |

#### TC-PREP-20 Chuẩn bị dữ liệu riêng của collection 20

Kho, vị trí, hàng hóa, tồn theo lô dùng cho các bước sau; không phụ thuộc 10-inbound.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PREP-20.1 | Tạo đơn vị tính Hộp | ADMIN của A gọi `POST /api/products/uoms` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.1 Tạo đơn vị tính Hộp | chưa chạy |
| TC-PREP-20.2 | Tạo hàng hóa QA-FEFO-01 | ADMIN của A gọi `POST /api/products` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.2 Tạo hàng hóa QA-FEFO-01 | chưa chạy |
| TC-PREP-20.3 | Tạo hàng hóa QA-STK-01 | ADMIN của A gọi `POST /api/products` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.3 Tạo hàng hóa QA-STK-01 | chưa chạy |
| TC-PREP-20.4 | Tạo hàng hóa QA-FRZ-01 | ADMIN của A gọi `POST /api/products` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.4 Tạo hàng hóa QA-FRZ-01 | chưa chạy |
| TC-PREP-20.5 | Tạo kho QA-WHS | ADMIN của A gọi `POST /api/inventory/warehouses` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.5 Tạo kho QA-WHS | chưa chạy |
| TC-PREP-20.6 | Tạo kho QA-WHD | ADMIN của A gọi `POST /api/inventory/warehouses` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.6 Tạo kho QA-WHD | chưa chạy |
| TC-PREP-20.7 | Tạo vị trí khu A kho nguồn | ADMIN của A gọi `POST /api/inventory/locations/bulk` | 201, QA-WHS-A-R01-B01 và B02 | `20-outbound-transfer-stocktake` / TC-PREP-20.7 Tạo vị trí khu A kho nguồn | chưa chạy |
| TC-PREP-20.8 | Tạo vị trí kiểm kê khu K kho nguồn | ADMIN của A gọi `POST /api/inventory/locations` | 201, QA-WHS-K-R01-B01 | `20-outbound-transfer-stocktake` / TC-PREP-20.8 Tạo vị trí kiểm kê khu K kho nguồn | chưa chạy |
| TC-PREP-20.9 | Tạo vị trí kho đích | ADMIN của A gọi `POST /api/inventory/locations` | 201, QA-WHD-A-R01-B01 | `20-outbound-transfer-stocktake` / TC-PREP-20.9 Tạo vị trí kho đích | chưa chạy |
| TC-PREP-20.10 | Lấy toàn bộ kho của A để gán quyền | ADMIN của A gọi `GET /api/inventory/warehouses` | 200, có QA-WHS và QA-WHD | `20-outbound-transfer-stocktake` / TC-PREP-20.10 Lấy toàn bộ kho của A để gán quyền | chưa chạy |
| TC-PREP-20.11 | Tra ID tài khoản WAREHOUSE_MANAGER của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_MANAGER vừa tạo | `20-outbound-transfer-stocktake` / TC-PREP-20.11 Tra ID tài khoản WAREHOUSE_MANAGER của A | chưa chạy |
| TC-PREP-20.12 | Gán mọi kho của A cho WAREHOUSE_MANAGER | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_MANAGER}}/warehouses` | 200 | `20-outbound-transfer-stocktake` / TC-PREP-20.12 Gán mọi kho của A cho WAREHOUSE_MANAGER | chưa chạy |
| TC-PREP-20.13 | Tra ID tài khoản WAREHOUSE_STAFF của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_STAFF vừa tạo | `20-outbound-transfer-stocktake` / TC-PREP-20.13 Tra ID tài khoản WAREHOUSE_STAFF của A | chưa chạy |
| TC-PREP-20.14 | Gán mọi kho của A cho WAREHOUSE_STAFF | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_STAFF}}/warehouses` | 200 | `20-outbound-transfer-stocktake` / TC-PREP-20.14 Gán mọi kho của A cho WAREHOUSE_STAFF | chưa chạy |
| TC-PREP-20.15 | Tra ID tài khoản PURCHASING của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản PURCHASING vừa tạo | `20-outbound-transfer-stocktake` / TC-PREP-20.15 Tra ID tài khoản PURCHASING của A | chưa chạy |
| TC-PREP-20.16 | Gán mọi kho của A cho PURCHASING | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_PURCHASING}}/warehouses` | 200 | `20-outbound-transfer-stocktake` / TC-PREP-20.16 Gán mọi kho của A cho PURCHASING | chưa chạy |
| TC-PREP-20.17 | Tra ID tài khoản SALES của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản SALES vừa tạo | `20-outbound-transfer-stocktake` / TC-PREP-20.17 Tra ID tài khoản SALES của A | chưa chạy |
| TC-PREP-20.18 | Gán mọi kho của A cho SALES | ADMIN của A gọi `PUT /api/auth/users/{{uid_A_SALES}}/warehouses` | 200 | `20-outbound-transfer-stocktake` / TC-PREP-20.18 Gán mọi kho của A cho SALES | chưa chạy |
| TC-PREP-20.19 | Đăng nhập WAREHOUSE_MANAGER của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-PREP-20.19 Đăng nhập WAREHOUSE_MANAGER của công ty A | chưa chạy |
| TC-PREP-20.20 | Đăng nhập WAREHOUSE_STAFF của công ty A | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_STAFF của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_STAFF; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-PREP-20.20 Đăng nhập WAREHOUSE_STAFF của công ty A | chưa chạy |
| TC-PREP-20.21 | Đăng nhập PURCHASING của công ty A | Gọi `POST /api/auth/login` bằng tài khoản PURCHASING của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa PURCHASING; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-PREP-20.21 Đăng nhập PURCHASING của công ty A | chưa chạy |
| TC-PREP-20.22 | Đăng nhập SALES của công ty A | Gọi `POST /api/auth/login` bằng tài khoản SALES của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa SALES; company.id đúng doanh nghiệp A | `20-outbound-transfer-stocktake` / TC-PREP-20.22 Đăng nhập SALES của công ty A | chưa chạy |
| TC-PREP-20.23 | Tạo nhà cung cấp NCC-QA-20 | ADMIN của A gọi `POST /api/orders/suppliers` | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.23 Tạo nhà cung cấp NCC-QA-20 | chưa chạy |
| TC-PREP-20.24 | Lập đơn mua ba mặt hàng | ADMIN của A gọi `POST /api/orders/purchase` | 201, PENDING | `20-outbound-transfer-stocktake` / TC-PREP-20.24 Lập đơn mua ba mặt hàng | chưa chạy |
| TC-PREP-20.25 | Duyệt đơn mua | ADMIN của A gọi `POST /api/orders/purchase/{{o_po}}/approve` | 200, APPROVED | `20-outbound-transfer-stocktake` / TC-PREP-20.25 Duyệt đơn mua | chưa chạy |
| TC-PREP-20.26 | Nhập lô L-EXP ghi lùi ngày (lô đã hết hạn hôm qua) | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes`; receiptDate = hôm nay - 10, expDate = hôm qua | 201 | `20-outbound-transfer-stocktake` / TC-PREP-20.26 Nhập lô L-EXP ghi lùi ngày (lô đã hết hạn hôm qua) | chưa chạy |
| TC-PREP-20.27 | Nhập ba lô FEFO và lô kiểm kê | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 201, totalAcceptedQuantity 120 | `20-outbound-transfer-stocktake` / TC-PREP-20.27 Nhập ba lô FEFO và lô kiểm kê | chưa chạy |
| TC-PREP-20.28 | Tồn FEFO khả dụng 80 (lô hết hạn không tính) | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 80 (10 + 20 + 50; lô L-EXP hết hạn bị loại khỏi tồn khả dụng) | `20-outbound-transfer-stocktake` / TC-PREP-20.28 Tồn FEFO khả dụng 80 (lô hết hạn không tính) | chưa chạy |
| TC-PREP-20.29 | Tồn hàng kiểm kê 40 | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodStk}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40 | `20-outbound-transfer-stocktake` / TC-PREP-20.29 Tồn hàng kiểm kê 40 | chưa chạy |
| TC-PREP-20.30 | Lô hết hạn hiển thị trạng thái EXPIRED | Quản lý kho của A gọi `GET /api/inventory/lots?productId={{o_prodFefo}}&warehouseId={{o_whS}}&size=50` | 200, bốn lô; L-EXP có expiryStatus EXPIRED; lưu ID lô L-LOCK | `20-outbound-transfer-stocktake` / TC-PREP-20.30 Lô hết hạn hiển thị trạng thái EXPIRED | chưa chạy |
| TC-PREP-20.31 | Bán hàng không được khóa lô | Bán hàng của A gọi `PUT /api/inventory/lots/{{o_lotLock}}/lock` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-PREP-20.31 Bán hàng không được khóa lô | chưa chạy |
| TC-PREP-20.32 | Quản lý kho khóa lô L-LOCK | Quản lý kho của A gọi `PUT /api/inventory/lots/{{o_lotLock}}/lock` | 200, locked = true, lockedReason lưu lại | `20-outbound-transfer-stocktake` / TC-PREP-20.32 Quản lý kho khóa lô L-LOCK | chưa chạy |
| TC-PREP-20.33 | Tồn FEFO khả dụng còn 70 sau khi khóa lô | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 70, reservedQuantity 0 | `20-outbound-transfer-stocktake` / TC-PREP-20.33 Tồn FEFO khả dụng còn 70 sau khi khóa lô | chưa chạy |

#### TC-OUT-01 Khách hàng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-01.1 | Bán hàng tạo khách hàng bán sỉ | Bán hàng của A gọi `POST /api/orders/customers` | 201, customerType WHOLESALE, currentDebt 0 | `20-outbound-transfer-stocktake` / TC-OUT-01.1 Bán hàng tạo khách hàng bán sỉ | chưa chạy |
| TC-OUT-01.2 | Khách hàng thiếu loại | Bán hàng của A gọi `POST /api/orders/customers` | 400 VALIDATION_ERROR, data.customerType | `20-outbound-transfer-stocktake` / TC-OUT-01.2 Khách hàng thiếu loại | chưa chạy |
| TC-OUT-01.3 | Loại khách hàng không hợp lệ | Bán hàng của A gọi `POST /api/orders/customers` | 400 BAD_REQUEST (giá trị enum không đọc được) | `20-outbound-transfer-stocktake` / TC-OUT-01.3 Loại khách hàng không hợp lệ | chưa chạy |
| TC-OUT-01.4 | Trùng mã khách hàng | ADMIN của A gọi `POST /api/orders/customers` | 409 CONFLICT | `20-outbound-transfer-stocktake` / TC-OUT-01.4 Trùng mã khách hàng | chưa chạy |
| TC-OUT-01.5 | Nhân viên kho không được tạo khách hàng | Nhân viên kho của A gọi `POST /api/orders/customers` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-01.5 Nhân viên kho không được tạo khách hàng | chưa chạy |

#### TC-OUT-02 Đơn bán, xác nhận và giữ chỗ

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-02.1 | Bán hàng lập đơn nháp 30 hộp | Bán hàng của A gọi `POST /api/orders/sales` | 201, status DRAFT, tổng 2.592.000 (30 x 80.000 + VAT 8%) | `20-outbound-transfer-stocktake` / TC-OUT-02.1 Bán hàng lập đơn nháp 30 hộp | chưa chạy |
| TC-OUT-02.2 | Đơn nháp chưa giữ hàng | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, reservedQuantity 0, onHandQuantity 70 | `20-outbound-transfer-stocktake` / TC-OUT-02.2 Đơn nháp chưa giữ hàng | chưa chạy |
| TC-OUT-02.3 | Nhân viên kho không được xác nhận đơn bán | Nhân viên kho của A gọi `POST /api/orders/sales/{{o_so}}/confirm` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-02.3 Nhân viên kho không được xác nhận đơn bán | chưa chạy |
| TC-OUT-02.4 | Kế toán không được xác nhận đơn bán | Kế toán của A gọi `POST /api/orders/sales/{{o_so}}/confirm` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-02.4 Kế toán không được xác nhận đơn bán | chưa chạy |
| TC-OUT-02.5 | Bán hàng xác nhận đơn | Bán hàng của A gọi `POST /api/orders/sales/{{o_so}}/confirm` | 200, status CONFIRMED, confirmedBy = tài khoản bán hàng | `20-outbound-transfer-stocktake` / TC-OUT-02.5 Bán hàng xác nhận đơn | chưa chạy |
| TC-OUT-02.6 | Giữ chỗ làm giảm khả dụng, không đổi tồn thực tế | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 70, reservedQuantity 30, atpQuantity 40 | `20-outbound-transfer-stocktake` / TC-OUT-02.6 Giữ chỗ làm giảm khả dụng, không đổi tồn thực tế | chưa chạy |
| TC-OUT-02.7 | Giữ chỗ theo FEFO trên lô | Quản lý kho của A gọi `GET /api/inventory/lots?productId={{o_prodFefo}}&warehouseId={{o_whS}}&size=50` | 200, L-EARLY giữ 20, L-LATE giữ 10, L-LOCK và L-EXP không bị giữ | `20-outbound-transfer-stocktake` / TC-OUT-02.7 Giữ chỗ theo FEFO trên lô | chưa chạy |
| TC-OUT-02.8 | Xác nhận lại đơn đã xác nhận | Bán hàng của A gọi `POST /api/orders/sales/{{o_so}}/confirm` | 400 BAD_REQUEST, không giữ thêm | `20-outbound-transfer-stocktake` / TC-OUT-02.8 Xác nhận lại đơn đã xác nhận | chưa chạy |
| TC-OUT-02.9 | Lập đơn nháp vượt khả dụng | Bán hàng của A gọi `POST /api/orders/sales` | 201, DRAFT | `20-outbound-transfer-stocktake` / TC-OUT-02.9 Lập đơn nháp vượt khả dụng | chưa chạy |
| TC-OUT-02.10 | Không xác nhận được đơn vượt khả dụng | Bán hàng của A gọi `POST /api/orders/sales/{{o_so2}}/confirm` | 400 BAD_REQUEST (khả dụng 40 < 41) | `20-outbound-transfer-stocktake` / TC-OUT-02.10 Không xác nhận được đơn vượt khả dụng | chưa chạy |
| TC-OUT-02.11 | Đơn không cho backorder bị chặn khi thiếu hàng | Bán hàng của A gọi `POST /api/orders/sales` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-OUT-02.11 Đơn không cho backorder bị chặn khi thiếu hàng | chưa chạy |
| TC-OUT-02.12 | Xóa đơn nháp thừa | Bán hàng của A gọi `DELETE /api/orders/sales/{{o_so2}}` | 200 | `20-outbound-transfer-stocktake` / TC-OUT-02.12 Xóa đơn nháp thừa | chưa chạy |
| TC-OUT-02.13 | Khả dụng không đổi sau các lần bị từ chối | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, reservedQuantity 30, atpQuantity 40 | `20-outbound-transfer-stocktake` / TC-OUT-02.13 Khả dụng không đổi sau các lần bị từ chối | chưa chạy |

#### TC-OUT-03 Danh sách lấy hàng FEFO

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-03.1 | Danh sách lấy hàng theo FEFO, bỏ lô khóa và hết hạn | Nhân viên kho của A gọi `GET /api/orders/sales/{{o_so}}/picking-list` | 200, hai bước: L-EARLY 20 rồi L-LATE 10; không có L-LOCK, L-EXP; totalQuantity 30 | `20-outbound-transfer-stocktake` / TC-OUT-03.1 Danh sách lấy hàng theo FEFO, bỏ lô khóa và hết hạn | chưa chạy |
| TC-OUT-03.2 | Mua hàng không xem được danh sách lấy hàng | Mua hàng của A gọi `GET /api/orders/sales/{{o_so}}/picking-list` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-03.2 Mua hàng không xem được danh sách lấy hàng | chưa chạy |

#### TC-OUT-04 Phiếu xuất từng phần rồi đủ

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-OUT-04.1 | Bán hàng không được lập phiếu xuất | Bán hàng của A gọi `POST /api/orders/goods-delivery-notes` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-OUT-04.1 Bán hàng không được lập phiếu xuất | chưa chạy |
| TC-OUT-04.2 | Nhân viên kho xuất 10 hộp | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes` | 201, status COMPLETED, totalQuantity 10 | `20-outbound-transfer-stocktake` / TC-OUT-04.2 Nhân viên kho xuất 10 hộp | chưa chạy |
| TC-OUT-04.3 | Tồn giảm 10, giữ chỗ giảm 10 | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 60, reservedQuantity 20 | `20-outbound-transfer-stocktake` / TC-OUT-04.3 Tồn giảm 10, giữ chỗ giảm 10 | chưa chạy |
| TC-OUT-04.4 | Đơn bán vẫn đang xác nhận | Bán hàng của A gọi `GET /api/orders/sales/{{o_so}}` | 200, status CONFIRMED | `20-outbound-transfer-stocktake` / TC-OUT-04.4 Đơn bán vẫn đang xác nhận | chưa chạy |
| TC-OUT-04.5 | Xuất vượt số còn phải giao | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes` | 400 BAD_REQUEST (còn 20) | `20-outbound-transfer-stocktake` / TC-OUT-04.5 Xuất vượt số còn phải giao | chưa chạy |
| TC-OUT-04.6 | Số lượng xuất bằng 0 | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes` | 400 VALIDATION_ERROR | `20-outbound-transfer-stocktake` / TC-OUT-04.6 Số lượng xuất bằng 0 | chưa chạy |
| TC-OUT-04.7 | Xuất nốt 20 hộp | Quản lý kho của A gọi `POST /api/orders/goods-delivery-notes` | 201, COMPLETED, totalQuantity 20 | `20-outbound-transfer-stocktake` / TC-OUT-04.7 Xuất nốt 20 hộp | chưa chạy |
| TC-OUT-04.8 | Đơn bán hoàn tất | Bán hàng của A gọi `GET /api/orders/sales/{{o_so}}` | 200, status COMPLETED | `20-outbound-transfer-stocktake` / TC-OUT-04.8 Đơn bán hoàn tất | chưa chạy |
| TC-OUT-04.9 | Tồn còn 40, giữ chỗ về 0 | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40, reservedQuantity 0 | `20-outbound-transfer-stocktake` / TC-OUT-04.9 Tồn còn 40, giữ chỗ về 0 | chưa chạy |
| TC-OUT-04.10 | Tồn không bị trừ thêm | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity vẫn 40 sau 4 giây | `20-outbound-transfer-stocktake` / TC-OUT-04.10 Tồn không bị trừ thêm | chưa chạy |
| TC-OUT-04.11 | Không xuất thêm cho đơn đã hoàn tất | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-OUT-04.11 Không xuất thêm cho đơn đã hoàn tất | chưa chạy |
| TC-OUT-04.12 | Hai phiếu xuất của đơn | Kế toán của A gọi `GET /api/orders/goods-delivery-notes/so/{{o_so}}` | 200, hai phiếu, tổng 30 | `20-outbound-transfer-stocktake` / TC-OUT-04.12 Hai phiếu xuất của đơn | chưa chạy |
| TC-OUT-04.13 | Xác nhận xuất lại phiếu đã hoàn tất | Nhân viên kho của A gọi `POST /api/orders/goods-delivery-notes/{{o_gdn1}}/dispatch` | 200, trả nguyên phiếu COMPLETED, không phát sự kiện trừ tồn lần hai | `20-outbound-transfer-stocktake` / TC-OUT-04.13 Xác nhận xuất lại phiếu đã hoàn tất | chưa chạy |
| TC-OUT-04.14 | Tồn vẫn 40 sau khi bấm xuất lại | Bán hàng của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40 | `20-outbound-transfer-stocktake` / TC-OUT-04.14 Tồn vẫn 40 sau khi bấm xuất lại | chưa chạy |

#### TC-TRF-01 Điều chuyển giữa hai kho có hàng thiếu, hỏng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-TRF-01.1 | Kho nguồn trùng kho đích | Quản lý kho của A gọi `POST /api/inventory/transfers` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.1 Kho nguồn trùng kho đích | chưa chạy |
| TC-TRF-01.2 | Bán hàng không được lập lệnh điều chuyển | Bán hàng của A gọi `POST /api/inventory/transfers` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-TRF-01.2 Bán hàng không được lập lệnh điều chuyển | chưa chạy |
| TC-TRF-01.3 | Quản lý kho lập lệnh điều chuyển 15 hộp | Quản lý kho của A gọi `POST /api/inventory/transfers` | 201, status DRAFT, totalQuantity 15 | `20-outbound-transfer-stocktake` / TC-TRF-01.3 Quản lý kho lập lệnh điều chuyển 15 hộp | chưa chạy |
| TC-TRF-01.4 | Lập lệnh chưa trừ tồn kho nguồn | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40 | `20-outbound-transfer-stocktake` / TC-TRF-01.4 Lập lệnh chưa trừ tồn kho nguồn | chưa chạy |
| TC-TRF-01.5 | Nhân viên kho không được xuất điều chuyển | Nhân viên kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/dispatch` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-TRF-01.5 Nhân viên kho không được xuất điều chuyển | chưa chạy |
| TC-TRF-01.6 | Quản lý kho xuất điều chuyển | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/dispatch` | 200, status IN_TRANSIT, totalTransferredQuantity 15 | `20-outbound-transfer-stocktake` / TC-TRF-01.6 Quản lý kho xuất điều chuyển | chưa chạy |
| TC-TRF-01.7 | Tồn kho nguồn giảm 15 | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 25 | `20-outbound-transfer-stocktake` / TC-TRF-01.7 Tồn kho nguồn giảm 15 | chưa chạy |
| TC-TRF-01.8 | Xuất điều chuyển lần hai | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/dispatch` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.8 Xuất điều chuyển lần hai | chưa chạy |
| TC-TRF-01.9 | Tổng nhận và hỏng vượt số đã xuất | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 400 BAD_REQUEST (12 + 4 > 15) | `20-outbound-transfer-stocktake` / TC-TRF-01.9 Tổng nhận và hỏng vượt số đã xuất | chưa chạy |
| TC-TRF-01.10 | Nhận hàng thiếu vị trí đích | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.10 Nhận hàng thiếu vị trí đích | chưa chạy |
| TC-TRF-01.11 | Vị trí đích không thuộc kho đích | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.11 Vị trí đích không thuộc kho đích | chưa chạy |
| TC-TRF-01.12 | Nhận 12, hỏng 2, thiếu 1 | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 200, status PARTIAL_RECEIVED, receivedQuantity 12, damagedQuantity 2 | `20-outbound-transfer-stocktake` / TC-TRF-01.12 Nhận 12, hỏng 2, thiếu 1 | chưa chạy |
| TC-TRF-01.13 | Tồn kho đích bằng số thực nhận | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whD}}` | 200, onHandQuantity 12 | `20-outbound-transfer-stocktake` / TC-TRF-01.13 Tồn kho đích bằng số thực nhận | chưa chạy |
| TC-TRF-01.14 | Nhận lại lệnh đã nhận | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf}}/receive` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-TRF-01.14 Nhận lại lệnh đã nhận | chưa chạy |

#### TC-STK-01 Kiểm kê: đóng băng, đếm mù, duyệt hai cấp, điều chỉnh tồn

Đợt kiểm kê theo vị trí QA-WHS-K-R01-B01 (hàng QA-STK-01 lô STK-1, hệ thống 40, đếm được 37).

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-STK-01.1 | Chuẩn bị lệnh điều chuyển ngược 2 hộp sẽ nhận vào vị trí kiểm kê | Quản lý kho của A gọi `POST /api/inventory/transfers` | 201, DRAFT | `20-outbound-transfer-stocktake` / TC-STK-01.1 Chuẩn bị lệnh điều chuyển ngược 2 hộp sẽ nhận vào vị trí kiểm kê | chưa chạy |
| TC-STK-01.2 | Xuất lệnh điều chuyển ngược | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf2}}/dispatch` | 200, IN_TRANSIT | `20-outbound-transfer-stocktake` / TC-STK-01.2 Xuất lệnh điều chuyển ngược | chưa chạy |
| TC-STK-01.3 | Phạm vi theo vị trí thiếu danh sách vị trí | Quản lý kho của A gọi `POST /api/inventory/stocktakes` | 400 (VALIDATION_ERROR hoặc BAD_REQUEST) | `20-outbound-transfer-stocktake` / TC-STK-01.3 Phạm vi theo vị trí thiếu danh sách vị trí | chưa chạy |
| TC-STK-01.4 | Nhân viên kho không được lập đợt kiểm kê | Nhân viên kho của A gọi `POST /api/inventory/stocktakes` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-STK-01.4 Nhân viên kho không được lập đợt kiểm kê | chưa chạy |
| TC-STK-01.5 | Quản lý kho lập đợt kiểm kê vị trí K | Quản lý kho của A gọi `POST /api/inventory/stocktakes` | 201, status DRAFT, mã ST-yyyyMM-NN | `20-outbound-transfer-stocktake` / TC-STK-01.5 Quản lý kho lập đợt kiểm kê vị trí K | chưa chạy |
| TC-STK-01.6 | Bắt đầu kiểm kê đóng băng vị trí | Quản lý kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/start` | 200, status COUNTING, totalLines 1 | `20-outbound-transfer-stocktake` / TC-STK-01.6 Bắt đầu kiểm kê đóng băng vị trí | chưa chạy |
| TC-STK-01.7 | Vị trí hiển thị đang đóng băng | Nhân viên kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}` | 200, locations[0].frozen = true | `20-outbound-transfer-stocktake` / TC-STK-01.7 Vị trí hiển thị đang đóng băng | chưa chạy |
| TC-STK-01.8 | Trừ tồn tại vị trí đóng băng bị chặn | ADMIN của A gọi `POST /api/inventory/stock/deduct` | 409 CONFLICT, data.sessionCode = mã đợt | `20-outbound-transfer-stocktake` / TC-STK-01.8 Trừ tồn tại vị trí đóng băng bị chặn | chưa chạy |
| TC-STK-01.9 | Nhận điều chuyển vào vị trí đóng băng bị chặn | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf2}}/receive` | 409 CONFLICT | `20-outbound-transfer-stocktake` / TC-STK-01.9 Nhận điều chuyển vào vị trí đóng băng bị chặn | chưa chạy |
| TC-STK-01.10 | Phiếu nhập vào vị trí đóng băng phải bị chặn | Nhân viên kho của A gọi `POST /api/orders/goods-receipt-notes` | 409 CONFLICT theo yêu cầu (SRS UC-09 1a). Dự kiến KHÔNG ĐẠT: order-service không kiểm tra đóng băng khi lập phiếu nhập: API trả 201, inventory-service từ chối sự kiện order.received ở luồng nền (thử lại 3 lần rồi vào dead-letter), tồn của QA-FRZ-01 không tăng nhưng phiếu nhập, hóa đơn mua vẫn được ghi. | `20-outbound-transfer-stocktake` / TC-STK-01.10 Phiếu nhập vào vị trí đóng băng phải bị chặn | chưa chạy |
| TC-STK-01.11 | Dòng đếm không lộ số hệ thống | Nhân viên kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}/count-lines` | 200, một dòng STK-1, không có trường systemQuantity | `20-outbound-transfer-stocktake` / TC-STK-01.11 Dòng đếm không lộ số hệ thống | chưa chạy |
| TC-STK-01.12 | Số đếm âm bị từ chối | Nhân viên kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/counts` | 400 VALIDATION_ERROR | `20-outbound-transfer-stocktake` / TC-STK-01.12 Số đếm âm bị từ chối | chưa chạy |
| TC-STK-01.13 | Nhân viên kho đếm được 37 | Nhân viên kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/counts` | 200, countedQuantity 37, không có systemQuantity | `20-outbound-transfer-stocktake` / TC-STK-01.13 Nhân viên kho đếm được 37 | chưa chạy |
| TC-STK-01.14 | Nhân viên kho không xem được chênh lệch | Nhân viên kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}/variances` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-STK-01.14 Nhân viên kho không xem được chênh lệch | chưa chạy |
| TC-STK-01.15 | Chưa kết thúc đếm thì chưa có chênh lệch | Quản lý kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}/variances` | 409 CONFLICT | `20-outbound-transfer-stocktake` / TC-STK-01.15 Chưa kết thúc đếm thì chưa có chênh lệch | chưa chạy |
| TC-STK-01.16 | Kết thúc đếm | Quản lý kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/finish` | 200, status COUNTED, uncountedLines 0 | `20-outbound-transfer-stocktake` / TC-STK-01.16 Kết thúc đếm | chưa chạy |
| TC-STK-01.17 | Chênh lệch giảm 3 | Quản lý kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}/variances?onlyDifferences=true` | 200, linesWithVariance 1, totalDecreaseQuantity 3, netVarianceQuantity -3; dòng: systemQuantity 40, countedQuantity 37, varianceQuantity -3 | `20-outbound-transfer-stocktake` / TC-STK-01.17 Chênh lệch giảm 3 | chưa chạy |
| TC-STK-01.18 | Ghi lý do chênh lệch | Quản lý kho của A gọi `PUT /api/inventory/stocktakes/{{o_stk}}/lines/{{o_stkLine}}/reason` | 200, reason được lưu | `20-outbound-transfer-stocktake` / TC-STK-01.18 Ghi lý do chênh lệch | chưa chạy |
| TC-STK-01.19 | Quản lý kho duyệt cấp một | Quản lý kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/approve` | 200, status PENDING_ACCOUNTING, adjustmentStatus PENDING_APPROVAL | `20-outbound-transfer-stocktake` / TC-STK-01.19 Quản lý kho duyệt cấp một | chưa chạy |
| TC-STK-01.20 | Chưa duyệt cấp kế toán thì tồn chưa đổi | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodStk}}&warehouseId={{o_whS}}` | 200, onHandQuantity 40 | `20-outbound-transfer-stocktake` / TC-STK-01.20 Chưa duyệt cấp kế toán thì tồn chưa đổi | chưa chạy |
| TC-STK-01.21 | Quản lý kho không được duyệt cấp kế toán | Quản lý kho của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/adjustment/approve` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-STK-01.21 Quản lý kho không được duyệt cấp kế toán | chưa chạy |
| TC-STK-01.22 | Kế toán duyệt phiếu điều chỉnh | Kế toán của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/adjustment/approve` | 200, status COMPLETED, adjustmentStatus APPROVED | `20-outbound-transfer-stocktake` / TC-STK-01.22 Kế toán duyệt phiếu điều chỉnh | chưa chạy |
| TC-STK-01.23 | Tồn được điều chỉnh về 37 | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodStk}}&warehouseId={{o_whS}}` | 200, onHandQuantity 37 | `20-outbound-transfer-stocktake` / TC-STK-01.23 Tồn được điều chỉnh về 37 | chưa chạy |
| TC-STK-01.24 | Duyệt lại phiếu đã duyệt | Kế toán của A gọi `POST /api/inventory/stocktakes/{{o_stk}}/adjustment/approve` | 409 CONFLICT | `20-outbound-transfer-stocktake` / TC-STK-01.24 Duyệt lại phiếu đã duyệt | chưa chạy |
| TC-STK-01.25 | Tồn chỉ điều chỉnh một lần | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodStk}}&warehouseId={{o_whS}}` | 200, onHandQuantity vẫn 37 | `20-outbound-transfer-stocktake` / TC-STK-01.25 Tồn chỉ điều chỉnh một lần | chưa chạy |
| TC-STK-01.26 | Phiếu điều chỉnh ghi tồn trước và sau | Kế toán của A gọi `GET /api/inventory/stock-adjustments/{{o_adj}}` | 200, status APPROVED, dòng: differenceQuantity -3, quantityBefore 40, quantityAfter 37, approvedBy = kế toán | `20-outbound-transfer-stocktake` / TC-STK-01.26 Phiếu điều chỉnh ghi tồn trước và sau | chưa chạy |
| TC-STK-01.27 | Vị trí được mở đóng băng | Quản lý kho của A gọi `GET /api/inventory/stocktakes/{{o_stk}}` | 200, locations[0].frozen = false | `20-outbound-transfer-stocktake` / TC-STK-01.27 Vị trí được mở đóng băng | chưa chạy |
| TC-STK-01.28 | Nhận điều chuyển vào vị trí sau kiểm kê | Quản lý kho của A gọi `POST /api/inventory/transfers/{{o_trf2}}/receive` | 200, status COMPLETED | `20-outbound-transfer-stocktake` / TC-STK-01.28 Nhận điều chuyển vào vị trí sau kiểm kê | chưa chạy |

#### TC-STK-02 Kiểm kê: hai cấp duyệt phải khác người, từ chối không đổi tồn

Đợt kiểm kê vị trí QA-WHD-A-R01-B01 do ADMIN thực hiện cả cấp quản lý.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-STK-02.1 | ADMIN lập đợt kiểm kê kho đích | ADMIN của A gọi `POST /api/inventory/stocktakes` | 201, DRAFT | `20-outbound-transfer-stocktake` / TC-STK-02.1 ADMIN lập đợt kiểm kê kho đích | chưa chạy |
| TC-STK-02.2 | ADMIN bắt đầu đếm | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/start` | 200, COUNTING | `20-outbound-transfer-stocktake` / TC-STK-02.2 ADMIN bắt đầu đếm | chưa chạy |
| TC-STK-02.3 | Lấy dòng cần đếm | ADMIN của A gọi `GET /api/inventory/stocktakes/{{o_stk2}}/count-lines` | 200, một dòng hàng QA-FEFO-01 không có số lô (hàng nhận điều chuyển) | `20-outbound-transfer-stocktake` / TC-STK-02.3 Lấy dòng cần đếm | chưa chạy |
| TC-STK-02.4 | Đếm được 9 | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/counts` | 200 | `20-outbound-transfer-stocktake` / TC-STK-02.4 Đếm được 9 | chưa chạy |
| TC-STK-02.5 | Kết thúc đếm | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/finish` | 200, COUNTED | `20-outbound-transfer-stocktake` / TC-STK-02.5 Kết thúc đếm | chưa chạy |
| TC-STK-02.6 | ADMIN duyệt cấp quản lý | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/approve` | 200, PENDING_ACCOUNTING | `20-outbound-transfer-stocktake` / TC-STK-02.6 ADMIN duyệt cấp quản lý | chưa chạy |
| TC-STK-02.7 | Cùng người không được duyệt cấp kế toán | ADMIN của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/adjustment/approve` | 403 FORBIDDEN (người duyệt cấp kế toán phải khác người duyệt cấp quản lý) | `20-outbound-transfer-stocktake` / TC-STK-02.7 Cùng người không được duyệt cấp kế toán | chưa chạy |
| TC-STK-02.8 | Từ chối không có lý do | Kế toán của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/reject` | 400 BAD_REQUEST | `20-outbound-transfer-stocktake` / TC-STK-02.8 Từ chối không có lý do | chưa chạy |
| TC-STK-02.9 | Kế toán từ chối phiếu điều chỉnh | Kế toán của A gọi `POST /api/inventory/stocktakes/{{o_stk2}}/reject` | 200, status REJECTED | `20-outbound-transfer-stocktake` / TC-STK-02.9 Kế toán từ chối phiếu điều chỉnh | chưa chạy |
| TC-STK-02.10 | Từ chối thì tồn giữ nguyên | Quản lý kho của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whD}}` | 200, onHandQuantity 10 (12 nhận - 2 điều chuyển ngược) | `20-outbound-transfer-stocktake` / TC-STK-02.10 Từ chối thì tồn giữ nguyên | chưa chạy |

#### TC-RPT-01 Sổ kho khớp tồn sau toàn bộ luồng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-RPT-01.1 | Lấy tồn khả dụng và các lô của hàng FEFO ở kho nguồn | Kế toán của A gọi `GET /api/inventory/lots?productId={{o_prodFefo}}&warehouseId={{o_whS}}&size=50` | 200; lô khóa và lô hết hạn cộng lại 15 (L-LOCK 10, L-EXP 5); L-LATE còn 25, L-EARLY 0 | `20-outbound-transfer-stocktake` / TC-RPT-01.1 Lấy tồn khả dụng và các lô của hàng FEFO ở kho nguồn | chưa chạy |
| TC-RPT-01.2 | Tồn khả dụng hàng FEFO ở kho nguồn | Kế toán của A gọi `GET /api/inventory/stock/atp?productId={{o_prodFefo}}&warehouseId={{o_whS}}` | 200, onHandQuantity 27 (L-LATE 25 + 2 nhận điều chuyển ngược) | `20-outbound-transfer-stocktake` / TC-RPT-01.2 Tồn khả dụng hàng FEFO ở kho nguồn | chưa chạy |
| TC-RPT-01.3 | Xuất nhập tồn hàng FEFO kho nguồn | Kế toán của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{o_whS}}&productId={{o_prodFefo}}&fromDate={{d_m60}}&toDate={{d_today}}` | 200; opening 0, nhập 87 (85 phiếu nhập + 2 điều chuyển), xuất 45 (30 bán + 15 điều chuyển), tồn cuối 42 = tồn khả dụng + lô khóa, hết hạn | `20-outbound-transfer-stocktake` / TC-RPT-01.3 Xuất nhập tồn hàng FEFO kho nguồn | chưa chạy |
| TC-RPT-01.4 | Thẻ kho hàng FEFO kho nguồn | Quản lý kho của A gọi `GET /api/inventory/reports/stock-card?productId={{o_prodFefo}}&warehouseId={{o_whS}}&fromDate={{d_m60}}&toDate={{d_today}}&size=200` | 200; totalIn 87, totalOut 45, closingQuantity 42; dòng cuối balanceAfter 42; có RECEIPT, SALES_DISPATCH, TRANSFER_OUT, TRANSFER_IN | `20-outbound-transfer-stocktake` / TC-RPT-01.4 Thẻ kho hàng FEFO kho nguồn | chưa chạy |
| TC-RPT-01.5 | Xuất nhập tồn hàng kiểm kê | Kế toán của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{o_whS}}&productId={{o_prodStk}}&fromDate={{d_m60}}&toDate={{d_today}}` | 200; nhập 40, xuất 3 (điều chỉnh giảm), tồn cuối 37 = tồn khả dụng | `20-outbound-transfer-stocktake` / TC-RPT-01.5 Xuất nhập tồn hàng kiểm kê | chưa chạy |
| TC-RPT-01.6 | Xuất nhập tồn kho đích | Kế toán của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{o_whD}}&productId={{o_prodFefo}}&fromDate={{d_m60}}&toDate={{d_today}}` | 200; nhập 12, xuất 2, tồn cuối 10 (đợt kiểm kê bị từ chối không ghi sổ) | `20-outbound-transfer-stocktake` / TC-RPT-01.6 Xuất nhập tồn kho đích | chưa chạy |
| TC-RPT-01.7 | Bán hàng không xem được báo cáo kho | Bán hàng của A gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{o_whS}}&productId={{o_prodFefo}}&fromDate={{d_m60}}&toDate={{d_today}}` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-RPT-01.7 Bán hàng không xem được báo cáo kho | chưa chạy |
| TC-RPT-01.8 | Truy vết lô L-LATE | Quản lý kho của A gọi `GET /api/inventory/reports/lot-trace?lotNo=L-LATE&productId={{o_prodFefo}}` | 200; nhận 50, xuất bán 10, điều chuyển 15, tồn 25; nguồn gốc là phiếu nhập; nơi đi gồm đơn bán và lệnh điều chuyển | `20-outbound-transfer-stocktake` / TC-RPT-01.8 Truy vết lô L-LATE | chưa chạy |

#### TC-RPT-02 Truy vết lô nhận ở collection 10

Cần chạy 10-inbound trước với cùng runId.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-RPT-02.1 | Tra hàng hóa QA-SUA-01 của collection 10 | ADMIN của A gọi `GET /api/products/sku/QA-SUA-01` | 200 (10-inbound đã chạy) | `20-outbound-transfer-stocktake` / TC-RPT-02.1 Tra hàng hóa QA-SUA-01 của collection 10 | chưa chạy |
| TC-RPT-02.2 | Truy vết lô LOT-A1 | Kế toán của A gọi `GET /api/inventory/reports/lot-trace?lotNo=LOT-A1&productId={{o_prod10}}` | 200; nguồn gốc RECEIPT từ phiếu nhập lần 1 của đơn mua ở collection 10; totalReceived 60; currentOnHand 60; cây có nút GOODS_RECEIPT, PURCHASE_ORDER, SUPPLIER | `20-outbound-transfer-stocktake` / TC-RPT-02.2 Truy vết lô LOT-A1 | chưa chạy |
| TC-RPT-02.3 | Bán hàng không truy vết được lô | Bán hàng của A gọi `GET /api/inventory/reports/lot-trace?lotNo=LOT-A1` | 403 FORBIDDEN | `20-outbound-transfer-stocktake` / TC-RPT-02.3 Bán hàng không truy vết được lô | chưa chạy |

## 5. WMS-22.5 Cách ly doanh nghiệp, giới hạn gói, hết hạn và API key (30-isolation-plan-expiry)

Phần cách ly lấy ID dữ liệu của A bằng API danh sách nên cần chạy 10 và 20 trước với cùng `runId`. Doanh nghiệp C được
đăng ký ngay trong collection: chuyển TRIAL về FREE để thử giới hạn, đặt hết hạn bằng công cụ demo, thanh toán giả lập
lên PRO rồi thử API key.

#### TC-ISO-00 Đăng nhập và lấy ID dữ liệu của A

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-00.1 | Kiểm tra runId của lượt chạy | Kiểm tra biến môi trường `runId` đã được truyền (`--env-var runId=...`), rồi gọi `GET /api/auth/plans` | runId có giá trị (cùng runId đã dùng cho 00-setup); API công khai trả 200 | `30-isolation-plan-expiry` / TC-ISO-00.1 Kiểm tra runId của lượt chạy | chưa chạy |
| TC-ISO-00.2 | Đăng nhập ADMIN của công ty A | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty A (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp A | `30-isolation-plan-expiry` / TC-ISO-00.2 Đăng nhập ADMIN của công ty A | chưa chạy |
| TC-ISO-00.3 | Đăng nhập ADMIN của công ty B | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty B (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp B | `30-isolation-plan-expiry` / TC-ISO-00.3 Đăng nhập ADMIN của công ty B | chưa chạy |
| TC-ISO-00.4 | Tra ID tài khoản WAREHOUSE_MANAGER của A | ADMIN của A gọi `GET /api/auth/users?keyword={{lookupUser}}&size=5` | 200, danh sách có đúng tài khoản WAREHOUSE_MANAGER vừa tạo | `30-isolation-plan-expiry` / TC-ISO-00.4 Tra ID tài khoản WAREHOUSE_MANAGER của A | chưa chạy |
| TC-ISO-00.5 | Kho của A | ADMIN của A gọi `GET /api/inventory/warehouses` | 200, A có ít nhất một kho | `30-isolation-plan-expiry` / TC-ISO-00.5 Kho của A | chưa chạy |
| TC-ISO-00.6 | Vị trí của A | ADMIN của A gọi `GET /api/inventory/locations?warehouseId={{A_wh}}` | 200, A có ít nhất một vị trí | `30-isolation-plan-expiry` / TC-ISO-00.6 Vị trí của A | chưa chạy |
| TC-ISO-00.7 | Hàng hóa của A | ADMIN của A gọi `GET /api/products?size=5` | 200, A có ít nhất một hàng hóa | `30-isolation-plan-expiry` / TC-ISO-00.7 Hàng hóa của A | chưa chạy |
| TC-ISO-00.8 | Danh mục của A | ADMIN của A gọi `GET /api/products/categories` | 200, A có ít nhất một danh mục | `30-isolation-plan-expiry` / TC-ISO-00.8 Danh mục của A | chưa chạy |
| TC-ISO-00.9 | Đơn vị tính của A | ADMIN của A gọi `GET /api/products/uoms` | 200, A có ít nhất một đơn vị tính | `30-isolation-plan-expiry` / TC-ISO-00.9 Đơn vị tính của A | chưa chạy |
| TC-ISO-00.10 | Nhà cung cấp của A | ADMIN của A gọi `GET /api/orders/suppliers` | 200, A có ít nhất một nhà cung cấp | `30-isolation-plan-expiry` / TC-ISO-00.10 Nhà cung cấp của A | chưa chạy |
| TC-ISO-00.11 | Khách hàng của A | ADMIN của A gọi `GET /api/orders/customers` | 200, A có ít nhất một khách hàng | `30-isolation-plan-expiry` / TC-ISO-00.11 Khách hàng của A | chưa chạy |
| TC-ISO-00.12 | Đơn mua của A | ADMIN của A gọi `GET /api/orders/purchase` | 200, A có ít nhất một đơn mua | `30-isolation-plan-expiry` / TC-ISO-00.12 Đơn mua của A | chưa chạy |
| TC-ISO-00.13 | Đơn bán của A | ADMIN của A gọi `GET /api/orders/sales` | 200, A có ít nhất một đơn bán | `30-isolation-plan-expiry` / TC-ISO-00.13 Đơn bán của A | chưa chạy |
| TC-ISO-00.14 | Phiếu nhập của A | ADMIN của A gọi `GET /api/orders/goods-receipt-notes` | 200, A có ít nhất một phiếu nhập | `30-isolation-plan-expiry` / TC-ISO-00.14 Phiếu nhập của A | chưa chạy |
| TC-ISO-00.15 | Phiếu xuất của A | ADMIN của A gọi `GET /api/orders/goods-delivery-notes` | 200, A có ít nhất một phiếu xuất | `30-isolation-plan-expiry` / TC-ISO-00.15 Phiếu xuất của A | chưa chạy |
| TC-ISO-00.16 | Hóa đơn mua của A | ADMIN của A gọi `GET /api/orders/purchase-bills` | 200, A có ít nhất một hóa đơn mua | `30-isolation-plan-expiry` / TC-ISO-00.16 Hóa đơn mua của A | chưa chạy |
| TC-ISO-00.17 | Lệnh điều chuyển của A | ADMIN của A gọi `GET /api/inventory/transfers` | 200, A có ít nhất một lệnh điều chuyển | `30-isolation-plan-expiry` / TC-ISO-00.17 Lệnh điều chuyển của A | chưa chạy |
| TC-ISO-00.18 | Đợt kiểm kê của A | ADMIN của A gọi `GET /api/inventory/stocktakes` | 200, A có ít nhất một đợt kiểm kê | `30-isolation-plan-expiry` / TC-ISO-00.18 Đợt kiểm kê của A | chưa chạy |
| TC-ISO-00.19 | Phiếu điều chỉnh của A | ADMIN của A gọi `GET /api/inventory/stock-adjustments` | 200, A có ít nhất một phiếu điều chỉnh | `30-isolation-plan-expiry` / TC-ISO-00.19 Phiếu điều chỉnh của A | chưa chạy |
| TC-ISO-00.20 | Lô còn hàng, chưa khóa của A | ADMIN của A gọi `GET /api/inventory/lots?hasStock=true&locked=false&size=5` | 200, A có ít nhất một lô | `30-isolation-plan-expiry` / TC-ISO-00.20 Lô còn hàng, chưa khóa của A | chưa chạy |
| TC-ISO-00.21 | Tồn của hàng hóa A | ADMIN của A gọi `GET /api/inventory/stock/atp?productId={{A_lotProd}}&warehouseId={{A_lotWh}}` | 200, onHandQuantity > 0 | `30-isolation-plan-expiry` / TC-ISO-00.21 Tồn của hàng hóa A | chưa chạy |
| TC-ISO-00.22 | A tạo API key | ADMIN của A gọi `POST /api/auth/api-keys` | 201 (gói TRIAL có quyền API) | `30-isolation-plan-expiry` / TC-ISO-00.22 A tạo API key | chưa chạy |
| TC-ISO-00.23 | A tạo giao dịch thanh toán chờ | ADMIN của A gọi `POST /api/auth/payments` | 201, status PENDING, amount 499000 | `30-isolation-plan-expiry` / TC-ISO-00.23 A tạo giao dịch thanh toán chờ | chưa chạy |
| TC-ISO-00.24 | A trích xuất file CSV bằng AI | ADMIN của A gọi `POST /api/ai/extractions`; multipart `file` = qa/postman/files/hang-hoa-qa.csv, `dataType` = PRODUCT (chạy với --working-dir qa/postman) | 200, extractionId, method HEADER_MATCHING khi AI_MOCK=true, 2 dòng | `30-isolation-plan-expiry` / TC-ISO-00.24 A trích xuất file CSV bằng AI | chưa chạy |
| TC-ISO-00.25 | A hỏi trợ lý về tồn của hàng hóa | ADMIN của A gọi `POST /api/ai/chat` | 200, có conversationId | `30-isolation-plan-expiry` / TC-ISO-00.25 A hỏi trợ lý về tồn của hàng hóa | chưa chạy |
| TC-ISO-00.26 | KPI thời gian thực của A | ADMIN của A gọi `GET /api/notifications/realtime/kpi` | 200 (ghi lại để so sánh) | `30-isolation-plan-expiry` / TC-ISO-00.26 KPI thời gian thực của A | chưa chạy |

#### TC-ISO-01 Cách ly: người dùng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-01.1 | B xem danh sách người dùng | ADMIN của B gọi `GET /api/auth/users?size=100` | 200, chỉ có người dùng của B | `30-isolation-plan-expiry` / TC-ISO-01.1 B xem danh sách người dùng | chưa chạy |
| TC-ISO-01.2 | B xem người dùng của A theo ID | ADMIN của B gọi `GET /api/auth/users/{{uid_A_MANAGER}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-01.2 B xem người dùng của A theo ID | chưa chạy |
| TC-ISO-01.3 | B sửa người dùng của A | ADMIN của B gọi `PUT /api/auth/users/{{uid_A_MANAGER}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-01.3 B sửa người dùng của A | chưa chạy |
| TC-ISO-01.4 | B khóa người dùng của A | ADMIN của B gọi `PATCH /api/auth/users/{{uid_A_MANAGER}}/status` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-01.4 B khóa người dùng của A | chưa chạy |
| TC-ISO-01.5 | B gán kho cho người dùng của A | ADMIN của B gọi `PUT /api/auth/users/{{uid_A_MANAGER}}/warehouses` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-01.5 B gán kho cho người dùng của A | chưa chạy |
| TC-ISO-01.6 | Người dùng của A không đổi | ADMIN của A gọi `GET /api/auth/users/{{uid_A_MANAGER}}` | 200, vẫn bật, vai trò WAREHOUSE_MANAGER | `30-isolation-plan-expiry` / TC-ISO-01.6 Người dùng của A không đổi | chưa chạy |

#### TC-ISO-02 Cách ly: kho

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-02.1 | B xem danh sách kho | ADMIN của B gọi `GET /api/inventory/warehouses` | 200, không có kho của A | `30-isolation-plan-expiry` / TC-ISO-02.1 B xem danh sách kho | chưa chạy |
| TC-ISO-02.2 | B xem kho của A | ADMIN của B gọi `GET /api/inventory/warehouses/{{A_wh}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-02.2 B xem kho của A | chưa chạy |
| TC-ISO-02.3 | B sửa kho của A | ADMIN của B gọi `PUT /api/inventory/warehouses/{{A_wh}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-02.3 B sửa kho của A | chưa chạy |
| TC-ISO-02.4 | B ngưng kho của A | ADMIN của B gọi `PATCH /api/inventory/warehouses/{{A_wh}}/status` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-02.4 B ngưng kho của A | chưa chạy |
| TC-ISO-02.5 | B xóa kho của A | ADMIN của B gọi `DELETE /api/inventory/warehouses/{{A_wh}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-02.5 B xóa kho của A | chưa chạy |
| TC-ISO-02.6 | Kho của A không đổi | ADMIN của A gọi `GET /api/inventory/warehouses/{{A_wh}}` | 200, tên và trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-02.6 Kho của A không đổi | chưa chạy |

#### TC-ISO-03 Cách ly: vị trí

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-03.1 | B lọc vị trí theo kho của A | ADMIN của B gọi `GET /api/inventory/locations?warehouseId={{A_wh}}` | 200 rỗng hoặc 404 NOT_FOUND, không lộ vị trí của A | `30-isolation-plan-expiry` / TC-ISO-03.1 B lọc vị trí theo kho của A | chưa chạy |
| TC-ISO-03.2 | B xem vị trí của A | ADMIN của B gọi `GET /api/inventory/locations/{{A_loc}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-03.2 B xem vị trí của A | chưa chạy |
| TC-ISO-03.3 | B sửa vị trí của A | ADMIN của B gọi `PUT /api/inventory/locations/{{A_loc}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-03.3 B sửa vị trí của A | chưa chạy |
| TC-ISO-03.4 | B xóa vị trí của A | ADMIN của B gọi `DELETE /api/inventory/locations/{{A_loc}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-03.4 B xóa vị trí của A | chưa chạy |
| TC-ISO-03.5 | Vị trí của A không đổi | ADMIN của A gọi `GET /api/inventory/locations/{{A_loc}}` | 200, vẫn hoạt động, đúng mã | `30-isolation-plan-expiry` / TC-ISO-03.5 Vị trí của A không đổi | chưa chạy |

#### TC-ISO-04 Cách ly: tồn kho

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-04.1 | B đọc tồn của hàng hóa A ở kho A | ADMIN của B gọi `GET /api/inventory/stock/atp?productId={{A_lotProd}}&warehouseId={{A_lotWh}}` | 200 với onHandQuantity 0 hoặc 404; không lộ số tồn của A | `30-isolation-plan-expiry` / TC-ISO-04.1 B đọc tồn của hàng hóa A ở kho A | chưa chạy |
| TC-ISO-04.2 | B đọc tồn theo lô sản phẩm của A | ADMIN của B gọi `POST /api/inventory/stock/atp/batch` | 200, tồn bằng 0 | `30-isolation-plan-expiry` / TC-ISO-04.2 B đọc tồn theo lô sản phẩm của A | chưa chạy |
| TC-ISO-04.3 | B trừ tồn ở kho của A | ADMIN của B gọi `POST /api/inventory/stock/deduct` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-04.3 B trừ tồn ở kho của A | chưa chạy |
| TC-ISO-04.4 | Tồn của A không đổi | ADMIN của A gọi `GET /api/inventory/stock/atp?productId={{A_lotProd}}&warehouseId={{A_lotWh}}` | 200, onHandQuantity như trước | `30-isolation-plan-expiry` / TC-ISO-04.4 Tồn của A không đổi | chưa chạy |

#### TC-ISO-05 Cách ly: lô hàng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-05.1 | B lọc lô theo hàng hóa của A | ADMIN của B gọi `GET /api/inventory/lots?productId={{A_lotProd}}` | 200, totalElements 0 | `30-isolation-plan-expiry` / TC-ISO-05.1 B lọc lô theo hàng hóa của A | chưa chạy |
| TC-ISO-05.2 | B tìm lô theo số lô của A | ADMIN của B gọi `GET /api/inventory/lots?lotNo={{A_lotNo}}` | 200, không có lô của A | `30-isolation-plan-expiry` / TC-ISO-05.2 B tìm lô theo số lô của A | chưa chạy |
| TC-ISO-05.3 | B khóa lô của A | ADMIN của B gọi `PUT /api/inventory/lots/{{A_lot}}/lock` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-05.3 B khóa lô của A | chưa chạy |
| TC-ISO-05.4 | Lô của A vẫn chưa khóa | ADMIN của A gọi `GET /api/inventory/lots?productId={{A_lotProd}}&lotNo={{A_lotNo}}` | 200, lô locked = false | `30-isolation-plan-expiry` / TC-ISO-05.4 Lô của A vẫn chưa khóa | chưa chạy |

#### TC-ISO-06 Cách ly: hàng hóa

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-06.1 | B xem danh sách hàng hóa | ADMIN của B gọi `GET /api/products?size=100` | 200, không có hàng của A | `30-isolation-plan-expiry` / TC-ISO-06.1 B xem danh sách hàng hóa | chưa chạy |
| TC-ISO-06.2 | B xem hàng hóa của A | ADMIN của B gọi `GET /api/products/{{A_prod}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-06.2 B xem hàng hóa của A | chưa chạy |
| TC-ISO-06.3 | B tra SKU của A | ADMIN của B gọi `GET /api/products/sku/{{A_prodSku}}` | 404 NOT_FOUND (B chưa có SKU này) | `30-isolation-plan-expiry` / TC-ISO-06.3 B tra SKU của A | chưa chạy |
| TC-ISO-06.4 | B sửa hàng hóa của A | ADMIN của B gọi `PUT /api/products/{{A_prod}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-06.4 B sửa hàng hóa của A | chưa chạy |
| TC-ISO-06.5 | B ngưng hàng hóa của A | ADMIN của B gọi `DELETE /api/products/{{A_prod}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-06.5 B ngưng hàng hóa của A | chưa chạy |
| TC-ISO-06.6 | Hàng hóa của A không đổi | ADMIN của A gọi `GET /api/products/{{A_prod}}` | 200, tên, SKU, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-06.6 Hàng hóa của A không đổi | chưa chạy |

#### TC-ISO-07 Cách ly: danh mục

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-07.1 | B xem danh mục | ADMIN của B gọi `GET /api/products/categories` | 200, không có danh mục của A | `30-isolation-plan-expiry` / TC-ISO-07.1 B xem danh mục | chưa chạy |
| TC-ISO-07.2 | B xem danh mục của A | ADMIN của B gọi `GET /api/products/categories/{{A_cat}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-07.2 B xem danh mục của A | chưa chạy |
| TC-ISO-07.3 | B gắn danh mục của A vào hàng hóa mới | ADMIN của B gọi `POST /api/products`; danh mục không có API sửa, xóa: thử dùng ID danh mục của A khi tạo hàng hóa | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-07.3 B gắn danh mục của A vào hàng hóa mới | chưa chạy |

#### TC-ISO-08 Cách ly: đơn vị tính

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-08.1 | B xem đơn vị tính | ADMIN của B gọi `GET /api/products/uoms` | 200, không có đơn vị tính của A | `30-isolation-plan-expiry` / TC-ISO-08.1 B xem đơn vị tính | chưa chạy |
| TC-ISO-08.2 | B xem đơn vị tính của A | ADMIN của B gọi `GET /api/products/uoms/{{A_uom}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-08.2 B xem đơn vị tính của A | chưa chạy |
| TC-ISO-08.3 | B dùng đơn vị tính của A | ADMIN của B gọi `POST /api/products`; đơn vị tính không có API sửa, xóa: thử dùng ID của A khi tạo hàng hóa | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-08.3 B dùng đơn vị tính của A | chưa chạy |

#### TC-ISO-09 Cách ly: nhà cung cấp

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-09.1 | B xem danh sách nhà cung cấp | ADMIN của B gọi `GET /api/orders/suppliers?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-09.1 B xem danh sách nhà cung cấp | chưa chạy |
| TC-ISO-09.2 | B xem nhà cung cấp của A | ADMIN của B gọi `GET /api/orders/suppliers/{{A_sup}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.2 B xem nhà cung cấp của A | chưa chạy |
| TC-ISO-09.3 | B xem công nợ nhà cung cấp của A | ADMIN của B gọi `GET /api/orders/suppliers/{{A_sup}}/transactions` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.3 B xem công nợ nhà cung cấp của A | chưa chạy |
| TC-ISO-09.4 | B sửa nhà cung cấp của A | ADMIN của B gọi `PUT /api/orders/suppliers/{{A_sup}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.4 B sửa nhà cung cấp của A | chưa chạy |
| TC-ISO-09.5 | B xóa nhà cung cấp của A | ADMIN của B gọi `DELETE /api/orders/suppliers/{{A_sup}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.5 B xóa nhà cung cấp của A | chưa chạy |
| TC-ISO-09.6 | B ghi công nợ nhà cung cấp của A | ADMIN của B gọi `POST /api/orders/suppliers/{{A_sup}}/transactions` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-09.6 B ghi công nợ nhà cung cấp của A | chưa chạy |
| TC-ISO-09.7 | Nhà cung cấp của A không đổi | ADMIN của A gọi `GET /api/orders/suppliers/{{A_sup}}` | 200, tên giữ nguyên, còn hoạt động | `30-isolation-plan-expiry` / TC-ISO-09.7 Nhà cung cấp của A không đổi | chưa chạy |

#### TC-ISO-10 Cách ly: khách hàng

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-10.1 | B xem danh sách khách hàng | ADMIN của B gọi `GET /api/orders/customers?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-10.1 B xem danh sách khách hàng | chưa chạy |
| TC-ISO-10.2 | B xem khách hàng của A | ADMIN của B gọi `GET /api/orders/customers/{{A_cus}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-10.2 B xem khách hàng của A | chưa chạy |
| TC-ISO-10.3 | B kiểm tra hạn mức khách hàng của A | ADMIN của B gọi `GET /api/orders/customers/{{A_cus}}/credit-check?orderAmount=1000` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-10.3 B kiểm tra hạn mức khách hàng của A | chưa chạy |
| TC-ISO-10.4 | B sửa khách hàng của A | ADMIN của B gọi `PUT /api/orders/customers/{{A_cus}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-10.4 B sửa khách hàng của A | chưa chạy |
| TC-ISO-10.5 | B xóa khách hàng của A | ADMIN của B gọi `DELETE /api/orders/customers/{{A_cus}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-10.5 B xóa khách hàng của A | chưa chạy |
| TC-ISO-10.6 | Khách hàng của A không đổi | ADMIN của A gọi `GET /api/orders/customers/{{A_cus}}` | 200, tên giữ nguyên, không bị khóa nợ | `30-isolation-plan-expiry` / TC-ISO-10.6 Khách hàng của A không đổi | chưa chạy |

#### TC-ISO-11 Cách ly: đơn mua

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-11.1 | B xem danh sách đơn mua | ADMIN của B gọi `GET /api/orders/purchase?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-11.1 B xem danh sách đơn mua | chưa chạy |
| TC-ISO-11.2 | B xem đơn mua của A | ADMIN của B gọi `GET /api/orders/purchase/{{A_po}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.2 B xem đơn mua của A | chưa chạy |
| TC-ISO-11.3 | B tra số đơn mua của A | ADMIN của B gọi `GET /api/orders/purchase/number/{{A_poNumber}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.3 B tra số đơn mua của A | chưa chạy |
| TC-ISO-11.4 | B duyệt đơn mua của A | ADMIN của B gọi `POST /api/orders/purchase/{{A_po}}/approve` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.4 B duyệt đơn mua của A | chưa chạy |
| TC-ISO-11.5 | B hủy đơn mua của A | ADMIN của B gọi `POST /api/orders/purchase/{{A_po}}/cancel` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.5 B hủy đơn mua của A | chưa chạy |
| TC-ISO-11.6 | B xóa đơn mua của A | ADMIN của B gọi `DELETE /api/orders/purchase/{{A_po}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-11.6 B xóa đơn mua của A | chưa chạy |
| TC-ISO-11.7 | Đơn mua của A không đổi | ADMIN của A gọi `GET /api/orders/purchase/{{A_po}}` | 200, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-11.7 Đơn mua của A không đổi | chưa chạy |

#### TC-ISO-12 Cách ly: đơn bán

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-12.1 | B xem danh sách đơn bán | ADMIN của B gọi `GET /api/orders/sales?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-12.1 B xem danh sách đơn bán | chưa chạy |
| TC-ISO-12.2 | B xem đơn bán của A | ADMIN của B gọi `GET /api/orders/sales/{{A_so}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.2 B xem đơn bán của A | chưa chạy |
| TC-ISO-12.3 | B tra số đơn bán của A | ADMIN của B gọi `GET /api/orders/sales/number/{{A_soNumber}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.3 B tra số đơn bán của A | chưa chạy |
| TC-ISO-12.4 | B xem danh sách lấy hàng của đơn A | ADMIN của B gọi `GET /api/orders/sales/{{A_so}}/picking-list` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.4 B xem danh sách lấy hàng của đơn A | chưa chạy |
| TC-ISO-12.5 | B xác nhận đơn bán của A | ADMIN của B gọi `POST /api/orders/sales/{{A_so}}/confirm` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.5 B xác nhận đơn bán của A | chưa chạy |
| TC-ISO-12.6 | B hủy đơn bán của A | ADMIN của B gọi `POST /api/orders/sales/{{A_so}}/cancel` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.6 B hủy đơn bán của A | chưa chạy |
| TC-ISO-12.7 | B xóa đơn bán của A | ADMIN của B gọi `DELETE /api/orders/sales/{{A_so}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.7 B xóa đơn bán của A | chưa chạy |
| TC-ISO-12.8 | B lập phiếu xuất cho đơn bán của A | ADMIN của B gọi `POST /api/orders/goods-delivery-notes` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-12.8 B lập phiếu xuất cho đơn bán của A | chưa chạy |
| TC-ISO-12.9 | Đơn bán của A không đổi | ADMIN của A gọi `GET /api/orders/sales/{{A_so}}` | 200, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-12.9 Đơn bán của A không đổi | chưa chạy |

#### TC-ISO-13 Cách ly: phiếu nhập

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-13.1 | B xem danh sách phiếu nhập | ADMIN của B gọi `GET /api/orders/goods-receipt-notes?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-13.1 B xem danh sách phiếu nhập | chưa chạy |
| TC-ISO-13.2 | B xem phiếu nhập của A | ADMIN của B gọi `GET /api/orders/goods-receipt-notes/{{A_grn}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-13.2 B xem phiếu nhập của A | chưa chạy |
| TC-ISO-13.3 | B tra số phiếu nhập của A | ADMIN của B gọi `GET /api/orders/goods-receipt-notes/number/{{A_grnNumber}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-13.3 B tra số phiếu nhập của A | chưa chạy |
| TC-ISO-13.4 | B xác nhận phiếu nhập của A | ADMIN của B gọi `POST /api/orders/goods-receipt-notes/{{A_grn}}/confirm` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-13.4 B xác nhận phiếu nhập của A | chưa chạy |
| TC-ISO-13.5 | B lập phiếu nhập cho đơn mua của A | ADMIN của B gọi `POST /api/orders/goods-receipt-notes` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-13.5 B lập phiếu nhập cho đơn mua của A | chưa chạy |

#### TC-ISO-14 Cách ly: phiếu xuất

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-14.1 | B xem danh sách phiếu xuất | ADMIN của B gọi `GET /api/orders/goods-delivery-notes?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-14.1 B xem danh sách phiếu xuất | chưa chạy |
| TC-ISO-14.2 | B xem phiếu xuất của A | ADMIN của B gọi `GET /api/orders/goods-delivery-notes/{{A_gdn}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-14.2 B xem phiếu xuất của A | chưa chạy |
| TC-ISO-14.3 | B tra số phiếu xuất của A | ADMIN của B gọi `GET /api/orders/goods-delivery-notes/number/{{A_gdnNumber}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-14.3 B tra số phiếu xuất của A | chưa chạy |
| TC-ISO-14.4 | B xác nhận xuất phiếu của A | ADMIN của B gọi `POST /api/orders/goods-delivery-notes/{{A_gdn}}/dispatch` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-14.4 B xác nhận xuất phiếu của A | chưa chạy |

#### TC-ISO-15 Cách ly: hóa đơn mua và thanh toán nhà cung cấp

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-15.1 | B xem danh sách hóa đơn mua | ADMIN của B gọi `GET /api/orders/purchase-bills?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-15.1 B xem danh sách hóa đơn mua | chưa chạy |
| TC-ISO-15.2 | B xem hóa đơn mua của A | ADMIN của B gọi `GET /api/orders/purchase-bills/{{A_bill}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-15.2 B xem hóa đơn mua của A | chưa chạy |
| TC-ISO-15.3 | B ghi thanh toán hóa đơn của A | ADMIN của B gọi `POST /api/orders/purchase-bills/{{A_bill}}/payments` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-15.3 B ghi thanh toán hóa đơn của A | chưa chạy |
| TC-ISO-15.4 | Hóa đơn của A không đổi | ADMIN của A gọi `GET /api/orders/purchase-bills/{{A_bill}}` | 200, paidAmount giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-15.4 Hóa đơn của A không đổi | chưa chạy |

#### TC-ISO-16 Cách ly: điều chuyển

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-16.1 | B xem danh sách điều chuyển | ADMIN của B gọi `GET /api/inventory/transfers?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-16.1 B xem danh sách điều chuyển | chưa chạy |
| TC-ISO-16.2 | B xem lệnh điều chuyển của A | ADMIN của B gọi `GET /api/inventory/transfers/{{A_trf}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.2 B xem lệnh điều chuyển của A | chưa chạy |
| TC-ISO-16.3 | B sửa lệnh điều chuyển của A | ADMIN của B gọi `PUT /api/inventory/transfers/{{A_trf}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.3 B sửa lệnh điều chuyển của A | chưa chạy |
| TC-ISO-16.4 | B hủy lệnh điều chuyển của A | ADMIN của B gọi `DELETE /api/inventory/transfers/{{A_trf}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.4 B hủy lệnh điều chuyển của A | chưa chạy |
| TC-ISO-16.5 | B xuất lệnh điều chuyển của A | ADMIN của B gọi `POST /api/inventory/transfers/{{A_trf}}/dispatch` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.5 B xuất lệnh điều chuyển của A | chưa chạy |
| TC-ISO-16.6 | B nhận lệnh điều chuyển của A | ADMIN của B gọi `POST /api/inventory/transfers/{{A_trf}}/receive` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-16.6 B nhận lệnh điều chuyển của A | chưa chạy |
| TC-ISO-16.7 | Lệnh điều chuyển của A không đổi | ADMIN của A gọi `GET /api/inventory/transfers/{{A_trf}}` | 200, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-16.7 Lệnh điều chuyển của A không đổi | chưa chạy |

#### TC-ISO-17 Cách ly: kiểm kê và phiếu điều chỉnh

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-17.1 | B xem danh sách đợt kiểm kê | ADMIN của B gọi `GET /api/inventory/stocktakes?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-17.1 B xem danh sách đợt kiểm kê | chưa chạy |
| TC-ISO-17.2 | B xem đợt kiểm kê của A | ADMIN của B gọi `GET /api/inventory/stocktakes/{{A_stk}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-17.2 B xem đợt kiểm kê của A | chưa chạy |
| TC-ISO-17.3 | B xem dòng đếm của A | ADMIN của B gọi `GET /api/inventory/stocktakes/{{A_stk}}/count-lines` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-17.3 B xem dòng đếm của A | chưa chạy |
| TC-ISO-17.4 | B hủy đợt kiểm kê của A | ADMIN của B gọi `POST /api/inventory/stocktakes/{{A_stk}}/cancel` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-17.4 B hủy đợt kiểm kê của A | chưa chạy |
| TC-ISO-17.5 | B xem danh sách phiếu điều chỉnh | ADMIN của B gọi `GET /api/inventory/stock-adjustments?size=100` | 200 | `30-isolation-plan-expiry` / TC-ISO-17.5 B xem danh sách phiếu điều chỉnh | chưa chạy |
| TC-ISO-17.6 | B xem phiếu điều chỉnh của A | ADMIN của B gọi `GET /api/inventory/stock-adjustments/{{A_adj}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-17.6 B xem phiếu điều chỉnh của A | chưa chạy |
| TC-ISO-17.7 | Đợt kiểm kê của A không đổi | ADMIN của A gọi `GET /api/inventory/stocktakes/{{A_stk}}` | 200, trạng thái giữ nguyên | `30-isolation-plan-expiry` / TC-ISO-17.7 Đợt kiểm kê của A không đổi | chưa chạy |

#### TC-ISO-18 Cách ly: báo cáo kho

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-18.1 | B xem báo cáo xuất nhập tồn của mình | ADMIN của B gọi `GET /api/inventory/reports/in-out-balance?fromDate={{d_m60}}&toDate={{d_today}}&size=200` | 200, không có dòng nào của sản phẩm A | `30-isolation-plan-expiry` / TC-ISO-18.1 B xem báo cáo xuất nhập tồn của mình | chưa chạy |
| TC-ISO-18.2 | B xem báo cáo theo kho của A | ADMIN của B gọi `GET /api/inventory/reports/in-out-balance?warehouseId={{A_lotWh}}&fromDate={{d_m60}}&toDate={{d_today}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-18.2 B xem báo cáo theo kho của A | chưa chạy |
| TC-ISO-18.3 | B xem thẻ kho của A | ADMIN của B gọi `GET /api/inventory/reports/stock-card?productId={{A_lotProd}}&warehouseId={{A_lotWh}}&fromDate={{d_m60}}&toDate={{d_today}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-18.3 B xem thẻ kho của A | chưa chạy |
| TC-ISO-18.4 | B truy vết lô của A | ADMIN của B gọi `GET /api/inventory/reports/lot-trace?lotNo={{A_lotNo}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-18.4 B truy vết lô của A | chưa chạy |

#### TC-ISO-19 Cách ly: cảnh báo tồn kho

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-19.1 | B xem cảnh báo của mình | ADMIN của B gọi `GET /api/inventory/alerts?size=100` | 200, không có cảnh báo của kho A | `30-isolation-plan-expiry` / TC-ISO-19.1 B xem cảnh báo của mình | chưa chạy |
| TC-ISO-19.2 | B lọc cảnh báo theo kho của A | ADMIN của B gọi `GET /api/inventory/alerts?warehouseId={{A_lotWh}}` | 200 với totalElements 0 hoặc 404 | `30-isolation-plan-expiry` / TC-ISO-19.2 B lọc cảnh báo theo kho của A | chưa chạy |
| TC-ISO-19.3 | B xem tổng hợp cảnh báo kho của A | ADMIN của B gọi `GET /api/inventory/alerts/summary?warehouseId={{A_lotWh}}` | 200 với total 0 hoặc 404 | `30-isolation-plan-expiry` / TC-ISO-19.3 B xem tổng hợp cảnh báo kho của A | chưa chạy |

#### TC-ISO-20 Cách ly: thông báo và KPI

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-20.1 | KPI thời gian thực của B không đếm sự kiện của A | ADMIN của B gọi `GET /api/notifications/realtime/kpi`; B chưa lập đơn bán, chưa xuất kho; notification-service không có API danh sách thông báo nên kiểm tra qua KPI | 200, salesOrders 0, dispatchedOrders 0 | `30-isolation-plan-expiry` / TC-ISO-20.1 KPI thời gian thực của B không đếm sự kiện của A | chưa chạy |
| TC-ISO-20.2 | B xem KPI dashboard với kho của A | ADMIN của B gọi `GET /api/notifications/dashboard/kpi-summary?warehouseId={{A_lotWh}}` | Không lỗi 5xx; nếu 200 thì không chứa mã, tên kho của A | `30-isolation-plan-expiry` / TC-ISO-20.2 B xem KPI dashboard với kho của A | chưa chạy |

#### TC-ISO-21 Cách ly: API key

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-21.1 | B xem danh sách API key | ADMIN của B gọi `GET /api/auth/api-keys` | 200, không có key của A | `30-isolation-plan-expiry` / TC-ISO-21.1 B xem danh sách API key | chưa chạy |
| TC-ISO-21.2 | B thu hồi API key của A | ADMIN của B gọi `DELETE /api/auth/api-keys/{{A_keyId}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-21.2 B thu hồi API key của A | chưa chạy |
| TC-ISO-21.3 | Key của A vẫn hoạt động | ADMIN của A gọi `GET /api/auth/api-keys` | 200, key của A active = true | `30-isolation-plan-expiry` / TC-ISO-21.3 Key của A vẫn hoạt động | chưa chạy |
| TC-ISO-21.4 | Key của A đọc được hàng hóa của chính A | Hệ thống ngoài (API key) gọi `GET /api/products?size=100` | 200, chỉ có hàng hóa của A (có SKU của A) | `30-isolation-plan-expiry` / TC-ISO-21.4 Key của A đọc được hàng hóa của chính A | chưa chạy |
| TC-ISO-21.5 | A thu hồi key sau kiểm thử | ADMIN của A gọi `DELETE /api/auth/api-keys/{{A_keyId}}` | 200, active false | `30-isolation-plan-expiry` / TC-ISO-21.5 A thu hồi key sau kiểm thử | chưa chạy |

#### TC-ISO-22 Cách ly: thanh toán

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-22.1 | B xem lịch sử thanh toán | ADMIN của B gọi `GET /api/auth/payments?size=100` | 200, không có giao dịch của A | `30-isolation-plan-expiry` / TC-ISO-22.1 B xem lịch sử thanh toán | chưa chạy |
| TC-ISO-22.2 | B xem giao dịch của A | ADMIN của B gọi `GET /api/auth/payments/{{A_payOrder}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-22.2 B xem giao dịch của A | chưa chạy |
| TC-ISO-22.3 | B xác nhận giả lập giao dịch của A | ADMIN của B gọi `POST /api/auth/payments/{{A_payOrder}}/mock-confirm` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-22.3 B xác nhận giả lập giao dịch của A | chưa chạy |
| TC-ISO-22.4 | B hủy giao dịch của A | ADMIN của B gọi `POST /api/auth/payments/{{A_payOrder}}/cancel` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-22.4 B hủy giao dịch của A | chưa chạy |
| TC-ISO-22.5 | Giao dịch của A vẫn chờ, thuê bao A không đổi | ADMIN của A gọi `GET /api/auth/payments/{{A_payOrder}}` | 200, status PENDING | `30-isolation-plan-expiry` / TC-ISO-22.5 Giao dịch của A vẫn chờ, thuê bao A không đổi | chưa chạy |
| TC-ISO-22.6 | A hủy giao dịch sau kiểm thử | ADMIN của A gọi `POST /api/auth/payments/{{A_payOrder}}/cancel` | 200, CANCELLED | `30-isolation-plan-expiry` / TC-ISO-22.6 A hủy giao dịch sau kiểm thử | chưa chạy |
| TC-ISO-22.7 | Thuê bao của B vẫn là dùng thử | ADMIN của B gọi `GET /api/auth/subscription` | 200, plan TRIAL | `30-isolation-plan-expiry` / TC-ISO-22.7 Thuê bao của B vẫn là dùng thử | chưa chạy |

#### TC-ISO-23 Cách ly: trích xuất AI

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-23.1 | B xem kết quả trích xuất của A | ADMIN của B gọi `GET /api/ai/extractions/{{A_ext}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-23.1 B xem kết quả trích xuất của A | chưa chạy |
| TC-ISO-23.2 | B nhập kết quả trích xuất của A | ADMIN của B gọi `POST /api/ai/extractions/{{A_ext}}/import` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-ISO-23.2 B nhập kết quả trích xuất của A | chưa chạy |
| TC-ISO-23.3 | A vẫn xem được kết quả trích xuất | ADMIN của A gọi `GET /api/ai/extractions/{{A_ext}}` | 200 | `30-isolation-plan-expiry` / TC-ISO-23.3 A vẫn xem được kết quả trích xuất | chưa chạy |

#### TC-ISO-24 Cách ly: trợ lý AI

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-24.1 | B dùng mã hội thoại của A | ADMIN của B gọi `POST /api/ai/chat` | 200, newConversation true, conversationId khác của A | `30-isolation-plan-expiry` / TC-ISO-24.1 B dùng mã hội thoại của A | chưa chạy |
| TC-ISO-24.2 | B hỏi tồn của SKU chỉ có ở A | ADMIN của B gọi `POST /api/ai/chat` | 200, câu trả lời không chứa tên hàng của A (không tìm thấy trong dữ liệu của B) | `30-isolation-plan-expiry` / TC-ISO-24.2 B hỏi tồn của SKU chỉ có ở A | chưa chạy |

#### TC-ISO-25 Hai doanh nghiệp dùng trùng mã

B tạo dữ liệu trùng mã với A: được phép (ràng buộc duy nhất theo doanh nghiệp).

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-ISO-25.1 | B tạo đơn vị tính trùng mã với A | ADMIN của B gọi `POST /api/products/uoms` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.1 B tạo đơn vị tính trùng mã với A | chưa chạy |
| TC-ISO-25.2 | B tạo danh mục trùng mã với A | ADMIN của B gọi `POST /api/products/categories` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.2 B tạo danh mục trùng mã với A | chưa chạy |
| TC-ISO-25.3 | B tạo hàng hóa trùng SKU với A | ADMIN của B gọi `POST /api/products` | 201, ID khác hàng của A | `30-isolation-plan-expiry` / TC-ISO-25.3 B tạo hàng hóa trùng SKU với A | chưa chạy |
| TC-ISO-25.4 | Tra SKU trùng trả hàng của chính mình | ADMIN của A gọi `GET /api/products/sku/{{A_prodSku}}` | 200, A nhận hàng của A | `30-isolation-plan-expiry` / TC-ISO-25.4 Tra SKU trùng trả hàng của chính mình | chưa chạy |
| TC-ISO-25.5 | B tạo kho trùng mã với A | ADMIN của B gọi `POST /api/inventory/warehouses` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.5 B tạo kho trùng mã với A | chưa chạy |
| TC-ISO-25.6 | B tạo vị trí trùng mã với A | ADMIN của B gọi `POST /api/inventory/locations`; cùng khu, kệ, ô với vị trí đầu tiên của A trong kho trùng mã | 201, cùng mã vị trí với A | `30-isolation-plan-expiry` / TC-ISO-25.6 B tạo vị trí trùng mã với A | chưa chạy |
| TC-ISO-25.7 | B tạo nhà cung cấp trùng mã với A | ADMIN của B gọi `POST /api/orders/suppliers` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.7 B tạo nhà cung cấp trùng mã với A | chưa chạy |
| TC-ISO-25.8 | B tạo khách hàng trùng mã với A | ADMIN của B gọi `POST /api/orders/customers` | 201 | `30-isolation-plan-expiry` / TC-ISO-25.8 B tạo khách hàng trùng mã với A | chưa chạy |
| TC-ISO-25.9 | B lập đơn mua trùng số chứng từ với A | ADMIN của B gọi `POST /api/orders/purchase` | 201, poNumber trùng với A | `30-isolation-plan-expiry` / TC-ISO-25.9 B lập đơn mua trùng số chứng từ với A | chưa chạy |
| TC-ISO-25.10 | B lập đơn bán trùng số chứng từ với A | ADMIN của B gọi `POST /api/orders/sales` | 201, soNumber trùng với A | `30-isolation-plan-expiry` / TC-ISO-25.10 B lập đơn bán trùng số chứng từ với A | chưa chạy |
| TC-ISO-25.11 | Số đơn mua trùng: mỗi bên thấy đơn của mình | ADMIN của A gọi `GET /api/orders/purchase/number/{{A_poNumber}}` | 200, A nhận đơn của A | `30-isolation-plan-expiry` / TC-ISO-25.11 Số đơn mua trùng: mỗi bên thấy đơn của mình | chưa chạy |
| TC-ISO-25.12 | Tên đăng nhập vẫn duy nhất toàn hệ thống | ADMIN của B gọi `POST /api/auth/users` | 409 CONFLICT (ngoại lệ mục 2.3.5: tên đăng nhập, email duy nhất toàn hệ thống) | `30-isolation-plan-expiry` / TC-ISO-25.12 Tên đăng nhập vẫn duy nhất toàn hệ thống | chưa chạy |

#### TC-PLAN-01 Doanh nghiệp C chuyển về gói FREE

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-01.1 | Đăng ký doanh nghiệp C | Khách gọi `POST /api/auth/companies/register` | 201, TRIAL, activationRequired false | `30-isolation-plan-expiry` / TC-PLAN-01.1 Đăng ký doanh nghiệp C | chưa chạy |
| TC-PLAN-01.2 | Đăng nhập ADMIN của công ty C | Gọi `POST /api/auth/login` bằng tài khoản ADMIN của công ty C (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa ADMIN; company.id đúng doanh nghiệp C | `30-isolation-plan-expiry` / TC-PLAN-01.2 Đăng nhập ADMIN của công ty C | chưa chạy |
| TC-PLAN-01.3 | Doanh nghiệp mới nhận gói dùng thử | ADMIN của C gọi `GET /api/auth/subscription` | 200, plan TRIAL | `30-isolation-plan-expiry` / TC-PLAN-01.3 Doanh nghiệp mới nhận gói dùng thử | chưa chạy |
| TC-PLAN-01.4 | Chuyển về gói Khởi Đầu | ADMIN của C gọi `POST /api/auth/subscription/downgrade` | 200 | `30-isolation-plan-expiry` / TC-PLAN-01.4 Chuyển về gói Khởi Đầu | chưa chạy |
| TC-PLAN-01.5 | Thuê bao FREE với giới hạn đúng | ADMIN của C gọi `GET /api/auth/subscription` | 200, plan FREE, status ACTIVE, limits 3 / 1 / 200 / 20, apiAccess false | `30-isolation-plan-expiry` / TC-PLAN-01.5 Thuê bao FREE với giới hạn đúng | chưa chạy |
| TC-PLAN-01.6 | Đang FREE không chuyển về FREE lần nữa | ADMIN của C gọi `POST /api/auth/subscription/downgrade` | 409 CONFLICT | `30-isolation-plan-expiry` / TC-PLAN-01.6 Đang FREE không chuyển về FREE lần nữa | chưa chạy |

#### TC-PLAN-02 Giới hạn người dùng của gói FREE

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-02.1 | Tạo tài khoản thứ 2 (WAREHOUSE_MANAGER) | ADMIN của C gọi `POST /api/auth/users` | 201 | `30-isolation-plan-expiry` / TC-PLAN-02.1 Tạo tài khoản thứ 2 (WAREHOUSE_MANAGER) | chưa chạy |
| TC-PLAN-02.2 | Tạo tài khoản thứ 3 (WAREHOUSE_STAFF) | ADMIN của C gọi `POST /api/auth/users` | 201 | `30-isolation-plan-expiry` / TC-PLAN-02.2 Tạo tài khoản thứ 3 (WAREHOUSE_STAFF) | chưa chạy |
| TC-PLAN-02.3 | Tạo tài khoản thứ 4 bị chặn | ADMIN của C gọi `POST /api/auth/users` | 403 PLAN_LIMIT_EXCEEDED, data {limit USERS, current 3, max 3, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-02.3 Tạo tài khoản thứ 4 bị chặn | chưa chạy |
| TC-PLAN-02.4 | Mức dùng người dùng là 3 | ADMIN của C gọi `GET /api/auth/subscription` | 200, usage.users 3 | `30-isolation-plan-expiry` / TC-PLAN-02.4 Mức dùng người dùng là 3 | chưa chạy |
| TC-PLAN-02.5 | Đăng nhập WAREHOUSE_MANAGER của công ty C | Gọi `POST /api/auth/login` bằng tài khoản WAREHOUSE_MANAGER của công ty C (tên đăng nhập và mật khẩu suy ra từ runId) | 200, có accessToken; roles chứa WAREHOUSE_MANAGER; company.id đúng doanh nghiệp C | `30-isolation-plan-expiry` / TC-PLAN-02.5 Đăng nhập WAREHOUSE_MANAGER của công ty C | chưa chạy |

#### TC-PLAN-03 Giới hạn kho của gói FREE

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-03.1 | Tạo kho thứ nhất | ADMIN của C gọi `POST /api/inventory/warehouses` | 201 | `30-isolation-plan-expiry` / TC-PLAN-03.1 Tạo kho thứ nhất | chưa chạy |
| TC-PLAN-03.2 | Tạo kho thứ hai bị chặn | ADMIN của C gọi `POST /api/inventory/warehouses` | 403 PLAN_LIMIT_EXCEEDED, data {limit WAREHOUSES, current 1, max 1, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-03.2 Tạo kho thứ hai bị chặn | chưa chạy |

#### TC-PLAN-04 Giới hạn SKU của gói FREE

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-04.1 | Nhập hàng loạt 199 SKU | ADMIN của C gọi `POST /api/products/bulk`; body có 199 dòng QA-PL-001 đến QA-PL-199 | 200, created 199, failed 0 | `30-isolation-plan-expiry` / TC-PLAN-04.1 Nhập hàng loạt 199 SKU | chưa chạy |
| TC-PLAN-04.2 | Lấy đơn vị tính tự tạo | ADMIN của C gọi `GET /api/products/uoms` | 200, có đơn vị Cái do nhập hàng loạt tạo | `30-isolation-plan-expiry` / TC-PLAN-04.2 Lấy đơn vị tính tự tạo | chưa chạy |
| TC-PLAN-04.3 | Tạo SKU thứ 200 | ADMIN của C gọi `POST /api/products` | 201 | `30-isolation-plan-expiry` / TC-PLAN-04.3 Tạo SKU thứ 200 | chưa chạy |
| TC-PLAN-04.4 | Tạo SKU thứ 201 bị chặn | ADMIN của C gọi `POST /api/products` | 403 PLAN_LIMIT_EXCEEDED, data {limit SKUS, current 200, max 200, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-04.4 Tạo SKU thứ 201 bị chặn | chưa chạy |
| TC-PLAN-04.5 | Nhập hàng loạt vượt giới hạn SKU | ADMIN của C gọi `POST /api/products/bulk` | 200, failed 1, dòng FAILED kèm lý do vượt 200 SKU | `30-isolation-plan-expiry` / TC-PLAN-04.5 Nhập hàng loạt vượt giới hạn SKU | chưa chạy |

#### TC-PLAN-05 Giới hạn lượt AI của gói FREE (AI_MOCK=true vẫn tính lượt)

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-05.1 | Mức dùng AI ban đầu | ADMIN của C gọi `GET /api/ai/usage` | 200, used 0, max 20, plan FREE | `30-isolation-plan-expiry` / TC-PLAN-05.1 Mức dùng AI ban đầu | chưa chạy |
| TC-PLAN-05.2 | Hỏi trợ lý cho tới hết lượt | ADMIN của C gọi `POST /api/ai/ask`; lặp lại request tới khi usage.used = usage.max (tối đa 25 lần) | mỗi lần 200 và used tăng 1, dừng ở used = 20 | `30-isolation-plan-expiry` / TC-PLAN-05.2 Hỏi trợ lý cho tới hết lượt | chưa chạy |
| TC-PLAN-05.3 | Lượt AI thứ 21 bị chặn | ADMIN của C gọi `POST /api/ai/ask` | 403 PLAN_LIMIT_EXCEEDED, data {limit AI_REQUESTS, current 20, max 20, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-05.3 Lượt AI thứ 21 bị chặn | chưa chạy |
| TC-PLAN-05.4 | Trợ lý hội thoại cũng bị chặn khi hết lượt | ADMIN của C gọi `POST /api/ai/chat` | 403 PLAN_LIMIT_EXCEEDED, limit AI_REQUESTS | `30-isolation-plan-expiry` / TC-PLAN-05.4 Trợ lý hội thoại cũng bị chặn khi hết lượt | chưa chạy |

#### TC-PLAN-06 Gói FREE không tạo được API key

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-PLAN-06.1 | Tạo API key ở gói FREE | ADMIN của C gọi `POST /api/auth/api-keys` | 403 PLAN_LIMIT_EXCEEDED, data {limit API_ACCESS, current 0, max 0, plan FREE} | `30-isolation-plan-expiry` / TC-PLAN-06.1 Tạo API key ở gói FREE | chưa chạy |

#### TC-EXP-01 Hết hạn: chỉ đọc

Cần APP_DEMO_TOOLS=true cho auth-service (qa/docker-compose.qa.yml).

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-EXP-01.1 | Đặt thuê bao của C hết hạn bằng công cụ demo | ADMIN của C gọi `POST /api/auth/subscription/demo/expire` | 200 (404 nghĩa là chưa bật APP_DEMO_TOOLS) | `30-isolation-plan-expiry` / TC-EXP-01.1 Đặt thuê bao của C hết hạn bằng công cụ demo | chưa chạy |
| TC-EXP-01.2 | Thuê bao ở trạng thái hết hạn, chỉ đọc | ADMIN của C gọi `GET /api/auth/subscription` | 200, status EXPIRED, readOnly true | `30-isolation-plan-expiry` / TC-EXP-01.2 Thuê bao ở trạng thái hết hạn, chỉ đọc | chưa chạy |
| TC-EXP-01.3 | Đọc hàng hóa vẫn được | ADMIN của C gọi `GET /api/products?size=5` | 200 | `30-isolation-plan-expiry` / TC-EXP-01.3 Đọc hàng hóa vẫn được | chưa chạy |
| TC-EXP-01.4 | Đọc đơn vị tính vẫn được | Quản lý kho của C gọi `GET /api/products/uoms` | 200 | `30-isolation-plan-expiry` / TC-EXP-01.4 Đọc đơn vị tính vẫn được | chưa chạy |
| TC-EXP-01.5 | Tạo hàng hóa khi hết hạn | ADMIN của C gọi `POST /api/products` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.5 Tạo hàng hóa khi hết hạn | chưa chạy |
| TC-EXP-01.6 | Sửa kho khi hết hạn | ADMIN của C gọi `PUT /api/inventory/warehouses/{{C_wh1}}` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.6 Sửa kho khi hết hạn | chưa chạy |
| TC-EXP-01.7 | Ngưng hàng hóa khi hết hạn | ADMIN của C gọi `DELETE /api/products/{{C_prod1}}` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.7 Ngưng hàng hóa khi hết hạn | chưa chạy |
| TC-EXP-01.8 | Tạo nhà cung cấp khi hết hạn | Quản lý kho của C gọi `POST /api/orders/suppliers` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.8 Tạo nhà cung cấp khi hết hạn | chưa chạy |
| TC-EXP-01.9 | Khóa tài khoản khi hết hạn | ADMIN của C gọi `PATCH /api/auth/users/{{uid_C_STAFF}}/status` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.9 Khóa tài khoản khi hết hạn | chưa chạy |
| TC-EXP-01.10 | Hỏi AI khi hết hạn | ADMIN của C gọi `POST /api/ai/ask` | 403 SUBSCRIPTION_EXPIRED | `30-isolation-plan-expiry` / TC-EXP-01.10 Hỏi AI khi hết hạn | chưa chạy |

#### TC-EXP-02 Thanh toán giả lập mở lại quyền ghi

Cần PAYOS_MOCK=true.

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-EXP-02.1 | Tạo giao dịch gói PRO theo tháng | ADMIN của C gọi `POST /api/auth/payments` | 201, PENDING, amount 499000, checkoutUrl trỏ tới /payment/mock?orderCode= | `30-isolation-plan-expiry` / TC-EXP-02.1 Tạo giao dịch gói PRO theo tháng | chưa chạy |
| TC-EXP-02.2 | Thiếu chu kỳ thanh toán | ADMIN của C gọi `POST /api/auth/payments` | 400 VALIDATION_ERROR, data.cycle | `30-isolation-plan-expiry` / TC-EXP-02.2 Thiếu chu kỳ thanh toán | chưa chạy |
| TC-EXP-02.3 | Quản lý kho không được tạo thanh toán | Quản lý kho của C gọi `POST /api/auth/payments` | 403 FORBIDDEN | `30-isolation-plan-expiry` / TC-EXP-02.3 Quản lý kho không được tạo thanh toán | chưa chạy |
| TC-EXP-02.4 | Giao dịch đang chờ | ADMIN của C gọi `GET /api/auth/payments/{{C_payOrder}}` | 200, PENDING | `30-isolation-plan-expiry` / TC-EXP-02.4 Giao dịch đang chờ | chưa chạy |
| TC-EXP-02.5 | Xác nhận thanh toán giả lập | ADMIN của C gọi `POST /api/auth/payments/{{C_payOrder}}/mock-confirm` | 200, PAID, paidAt có giá trị, reference MOCK-<orderCode> | `30-isolation-plan-expiry` / TC-EXP-02.5 Xác nhận thanh toán giả lập | chưa chạy |
| TC-EXP-02.6 | Gói PRO hoạt động 30 ngày | ADMIN của C gọi `GET /api/auth/subscription` | 200, plan PRO, status ACTIVE, readOnly false, daysRemaining 30, apiAccess true | `30-isolation-plan-expiry` / TC-EXP-02.6 Gói PRO hoạt động 30 ngày | chưa chạy |
| TC-EXP-02.7 | Xác nhận lại không cộng thêm ngày | ADMIN của C gọi `POST /api/auth/payments/{{C_payOrder}}/mock-confirm` | 200, vẫn PAID | `30-isolation-plan-expiry` / TC-EXP-02.7 Xác nhận lại không cộng thêm ngày | chưa chạy |
| TC-EXP-02.8 | Ngày hết hạn không đổi sau lần xác nhận thứ hai | ADMIN của C gọi `GET /api/auth/subscription` | 200, expiresAt bằng lần đọc trước | `30-isolation-plan-expiry` / TC-EXP-02.8 Ngày hết hạn không đổi sau lần xác nhận thứ hai | chưa chạy |
| TC-EXP-02.9 | Không hủy được giao dịch đã trả | ADMIN của C gọi `POST /api/auth/payments/{{C_payOrder}}/cancel` | 409 CONFLICT | `30-isolation-plan-expiry` / TC-EXP-02.9 Không hủy được giao dịch đã trả | chưa chạy |
| TC-EXP-02.10 | Ghi lại được ngay, không cần đăng nhập lại | ADMIN của C gọi `POST /api/inventory/warehouses`; dùng token cấp từ trước khi hết hạn | 201 (gói PRO cho 20 kho) | `30-isolation-plan-expiry` / TC-EXP-02.10 Ghi lại được ngay, không cần đăng nhập lại | chưa chạy |
| TC-EXP-02.11 | Tạo SKU vượt 200 sau khi lên PRO | ADMIN của C gọi `POST /api/products` | 201 (PRO không giới hạn SKU) | `30-isolation-plan-expiry` / TC-EXP-02.11 Tạo SKU vượt 200 sau khi lên PRO | chưa chạy |

#### TC-KEY-01 API key cho hệ thống ngoài (doanh nghiệp C gói PRO)

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa (collection / request) | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-KEY-01.1 | ADMIN tạo API key | ADMIN của C gọi `POST /api/auth/api-keys` | 201, key dạng wms_ + 43 ký tự, keyPrefix là 8 ký tự đầu | `30-isolation-plan-expiry` / TC-KEY-01.1 ADMIN tạo API key | chưa chạy |
| TC-KEY-01.2 | Tên key để trống | ADMIN của C gọi `POST /api/auth/api-keys` | 400 VALIDATION_ERROR | `30-isolation-plan-expiry` / TC-KEY-01.2 Tên key để trống | chưa chạy |
| TC-KEY-01.3 | Quản lý kho không được tạo key | Quản lý kho của C gọi `POST /api/auth/api-keys` | 403 FORBIDDEN | `30-isolation-plan-expiry` / TC-KEY-01.3 Quản lý kho không được tạo key | chưa chạy |
| TC-KEY-01.4 | Đọc tồn bằng API key | Hệ thống ngoài (API key) gọi `GET /api/inventory/stock/atp?productId={{C_prodPro}}` | 200, đúng productId | `30-isolation-plan-expiry` / TC-KEY-01.4 Đọc tồn bằng API key | chưa chạy |
| TC-KEY-01.5 | Đọc danh mục hàng hóa bằng API key | Hệ thống ngoài (API key) gọi `GET /api/products?size=50` | 200, chỉ hàng của C (SKU QA-PL-...) | `30-isolation-plan-expiry` / TC-KEY-01.5 Đọc danh mục hàng hóa bằng API key | chưa chạy |
| TC-KEY-01.6 | Đọc đơn bán bằng API key | Hệ thống ngoài (API key) gọi `GET /api/orders/sales` | 200 | `30-isolation-plan-expiry` / TC-KEY-01.6 Đọc đơn bán bằng API key | chưa chạy |
| TC-KEY-01.7 | Key của C không đọc được hàng hóa của A | Hệ thống ngoài (API key) gọi `GET /api/products/{{A_prod}}` | 404 NOT_FOUND | `30-isolation-plan-expiry` / TC-KEY-01.7 Key của C không đọc được hàng hóa của A | chưa chạy |
| TC-KEY-01.8 | Ghi bằng API key bị từ chối | Hệ thống ngoài (API key) gọi `POST /api/products` | 403 FORBIDDEN | `30-isolation-plan-expiry` / TC-KEY-01.8 Ghi bằng API key bị từ chối | chưa chạy |
| TC-KEY-01.9 | API key không gọi được API thuê bao | Hệ thống ngoài (API key) gọi `GET /api/auth/subscription` | 403 FORBIDDEN (ngoài /api/products, /api/inventory, /api/orders) | `30-isolation-plan-expiry` / TC-KEY-01.9 API key không gọi được API thuê bao | chưa chạy |
| TC-KEY-01.10 | API key không gọi được AI | Hệ thống ngoài (API key) gọi `GET /api/ai/usage` | 403 FORBIDDEN | `30-isolation-plan-expiry` / TC-KEY-01.10 API key không gọi được AI | chưa chạy |
| TC-KEY-01.11 | Key sai | Hệ thống ngoài (API key) gọi `GET /api/products` | 401 UNAUTHORIZED | `30-isolation-plan-expiry` / TC-KEY-01.11 Key sai | chưa chạy |
| TC-KEY-01.12 | Danh sách key không lộ key đầy đủ | ADMIN của C gọi `GET /api/auth/api-keys` | 200, có key vừa tạo, active true, không có trường key | `30-isolation-plan-expiry` / TC-KEY-01.12 Danh sách key không lộ key đầy đủ | chưa chạy |
| TC-KEY-01.13 | Thu hồi key | ADMIN của C gọi `DELETE /api/auth/api-keys/{{C_keyId}}` | 200, active false, revokedAt có giá trị | `30-isolation-plan-expiry` / TC-KEY-01.13 Thu hồi key | chưa chạy |
| TC-KEY-01.14 | Thu hồi lần hai không đổi thời điểm | ADMIN của C gọi `DELETE /api/auth/api-keys/{{C_keyId}}` | 200, revokedAt giữ nguyên | `30-isolation-plan-expiry` / TC-KEY-01.14 Thu hồi lần hai không đổi thời điểm | chưa chạy |
| TC-KEY-01.15 | Key đã thu hồi bị từ chối | Hệ thống ngoài (API key) gọi `GET /api/products` | 401 UNAUTHORIZED | `30-isolation-plan-expiry` / TC-KEY-01.15 Key đã thu hồi bị từ chối | chưa chạy |
| TC-KEY-01.16 | Tạo key thứ hai để thử giới hạn tốc độ | ADMIN của C gọi `POST /api/auth/api-keys` | 201 | `30-isolation-plan-expiry` / TC-KEY-01.16 Tạo key thứ hai để thử giới hạn tốc độ | chưa chạy |
| TC-KEY-01.17 | Gửi dồn 40 request trong một giây bằng một key | Hệ thống ngoài (API key) gọi `GET /api/products?size=1`; script gửi song song thêm 40 request cùng key bằng pm.sendRequest | ít nhất một phản hồi 429 TOO_MANY_REQUESTS kèm header Retry-After: 1 | `30-isolation-plan-expiry` / TC-KEY-01.17 Gửi dồn 40 request trong một giây bằng một key | chưa chạy |
| TC-KEY-01.18 | Thu hồi key thứ hai | ADMIN của C gọi `DELETE /api/auth/api-keys/{{C_key2Id}}` | 200 | `30-isolation-plan-expiry` / TC-KEY-01.18 Thu hồi key thứ hai | chưa chạy |

## 6. WMS-19.2 Tải đồng thời và bất biến tồn kho (qa/load/concurrency.mjs)

| ID | Tên | Các bước | Kết quả mong đợi | Tự động hóa | Kết quả |
| --- | --- | --- | --- | --- | --- |
| TC-LOAD-01.1 | Chuẩn bị doanh nghiệp, hàng hóa và tồn đã biết | Đăng ký doanh nghiệp mới, tạo kho, vị trí, hàng hóa, nhà cung cấp, khách hàng; đơn mua `--initial-stock` (mặc định 300), duyệt, nhập đủ; chờ `GET /api/inventory/stock/atp` đạt số tồn | Tồn thực tế bằng số đã nhập, giữ chỗ 0 | `qa/load/concurrency.mjs` / phần `prepare()` | chưa chạy |
| TC-LOAD-01.2 | Tải hỗn hợp 40 request/giây trong 30 giây | Bắn lịch đều `--rps` request/giây trong `--duration` giây, trộn: lập đơn bán nháp, xác nhận đơn (giữ cùng một tồn), lập phiếu xuất cho đơn đã xác nhận, đọc tồn, danh sách đơn bán, lô, phiếu xuất | Không có HTTP 5xx; từ chối nghiệp vụ là 400 (thiếu tồn khả dụng, xuất vượt) hoặc 409 | `qa/load/concurrency.mjs` / chế độ `load` | chưa chạy |
| TC-LOAD-01.3 | Tồn thực tế không âm, giữ chỗ không vượt tồn | Sau khi tải xong, chờ sự kiện xử lý xong (giữ chỗ và tồn ổn định 3 lần đọc liên tiếp), đọc ATP và từng lô | onHand >= 0, reserved >= 0, reserved <= onHand ở tổng và ở từng lô | `qa/load/concurrency.mjs` / bất biến `I1`, `I2` | chưa chạy |
| TC-LOAD-01.4 | Tổng đã xuất không vượt tồn đã nhập | Cộng `totalQuantity` của mọi phiếu xuất của kho | Tổng đã xuất <= tồn đã nhập | `qa/load/concurrency.mjs` / bất biến `I3` | chưa chạy |
| TC-LOAD-01.5 | Mỗi phiếu xuất trừ tồn đúng một lần | So tồn hiện tại với tồn đã nhập trừ tổng đã xuất | onHand = tồn đã nhập - tổng đã xuất. Dự kiến KHÔNG ĐẠT: Phiếu xuất không kiểm tra tồn, trừ tồn chạy nền | `qa/load/concurrency.mjs` / bất biến `I4` | chưa chạy |
| TC-LOAD-01.6 | Đơn đã xác nhận được giữ đủ hàng | Cộng số lượng các đơn bán đang CONFIRMED (chưa xuất), so với giữ chỗ và tồn | Tổng cam kết = giữ chỗ và <= tồn thực tế (không bán vượt tồn khi xác nhận đồng thời). Dự kiến KHÔNG ĐẠT: Xác nhận đơn bán đồng thời có thể bán vượt tồn | `qa/load/concurrency.mjs` / bất biến `I5`, `I6` | chưa chạy |
| TC-LOAD-01.7 | Sổ kho khớp tồn sau tải | `GET /api/inventory/reports/in-out-balance` cho sản phẩm và kho trong ngày | Nhập = tồn đã nhập, xuất = tổng đã xuất, tồn cuối = onHand | `qa/load/concurrency.mjs` / bất biến `I7` | chưa chạy |
| TC-LOAD-01.8 | Không lỗi máy chủ, không deadlock | Đếm phản hồi 5xx, lỗi mạng, phản hồi có chữ deadlock hoặc lock wait | Bằng 0 (xem thêm log: `docker compose logs inventory-service order-service \\| grep -i deadlock`) | `qa/load/concurrency.mjs` / bất biến `I8`, `I9` | chưa chạy |
| TC-LOAD-01.9 | Độ trễ theo API | In p50, p95, p99, lớn nhất theo từng API | Báo cáo; SRS yêu cầu API nghiệp vụ trả lời trong 500 ms (cột NFR in "vượt" khi p95 > 500 ms, không làm thất bại lượt chạy trừ khi đặt `--max-p95-ms`) | `qa/load/concurrency.mjs` / bảng độ trễ | chưa chạy |
| TC-LOAD-02.1 | Nộp trùng song song phiếu nhập nhận đủ phần còn lại | Đơn mua 50, duyệt; gửi đồng thời hai `POST /api/orders/goods-receipt-notes` nhận đủ 50 | Đúng một 201, một 400; tồn tăng đúng 50 | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G1` | chưa chạy |
| TC-LOAD-02.2 | Nộp trùng song song cùng một phiếu nhập từng phần | Đơn mua 100; gửi đồng thời hai lần cùng body nhận 30 | Theo yêu cầu: chỉ một lần được ghi, tồn tăng 30. Dự kiến KHÔNG ĐẠT: Nộp trùng cùng một phiếu nhập từng phần cộng tồn hai lần | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G2` | chưa chạy |
| TC-LOAD-02.3 | Xác nhận đồng thời cùng một đơn bán | Đơn bán nháp 20; gửi đồng thời hai `POST /api/orders/sales/{id}/confirm` | Giữ chỗ tăng đúng 20, không lỗi 5xx (`G3`); chỉ một lần thành công, lần sau 400 (`G3b`). Dự kiến KHÔNG ĐẠT: Hai lần xác nhận cùng một đơn bán có thể cùng thành công (G3b) | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G3`, `G3b` | chưa chạy |
| TC-LOAD-02.4 | Nộp trùng song song phiếu xuất đủ số lượng | Đơn bán 20 đã xác nhận; gửi đồng thời hai `POST /api/orders/goods-delivery-notes` xuất 20 | Một 201, một 400; tồn giảm đúng 20, giữ chỗ về 0 | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G4` | chưa chạy |
| TC-LOAD-02.5 | Bấm xác nhận xuất hai lần cùng lúc | Phiếu xuất nháp (status DRAFT) 10; gửi đồng thời hai `POST /api/orders/goods-delivery-notes/{id}/dispatch` | Cả hai 200 nhưng tồn chỉ giảm 10 | `qa/load/concurrency.mjs` / chế độ `idempotency`, `G5` | chưa chạy |

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
