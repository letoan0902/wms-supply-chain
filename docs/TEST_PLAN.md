# Kế hoạch kiểm thử đầu cuối, cách ly doanh nghiệp và tải đồng thời

Tài liệu gồm bảng test case của bốn task QA, phần kiểm tra thủ công và bảng truy vết yêu cầu. Bộ kiểm thử tự động nằm ở
`qa/`, cách dựng stack và chạy ở [qa/README.md](../qa/README.md).

| Task | Jira | Nội dung | Tự động hóa |
| --- | --- | --- | --- |
| WMS-20.1 | TASK-123 | Luồng mua và nhập kho | `qa/postman/00-setup`, `qa/postman/10-inbound` |
| WMS-20.2 | TASK-124 | Bán, xuất kho, điều chuyển, kiểm kê, sổ kho | `qa/postman/20-outbound-transfer-stocktake` |
| WMS-22.5 | TASK-139 | Cách ly doanh nghiệp, giới hạn gói, hết hạn, API key | bổ sung ở commit sau |
| WMS-19.2 | TASK-52 | Tải đồng thời, bất biến tồn kho, chống nộp trùng | bổ sung ở commit sau |

## 1. Quy ước

- Mỗi dòng của bảng là một request (hoặc một chế độ của kịch bản tải) có test tự động, cột "Tự động hóa" ghi tên file
  collection và tên request trong file. Thư mục trong collection trùng mã nhóm test case (`TC-IN-05`, `TC-ISO-12`...).
- Dữ liệu không cố định ID: mỗi lượt chạy có một `runId`, doanh nghiệp A, B (do `00-setup` đăng ký), C (do `30` đăng
  ký) và doanh nghiệp của kịch bản tải đều mới. Mật khẩu kiểm thử là chuỗi giả `QaTest-<runId>-Mk`.
- Mọi request có thêm test chung "Không có lỗi máy chủ (HTTP 5xx)".
- Cột "Kết quả" để "chưa chạy"; người chạy ghi "đạt" hoặc "không đạt" kèm mã lỗi, ảnh hoặc dòng log của Postman CLI.
- Số lượng request hiện có: `00-setup.postman_collection.json`: 20 request, `10-inbound.postman_collection.json`: 102 request, `20-outbound-transfer-stocktake.postman_collection.json`: 137 request.

## 2. Hành vi dự kiến không đạt (lỗi cần sửa trước)

Đọc mã nguồn cho thấy các yêu cầu sau chưa được đáp ứng; test tương ứng được giữ nguyên theo yêu cầu nên sẽ báo không đạt
cho tới khi sửa:

| Test case | Hành vi | Nguyên nhân trong mã và hướng sửa |
| --- | --- | --- |
| TC-STK-01.10 | Lập phiếu nhập vào vị trí đang kiểm kê trả 201 thay vì 409 | order-service `GoodsReceiptService.create` không hỏi inventory-service; `StockReceiptService.processOrderReceived` mới kiểm tra đóng băng ở luồng nền, ném 409 trong listener, message thử lại 3 lần rồi vào dead-letter. Hậu quả: phiếu nhập, hóa đơn mua, công nợ nhà cung cấp đã ghi nhưng tồn không tăng (lệch sổ). Sửa: order-service gọi inventory kiểm tra vị trí trước khi lưu phiếu, hoặc lập phiếu ở trạng thái chờ và chỉ hoàn tất khi inventory xác nhận. |

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

## 5. Kiểm tra thủ công (không tự động hóa được ở đây)

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

## 6. Truy vết yêu cầu

Mã FR theo danh sách yêu cầu chức năng ở `docs/PROPOSAL_PHAN_CONG_PHAM_VI.md` mục 2 (SRS `docs/project-management/srs`
dùng mã UC và tham chiếu các FR này); phần SaaS theo `docs/SAAS_SPEC.md`.

| Yêu cầu | Test case |
| --- | --- |
| FR01 Đăng nhập, token | TC-SET-03, TC-IN-00, TC-OUT-00 |
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
| FR16 Giữ hàng mềm khi xác nhận | TC-OUT-02.2, TC-OUT-02.5 đến TC-OUT-02.8, TC-OUT-02.13 |
| FR17 Picking list, phiếu xuất trừ OnHand, giải phóng Reserved | TC-OUT-03, TC-OUT-04 |
| FR19 Kiểm kê: đóng băng, snapshot, đếm mù, chênh lệch | TC-STK-01.3 đến TC-STK-01.18, TC-STK-02.1 đến TC-STK-02.5 |
| FR20 Điều chỉnh, duyệt Quản lý kho và Kế toán, mở đóng băng | TC-STK-01.19 đến TC-STK-01.28, TC-STK-02.6 đến TC-STK-02.10 |
| FR25 Xuất nhập tồn, thẻ kho, truy vết lô | TC-IN-08.13, TC-RPT-01, TC-RPT-02 |
| SRS UC-09 luồng 1a: nhập, xuất vào vị trí FROZEN bị từ chối | TC-STK-01.8, TC-STK-01.9, TC-STK-01.10 |
| SAAS_SPEC 2.5, WMS-23.2 Giới hạn gói | TC-SET-01.2 |
| SAAS_SPEC 2.6, WMS-23.1, WMS-23.2 Vòng đời thuê bao, chỉ đọc khi hết hạn | TC-SET-03.3 |
| WMS-23.6 Đăng ký, kích hoạt (tắt trên stack kiểm thử) | TC-SET-02; luồng kích hoạt thật ở mục kiểm tra thủ công |
