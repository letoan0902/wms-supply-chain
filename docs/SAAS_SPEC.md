# Đặc tả chức năng phần mở rộng SaaS (WMS-22 đến WMS-26)

Tài liệu này mô tả chi tiết từng task của EPIC 10 để người nhận task đọc xong là làm được: dữ liệu thêm gì, API nào, quy tắc nghiệp vụ ra sao và nghiệm thu bằng cách nào. Lập ngày 05/11/2026 theo đề nghị của nhóm.

Quan hệ với các tài liệu khác:

- [SAAS_PLAN.md](SAAS_PLAN.md): phạm vi, lịch và phân công. Ngày và người làm lấy theo tài liệu đó.
- SRS (`docs/project-management/srs/Template_SRS2.docx`), mục 2.7 và UC-11, UC-12, UC-13: mô tả ở mức use case.
- `docs/MULTI_TENANT.md` (có cùng WMS-22.1, ngày 06/11): hướng dẫn kỹ thuật áp dụng bộ lọc doanh nghiệp vào từng service.

Khi làm mà thấy cần lệch khỏi mô tả ở đây thì báo Toàn và sửa tài liệu này trong cùng Pull Request.

## 1. Khái niệm và tác nhân

| Khái niệm | Ý nghĩa |
| --- | --- |
| Doanh nghiệp (company) | Một khách hàng của nền tảng. Mọi dữ liệu nghiệp vụ và mọi tài khoản đều thuộc đúng một doanh nghiệp. |
| Gói dịch vụ (plan) | `FREE`, `TRIAL`, `PRO`. Gói quyết định giới hạn số người dùng, số kho, số SKU, số lượt AI và quyền dùng API key. |
| Thuê bao (subscription) | Trạng thái gói của một doanh nghiệp: đang dùng gói nào, còn hiệu lực hay đã hết hạn, hết hạn ngày nào. Mỗi doanh nghiệp có đúng một thuê bao. |
| Chỉ đọc | Trạng thái của doanh nghiệp khi thuê bao hết hạn: xem được mọi dữ liệu nhưng không tạo, sửa, xóa được. Dữ liệu không bị xóa. |

Tác nhân:

- Khách truy cập: xem trang giới thiệu, đăng ký doanh nghiệp.
- Admin của doanh nghiệp: tài khoản đầu tiên tạo lúc đăng ký và các tài khoản có vai trò `ADMIN`. Quản lý người dùng, gói dịch vụ, thanh toán, API key của chính doanh nghiệp mình. Hệ thống không có quản trị viên toàn nền tảng.
- Năm vai trò nghiệp vụ hiện có (`WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF`, `PURCHASING`, `SALES`, `ACCOUNTANT`): giữ nguyên quyền, nhưng chỉ trong phạm vi doanh nghiệp của mình.
- Hệ thống ngoài: gọi API bằng API key, vai trò `INTEGRATION`, chỉ đọc.
- PayOS: cổng thanh toán, gọi webhook về máy chủ khi khách chuyển khoản xong.

### 1.1. Nghiệp vụ thay đổi thế nào

Trước đây hệ thống phục vụ một doanh nghiệp duy nhất: quản trị viên tạo tài khoản cho nhân viên, mọi người cùng nhìn chung một bộ kho, hàng hóa, đơn hàng. Sau phần mở rộng, nhiều doanh nghiệp không liên quan tới nhau cùng dùng một hệ thống, mỗi bên chỉ thấy dữ liệu của mình và trả phí theo gói. Một ví dụ đi từ đầu tới cuối:

1. Anh An vào trang giới thiệu, bấm Đăng ký, nhập tên "Công ty A", email và mật khẩu. Hệ thống tạo doanh nghiệp Công ty A, tạo tài khoản của anh An với vai trò `ADMIN` của công ty đó và cho 30 ngày dùng thử đầy đủ tính năng.
2. Anh An đăng nhập và thấy một hệ thống trống. Anh tạo kho, nhập hàng hóa (tự nhập hoặc tải file để AI trích xuất), tạo tài khoản cho nhân viên. Mọi thứ anh và nhân viên tạo ra đều thuộc Công ty A.
3. Chị Bình đăng ký "Công ty B" theo cách tương tự. Chị và nhân viên của mình không thấy kho, hàng hóa, đơn hàng hay tài khoản nào của Công ty A, kể cả khi biết ID. Hai công ty có thể cùng đặt mã kho `WH01` hay mã hàng `SP001` mà không đụng nhau.
4. Trong 30 ngày dùng thử, mọi trang có dòng nhắc còn bao nhiêu ngày. Admin xem được mình đang dùng bao nhiêu người dùng, kho, mã hàng, lượt AI so với giới hạn của gói.
5. Hết 30 ngày mà chưa thanh toán, Công ty A vẫn đăng nhập và xem được toàn bộ dữ liệu nhưng không tạo, sửa, xóa được gì. Anh An mở trang Gói dịch vụ và chọn một trong hai: thanh toán gói Pro qua PayOS (quét mã QR chuyển khoản, hệ thống mở lại ngay khi nhận được tiền), hoặc chuyển về gói Khởi Đầu miễn phí với giới hạn 3 người dùng, 1 kho, 200 mã hàng.
6. Khi cần nối với phần mềm kế toán, anh An tạo một API key trong phần cài đặt; phần mềm đó dùng key để đọc tồn kho và đơn hàng của Công ty A.

Các task của EPIC 10 chia câu chuyện trên thành từng phần: WMS-22 lo việc "mỗi bên chỉ thấy dữ liệu của mình", WMS-23 lo đăng ký, gói dịch vụ và thanh toán, WMS-24 lo AI, WMS-25 lo API key.

## 2. Quy ước chung cho mọi task

### 2.1. JWT và kết quả đăng nhập

Access token hiện có các claim `sub` (tên đăng nhập), `userId`, `roles`, `warehouseIds`, `type`. Bổ sung:

| Claim | Kiểu | Thêm ở task | Ý nghĩa |
| --- | --- | --- | --- |
| `companyId` | số | WMS-22.2 | ID doanh nghiệp của người dùng |
| `companyCode` | chuỗi | WMS-22.2 | Mã doanh nghiệp, ví dụ `CTY000001` |
| `plan` | chuỗi | WMS-23.1 | `FREE`, `TRIAL` hoặc `PRO` tại thời điểm cấp token, chỉ để giao diện hiển thị |

Kết quả `POST /api/auth/login` và `GET /api/auth/me` thêm hai đối tượng:

- `company`: `id`, `code`, `name` (từ WMS-22.2).
- `subscription`: `plan`, `status`, `expiresAt` (từ WMS-23.1).

Việc chặn theo gói không dựa vào claim `plan` (token sống 15 phút nên có thể cũ) mà dựa vào bộ nhớ đệm ở mục 2.7.

### 2.2. Header do API Gateway chuyển tiếp

| Header | Có từ | Nội dung |
| --- | --- | --- |
| `X-User-Name`, `X-User-Roles`, `X-User-Warehouse-Ids` | đã có | Người dùng, vai trò, các kho được gán |
| `X-Company-Id` | WMS-22.3 | ID doanh nghiệp lấy từ JWT hoặc từ API key |
| `X-Plan` | WMS-23.2 | Gói hiện tại của doanh nghiệp |

Gateway luôn xóa các header trên nếu chúng do trình duyệt gửi lên, rồi mới gắn giá trị lấy từ token. Service chỉ tin các header này, không đọc mã doanh nghiệp từ body, query hay đường dẫn. Khi service gọi nhau bằng OpenFeign phải chuyển tiếp nguyên các header này (module `common` cung cấp sẵn interceptor ở WMS-22.1).

### 2.3. Quy tắc tách dữ liệu, áp dụng cho mọi service

1. Mọi bảng nghiệp vụ có cột `company_id BIGINT NOT NULL`. Bảng mới tạo từ ngày 09/11 trở đi (điều chuyển, kiểm kê, phiếu nhập, phiếu xuất, báo cáo) phải có cột này ngay từ đầu.
2. `company_id` được gán tự động khi thêm mới và tự động thêm vào điều kiện lọc của mọi truy vấn. Không nhận `company_id` từ phía client.
3. Truy cập bản ghi của doanh nghiệp khác bằng ID trả `404 NOT_FOUND` như thể bản ghi không tồn tại, không trả 403. Trong code, `findById` với ID của doanh nghiệp khác ném lỗi `NOT_FOUND` chứ không trả rỗng (chi tiết ở `docs/MULTI_TENANT.md`, mục 3).
4. Mọi ràng buộc duy nhất theo mã đổi thành duy nhất trong một doanh nghiệp: `(company_id, mã)`. Hai doanh nghiệp được phép dùng trùng mã SKU, mã kho, mã đối tác, số chứng từ.
5. Ngoại lệ: tên đăng nhập và email của tài khoản duy nhất trên toàn hệ thống, vì đăng nhập chỉ cần tên đăng nhập và mật khẩu.
6. Truy vấn SQL gốc (native query) không được lọc tự động, phải tự thêm điều kiện `company_id`.
7. Sự kiện RabbitMQ luôn mang doanh nghiệp trong header `x-wms-company-id` (do `WmsEventPublisher` gắn). Bên nhận được đặt lại ngữ cảnh doanh nghiệp trước khi đọc, ghi dữ liệu.
8. Khóa Redis của dữ liệu nghiệp vụ (cache, bộ đếm, KPI) phải có `companyId` trong tên khóa.
9. Dữ liệu đang có trên máy chủ được gán cho doanh nghiệp có `id = 1` trong migration.
10. Mỗi service có ít nhất một bài test cách ly: tạo dữ liệu cho hai doanh nghiệp, kiểm tra bên này không đọc, sửa, xóa được dữ liệu bên kia.

Lưu ý về Flyway: trước khi tạo file migration, xem số phiên bản mới nhất của service trên nhánh `develop` và báo trong nhóm số mình định dùng. Test chạy H2 không phát hiện được hai file trùng số.

### 2.4. Mã lỗi mới

Toàn thêm toàn bộ các mã dưới đây vào `ErrorCode` của module `common` trong WMS-22.1 để mọi người dùng chung, không ai phải sửa file này nữa.

| Mã | HTTP | Trả khi nào |
| --- | --- | --- |
| `COMPANY_REQUIRED` | 403 | Request tới service nghiệp vụ không xác định được doanh nghiệp |
| `COMPANY_INACTIVE` | 403 | Đăng nhập khi doanh nghiệp chưa kích hoạt qua email hoặc đang bị tạm khóa |
| `SUBSCRIPTION_EXPIRED` | 403 | Gọi API ghi khi thuê bao đã hết hạn |
| `PLAN_LIMIT_EXCEEDED` | 403 | Tạo mới vượt giới hạn của gói. Trường `data` gồm `limit` (`USERS`, `WAREHOUSES`, `SKUS`, `AI_REQUESTS`, `API_ACCESS`), `current`, `max`, `plan` |
| `PAYMENT_INVALID` | 400 | Webhook thanh toán sai chữ ký, sai số tiền hoặc mã đơn không tồn tại |
| `TOKEN_INVALID` | 400 | Mã kích hoạt hoặc mã đặt lại mật khẩu sai, đã dùng hoặc hết hạn |
| `TOO_MANY_REQUESTS` | 429 | API key gọi quá giới hạn tốc độ (thêm ở WMS-25.1, gateway trả) |

Các trường hợp còn lại dùng mã đã có (`VALIDATION_ERROR`, `CONFLICT`, `NOT_FOUND`, `UNAUTHORIZED`, `FORBIDDEN`). Frontend xử lý theo `code`, không theo nội dung `message`.

### 2.5. Gói dịch vụ và giới hạn

| | `FREE` (Khởi Đầu) | `TRIAL` (Dùng thử) | `PRO` (Chuyên Nghiệp) |
| --- | --- | --- | --- |
| Giá | 0 đ | 0 đ | 499.000 đ mỗi tháng hoặc 4.790.000 đ mỗi năm |
| Thời hạn | Không giới hạn | 30 ngày kể từ lúc đăng ký | 30 ngày hoặc 365 ngày cho mỗi lần thanh toán |
| Số người dùng | 3 | 50 | 50 |
| Số kho | 1 | 20 | 20 |
| Số SKU | 200 | Không giới hạn | Không giới hạn |
| Lượt AI mỗi tháng | 20 | 500 | 500 |
| API key | Không | Có | Có |

Cách đếm:

- Người dùng: tài khoản đang bật (`enabled = true`) của doanh nghiệp.
- Kho và SKU: bản ghi đang hoạt động, không tính bản ghi đã ngưng hoặc xóa mềm.
- Lượt AI: mỗi lần trích xuất tài liệu và mỗi câu hỏi gửi trợ lý tính một lượt. Đếm theo tháng dương lịch, giờ Việt Nam, sang ngày 1 thì về 0. Lượt gọi lỗi do hệ thống không bị tính.
- Giới hạn chỉ chặn việc tạo thêm. Dữ liệu đã có không bị khóa hay xóa khi doanh nghiệp chuyển xuống gói thấp hơn.

Gói "Doanh nghiệp" trên trang bảng giá chỉ là lời mời liên hệ, không có trong hệ thống.

### 2.6. Vòng đời thuê bao

```
đăng ký --> TRIAL/ACTIVE --(quá trial_ends_at)--> TRIAL/EXPIRED --+
                 |                                 (chỉ đọc)      |
                 | thanh toán                                     +-- thanh toán ---------> PRO/ACTIVE
                 v                                                +-- chọn gói Khởi Đầu --> FREE/ACTIVE
            PRO/ACTIVE ---(quá expires_at)------> PRO/EXPIRED ----+
                                                   (chỉ đọc)

TRIAL/ACTIVE --(chọn gói Khởi Đầu)--> FREE/ACTIVE --(thanh toán)--> PRO/ACTIVE
```

- Mỗi lần thanh toán thành công: `expires_at = max(thời điểm hiện tại, mốc hết hạn đang còn hiệu lực) + 30 hoặc 365 ngày`. Thanh toán sớm không bị mất ngày còn lại.
- Chuyển về `FREE` chỉ được phép khi đang `TRIAL` hoặc khi thuê bao đã `EXPIRED`. Đang `PRO` còn hạn thì chờ hết hạn.
- Khi `EXPIRED`: mọi request `POST`, `PUT`, `PATCH`, `DELETE` trả `SUBSCRIPTION_EXPIRED`, trừ đăng nhập, làm mới token, đăng xuất, đổi mật khẩu, thu hồi API key (`DELETE /api/auth/api-keys/{id}`) và các API dưới `/api/auth/subscription/**`, `/api/auth/payments/**`. Request `GET` vẫn chạy bình thường.

### 2.7. Bộ nhớ đệm thuê bao trên Redis

auth-service là nơi duy nhất ghi trạng thái thuê bao. Mỗi khi thuê bao thay đổi (đăng ký, kích hoạt, gia hạn, thanh toán, chuyển gói, hết hạn) và mỗi lần đăng nhập, auth-service ghi lại hash `wms:subscription:{companyId}`:

| Trường | Ví dụ | Ghi chú |
| --- | --- | --- |
| `plan` | `TRIAL` | |
| `status` | `ACTIVE` | `ACTIVE` hoặc `EXPIRED` |
| `expiresAt` | `2026-12-09T03:00:00Z` | Rỗng với gói `FREE` |
| `maxUsers`, `maxWarehouses`, `maxSkus`, `maxAiRequests` | `50`, `20`, `-1`, `500` | `-1` là không giới hạn |
| `apiAccess` | `true` | |

Gateway và `PlanLimitChecker` (WMS-23.2) chỉ đọc khóa này. Tên khóa và tên trường nằm ở `vn.wms.common.plan.SubscriptionCacheKeys`, auth-service dùng lại các hằng số đó khi ghi.

Các khóa mức sử dụng do service nghiệp vụ ghi (qua `PlanLimitChecker`), auth-service đọc để trả `usage`:

| Khóa | Kiểu | Nội dung |
| --- | --- | --- |
| `wms:usage:{companyId}` | hash | `users`, `warehouses`, `skus`: số bản ghi đang hoạt động, ghi lại mỗi lần kiểm tra giới hạn và sau mỗi lần tạo, bật lại, ngưng, xóa |
| `wms:usage:ai:{companyId}:{yyyyMM}` | chuỗi số | Lượt AI trong tháng (giờ Việt Nam), tự hết hạn sau 40 ngày |

Thiếu hash `wms:subscription:{companyId}` hoặc không kết nối được Redis thì cả gateway lẫn `PlanLimitChecker` đều cho qua (fail open): gateway không gắn `X-Plan`, không chặn; `PlanLimitChecker` cho tạo và ghi một dòng cảnh báo. auth-service ghi lại hash ở mỗi lần đăng nhập và định kỳ 5 phút nên khoảng thiếu dữ liệu ngắn.

### 2.8. Màn hình mới

| Đường dẫn | Màn hình | Task | Ai dùng |
| --- | --- | --- | --- |
| `/landing`, `/pricing` | Trang giới thiệu, bảng giá | WMS-23.3a (xong) | Khách truy cập |
| `/register` | Đăng ký doanh nghiệp | WMS-23.3b | Khách truy cập |
| `/activate`, `/reset-password` | Kích hoạt doanh nghiệp, đặt lại mật khẩu | WMS-23.6 | Khách truy cập |
| `/subscription` | Gói dịch vụ, mức dùng, lịch sử thanh toán | WMS-23.4, WMS-23.5b | Mọi tài khoản xem; chỉ `ADMIN` thanh toán |
| `/payment/result` | Kết quả thanh toán | WMS-23.5b | `ADMIN` |
| `/payment/mock` | Thanh toán giả lập khi `PAYOS_MOCK=true` (nút xác nhận gọi `mock-confirm`) | WMS-23.5b | `ADMIN` |
| `/ai-import` | Nhập dữ liệu bằng AI | WMS-24.3 | `ADMIN`, `WAREHOUSE_MANAGER`, `PURCHASING`, `SALES` |
| Cửa sổ trợ lý AI | Hỏi đáp | WMS-18.4 | Mọi tài khoản |
| `/settings/api-keys` | Quản lý API key | WMS-25.1 | `ADMIN` |

## 3. Đặc tả theo task

### WMS-22.1 [Backend] Bộ lọc doanh nghiệp dùng chung trong module common

Người làm: Toàn. Ngày: 06/11. Jira: TASK-133. Phụ thuộc: không.

**Mục tiêu**

Module `common` cung cấp sẵn cơ chế tách dữ liệu để mỗi service chỉ cần khai báo, không phải tự viết điều kiện lọc trong từng truy vấn.

**Mô tả chức năng**

- `TenantContext`: giữ ID doanh nghiệp của request đang xử lý; có hàm lấy bắt buộc, thiếu thì ném `COMPANY_REQUIRED`; có hàm chạy một đoạn code dưới danh nghĩa một doanh nghiệp cho tác vụ nền.
- Bộ lọc HTTP đọc header `X-Company-Id`, đặt vào `TenantContext` và xóa khi xử lý xong. Service nào đã chuyển sang `TenantEntity` thì bật `app.tenant.required=true`: từ đó request tới API nghiệp vụ mà thiếu header trả `403 COMPANY_REQUIRED`. Mặc định là tắt để service chưa chuyển đổi chạy như cũ. Không áp dụng cho `/actuator/**`, Swagger và các đường dẫn khai báo công khai.
- Khi chạy máy cá nhân với `GATEWAY_JWT_ENABLED=false`, service dùng doanh nghiệp mặc định cấu hình ở `app.tenant.default-company-id` (docker-compose của môi trường phát triển đặt là 1) để không ai bị chặn lúc phát triển. Trên máy chủ không đặt giá trị này.
- Lớp cha `TenantEntity` (kế thừa `AuditableEntity`) có cột `company_id` dùng `@TenantId` của Hibernate: tự gán khi thêm mới, tự lọc trong mọi truy vấn JPQL, Criteria và phương thức truy vấn của repository.
- Hibernate không lọc khi tải theo khóa chính, nên `TenantEntity` tự chặn: `findById`, `deleteById`, `save` một đối tượng mang ID của doanh nghiệp khác ném lỗi `NOT_FOUND` (API trả 404), không trả rỗng. Kiểm tra tồn tại thì dùng `existsById`.
- `WmsEventPublisher` tự gắn header `x-wms-company-id` vào sự kiện; trước mỗi phương thức `@RabbitListener`, ngữ cảnh doanh nghiệp được đặt lại từ header đó, service không phải viết thêm gì.
- Interceptor cho OpenFeign chuyển tiếp `X-Company-Id` và các header người dùng.
- Thêm các mã lỗi ở mục 2.4 vào `ErrorCode`.
- Tài liệu `docs/MULTI_TENANT.md`: các bước chuyển một service, mẫu migration, mẫu test cách ly.

**Tiêu chí nghiệm thu**

1. Test trong `common`: có dữ liệu của hai doanh nghiệp, truy vấn dưới doanh nghiệp A chỉ trả dữ liệu của A; tìm, sửa, xóa theo ID một bản ghi của B bị từ chối với `NOT_FOUND`; thêm mới dưới A thì bản ghi mang `company_id` của A.
2. Khi chưa xác định doanh nghiệp: truy vấn không trả dòng nào, lưu bị từ chối với `COMPANY_REQUIRED`.
3. Với `app.tenant.required=true`, request thiếu `X-Company-Id` nhận `403 COMPANY_REQUIRED`.
4. Sự kiện phát ra mang doanh nghiệp; listener nhận sự kiện của doanh nghiệp 2 thì ghi dữ liệu vào doanh nghiệp 2.
5. Toàn bộ test hiện có của năm service vẫn chạy được.

### WMS-22.2 [Backend] auth-service: doanh nghiệp, tài khoản thuộc doanh nghiệp, JWT mang companyId

Người làm: Phương Anh. Ngày: 06/11. Jira: TASK-134. Phụ thuộc: quy ước ở mục 2 của tài liệu này.

**Mục tiêu**

Mỗi tài khoản thuộc đúng một doanh nghiệp. Đăng nhập xong hệ thống biết người dùng thuộc doanh nghiệp nào và mọi thao tác quản lý người dùng chỉ nằm trong doanh nghiệp đó.

**Giải thích nghiệp vụ**

- Hiện nay bảng `users` không ghi ai thuộc công ty nào, nên `ADMIN` nào cũng thấy và sửa được mọi tài khoản. Task này thêm khái niệm doanh nghiệp vào auth-service và gắn mỗi tài khoản vào một doanh nghiệp.
- Người dùng không phải làm gì khác lúc đăng nhập: vẫn nhập tên đăng nhập (hoặc email) và mật khẩu. Hệ thống tự biết tài khoản thuộc doanh nghiệp nào và ghi `companyId` vào token. Các service khác (kho, hàng hóa, đơn hàng) sẽ dựa vào `companyId` này để lọc dữ liệu ở các task WMS-22.3 và WMS-22.4.
- Trang Người dùng của `ADMIN` Công ty A chỉ còn tài khoản của Công ty A. Tài khoản `ADMIN` tạo ra tự động thuộc công ty của người tạo.
- Task này chưa có chức năng đăng ký doanh nghiệp (đó là WMS-23.1). Vì vậy dùng hai công ty mẫu tạo sẵn bằng migration: toàn bộ tài khoản đang có thuộc Công ty mẫu A, thêm một tài khoản `admin_b` thuộc Công ty mẫu B để kiểm tra hai bên không thấy nhau.
- Ví dụ kiểm tra bằng tay: đăng nhập `admin` rồi gọi `GET /api/users` thì không thấy `admin_b`; lấy ID của `admin_b` gọi `GET /api/users/{id}` thì nhận `404`.

**Dữ liệu**

- Bảng mới `companies`: `id`, `code` (30 ký tự, duy nhất, dạng `CTY` cộng 6 chữ số theo `id`, ví dụ `CTY000001`), `name` (150, bắt buộc), `tax_code` (30), `email` (100), `phone` (30), `address` (255), `status` (`PENDING`, `ACTIVE`, `SUSPENDED`), `activated_at`, bốn cột audit.
- Bảng `users` thêm `company_id BIGINT NOT NULL`, khóa ngoại tới `companies`, có index. Ràng buộc duy nhất của `username` và `email` giữ nguyên trên toàn hệ thống.
- Migration (số kế tiếp của auth-service): tạo `companies`, thêm doanh nghiệp `id = 1` tên "Công ty mẫu A", gán toàn bộ tài khoản đang có vào doanh nghiệp 1 rồi mới đặt `NOT NULL`; thêm doanh nghiệp `id = 2` tên "Công ty mẫu B" với một tài khoản `admin_b` vai trò `ADMIN` để test cách ly. Mật khẩu tài khoản mẫu theo quy ước seed hiện có, không ghi vào tài liệu.
- Trong auth-service, lọc theo doanh nghiệp viết tường minh bằng `companyId` của người đang đăng nhập, không dùng `@TenantId` cho `users` và `companies`, vì lúc đăng nhập chưa biết doanh nghiệp.

**API thay đổi**

| API | Thay đổi |
| --- | --- |
| `POST /api/auth/login` | Body giữ nguyên (`username`, `password`). Sai thông tin vẫn trả lỗi như cũ. Doanh nghiệp không ở trạng thái `ACTIVE` trả `403 COMPANY_INACTIVE`. Thành công: token có `companyId`, `companyCode`; kết quả thêm `company` gồm `id`, `code`, `name` |
| `POST /api/auth/refresh` | Access token mới mang đúng `companyId`, `companyCode` đọc lại từ cơ sở dữ liệu |
| `GET /api/auth/me` | Thêm `company` |
| `POST /api/auth/register`, `POST /api/users` | Tài khoản mới luôn thuộc doanh nghiệp của `ADMIN` đang tạo; không nhận `companyId` từ body |
| `GET /api/users` và các API `/api/users/{id}/**` | Chỉ liệt kê và thao tác trên tài khoản cùng doanh nghiệp. ID của tài khoản thuộc doanh nghiệp khác trả `404` |
| `GET /api/auth/company` (mới) | Mọi tài khoản đã đăng nhập xem thông tin doanh nghiệp của mình: `id`, `code`, `name`, `taxCode`, `email`, `phone`, `address`, `status`, `createdAt` |
| `PUT /api/auth/company` (mới) | `ADMIN` sửa `name`, `taxCode`, `phone`, `address` của chính doanh nghiệp mình; không sửa được mã, trạng thái hay doanh nghiệp khác |

Kết quả đăng nhập sau khi sửa (phần `data`):

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "admin",
  "roles": ["ADMIN"],
  "company": { "id": 1, "code": "CTY000001", "name": "Công ty mẫu A" }
}
```

Nội dung access token sau khi giải mã:

```json
{
  "sub": "admin",
  "userId": 1,
  "roles": ["ADMIN"],
  "warehouseIds": [],
  "companyId": 1,
  "companyCode": "CTY000001",
  "type": "ACCESS"
}
```

**Quy tắc nghiệp vụ**

- Vai trò `ADMIN` là quản trị của một doanh nghiệp, không có quyền gì với doanh nghiệp khác.
- Quy tắc đang có "không được khóa hoặc bỏ quyền `ADMIN` cuối cùng" tính trong từng doanh nghiệp, không tính trên toàn hệ thống.
- Các hành vi đăng nhập đang có giữ nguyên: đăng nhập bằng tên đăng nhập hoặc email, khóa tạm sau 5 lần sai, xoay vòng refresh token.
- Tài khoản không chuyển được sang doanh nghiệp khác.
- Tài khoản dev (`dev-login`), nếu còn bật ở môi trường phát triển, thuộc doanh nghiệp 1.
- Danh sách kho gán cho người dùng (`user_warehouses`) giữ nguyên cách làm; giao diện chỉ đưa ra các kho của doanh nghiệp đó.

**Các bước triển khai gợi ý**

1. Migration mới trong `auth-service/src/main/resources/db/migration` (hiện có tới `V3`, kiểm tra lại trước khi đặt số): tạo bảng `companies`; thêm hai công ty mẫu; `ALTER TABLE users ADD COLUMN company_id BIGINT NULL`; `UPDATE users SET company_id = 1`; đổi cột thành `NOT NULL`, thêm khóa ngoại và index; thêm tài khoản `admin_b` và gán vai trò `ADMIN`.
2. Thêm `entity/Company` (kế thừa `AuditableEntity`), enum `CompanyStatus`, `repository/CompanyRepository`.
3. `entity/User`: thêm quan hệ `@ManyToOne(fetch = LAZY)` tới `Company` qua cột `company_id`, không cho null.
4. `security/JwtService`: `generateAccessToken` nhận thêm `companyId`, `companyCode` và ghi vào claim; thêm hàm đọc `companyId` từ claims. Giữ các hàm cũ gọi sang hàm mới để test đang có vẫn chạy.
5. `security/UserPrincipal` thêm trường `companyId`; `security/JwtAuthenticationFilter` đọc claim và điền vào.
6. `service/AuthService`: trong `login` và `refreshToken`, sau bước kiểm tra tài khoản bị khóa thì kiểm tra trạng thái doanh nghiệp, rồi truyền thông tin doanh nghiệp khi tạo token. `LoginResponse` và `UserInfoResponse` thêm `company`.
7. `AuthService.register` và `UserService.createUser`: gán doanh nghiệp của `ADMIN` đang đăng nhập (lấy `companyId` từ `UserPrincipal`).
8. `repository/UserRepository`: thêm điều kiện doanh nghiệp vào `searchUsers`; thêm `findByIdAndCompanyId`; sửa `countActiveAdmins` đếm theo doanh nghiệp. Trong `UserService`, mọi hàm nhận `id` (xem, sửa, gán kho, đổi trạng thái) tìm bằng `findByIdAndCompanyId`, không thấy thì trả `404`.
9. Thêm `GET /api/auth/company`.
10. Test: test chạy H2 và không chạy Flyway, nên `BaseIntegrationTest` phải tự tạo hai doanh nghiệp trước khi tạo tài khoản; sửa các test đang có cho phù hợp rồi thêm test theo sáu tiêu chí bên dưới.

**Tiêu chí nghiệm thu**

1. Đăng nhập tài khoản của Công ty mẫu A: giải mã token thấy `companyId = 1`; kết quả có `company.name`.
2. `ADMIN` của A gọi danh sách người dùng chỉ thấy tài khoản của A; gọi xem, sửa, khóa, gán kho với ID tài khoản của B đều nhận `404`.
3. `ADMIN` của A tạo tài khoản mới: tài khoản thuộc A dù body có gửi `companyId` khác.
4. Đổi trạng thái doanh nghiệp B thành `SUSPENDED` trong cơ sở dữ liệu: `admin_b` đăng nhập nhận `403 COMPANY_INACTIVE`.
5. Làm mới token: token mới vẫn có `companyId`.
6. Migration chạy được trên cơ sở dữ liệu đang có dữ liệu; test cũ của auth-service vẫn xanh, có thêm test cho các mục 1 đến 5.

### WMS-22.3 [Backend] API Gateway chuyển tiếp doanh nghiệp, áp mẫu vào inventory-service

Người làm: Toàn (phối hợp Linh). Ngày: 09/11. Jira: TASK-135. Phụ thuộc: WMS-22.1, WMS-22.2.

**Mô tả chức năng**

- Gateway đọc claim `companyId` trong JWT và gắn header `X-Company-Id` cho service phía sau. Token hợp lệ nhưng không có `companyId` (token cấp trước khi nâng cấp) trả `401` để giao diện buộc đăng nhập lại; công tắc `GATEWAY_COMPANY_REQUIRED` (mặc định bật) chỉ để tắt tạm khi auth-service chưa cấp claim này.
- Gateway xóa `X-Company-Id`, `X-Plan` và các header `X-User-*` do client tự gửi trước khi gắn giá trị thật.
- Bổ sung đường dẫn công khai cho các API không cần đăng nhập của WMS-23: đăng ký, kích hoạt, quên và đặt lại mật khẩu, danh sách gói, webhook PayOS.
- Phần áp `TenantEntity` vào inventory-service do Linh làm trọn trong WMS-22.4a (PR #53); task này lo phần cấu hình để các service chưa chuyển đổi vẫn phát sự kiện và gọi sang inventory-service kèm doanh nghiệp: doanh nghiệp mặc định ở máy phát triển cho order-service, product-service, notification-service, và để rỗng trên máy chủ.
- Test của gateway cho hai doanh nghiệp: mỗi token nhận đúng `X-Company-Id` của mình, header giả mạo bị thay thế, token thiếu `companyId` bị từ chối.

**Tiêu chí nghiệm thu**

1. Gọi API kho bằng token của A chỉ thấy kho của A; bằng token của B chỉ thấy kho của B.
2. Tự thêm header `X-Company-Id: 2` vào request dùng token của A: kết quả vẫn là dữ liệu của A.
3. Token không có `companyId` nhận `401`.

### WMS-22.4a [Backend] inventory-service: tách dữ liệu kho theo doanh nghiệp

Người làm: Linh. Ngày: 09/11. Jira: TASK-136. Phụ thuộc: WMS-22.1; làm theo mẫu của WMS-22.3 và `docs/MULTI_TENANT.md`.

**Mô tả chức năng**

- Thêm `company_id` và chuyển sang kế thừa `TenantEntity` cho các bảng `warehouses`, `bin_locations`, `stock_items`, `stock_reservations`, `incoming_stocks`. Dữ liệu đang có gán doanh nghiệp 1.
- Đổi ràng buộc duy nhất: mã kho thành `(company_id, code)`, mã vị trí thành `(company_id, code)`. Ràng buộc theo vị trí trong kho `(warehouse_id, zone, rack, bin)` và `(order_id, product_id)` giữ nguyên.
- Thêm index bắt đầu bằng `company_id` cho các cột hay lọc (kho, sản phẩm).
- Sự kiện inventory-service phát ra (`inventory.updated`, `alert.low-stock`) mang `companyId`. Listener nhận sự kiện đơn hàng đặt ngữ cảnh doanh nghiệp từ sự kiện trước khi ghi tồn kho, hàng đang về.
- Phân quyền theo kho được gán (WMS-04.2) vẫn áp dụng bên trong doanh nghiệp.
- Các bảng làm sau (khóa bi quan, điều chuyển, kiểm kê, thẻ kho) kế thừa `TenantEntity` ngay khi tạo.

**Tiêu chí nghiệm thu**

1. Doanh nghiệp A và B cùng tạo kho mã `WH01`: cả hai đều thành công.
2. B liệt kê kho, vị trí, tồn kho: không có bản ghi nào của A. B gọi theo ID kho của A nhận `404`.
3. B sửa hoặc xóa kho của A bằng ID nhận `404`, dữ liệu của A không đổi.
4. Sự kiện `order.created` của A chỉ tạo hàng đang về cho A.
5. Test cách ly nằm trong bộ test của service và chạy trên CI.

### WMS-22.4b [Backend] product-service và order-service: tách dữ liệu và API nhập hàng loạt

Người làm: Việt. Ngày: 10/11 đến 12/11. Jira: TASK-137. Phụ thuộc: WMS-22.1.

**Phần 1: tách dữ liệu**

- product-service: `categories`, `uoms`, `products`, `unit_conversions`. Ràng buộc duy nhất đổi thành `(company_id, code)` cho danh mục và đơn vị, `(company_id, sku)` và `(company_id, barcode)` cho hàng hóa.
- order-service: `suppliers`, `customers`, `supplier_transactions`, `customer_transactions`, `purchase_orders`, `po_items`, `sales_orders`, `so_items`. Ràng buộc duy nhất đổi thành `(company_id, code)` cho đối tác, `(company_id, po_number)` và `(company_id, so_number)` cho đơn.
- Kiểm tra trùng mã số thuế của nhà cung cấp, khách hàng chỉ xét trong cùng doanh nghiệp.
- Số chứng từ (PO, SO) sinh theo từng doanh nghiệp: hai doanh nghiệp có thể cùng có `PO-20261110-001`.
- Sự kiện đơn hàng mang `companyId`. Lời gọi sang inventory-service chuyển tiếp header doanh nghiệp.
- Doanh nghiệp mới đăng ký bắt đầu với danh mục rỗng, không dùng chung đơn vị tính hay danh mục với doanh nghiệp khác.

**Phần 2: API nhập hàng loạt**

| API | Vai trò được gọi | Trường của mỗi dòng |
| --- | --- | --- |
| `POST /api/products/bulk` | Như API tạo hàng hóa | `sku`, `name` (bắt buộc), `barcode`, `description`, `categoryName`, `uomName` (bắt buộc), `purchasePrice`, `sellingPrice`, `vatRate`, `safetyStock`, `reorderPoint`, `maxStock` |
| `POST /api/orders/suppliers/bulk` | Như API tạo nhà cung cấp | Các trường của `CreateSupplierRequest` |
| `POST /api/orders/customers/bulk` | Như API tạo khách hàng | Các trường của `CreateCustomerRequest` |

- Body: `{ "items": [ ... ] }`, tối đa 500 dòng. Body rỗng hoặc quá 500 dòng trả `400 VALIDATION_ERROR`.
- Kết quả luôn là `200` với `total`, `created`, `skipped`, `failed` và mảng `results`, mỗi phần tử gồm `row` (thứ tự dòng, từ 1), `status` (`CREATED`, `SKIPPED`, `FAILED`), `id`, `code`, `message`.
- Mỗi dòng là một giao dịch riêng: dòng lỗi không làm hỏng dòng khác.
- Mỗi dòng được kiểm tra đúng như khi tạo từng bản ghi. Mã đã tồn tại trong doanh nghiệp thì `SKIPPED`, không ghi đè. Mã lặp lại trong cùng file thì dòng sau `FAILED`.
- Với hàng hóa: `categoryName` và `uomName` chưa có trong doanh nghiệp thì tự tạo mới rồi gán.
- Khi WMS-23.2 có giới hạn gói: các dòng vượt giới hạn SKU nhận `FAILED` kèm lý do vượt giới hạn gói.

**Tiêu chí nghiệm thu**

1. Test cách ly cho hàng hóa, nhà cung cấp, khách hàng, đơn mua, đơn bán: B không đọc, sửa, xóa được dữ liệu của A; hai bên dùng trùng mã SKU và mã đối tác được.
2. Nhập 50 hàng hóa hợp lệ: `created = 50`, xem được trong danh mục.
3. File có 3 dòng lỗi (thiếu tên, trùng SKU đã có, trùng SKU trong file): 47 dòng còn lại vẫn vào, 3 dòng lỗi có `row` và `message` đúng.
4. Nhập lại đúng file đó: tất cả `SKIPPED`, không sinh bản ghi trùng.

**Phần product-service đã làm** (11/11, migration `V4`)

- Migration `V4__multi_tenant_company_id.sql`: thêm `company_id BIGINT NOT NULL` cho `categories`, `uoms`, `products`, `unit_conversions` (dữ liệu cũ thuộc doanh nghiệp 1, quy đổi lấy doanh nghiệp từ sản phẩm); ràng buộc duy nhất `uk_categories_company_code`, `uk_uoms_company_code`, `uk_products_company_sku`, `uk_products_company_barcode`; index `idx_products_company_active`, `idx_unit_conversions_company`. MySQL cho phép nhiều dòng `barcode` NULL trong chỉ mục duy nhất nên hàng hóa chưa có mã vạch vẫn tạo được không giới hạn như trước.
- Bốn entity kế thừa `TenantEntity`; `app.tenant.required: true`. Không có câu SQL gốc nào, mọi kiểm tra trùng (SKU, mã vạch, mã danh mục, mã đơn vị tính, cặp quy đổi) tự xét trong doanh nghiệp. Xem, sửa, xóa theo ID của doanh nghiệp khác, kể cả dùng ID danh mục hoặc đơn vị tính của doanh nghiệp khác khi tạo hàng hóa, quy đổi, đều trả `404`.
- Đơn vị tính mẫu do `V3` thêm (`PCS`, `BOTTLE`, `CAN`, `PACK`, `BOX`, `CTN`, `PLT`, `KG`, `BAG`) thuộc doanh nghiệp 1. Doanh nghiệp mới đăng ký bắt đầu với danh mục và đơn vị tính rỗng; API nhập hàng loạt tự tạo khi cần.
- Giới hạn SKU (WMS-23.2) đếm SKU đang hoạt động của chính doanh nghiệp (`assertCanCreate` thay cho `assertCanCreateIfTenant`), `wms:usage:{companyId}` trường `skus` ghi số của doanh nghiệp đó.
- product-service không phát, không nhận sự kiện RabbitMQ. Hiện không service nào gọi product-service: `ProductClient` của order-service và inventory-service đã khai báo nhưng chưa được dùng; khi dùng, `TenantFeignRequestInterceptor` của `common` (tự đăng ký cho mọi Feign client) chuyển tiếp `X-Company-Id`. Lời gọi từ listener hoặc tác vụ định kỳ phải bọc trong `TenantContext.runAs`.

`POST /api/products/bulk`

- Quyền: như `POST /api/products` (product-service chưa phân quyền theo vai trò, mọi tài khoản đã đăng nhập đều gọi được; cần doanh nghiệp, thiếu thì `403 COMPANY_REQUIRED`).
- Body: `{ "items": [ { "sku", "name", "barcode", "description", "categoryName", "uomName", "purchasePrice", "sellingPrice", "vatRate", "safetyStock", "reorderPoint", "maxStock" } ] }`, từ 1 đến 500 dòng. Body trống, thiếu `items`, danh sách rỗng hoặc quá 500 dòng trả `400 VALIDATION_ERROR`.
- Kết quả `200`, `data` là `{ "total", "created", "skipped", "failed", "results": [ { "row", "status", "id", "code", "message" } ] }`; `code` là SKU sau khi chuẩn hóa (cắt khoảng trắng, viết hoa), `id` là ID hàng vừa tạo hoặc hàng đã có.
- Thứ tự xét một dòng: SKU đã xuất hiện ở dòng trước trong file thì `FAILED` ("Mã SKU 'X' bị lặp lại trong file (đã có ở dòng N)"); kiểm tra dữ liệu bằng đúng ràng buộc của API tạo một hàng hóa cộng `uomName` bắt buộc (tối đa 100 ký tự), `categoryName` tối đa 150 ký tự, nhiều lỗi nối bằng dấu chấm phẩy; SKU đã có trong doanh nghiệp thì `SKIPPED`, không ghi đè; còn lại tạo qua đúng nghiệp vụ của `POST /api/products`, nên trùng mã vạch và vượt giới hạn SKU của gói thành `FAILED` kèm thông báo của lỗi đó (ví dụ "Gói Khởi Đầu chỉ cho phép tối đa 200 SKU. Nâng cấp gói để thêm SKU mới.").
- `categoryName`, `uomName` tìm theo tên (không phân biệt hoa thường và khoảng trắng hai đầu), không thấy thì tìm theo mã; vẫn không có thì tạo mới cho doanh nghiệp với mã sinh từ tên (bỏ dấu, viết hoa, ví dụ "Thùng" thành `THUNG`, trùng thì thêm `-2`). Danh mục, đơn vị tính tạo ở một dòng được các dòng sau trong cùng file dùng lại.
- Mỗi dòng là một transaction riêng (`REQUIRES_NEW` trong bean `ProductBulkRowImporter`): dòng lỗi rollback cả danh mục, đơn vị tính vừa tạo cho nó, không ảnh hưởng dòng khác.
- Lớp kết quả dùng chung ở `vn.wms.common.bulk` (`BulkRequest`, `BulkResult`, `BulkRowResult`, `BulkRowStatus`). order-service hiện có lớp riêng cùng cấu trúc JSON trong `vn.wms.order.dto.bulk`.

**Phần order-service đã làm** (11/11, migration `V9`)

- Tách dữ liệu toàn bộ 14 bảng của order-service, không chỉ tám bảng ở phần 1: thêm cả phiếu nhập (`goods_receipt_notes`, `goods_receipt_note_items`), hóa đơn mua (`purchase_bills`, `purchase_bill_items`), phiếu xuất (`goods_delivery_notes`, `goods_delivery_note_items`). Số chứng từ duy nhất trong một doanh nghiệp: `(company_id, po_number)`, `so_number`, `grn_number`, `bill_number`, `gdn_number`. Bật `app.tenant.required: true`; thiếu doanh nghiệp nhận `403 COMPANY_REQUIRED`.
- Số chứng từ PO, SO, GRN, GDN, PB cấp theo từng doanh nghiệp từ bảng mới `document_sequences` (khóa dòng trong transaction riêng), giữ định dạng 4 chữ số đang dùng: hai doanh nghiệp cùng có `PO-20261110-0001`.
- Sự kiện mang `x-wms-company-id` (tự động qua `WmsEventPublisher`, payload không đổi). Lời gọi OpenFeign sang inventory-service, product-service chuyển tiếp `X-Company-Id`. `/internal/orders/kpi-metrics` chỉ trả số liệu của doanh nghiệp gọi. Chi tiết kỹ thuật ở [MULTI_TENANT.md](MULTI_TENANT.md), mục 4.

**API nhập hàng loạt đã làm (order-service)**

| API | Vai trò | Body |
| --- | --- | --- |
| `POST /api/orders/suppliers/bulk` | Như `POST /api/orders/suppliers`: `ADMIN`, `WAREHOUSE_MANAGER`, `PURCHASING`, `ACCOUNTANT` (WMS-20.3) | `{ "items": [CreateSupplierRequest, ...] }`: `code`, `name` (bắt buộc), `taxCode`, `contactName`, `email`, `phone`, `address`, `bankAccount`, `bankName`, `paymentTerms`, `creditLimit`, `initialDebt` |
| `POST /api/orders/customers/bulk` | Như `POST /api/orders/customers` (khai báo `ADMIN`, `SALES`, `ACCOUNTANT`) | `{ "items": [CreateCustomerRequest, ...] }`: `code`, `name`, `customerType` (`WHOLESALE`/`RETAIL`, bắt buộc), `taxCode`, `contactName`, `email`, `phone`, `address`, `paymentTerms`, `creditLimit`, `initialDebt`, `notes` |

- Thiếu body, `items` rỗng hoặc hơn 500 dòng: `400 VALIDATION_ERROR` (với `items` rỗng hoặc quá dài, `data.items` là lý do). Còn lại luôn `200`, `data` = `{ total, created, skipped, failed, results: [{ row, status, id, code, message }] }`. `code` là mã đã chuẩn hóa (viết hoa, bỏ khoảng trắng hai đầu); `id` là bản ghi vừa tạo (`CREATED`) hoặc đang có (`SKIPPED`), `null` khi `FAILED`.
- Thứ tự xét mỗi dòng: kiểm tra ràng buộc như API tạo đơn lẻ (lỗi gộp các thông báo bằng `; `) -> mã trùng một dòng hợp lệ phía trên: `FAILED` "trùng với dòng N" -> mã đã có trong doanh nghiệp: `SKIPPED`, không ghi đè -> tạo bằng đúng service tạo đơn lẻ (trùng mã số thuế trong doanh nghiệp, kể cả với dòng phía trên vừa tạo: `FAILED`; công nợ ban đầu ghi giao dịch như tạo đơn lẻ).
- Mỗi dòng một transaction riêng (`REQUIRES_NEW`). Nhập lại cùng file: mọi dòng hợp lệ thành `SKIPPED`; dòng sai dữ liệu và dòng lặp mã trong file vẫn `FAILED` như lần đầu.
- Từ WMS-20.3 order-service bật method security: `@PreAuthorize` có hiệu lực, API nhập hàng loạt có đúng vai trò của API tạo đơn lẻ (ma trận ở [SECURITY.md](SECURITY.md)).

### WMS-22.4c [Backend] notification-service: thông báo, KPI và báo cáo theo doanh nghiệp

Người làm: Toàn. Ngày: 16/11. Jira: TASK-138. Phụ thuộc: WMS-22.1.

**Mô tả chức năng**

- `notification_logs` và các bảng báo cáo thêm `company_id`.
- KPI thời gian thực đếm riêng từng doanh nghiệp, khóa Redis có `companyId`.
- Kênh WebSocket tách theo doanh nghiệp: `/topic/company/{companyId}/alerts`, `/orders`, `/inventory`, `/kpi`. Khi đăng ký kênh, máy chủ chỉ cho phép kênh của đúng doanh nghiệp trong JWT. Toàn sửa luôn hook `useWebSocket` ở frontend.
- Listener bỏ qua và ghi log cảnh báo với sự kiện không có `companyId`.
- Báo cáo và email cảnh báo chỉ tổng hợp dữ liệu, chỉ gửi tới người dùng của doanh nghiệp phát sinh sự kiện.

**Tiêu chí nghiệm thu**

1. A tạo đơn hàng: chỉ người dùng của A nhận thông báo; KPI của B không đổi.
2. Người dùng của B đăng ký kênh của A bị từ chối.

**Đã làm (11/11)**

- Dữ liệu: `notification_logs` là bảng duy nhất của service (chưa có bảng báo cáo), kế thừa `TenantEntity`; migration `V2__multi_tenant_company_id.sql` thêm `company_id`, gán dữ liệu cũ cho doanh nghiệp 1, đặt NOT NULL, thêm index `(company_id, status)` và `(company_id, created_at)`. `app.tenant.required: true`, `default-company-id: ${TENANT_DEFAULT_COMPANY_ID:}` (compose dev 1, máy chủ rỗng). Đường dẫn bỏ qua kiểm tra doanh nghiệp: `/ws-wms/**`, `/api/notifications/health-info`.
- Listener: `AlertEventsListener`, `OrderEventsListener`, `InventoryEventsListener` lấy doanh nghiệp từ header `x-wms-company-id`, không có thì trường `companyId` của JSON, rồi xử lý trong `TenantContext.runAs`. Thiếu doanh nghiệp: ghi một dòng log cảnh báo và bỏ qua, không ném lỗi, không thử lại, không gửi email.
- KPI: khóa Redis `wms:notification:kpi:{companyId}:{yyyy-MM-dd}` và `wms:notification:kpi:{companyId}:processed:{messageId}`. `GET /api/notifications/realtime/kpi` trả bộ đếm của doanh nghiệp trong `X-Company-Id`, thiếu thì `403 COMPANY_REQUIRED`. Cache của `GET /api/notifications/dashboard/kpi-summary` đã theo doanh nghiệp: `wms:kpi:company:{companyId}:summary:...`.
- WebSocket: kênh `/topic/company/{companyId}/alerts`, `/orders`, `/inventory`, `/kpi` thay cho bốn topic dùng chung. Frame CONNECT gửi `Authorization: Bearer <accessToken>`; máy chủ kiểm tra chữ ký bằng `JWT_SECRET` như gateway và lấy doanh nghiệp từ claim `companyId`. Bật `GATEWAY_JWT_ENABLED` (máy chủ): thiếu, sai token hoặc token không có `companyId` bị từ chối ở CONNECT. Tắt (máy phát triển): token không bắt buộc, phiên không có token dùng doanh nghiệp mặc định. SUBSCRIBE chỉ được vào kênh của doanh nghiệp của phiên; kênh doanh nghiệp khác, phiên chưa xác thực, topic cũ đều nhận frame ERROR.
- Email: gửi và ghi nhật ký dưới doanh nghiệp của sự kiện; không có doanh nghiệp thì không gửi. Người nhận nằm sẵn trong tin (`recipient`) do service phát chọn trong doanh nghiệp của nó; notification-service hiện không gọi auth-service tra người nhận.
- Frontend: `realtimeConnection.ts` đăng ký `realtimeTopics(companyId)`, `companyId` lấy từ claim của token gửi ở CONNECT, không có thì từ `company.id` lưu lúc đăng nhập (`LoginPage` lưu thêm `company`).
- Test: cách ly nhật ký (B không đọc, không xóa được bản ghi của A), sự kiện của A chỉ tăng KPI và chỉ tới kênh của A, sự kiện thiếu doanh nghiệp bị bỏ qua, quyền SUBSCRIBE (đúng doanh nghiệp được, doanh nghiệp khác và phiên chưa xác thực bị từ chối) cả ở mức interceptor lẫn kết nối WebSocket thật.

### WMS-22.5 [QA] Kiểm thử cách ly dữ liệu, giới hạn gói và hết hạn

Người làm: Phương Anh. Ngày: 19/11 đến 20/11. Jira: TASK-139. Phụ thuộc: toàn bộ WMS-22 và WMS-23.

**Mô tả công việc**

- Lập bảng test case và chạy trên máy chủ với hai doanh nghiệp đăng ký mới, ghi kết quả đạt hoặc không đạt, kèm ảnh hoặc lệnh gọi API.
- Nhóm cách ly: với từng nhóm dữ liệu (người dùng, kho, vị trí, tồn kho, hàng hóa, nhà cung cấp, khách hàng, đơn mua, đơn bán, thông báo, báo cáo, API key) thử ba thao tác của B lên dữ liệu của A: xem danh sách, xem theo ID, sửa hoặc xóa theo ID.
- Nhóm giới hạn gói: ở gói `FREE` tạo tài khoản thứ 4, kho thứ 2, SKU thứ 201, lượt AI thứ 21, tạo API key.
- Nhóm hết hạn: dùng công cụ demo đặt hết hạn; kiểm tra xem được, không ghi được, thanh toán xong ghi lại được ngay.
- Lỗi tìm thấy tạo thành issue giao cho người phụ trách service, gộp vào đợt sửa lỗi WMS-20.4.

**Tiêu chí nghiệm thu**

Bảng test case có kết quả nằm trong `docs/`, không còn trường hợp lọt dữ liệu nào chưa được xử lý.

### WMS-23.0 [Fullstack] Bỏ gợi ý tài khoản mặc định trên trang đăng nhập

Người làm: Toàn. Đã xong ngày 04/11. Jira: TASK-141.

Trang đăng nhập không còn hiển thị gợi ý tài khoản mặc định. Theo quyết định của Toàn, phạm vi dừng ở giao diện; các tài khoản mẫu trong dữ liệu seed giữ nguyên và thuộc Công ty mẫu A sau WMS-22.2.

### WMS-23.1 [Backend] Đăng ký doanh nghiệp, gói dịch vụ và thuê bao

Người làm: Phương Anh. Ngày: 09/11. Jira: TASK-142. Phụ thuộc: WMS-22.2.

**Mục tiêu**

Một doanh nghiệp mới tự đăng ký được mà không cần ai tạo hộ, nhận 30 ngày dùng thử, và hệ thống luôn biết doanh nghiệp đang ở gói nào, còn hạn hay đã hết hạn.

**Giải thích nghiệp vụ**

- Đăng ký: khách điền một form duy nhất gồm thông tin công ty và thông tin người quản trị. Hệ thống tạo cùng lúc ba thứ: doanh nghiệp, tài khoản `ADMIN` đầu tiên của doanh nghiệp đó, và thuê bao dùng thử 30 ngày. Hoặc cả ba cùng được tạo, hoặc không có gì được tạo.
- Gói dịch vụ là bảng giá và giới hạn cố định của nền tảng (ba dòng `FREE`, `TRIAL`, `PRO`). Thuê bao là trạng thái của riêng từng doanh nghiệp: đang ở gói nào, tới ngày nào.
- Task này chỉ lưu và trả lời "doanh nghiệp đang ở gói nào, còn bao nhiêu ngày". Việc chặn khi vượt giới hạn hoặc hết hạn do WMS-23.2 (Toàn) làm, dựa trên dữ liệu task này ghi ra Redis.
- Chưa có thanh toán thật ở task này. API `renew-mock` là cách gia hạn tạm để nhóm test được luồng đổi gói; khi WMS-23.5a xong, thanh toán PayOS sẽ gọi đúng hàm kích hoạt gói mà task này viết.
- API `demo/expire` là công cụ để trình diễn với thầy: bấm một cái cho doanh nghiệp hết hạn ngay thay vì chờ 30 ngày.

**Dữ liệu**

- Bảng `plans` (seed bằng migration theo mục 2.5): `code` (`FREE`, `TRIAL`, `PRO`), `name`, `price_monthly`, `price_yearly`, `max_users`, `max_warehouses`, `max_skus`, `max_ai_requests`, `api_access`, `trial_days`. Giá trị `-1` là không giới hạn.
- Bảng `subscriptions`: `company_id` (duy nhất), `plan_code`, `status` (`ACTIVE`, `EXPIRED`), `trial_ends_at`, `expires_at`, bốn cột audit. Migration tạo thuê bao `PRO` còn hạn tới hết 31/12/2026 cho hai công ty mẫu.

**API**

| API | Ai gọi | Mô tả |
| --- | --- | --- |
| `POST /api/auth/companies/register` | Công khai | Body: `companyName` (2 đến 150 ký tự), `taxCode`, `phone` (không bắt buộc), `fullName`, `username`, `email`, `password` (quy tắc như tạo tài khoản hiện nay). Trong một giao dịch: tạo doanh nghiệp, tài khoản `ADMIN` đầu tiên, thuê bao `TRIAL` với `trial_ends_at` bằng thời điểm đăng ký cộng 30 ngày. Trả `201` với `company`, `admin`, `subscription`. Không trả token; giao diện chuyển sang trang đăng nhập |
| `GET /api/auth/plans` | Công khai | Danh sách gói, giá và giới hạn |
| `GET /api/auth/subscription` | Mọi tài khoản | `plan`, `planName`, `status`, `trialEndsAt`, `expiresAt`, `daysRemaining`, `readOnly`, `limits`, `usage` (`users`, `warehouses`, `skus`, `aiRequests`; ở task này trả `users`, WMS-23.2 bổ sung ba số còn lại: `users` đếm tài khoản đang bật trong cơ sở dữ liệu, `warehouses` và `skus` đọc hash `wms:usage:{companyId}` (chưa có thì `null`), `aiRequests` đọc bộ đếm tháng hiện tại (chưa có thì `0`)) |
| `POST /api/auth/subscription/renew-mock` | `ADMIN`, chỉ khi `PAYOS_MOCK=true` | Body: `plan` (`PRO`), `cycle` (`MONTHLY`, `YEARLY`). Kích hoạt ngay không qua thanh toán, dùng để phát triển và test |
| `POST /api/auth/subscription/downgrade` | `ADMIN` | Chuyển về `FREE` theo quy tắc mục 2.6; không hợp lệ trả `409 CONFLICT` |
| `POST /api/auth/subscription/demo/expire` | `ADMIN`, chỉ khi `APP_DEMO_TOOLS=true` | Đặt thuê bao của doanh nghiệp mình thành hết hạn để trình diễn |

Ví dụ đăng ký, body gửi lên `POST /api/auth/companies/register`:

```json
{
  "companyName": "Công ty TNHH Kho Vận Mẫu",
  "taxCode": "0101234567",
  "phone": "0901234567",
  "fullName": "Nguyễn Văn An",
  "username": "an.nguyen",
  "email": "an@example.com",
  "password": "<mật khẩu>"
}
```

Phần `data` trả về:

```json
{
  "company": { "id": 3, "code": "CTY000003", "name": "Công ty TNHH Kho Vận Mẫu", "status": "ACTIVE" },
  "admin": { "username": "an.nguyen", "email": "an@example.com" },
  "subscription": { "plan": "TRIAL", "status": "ACTIVE", "trialEndsAt": "2026-12-09T03:00:00Z" }
}
```

Phần `data` của `GET /api/auth/subscription` ngay sau khi đăng ký:

```json
{
  "plan": "TRIAL",
  "planName": "Dùng thử",
  "status": "ACTIVE",
  "trialEndsAt": "2026-12-09T03:00:00Z",
  "expiresAt": null,
  "daysRemaining": 30,
  "readOnly": false,
  "limits": { "maxUsers": 50, "maxWarehouses": 20, "maxSkus": -1, "maxAiRequests": 500, "apiAccess": true },
  "usage": { "users": 1, "warehouses": null, "skus": null, "aiRequests": 0 }
}
```

**Quy tắc nghiệp vụ**

- Tên đăng nhập hoặc email đã tồn tại trả `409 CONFLICT`, nêu rõ trường nào trùng. Mỗi email chỉ đăng ký được một doanh nghiệp.
- Mã doanh nghiệp do hệ thống sinh, người dùng không nhập.
- Trạng thái doanh nghiệp sau đăng ký: từ WMS-23.6 là `PENDING` cho tới khi bấm liên kết kích hoạt trong email (tắt được bằng `REGISTRATION_REQUIRE_ACTIVATION=false` trên máy phát triển, xem WMS-23.6).
- Việc chuyển sang `EXPIRED` do một tác vụ định kỳ 5 phút một lần thực hiện, đồng thời kiểm tra lại khi đăng nhập và khi gọi `GET /api/auth/subscription`.
- Mỗi thay đổi thuê bao ghi lại bộ nhớ đệm theo mục 2.7.
- Viết phần kích hoạt gói thành một hàm dùng chung của `SubscriptionService` (nhận doanh nghiệp, gói, chu kỳ) để WMS-23.5a gọi lại khi thanh toán thành công.
- Token và kết quả đăng nhập thêm `plan`, `subscription` theo mục 2.1.

**Các bước triển khai gợi ý**

1. Migration kế tiếp của auth-service: tạo `plans` và thêm ba dòng theo bảng ở mục 2.5; tạo `subscriptions` và thêm thuê bao cho hai công ty mẫu.
2. Thêm entity `Plan`, `Subscription`, enum `SubscriptionStatus`, `BillingCycle` và hai repository.
3. `SubscriptionService` gồm: `startTrial(company)`; `activate(companyId, planCode, cycle)` tính ngày hết hạn theo công thức mục 2.6; `downgradeToFree(companyId)`; `expireIfDue(subscription)`; `getCurrent(companyId)` trả DTO cho API. Sau mỗi thay đổi gọi một hàm ghi hash `wms:subscription:{companyId}` vào Redis (auth-service đã có sẵn `RedisTemplate`).
4. `CompanyRegistrationService.register(request)` đặt `@Transactional`: kiểm tra trùng tên đăng nhập và email; lưu doanh nghiệp để có `id` rồi đặt `code`; tạo tài khoản với vai trò `ADMIN` (dùng lại cách băm mật khẩu và gán vai trò của `AuthService.register`); gọi `startTrial`.
5. Controller mới cho `/api/auth/companies/register`, `/api/auth/plans`, `/api/auth/subscription/**`. Trong `SecurityConfig` cho phép gọi không cần đăng nhập với hai API công khai.
6. Đường dẫn công khai ở gateway (`gateway.jwt.public-paths` trong `api-gateway/src/main/resources/application.yml`) do Toàn thêm ở WMS-22.3 cùng ngày; nếu Pull Request của task này vào trước thì thêm luôn `/api/auth/companies/register` và `/api/auth/plans`.
7. Tác vụ định kỳ: bật `@EnableScheduling`, một hàm `@Scheduled` chạy 5 phút một lần tìm thuê bao `ACTIVE` đã quá `trial_ends_at` hoặc `expires_at`, chuyển `EXPIRED` và ghi lại Redis.
8. `AuthService.login` và `refreshToken`: gọi `expireIfDue`, ghi lại Redis, thêm claim `plan` và đối tượng `subscription` vào kết quả.
9. Hai API chỉ dùng khi phát triển và trình diễn đọc cấu hình `PAYOS_MOCK` (mặc định `true`) và `APP_DEMO_TOOLS` (mặc định `false`); khi tắt thì trả `404`.
10. Test theo năm tiêu chí bên dưới; bài test đăng ký trùng email kiểm tra thêm là không có dòng `companies` nào được tạo.

**Tiêu chí nghiệm thu**

1. Đăng ký xong đăng nhập được ngay bằng tài khoản vừa tạo; `GET /api/auth/subscription` trả `TRIAL`, `daysRemaining = 30`.
2. Đăng ký lần hai với cùng email hoặc cùng tên đăng nhập nhận `409`, không sinh doanh nghiệp rác.
3. `renew-mock` gói `PRO` chu kỳ tháng: `plan = PRO`, `expiresAt` tăng 30 ngày theo đúng công thức mục 2.6.
4. `demo/expire` rồi `downgrade`: thuê bao thành `FREE / ACTIVE`.
5. Sau mỗi bước trên, khóa `wms:subscription:{companyId}` trong Redis có giá trị mới.

### WMS-23.2 [Backend] Thực thi giới hạn gói và trạng thái hết hạn

Người làm: Toàn. Ngày: 10/11. Jira: TASK-143. Phụ thuộc: WMS-23.1.

**Mô tả chức năng**

- Gateway đọc bộ nhớ đệm thuê bao, gắn `X-Plan`, và chặn request ghi khi thuê bao `EXPIRED` theo danh sách ngoại lệ ở mục 2.6.
- `PlanLimitChecker` trong `common`: service gọi trước khi tạo mới. auth-service kiểm tra số người dùng, inventory-service kiểm tra số kho, product-service kiểm tra số SKU (cả API nhập hàng loạt), ai-service kiểm tra lượt AI. Vượt thì trả `PLAN_LIMIT_EXCEEDED` kèm `data` theo mục 2.4.
- Bật lại một tài khoản đã khóa hoặc một kho đã ngưng cũng tính là tạo thêm.
- `GET /api/auth/subscription` trả đủ bốn số `usage`.

**Tiêu chí nghiệm thu**

1. Gói `FREE`: tạo tài khoản thứ 4, kho thứ 2, SKU thứ 201 đều nhận `403 PLAN_LIMIT_EXCEEDED` với `limit`, `current`, `max` đúng.
2. Thuê bao hết hạn: `GET` vẫn chạy, `POST`, `PUT`, `PATCH`, `DELETE` nghiệp vụ nhận `403 SUBSCRIPTION_EXPIRED`; đăng nhập và các API thuê bao, thanh toán vẫn dùng được.
3. Gia hạn xong, request ghi kế tiếp chạy được ngay, không cần đăng nhập lại.

**Cách làm (phần 1, nhánh `wms-23-2`)**

- `common`, gói `vn.wms.common.plan`: `PlanLimitChecker` tự đăng ký khi service có bean `StringRedisTemplate`; tắt bằng `app.plan-limit.enabled=false` (test không có Redis đặt sẵn giá trị này). Service tiêm dạng tùy chọn (`ObjectProvider<PlanLimitChecker>`).
  - `assertCanCreate(limit, currentCount)`: `currentCount` là số bản ghi đang hoạt động trước khi tạo; `currentCount >= max` (và `max != -1`) thì ném `PLAN_LIMIT_EXCEEDED`, `data` là `{limit, current, max, plan}`.
  - `assertCanCreateIfTenant(limit, supplier)`: bỏ qua khi chưa có doanh nghiệp trong ngữ cảnh, dùng cho service chưa tách dữ liệu (product-service dùng tới trước WMS-22.4b, nay không còn service nào dùng).
  - `reportUsage(limit, count)`, `incrementMonthlyAiUsage()`, `assertAiQuota()` cho ai-service.
- `ApiException.withData(code, message, data)`: `GlobalExceptionHandler` trả `data` kèm lỗi.
- Gateway: `SubscriptionGlobalFilter` (order -99, ngay sau bộ lọc JWT) gắn `X-Plan` và chặn ghi khi `EXPIRED`. Danh sách ngoại lệ ở `gateway.subscription.write-allowed-paths`; tắt cả bộ lọc bằng `GATEWAY_SUBSCRIPTION_ENABLED=false`. Bộ lọc chỉ có tác dụng khi bật kiểm tra JWT (mới có `X-Company-Id`).
- inventory-service kiểm tra số kho khi tạo kho đang hoạt động và khi bật lại kho đã ngưng (PATCH trạng thái hoặc PUT có `active: true`); product-service kiểm tra số SKU khi tạo, khi nhập hàng loạt và khi bật lại SKU đã xóa mềm. Từ WMS-22.4b product-service đếm SKU của chính doanh nghiệp.
- Phần 2 (auth-service, nhánh `wms-23-2b`):
  - `UserPlanLimitGuard` kiểm tra `USERS` (số tài khoản `enabled = true` của doanh nghiệp) trước khi tạo tài khoản (`POST /api/auth/users`, `POST /api/auth/register`) và trước khi mở khóa (`PATCH .../status` hoặc `PUT` có `enabled: true`); sau mỗi lần tạo, mở khóa, khóa thì ghi lại `users` vào `wms:usage:{companyId}` sau khi commit. auth-service không dùng `TenantContext` nên gọi bản nhận doanh nghiệp trực tiếp `assertCanCreate(companyId, limit, currentCount)`, `reportUsage(companyId, limit, count)` (thêm vào `PlanLimitChecker`, bản cũ giữ nguyên). Đăng ký doanh nghiệp mới không bị chặn.
  - `GET /api/auth/subscription` trả đủ bốn số `usage` (cách đọc ở mục API của WMS-23.1); Redis lỗi thì ba số đọc từ Redis là `null`.
  - `SubscriptionCacheWriter` dùng tên khóa, tên trường của `SubscriptionCacheKeys`; test hợp đồng kiểm tra hash ghi ra được `SubscriptionSnapshot.fromHash` đọc lại đúng.
  - Tác vụ định kỳ 5 phút, sau khi chuyển `EXPIRED`, ghi lại hash của mọi thuê bao để Redis mất dữ liệu thì tự lành trong 5 phút; Redis không chạy chỉ ghi cảnh báo.

### WMS-23.3a [Frontend] Trang giới thiệu và bảng giá

Người làm: Linh. Đã xong ngày 05/11. Jira: TASK-144.

Trang `/landing` và `/pricing` đã có, dữ liệu gói lấy từ `pricingConfig.ts`. Từ WMS-23.3b mọi nút đăng ký, dùng thử của trang giới thiệu trỏ sang `/register` (thẻ gói Khởi Đầu, Chuyên Nghiệp kèm `?plan=free|pro`); số liệu trong `pricingConfig.ts` giữ khớp mục 2.5.

### WMS-23.3b [Frontend] Đăng ký doanh nghiệp và đăng nhập theo doanh nghiệp

Người làm: Phương Anh. Ngày: 17/11. Jira: TASK-145. Phụ thuộc: WMS-23.1.

**Mô tả chức năng**

- Trang `/register`: các trường tên doanh nghiệp, mã số thuế, số điện thoại, họ tên người quản trị, tên đăng nhập, email, mật khẩu, nhập lại mật khẩu, ô đồng ý điều khoản. Kiểm tra dữ liệu ngay trên form theo đúng quy tắc của API.
- Gửi thành công: thông báo đăng ký thành công và 30 ngày dùng thử, chuyển sang `/login` (sau WMS-23.6 thì hiện hướng dẫn mở email để kích hoạt). Lỗi `409` hiển thị ngay dưới trường bị trùng.
- Đăng nhập: lưu `company` và `subscription` cùng thông tin người dùng; hiển thị tên doanh nghiệp ở thanh tiêu đề và trang hồ sơ. Lỗi `COMPANY_INACTIVE` hiện thông báo riêng, kèm nút gửi lại email kích hoạt khi WMS-23.6 xong.
- Trang đăng nhập có liên kết "Đăng ký doanh nghiệp" và "Quên mật khẩu".

**Tiêu chí nghiệm thu**

Từ trang giới thiệu bấm đăng ký, điền form, đăng nhập và vào được trang tổng quan của doanh nghiệp mới với dữ liệu rỗng, thanh tiêu đề hiện đúng tên doanh nghiệp.

**Đã làm (12/11)**

- `/register` (`features/auth/RegisterPage.tsx`, khung `AuthCardLayout`): quy tắc kiểm tra trên form chép đúng `RegisterCompanyRequest` (`registerForm.ts`): tên doanh nghiệp bắt buộc 2 đến 150 ký tự; mã số thuế, số điện thoại không bắt buộc, tối đa 30; họ tên bắt buộc, tối đa 100; tên đăng nhập 3 đến 50 ký tự theo `^[a-zA-Z0-9._-]+$`; email bắt buộc, đúng `@Email` của Hibernate Validator, tối đa 100; mật khẩu 6 đến 72. Các trường chữ được cắt khoảng trắng trước khi gửi; trường không bắt buộc để trống thì không gửi.
- Lỗi: `400 VALIDATION_ERROR` gắn `data` theo tên trường; `409 CONFLICT` không có `data`, giao diện nhận ra trường trùng (tên đăng nhập, email, mã số thuế) từ đầu thông báo của `CompanyRegistrationService` ("Tên đăng nhập đã tồn tại", "Email đã tồn tại", "Mã số thuế đã tồn tại"). Đổi các thông báo này thì phải sửa `CONFLICT_PREFIXES` trong `registerForm.ts`.
- `activationRequired = true`: ở lại trang, hiện "Đăng ký thành công", ngày hết dùng thử (`subscription.trialEndsAt`), hướng dẫn mở email kích hoạt (24 giờ), form gửi lại email kích hoạt và liên kết sang đăng nhập; không tự đăng nhập. `false`: báo thành công, chuyển `/login` điền sẵn tên đăng nhập.
- Trang giới thiệu không còn form đăng ký riêng: khung dùng thử ở mục Hỏi đáp và mọi nút "Dùng thử", "Đăng ký" dẫn sang `/register`.
- Đăng nhập lưu `company` và `subscription` cùng người dùng; tên doanh nghiệp hiện ở thanh tiêu đề và trang hồ sơ (trang hồ sơ đọc lại `GET /api/auth/me`). Đã đăng nhập mà mở `/register` hoặc `/login` thì vào trang tổng quan.

### WMS-23.4 [Frontend] Trang gói dịch vụ, nhắc hạn dùng thử và chế độ chỉ đọc

Người làm: Linh. Ngày: 16/11. Jira: TASK-146. Phụ thuộc: WMS-23.1, WMS-23.2.

**Mô tả chức năng**

- Trang `/subscription`: gói hiện tại, trạng thái, ngày hết hạn, số ngày còn lại; bốn thanh mức dùng (người dùng, kho, SKU, lượt AI trong tháng) so với giới hạn; bảng so sánh gói; nút nâng cấp hoặc gia hạn theo tháng, theo năm (nối thanh toán ở WMS-23.5b); nút chuyển về gói Khởi Đầu khi được phép.
- Chỉ `ADMIN` thấy các nút thanh toán và chuyển gói. Vai trò khác thấy dòng "Liên hệ quản trị viên của doanh nghiệp".
- Dải nhắc ở đầu mọi trang khi đang `TRIAL`: "Còn N ngày dùng thử", đổi màu cảnh báo khi còn từ 7 ngày trở xuống, có liên kết tới `/subscription`.
- Khi thuê bao `EXPIRED`: hiện dải thông báo cố định "Gói đã hết hạn, hệ thống đang ở chế độ chỉ đọc"; các nút tạo, sửa, xóa trên toàn hệ thống bị vô hiệu hóa qua một hook dùng chung (ví dụ `useSubscription().readOnly`); mọi phản hồi `SUBSCRIPTION_EXPIRED` mở hộp thoại dẫn tới `/subscription`.
- Phản hồi `PLAN_LIMIT_EXCEEDED` ở bất kỳ màn hình nào mở hộp thoại nêu giới hạn nào đã chạm, đang dùng bao nhiêu trên tối đa bao nhiêu, kèm nút nâng cấp.

**Tiêu chí nghiệm thu**

1. Tài khoản dùng thử thấy dải nhắc với số ngày đúng.
2. Sau khi đặt hết hạn bằng công cụ demo: mọi nút ghi bị khóa, xem dữ liệu bình thường, hộp thoại dẫn tới trang gia hạn.
3. Gói `FREE` tạo kho thứ 2: hiện hộp thoại vượt giới hạn với số liệu đúng.

**Cách làm (đã làm, thư mục `frontend/src/features/subscription`)**

- `useSubscription()` (zustand, `subscriptionStore.ts`) trả `plan`, `planName`, `status`, `daysLeft` (lấy `daysRemaining` của máy chủ), `readOnly`, `limits`, `usage`, `refresh`. `SubscriptionLayer` gắn một lần trong `MainLayout`: tải `GET /api/auth/subscription` sau khi đăng nhập, tải lại khi quay lại tab sau 30 giây, hiện dải nhắc dùng thử hoặc dải hết hạn (dính ở đầu trang).
- Lỗi `SUBSCRIPTION_EXPIRED`, `PLAN_LIMIT_EXCEEDED`: interceptor trong `src/services/api.ts` chuyển cho `SubscriptionDialogs` (một hộp thoại cho nhiều request lỗi). Khi đó `getApiErrorMessage` trả chuỗi rỗng và CSS ẩn thông báo nổi rỗng, nên màn hình đang gọi `message.error(getApiErrorMessage(...))` không báo lần hai.
- Chế độ chỉ đọc (`readOnlyGuard.ts`): thân trang có lớp `wms-read-only`; CSS làm mờ và bộ lắng nghe click pha capture chặn các nút chứa biểu tượng thêm, sửa, xóa, lưu, xác nhận (check-circle), hủy (stop), từ chối (close-circle), thu tiền (dollar), nhận hàng (inbox), phát hành hóa đơn (file-done), nút xác nhận của mọi Popconfirm và vùng có `data-write-action`. Vùng có lớp `wms-read-only-allowed` hoặc `data-read-only-allowed` không bị chặn. Nút ghi không mang các dấu hiệu này (ví dụ "Nhập Excel", "Lấy hàng") vẫn bị gateway trả 403 và mở hộp thoại hết hạn.
- Trang `/subscription`: gói, trạng thái, ngày hết hạn, số ngày còn lại; bốn thanh mức dùng (`-1` là không giới hạn, `null` là chưa có số liệu); bảng so sánh lấy từ `GET /api/auth/plans`; ADMIN chọn chu kỳ, xem số tiền, chuyển về gói Khởi Đầu khi đang dùng thử hoặc đã hết hạn (`POST /api/auth/subscription/downgrade`); vai trò khác thấy "Liên hệ quản trị viên của doanh nghiệp".

### WMS-23.5a [Backend] Thanh toán PayOS

Người làm: Toàn. Ngày: 10/11. Jira: TASK-147. Phụ thuộc: WMS-23.1.

**Dữ liệu**

Bảng `payments` ở auth-service (migration `V6__create_payments.sql`): `company_id`, `order_code` (số, duy nhất toàn hệ thống), `plan_code`, `cycle`, `amount` (VND, số nguyên), `status` (`PENDING`, `PAID`, `CANCELLED`, `EXPIRED`), `checkout_url`, `qr_code`, `payment_link_id`, `reference` (mã giao dịch ngân hàng), `paid_at`, `expires_at`, `created_by_user` (ID tài khoản tạo), bốn cột audit.

- Mã đơn `order_code`: mili giây epoch nhân 1000 cộng ba chữ số ngẫu nhiên, ví dụ `1762761600123457` (16 chữ số, dưới giới hạn `9007199254740991` của PayOS). Trùng thì sinh lại.
- Nội dung chuyển khoản gửi PayOS: `WMS PRO <orderCode>` (24 ký tự, PayOS giới hạn 25).
- Không tạo được liên kết ở PayOS: giao dịch chuyển `CANCELLED`, API trả `503 SERVICE_UNAVAILABLE`.

**Cấu hình** (`app.payos.*` trong application.yml của auth-service)

| Biến | Mặc định | Ghi chú |
| --- | --- | --- |
| `PAYOS_MOCK` | `true` | `true`: không gọi PayOS, `checkoutUrl` là `<gốc của PAYOS_RETURN_URL>/payment/mock?orderCode=...` |
| `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` | rỗng | Chỉ nằm trong `.env` trên máy chủ |
| `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL` | `http://localhost:5173/payment/result` | docker-compose đặt `http://localhost/payment/result`, production đặt `https://<DOMAIN>/payment/result` |
| `PAYOS_BASE_URL` | `https://api-merchant.payos.vn` | |

**Chữ ký** (HMAC-SHA256 bằng checksum key, hex chữ thường, lớp `PayOsSignature`)

- Tạo liên kết: ký chuỗi `amount=<amount>&cancelUrl=<cancelUrl>&description=<description>&orderCode=<orderCode>&returnUrl=<returnUrl>`, giá trị giữ nguyên, không mã hóa URL.
- Webhook: lấy đối tượng `data`, sắp xếp khóa theo chữ cái, nối `key=value` bằng `&` (null thành chuỗi rỗng, mảng và đối tượng lồng nhau thành JSON), so sánh thời gian hằng với `signature`. Đã đối chiếu với ví dụ trong tài liệu PayOS "Kiểm tra dữ liệu với signature" (test `PayOsSignatureTest`).

**API**

| API | Ai gọi | Mô tả |
| --- | --- | --- |
| `POST /api/auth/payments` | `ADMIN` | Body: `plan` (`PRO`), `cycle` (`MONTHLY`, `YEARLY`). Số tiền lấy từ bảng `plans`, không nhận từ client. Tạo bản ghi `PENDING`, gọi PayOS tạo liên kết thanh toán, trả `201` với `orderCode`, `amount`, `checkoutUrl`, `qrCode`, `expiresAt` (đủ các trường ở ví dụ bên dưới). Liên kết hết hạn sau 15 phút |
| `POST /api/auth/payments/payos/webhook` | PayOS (công khai) | Kiểm tra chữ ký bằng checksum key, đối chiếu `orderCode` và số tiền với bản ghi `PENDING`. Hợp lệ: chuyển `PAID`, kích hoạt gói theo mục 2.6, ghi bộ nhớ đệm. Sai chữ ký hoặc sai số tiền: `400 PAYMENT_INVALID`, ghi log cảnh báo, gói không đổi. Gửi lặp cho đơn đã `PAID`: trả `200`, không cộng thêm ngày |
| `POST /api/auth/payments/{orderCode}/mock-confirm` | `ADMIN` của doanh nghiệp đó, chỉ khi `PAYOS_MOCK=true` | Trang giả lập gọi khi bấm xác nhận: chạy đúng đường xử lý của webhook hợp lệ (chuyển `PAID`, kích hoạt gói). Tắt giả lập hoặc đơn của doanh nghiệp khác: `404` |
| `GET /api/auth/payments/{orderCode}` | `ADMIN` của doanh nghiệp đó | Trạng thái một giao dịch, để trang kết quả hỏi lại. Đơn `PENDING` đã quá `expires_at` được chuyển `EXPIRED` ngay khi đọc. Đơn của doanh nghiệp khác: `404` |
| `POST /api/auth/payments/{orderCode}/cancel` | `ADMIN` của doanh nghiệp đó | Người dùng hủy ở trang PayOS: chuyển `CANCELLED` nếu đang `PENDING`; trạng thái khác (kể cả đã hết hạn) trả `409 CONFLICT` |
| `GET /api/auth/payments` | `ADMIN` | Lịch sử giao dịch của doanh nghiệp, mới nhất trước; tham số `page` (từ 0), `size` (mặc định 10, tối đa 100) |

Phần `data` của một giao dịch (tạo, xem, hủy, mock-confirm và mỗi phần tử của lịch sử):

```json
{
  "orderCode": 1762761600123457,
  "plan": "PRO",
  "cycle": "MONTHLY",
  "amount": 499000,
  "status": "PENDING",
  "checkoutUrl": "https://pay.payos.vn/web/...",
  "qrCode": "000201010212...",
  "reference": null,
  "expiresAt": "2026-11-10T03:15:00Z",
  "paidAt": null,
  "createdAt": "2026-11-10T03:00:00.123Z"
}
```

**Quy tắc nghiệp vụ**

- Gói chỉ được kích hoạt bởi webhook hợp lệ. Tham số trả về trên trình duyệt chỉ dùng để hiển thị.
- Webhook hợp lệ: trong một giao dịch, khóa dòng `payments` (`PESSIMISTIC_WRITE`), chuyển `PAID`, ghi `reference`, `paid_at`, gọi `SubscriptionService#activate` (công thức mục 2.6, ghi bộ nhớ đệm sau khi commit). Sai chữ ký, mã đơn không tồn tại hoặc sai số tiền: `400 PAYMENT_INVALID`, không đổi gì. Chưa cấu hình `PAYOS_CHECKSUM_KEY` thì mọi webhook bị từ chối, kể cả khi giả lập.
- Webhook mẫu PayOS gửi khi đăng ký địa chỉ webhook (confirm-webhook) có mã đơn `123`: chữ ký đúng thì trả `200`, không đổi gì. Mã đơn lạ khác: `400`.
- Webhook có chữ ký đúng nhưng `data.code` khác `"00"`: trả `200`, không đổi gì.
- Đơn đã `CANCELLED` hoặc `EXPIRED` mà nhận webhook đã trả tiền hợp lệ: vẫn coi là đã thanh toán (tiền đã vào tài khoản), chuyển `PAID` và kích hoạt gói.
- Chế độ giả lập `PAYOS_MOCK=true` (mặc định khi chưa có khóa): `checkoutUrl` trỏ tới trang giả lập `/payment/mock?orderCode=...` của frontend (WMS-23.5b làm trang này), bấm xác nhận thì gọi `mock-confirm`, backend xử lý như một webhook hợp lệ. Trên máy chủ thật đặt `PAYOS_MOCK=false` (các bước ở `deploy/README.md` mục 8.3).
- API `renew-mock` của WMS-23.1 giữ nguyên.
- Khóa `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` chỉ nằm trong `.env` trên máy chủ, không vào repo, không gửi qua chat.

**Tiêu chí nghiệm thu**

1. Test đơn vị cho việc tạo và kiểm tra chữ ký.
2. Thanh toán thành công (giả lập hoặc thật): giao dịch `PAID`, thuê bao thành `PRO`, ngày hết hạn tăng đúng chu kỳ.
3. Webhook sai chữ ký hoặc sai số tiền bị từ chối, thuê bao không đổi.
4. Gửi lại cùng webhook lần hai: không cộng thêm ngày.

### WMS-23.5b [Frontend] Thanh toán trên trang gói dịch vụ

Người làm: Linh. Ngày: 16/11. Jira: TASK-148. Phụ thuộc: WMS-23.5a, WMS-23.4.

**Mô tả chức năng**

- Nút nâng cấp hoặc gia hạn: chọn chu kỳ, xem số tiền, bấm thanh toán thì gọi `POST /api/auth/payments` rồi chuyển trình duyệt sang `checkoutUrl`.
- Trang `/payment/result`: đọc `orderCode` trên địa chỉ, gọi `GET /api/auth/payments/{orderCode}` 2 giây một lần, tối đa 60 giây. `PAID`: báo thành công, tải lại thông tin thuê bao, bỏ chế độ chỉ đọc. `CANCELLED`: báo đã hủy và gọi API hủy. Quá 60 giây vẫn `PENDING`: báo đang chờ ngân hàng xác nhận, có nút kiểm tra lại.
- Bảng lịch sử giao dịch trong trang `/subscription`: mã đơn, gói, chu kỳ, số tiền, trạng thái, thời điểm.

**Tiêu chí nghiệm thu**

Chạy trọn bước 5 của kịch bản demo trong SAAS_PLAN.md: hết hạn, chọn gói Pro, thanh toán, quay về thấy gói mở lại và ghi được dữ liệu.

**Cách làm (đã làm, thư mục `frontend/src/features/subscription`)**

- Nút thanh toán trên `/subscription` gửi `POST /api/auth/payments` với `plan: "PRO"` và `cycle` (không gửi số tiền), khóa nút trong lúc chờ, rồi chuyển trình duyệt sang `checkoutUrl` (chỉ nhận liên kết http, https).
- `/payment/result`: đọc `orderCode`; nếu PayOS trả về kèm `cancel=true` hoặc `status=CANCELLED` thì gọi `POST /api/auth/payments/{orderCode}/cancel` trước (409 thì hỏi lại trạng thái thật). Sau đó hỏi `GET /api/auth/payments/{orderCode}` mỗi 2 giây, tối đa 60 giây: `PAID` báo thành công và tải lại thuê bao (tắt chế độ chỉ đọc), `CANCELLED` báo đã hủy, `EXPIRED` báo liên kết hết hạn, quá 60 giây vẫn `PENDING` báo đang chờ ngân hàng kèm nút "Kiểm tra lại", 404 báo không tìm thấy giao dịch.
- `/payment/mock` (trang riêng, không có menu): mã đơn, số tiền, ô QR giả lập; "Xác nhận đã thanh toán" gọi `mock-confirm`, "Hủy" gọi API hủy, xong chuyển sang `/payment/result`.
- Bảng lịch sử giao dịch (chỉ `ADMIN`): `GET /api/auth/payments?page=&size=10`, phân trang phía máy chủ; giao dịch đang chờ có liên kết tiếp tục thanh toán và kiểm tra trạng thái.

### WMS-23.6 [Backend] Kích hoạt doanh nghiệp qua email và đặt lại mật khẩu

Người làm: Toàn. Ngày: 11/11. Jira: TASK-149. Phụ thuộc: WMS-23.1.

**API (đã làm)**

| API | Ai gọi | Mô tả |
| --- | --- | --- |
| `POST /api/auth/companies/register` (sửa) | Công khai | Doanh nghiệp tạo ra ở trạng thái `PENDING`, `activated_at` rỗng; phát sự kiện `auth.activation-requested` để notification-service gửi liên kết `{APP_FRONTEND_URL}/activate?token=...` (hiệu lực 24 giờ) tới email đăng ký. `data` thêm `activationRequired` (`true`); `company.status` là `PENDING`. Không trả token |
| `POST /api/auth/companies/activate` | Công khai | Body: `token`. Hợp lệ: `200`, doanh nghiệp thành `ACTIVE`, ghi `activated_at`, ghi lại bộ nhớ đệm thuê bao; `data`: `companyCode`, `companyName`, `status`, `activatedAt`, `username` (ADMIN nhận liên kết, để điền sẵn ở trang đăng nhập). Sai, đã dùng, quá 24 giờ, hoặc doanh nghiệp đã bị tạm khóa: `400 TOKEN_INVALID`. Thiếu `token`: `400 VALIDATION_ERROR` |
| `POST /api/auth/companies/resend-activation` | Công khai | Body: `email`. Luôn `200` với cùng thông báo, không có `data`. Chỉ gửi khi email thuộc tài khoản đang bật của doanh nghiệp còn `PENDING`; liên kết mới làm liên kết cũ hết dùng được. Mỗi email tối đa một lần mỗi phút. Email sai định dạng: `400 VALIDATION_ERROR` |
| `POST /api/auth/password/forgot` | Công khai | Body: `email`. Luôn `200` với cùng thông báo. Chỉ tài khoản đang bật của doanh nghiệp `ACTIVE` nhận liên kết `{APP_FRONTEND_URL}/reset-password?token=...`, hiệu lực 30 phút; liên kết đặt lại cũ hết dùng được. Mỗi email tối đa một lần mỗi phút |
| `POST /api/auth/password/reset` | Công khai | Body: `token`, `newPassword` (6 đến 72 ký tự như đổi mật khẩu). Hợp lệ: `200`, đổi mật khẩu, vô hiệu mọi liên kết đặt lại khác và refresh token của tài khoản (xóa `refresh:{username}` trên Redis). Mã sai, đã dùng, quá 30 phút, tài khoản bị khóa hoặc doanh nghiệp không còn hoạt động: `400 TOKEN_INVALID`. Mật khẩu không hợp lệ: `400 VALIDATION_ERROR` |

Đăng nhập (và làm mới token) khi doanh nghiệp còn `PENDING`: `403 COMPANY_INACTIVE`, thông báo hướng dẫn mở email kích hoạt, `data` là `{"companyStatus": "PENDING"}` để giao diện phân biệt với doanh nghiệp bị tạm khóa (`SUSPENDED` giữ thông báo cũ, không có `data`). Mật khẩu sai vẫn trả `401` như trước, nên chỉ người biết mật khẩu mới thấy trạng thái `PENDING`.

**Dữ liệu**

Bảng `account_tokens` (migration `V8__create_account_tokens.sql`): `company_id`, `user_id` (có thể rỗng), `type` (`COMPANY_ACTIVATION`, `PASSWORD_RESET`), `token_hash` (hex SHA-256, 64 ký tự, duy nhất), `expires_at`, `used_at` (đã dùng hoặc đã bị thay bằng mã mới hơn), bốn cột audit. Migration không đổi `companies`: hai công ty mẫu và các doanh nghiệp đã đăng ký trước đó giữ nguyên `ACTIVE`.

**Quy tắc nghiệp vụ (đã làm)**

- Mã gồm 32 byte ngẫu nhiên từ `SecureRandom`, mã hóa Base64 URL-safe không đệm (43 ký tự). Chỉ lưu SHA-256; mã gốc chỉ nằm trong liên kết. Dùng mã khóa dòng (`PESSIMISTIC_WRITE`) nên hai lần bấm cùng lúc chỉ một lần thành công. Mã kích hoạt không dùng được để đặt lại mật khẩu và ngược lại.
- 30 ngày dùng thử vẫn tính từ lúc đăng ký; kích hoạt không đổi thuê bao.
- auth-service không gửi mail: phát `auth.activation-requested` hoặc `auth.password-reset-requested` (payload `AccountEmailRequestedEvent`, header `x-wms-company-id` là doanh nghiệp của người nhận) sau khi transaction commit, xem `docs/MESSAGING.md` mục 3 và 8.7. notification-service gửi email HTML tiếng Việt và ghi `notification_logs` dưới doanh nghiệp đó.
- Không để lộ email có tồn tại: hai API nhận email trả cùng nội dung, việc tra cứu, phát mã và phát sự kiện chạy ở luồng nền (`app.account-email.async`, mặc định `true`) nên thời gian trả lời như nhau. Giới hạn một lần mỗi phút đếm bằng khóa Redis `wms:auth:mail-throttle:{activation|password-reset}:{sha256(email)}` (không lưu email gốc); Redis lỗi thì cho qua.
- Không ghi mã hay liên kết ra log ở auth-service. Môi trường chưa cấu hình SMTP (không có `MAIL_HOST`, hoặc có host nhưng không có `MAIL_USERNAME` và host không phải máy cục bộ): notification-service không gửi, ghi đúng một dòng INFO gồm người nhận và liên kết đầy đủ để nhóm bấm thử (`docker compose logs notification-service | grep "liên kết"`), nhật ký ghi `LOGGED`.
- Access token đã cấp trước khi đặt lại mật khẩu vẫn dùng được tới khi hết hạn (tối đa 15 phút), sau đó không làm mới được.

**Cấu hình**

| Biến | Mặc định | Ghi chú |
| --- | --- | --- |
| `APP_FRONTEND_URL` | rỗng: dùng gốc của `PAYOS_RETURN_URL`, cuối cùng là `http://localhost:5173` | docker-compose đặt `http://localhost`, production `https://<DOMAIN>` |
| `REGISTRATION_REQUIRE_ACTIVATION` | `true` (kể cả khi để trống) | Chỉ đặt `false` trên máy phát triển không có mail: đăng ký xong là `ACTIVE`, không phát liên kết. Production luôn để mặc định |

**Giao diện (đã làm)**

- `/activate`: đọc `token`, gọi API đúng một lần; thành công thì có nút Đăng nhập (điền sẵn tên đăng nhập); `TOKEN_INVALID` thì báo liên kết không dùng được kèm form gửi lại email kích hoạt.
- `/reset-password`: mật khẩu mới và nhập lại, gọi API rồi chuyển sang `/login`; `TOKEN_INVALID` thì dẫn tới `/forgot-password`.
- `/forgot-password`: chỉ còn ô email, gọi API và luôn hiện nguyên thông báo trung lập của máy chủ.
- `/login`: lỗi `COMPANY_INACTIVE` với `data.companyStatus = PENDING` hiện thông báo kèm form gửi lại email kích hoạt.
- Trang `/register` thuộc WMS-23.3b: khi `data.activationRequired` là `true`, hiện hướng dẫn mở email để kích hoạt thay vì chuyển sang đăng nhập.

**Tiêu chí nghiệm thu**

1. Đăng ký xong chưa đăng nhập được; bấm liên kết trong email thì đăng nhập được.
2. Liên kết kích hoạt dùng lần hai nhận `TOKEN_INVALID`.
3. Quên mật khẩu: nhận email, đặt mật khẩu mới, đăng nhập bằng mật khẩu mới được, mật khẩu cũ không còn dùng được.
4. Nhập email không tồn tại ở form quên mật khẩu: vẫn hiện thông báo như bình thường.

Bốn tiêu chí có test tích hợp trong `AccountEmailIntegrationTest` (auth-service); phần gửi email có `AccountEmailServiceTest`, `AccountEmailListenerTest`, `AccountEmailFlowIntegrationTest` (notification-service).

### WMS-24.1 [Backend] ai-service: khung dịch vụ AI

Người làm: Toàn. Ngày: 11/11. Jira: TASK-151. Phụ thuộc: WMS-23.2.

**Mô tả chức năng**

- Service mới `ai-service` (cổng 8086, package `vn.wms.ai`) gọi Claude qua SDK Java chính thức của Anthropic (`com.anthropic:anthropic-java`); có Dockerfile, khai báo trong docker-compose, job CI (build, test, image Docker, publish GHCR), route gateway `/api/ai/**` (cần đăng nhập). Chưa có CSDL, chỉ dùng Redis. Bật bắt buộc doanh nghiệp từ đầu (`app.tenant.required: true`).
- Khóa AI đọc từ biến môi trường `ANTHROPIC_API_KEY`, chỉ có trong `.env` trên máy chủ, không ghi log, không trả về, không nằm trong repo. Khi `AI_MOCK=true` (mặc định) hoặc không có khóa, service chạy chế độ giả lập trả kết quả mẫu để nhóm phát triển giao diện; thiếu khóa mà `AI_MOCK=false` thì ghi một dòng cảnh báo lúc khởi động và vẫn giả lập.
- Mỗi lượt gọi AI kiểm tra và trừ hạn mức tháng của doanh nghiệp; hết lượt trả `PLAN_LIMIT_EXCEEDED` với `limit = AI_REQUESTS`. Lỗi do AI không phản hồi trong 60 giây không bị trừ lượt.
- `GET /api/ai/health` và `GET /api/ai/usage` (số lượt đã dùng, giới hạn của tháng).

**API đã làm** (mọi API bọc trong `ApiResponse`, cần header `X-Company-Id` do gateway gắn; thiếu thì `403 COMPANY_REQUIRED`)

| API | Mô tả |
| --- | --- |
| `GET /api/ai/health` | `{ status: "UP", mode: "MOCK" hoặc "LIVE", model }`, không chứa bí mật |
| `GET /api/ai/usage` | `{ used, max, month (yyyy-MM), plan }` của doanh nghiệp đang đăng nhập. `max = -1` là không giới hạn; `max` và `plan` là `null` khi chưa có bộ nhớ đệm thuê bao trên Redis |
| `POST /api/ai/ask` | Body `{ question }` (1 đến 2000 ký tự, sai thì `400 VALIDATION_ERROR`). Kiểm tra hạn mức, gọi Claude với lời dặn ngắn (trợ lý của hệ thống quản lý kho, trả lời ngắn bằng tiếng Việt, không biết thì nói không biết). Trả `{ answer, truncated, refused, usage: { used, max } }`. Đây là "gọi thử một câu hỏi" của tiêu chí nghiệm thu và là nền để WMS-24.4 mở rộng |

**Cách gọi Claude**

- Dùng SDK chính thức thay vì Spring AI vì: Spring AI 1.x cần Spring Boot 3.4 trở lên trong khi dự án đang ở 3.3; và các mô hình Claude hiện tại từ chối tham số lấy mẫu khác mặc định (`temperature`, `top_p`, `top_k`) mà Spring AI gửi kèm theo mặc định. Không dùng HTTP tự viết hay lớp tương thích OpenAI.
- Mô hình đọc từ `AI_MODEL` (mặc định `claude-sonnet-5-5`); `max_tokens` từ `AI_MAX_TOKENS` (mặc định 4096, không stream). Không gửi `temperature`, `top_p`, `top_k`, `budget_tokens`; không đặt `thinking` (mô hình tự suy nghĩ thích ứng); mức suy nghĩ đặt qua `output_config.effort` từ `AI_EFFORT` (`low` mặc định, cho phép `low`, `medium`, `high`).
- Lời dặn hệ thống cố định đứng đầu và được đánh dấu bộ nhớ đệm prompt; WMS-24.4 nối thêm tài liệu hướng dẫn phía sau.
- Bật `fallbacks: "default"` với beta `server-side-fallback-2026-07-01` (đường `client.beta().messages()`), tắt bằng `AI_SERVER_FALLBACK=false` nếu API từ chối.
- Thời gian chờ mỗi lần gọi 60 giây, SDK tự thử lại tối đa 1 lần với lỗi tạm thời. Luôn xét `stop_reason` trước khi đọc nội dung: `refusal` trả lời xin lỗi soạn sẵn bằng tiếng Việt (`refused = true`); `max_tokens` trả phần đã sinh với `truncated = true`.
- Lỗi của SDK (vượt hạn mức nhà cung cấp, khóa sai, tham số sai, lỗi máy chủ, mất kết nối, quá thời gian chờ) đổi thành `503 SERVICE_UNAVAILABLE` với thông báo tiếng Việt; log chỉ ghi loại lỗi và mã HTTP, không ghi khóa, câu hỏi hay phản hồi thô.

**Cách đếm lượt**

- Bộ đếm `wms:usage:ai:{companyId}:{yyyyMM}` qua `PlanLimitChecker` (mục 2.7): kiểm tra trước khi gọi, chỉ tăng sau khi có câu trả lời.
- Không tính: lượt lỗi, quá thời gian chờ, bị mô hình từ chối, bị chặn vì hết lượt.
- Câu trả lời giả lập vẫn bị tính (để thử được giới hạn gói khi chưa có khóa), tắt bằng `AI_MOCK_COUNTS_USAGE=false`.
- Thiếu bộ nhớ đệm thuê bao hoặc Redis lỗi thì cho qua (fail open) như mọi giới hạn khác.

**Tiêu chí nghiệm thu**

`/api/ai/health` trả trạng thái sẵn sàng; gọi thử một câu hỏi nhận được trả lời; gọi quá hạn mức nhận đúng mã lỗi.

### WMS-24.2 [Backend] Trích xuất tài liệu thành dữ liệu danh mục

Người làm: Toàn. Ngày: 12/11. Jira: TASK-152. Phụ thuộc: WMS-24.1, WMS-22.4b.

**API**

| API | Mô tả |
| --- | --- |
| `POST /api/ai/extractions` | Dạng multipart: `file` (xlsx, xls, csv, pdf, png, jpg; tối đa 10 MB) và `dataType` (`PRODUCT`, `SUPPLIER`, `CUSTOMER`). Trả `extractionId`, `dataType`, danh sách cột và `rows`; mỗi dòng có `row`, `values`, `confidence` (0 đến 1 cho từng ô), `warnings`. Tối đa 200 dòng mỗi file. Tính một lượt AI |
| `GET /api/ai/extractions/{id}` | Lấy lại kết quả trong vòng 1 giờ |
| `POST /api/ai/extractions/{id}/import` | Body: `rows` đã được người dùng sửa và chọn. Gọi API nhập hàng loạt tương ứng của WMS-22.4b, trả nguyên kết quả theo dòng. Không tính lượt AI |

**Quy tắc nghiệp vụ**

- Các cột trả về khớp trường của API nhập hàng loạt (mục WMS-22.4b).
- Không bao giờ tự nhập khi người dùng chưa xác nhận.
- File không đọc được hoặc không có dữ liệu phù hợp: báo lỗi rõ ràng, không trừ lượt.
- Vai trò: `PRODUCT` và `SUPPLIER` cho `ADMIN`, `WAREHOUSE_MANAGER`, `PURCHASING`; `CUSTOMER` cho `ADMIN`, `SALES`.

**Đã làm (12/11)**

Mọi API bọc trong `ApiResponse`, cần `X-Company-Id` (thiếu thì `403 COMPANY_REQUIRED`). Vai trò kiểm tra tường minh từ `X-User-Roles` theo `dataType` (sai vai trò: `403 FORBIDDEN`); không có header người dùng (gateway tắt JWT ở máy phát triển) thì cho qua, cùng quy ước với `UserContext.requireWarehouseWriteAccess`.

Cách trích xuất:

- Excel, CSV đọc ngay trong ai-service (xlsx đọc kiểu luồng bằng Apache POI, xls qua HSSF, CSV UTF-8 có hoặc không có BOM, tự nhận dấu phẩy, chấm phẩy, tab, ô có ngoặc kép). Lấy trang tính đầu tiên có dữ liệu; dòng tiêu đề là dòng đầu tiên có từ hai ô trở lên trong 10 dòng đầu (bỏ được dòng tên bảng). Claude chỉ nhận tiêu đề và 5 dòng mẫu, trả cột nguồn của từng trường kèm độ tin cậy; server tự áp cho mọi dòng nên chính xác cho từng ô và nhanh (một lần gọi ngắn).
- PDF, ảnh png/jpg gửi Claude dạng khối tài liệu, hình ảnh; Claude trả từng dòng kèm độ tin cậy từng ô (gọi kiểu stream, chờ tối đa 180 giây).
- Hai kiểu gọi đều dùng structured outputs (lược đồ JSON cố định theo loại dữ liệu), lời dặn cố định đặt đầu và đánh dấu bộ nhớ đệm prompt, cùng mô hình, effort, quy tắc không tham số lấy mẫu của WMS-24.1. `stop_reason = refusal` trả `400` "AI từ chối xử lý nội dung file này"; `max_tokens` không dùng kết quả dở mà trả `400` yêu cầu tách file nhỏ hơn; cả hai không trừ lượt.
- Chế độ giả lập (`AI_MOCK=true` hoặc chưa có khóa): Excel, CSV vẫn chạy thật, ghép cột theo tên tiêu đề (tiếng Việt, tiếng Anh, không phân biệt hoa thường và dấu, ví dụ "Ma hang", "ĐVT", "Giá nhập (VNĐ)") với `method = HEADER_MATCHING`; PDF, ảnh trả `400` báo cần AI thật, không bịa dòng.
- Chuẩn hóa từng ô: cắt khoảng trắng; số kiểu Việt Nam và quốc tế ("1.250.000", "1,250,000", "1.250.000,5", "12,5", "150.000 đ"); thuế suất "10%", "10" và "0,1" đều thành 10 (trường hợp "0,1" có cảnh báo); loại khách hàng "sỉ", "đại lý", "bán lẻ", "cá nhân" thành `WHOLESALE`, `RETAIL`. Độ tin cậy của ô bắt đầu từ độ tin cậy ghép cột (hoặc do Claude đánh giá với PDF, ảnh), hạ xuống khi kiểm tra tại chỗ thấy vấn đề, kèm cảnh báo tiếng Việt: thiếu trường bắt buộc 0; không phải số 0,2; âm, quá dài 0,3; sai mẫu SKU, sai email 0,4; trùng mã với dòng trên, quá số chữ số thập phân 0,5; thuế suất viết dạng tỉ lệ 0,7. Trường không có cột nào khớp: ô trống, độ tin cậy 1 (trường bắt buộc thì 0).
- Quá 200 dòng: lấy 200 dòng đầu, `truncated = true` và một cảnh báo chung. Lỗi file (định dạng không hỗ trợ, nội dung không khớp phần mở rộng, quá 10 MB, file hỏng hoặc có mật khẩu, CSV không phải UTF-8, trang tính trống, chỉ có tiêu đề, không có cột nào khớp) đều trả `400 BAD_REQUEST` với thông báo tiếng Việt và không trừ lượt.
- Đếm lượt giống `POST /api/ai/ask`: kiểm tra hạn mức sau khi đọc được file và trước khi gọi Claude (hết lượt: `403 PLAN_LIMIT_EXCEEDED`), chỉ tăng bộ đếm sau khi đã lưu kết quả. Kết quả giả lập vẫn bị tính trừ khi `AI_MOCK_COUNTS_USAGE=false`.
- Kết quả lưu trên Redis khóa `wms:ai:extraction:{companyId}:{extractionId}`, sống 1 giờ; mã trích xuất của doanh nghiệp khác hoặc đã hết hạn trả `404 NOT_FOUND`. Không lưu được lên Redis thì `503`, không trừ lượt.
- Nhập: ai-service gọi `POST /api/products/bulk` (product-service) hoặc `POST /api/orders/suppliers/bulk`, `/api/orders/customers/bulk` (order-service) qua Eureka, chuyển tiếp `X-Company-Id`, `X-User-Name`, `X-User-Roles`, `X-User-Warehouse-Ids`, `X-Plan` của người bấm để service phía sau tự kiểm tra quyền, giới hạn gói, thuê bao. Lỗi nghiệp vụ của service phía sau (400, 401, 403, 409 kèm `code`, `data`) trả lại nguyên mã; service chưa chạy, chưa có API (404) hoặc lỗi 5xx trả `503`. Hai lần bấm cùng lúc: lần sau `409 CONFLICT`; bấm lại sau khi xong thì API nhập hàng loạt trả `SKIPPED` cho mã đã có.
- Giới hạn tải file: `spring.servlet.multipart.max-file-size = 10MB` ở ai-service (file lớn hơn trả `400` "tối đa 10 MB"); nginx của frontend có `location /api/ai/` nhận tới 11 MB (mặc định nginx chỉ 1 MB) và chờ tới 200 giây. api-gateway và Caddy không giới hạn dung lượng body nên không đổi.

Hợp đồng JSON cho giao diện (WMS-24.3):

`POST /api/ai/extractions` (multipart `file`, `dataType`) và `GET /api/ai/extractions/{id}` trả `data`:

```json
{
  "extractionId": "3f1c2b9e-1111-4a2b-9c3d-0123456789ab",
  "dataType": "PRODUCT",
  "fileName": "hang-hoa.xlsx",
  "fileType": "XLSX",
  "method": "COLUMN_MAPPING",
  "totalRows": 50,
  "truncated": false,
  "columns": [
    { "field": "sku", "label": "Mã SKU", "type": "SKU", "required": true, "maxLength": 64, "options": [],
      "sourceColumn": 1, "sourceHeader": "Mã hàng", "mappingConfidence": 0.95 },
    { "field": "uomName", "label": "Đơn vị tính", "type": "TEXT", "required": true, "maxLength": 100, "options": [],
      "sourceColumn": null, "sourceHeader": null, "mappingConfidence": null }
  ],
  "rows": [
    { "row": 1, "sourceRow": 2,
      "values": { "sku": "SP001", "name": "Nước suối", "uomName": null, "purchasePrice": 150000, "vatRate": 10 },
      "confidence": { "sku": 0.95, "name": 0.95, "uomName": 0.0, "purchasePrice": 0.95, "vatRate": 0.95 },
      "warnings": ["Thiếu Đơn vị tính (bắt buộc)"] }
  ],
  "warnings": ["Không tìm thấy cột Đơn vị tính (bắt buộc) trong file, hãy nhập bổ sung trong bảng xem trước."],
  "createdAt": "2026-11-12T03:00:00Z",
  "expiresAt": "2026-11-12T04:00:00Z",
  "usage": { "used": 4, "max": 20 }
}
```

- `columns` luôn đủ mọi trường của loại dữ liệu theo thứ tự hiển thị; `values` và `confidence` của mỗi dòng có đủ các khóa đó (ví dụ trên rút gọn). `type`: `TEXT`, `SKU`, `EMAIL`, `NUMBER`, `PERCENT`, `ENUM` (`options` liệt kê giá trị hợp lệ, hiện chỉ `customerType`: `WHOLESALE`, `RETAIL`).
- Trường số là số JSON; ô không đọc được thành số giữ nguyên chuỗi gốc kèm cảnh báo để người dùng sửa. `sourceRow` là số dòng trong file Excel, CSV (`null` với PDF, ảnh). `sourceColumn`, `sourceHeader`, `mappingConfidence` là `null` khi không có cột khớp hoặc khi AI đọc PDF, ảnh.
- `method`: `COLUMN_MAPPING` (Claude ghép cột), `HEADER_MATCHING` (chế độ giả lập), `AI_DOCUMENT` (Claude đọc PDF, ảnh). `usage` chỉ có trong kết quả `POST` (`max = -1` là không giới hạn), là `null` khi `GET`.

Trường theo `dataType` (đúng tên trường của API nhập hàng loạt):

| `dataType` | Trường (in đậm là bắt buộc) |
| --- | --- |
| `PRODUCT` | **`sku`**, **`name`**, `barcode`, `description`, `categoryName`, **`uomName`**, `purchasePrice`, `sellingPrice`, `vatRate`, `safetyStock`, `reorderPoint`, `maxStock` |
| `SUPPLIER` | **`code`**, **`name`**, `taxCode`, `contactName`, `email`, `phone`, `address`, `bankAccount`, `bankName`, `paymentTerms`, `creditLimit`, `initialDebt` |
| `CUSTOMER` | **`code`**, **`name`**, **`customerType`**, `taxCode`, `contactName`, `email`, `phone`, `address`, `paymentTerms`, `creditLimit`, `initialDebt`, `notes` |

`POST /api/ai/extractions/{id}/import` nhận:

```json
{ "rows": [ { "row": 1, "values": { "sku": "SP001", "name": "Nước suối", "uomName": "Chai", "purchasePrice": 150000 } } ] }
```

- Từ 1 đến 200 dòng (rỗng: `400 VALIDATION_ERROR`, `data.rows`). `row` chỉ để giao diện đối chiếu, không gửi đi; trường lạ bị bỏ, ô rỗng không gửi. Ô số nhận cả số JSON lẫn chuỗi "150.000"; ô số hoặc loại khách hàng không đọc được làm cả lần nhập trả `400 VALIDATION_ERROR` với `data` dạng `{ "rows[0].values.purchasePrice": "Giá nhập phải là số" }` (chỉ số `rows[i]` từ 0).
- Trả nguyên kết quả của API nhập hàng loạt: `{ "total", "created", "skipped", "failed", "results": [ { "row", "status": "CREATED" | "SKIPPED" | "FAILED", "id", "code", "message" } ] }`. `results[k].row` là thứ tự trong mảng `rows` đã gửi (từ 1), không phải `row` của bảng xem trước.

Chưa kiểm chứng: chưa gọi Claude thật (CI không có khóa), nên chất lượng ghép cột, đọc PDF, ảnh và thời gian thực tế chưa đo; API nhập hàng loạt của WMS-22.4b chưa có trên nhánh này, ai-service gọi theo hợp đồng ở mục WMS-22.4b và được kiểm thử bằng máy chủ giả lập.

**Tiêu chí nghiệm thu**

File Excel 50 dòng có tiêu đề cột rõ ràng: trích xuất đúng trên 90% số ô, dưới 30 giây; bấm nhập xong thấy dữ liệu trong danh mục của đúng doanh nghiệp.

### WMS-24.3 [Frontend] Màn hình Nhập dữ liệu bằng AI

Người làm: Việt. Ngày: 16/11 đến 17/11. Jira: TASK-153. Phụ thuộc: WMS-24.2.

**Mô tả chức năng**

Trang `/ai-import` gồm bốn bước:

1. Chọn loại dữ liệu (hàng hóa, nhà cung cấp, khách hàng) và tải file bằng kéo thả hoặc chọn file; kiểm tra định dạng và dung lượng trước khi gửi; hiển thị số lượt AI còn lại trong tháng.
2. Chờ trích xuất, có trạng thái đang xử lý và nút hủy.
3. Bảng xem trước sửa được từng ô; ô có `confidence` dưới 0,8 tô nền vàng; dòng có `warnings` có biểu tượng cảnh báo kèm lý do; cho phép bỏ chọn dòng, xóa dòng; hiện tổng số dòng sẽ nhập.
4. Bấm nhập: hiện kết quả theo dòng (đã tạo, bỏ qua, lỗi kèm lý do), tổng kết ba con số, nút mở danh mục tương ứng và nút tải danh sách dòng lỗi.

Lỗi `PLAN_LIMIT_EXCEEDED` dùng hộp thoại chung của WMS-23.4. Mục menu chỉ hiện với vai trò được phép.

**Đã làm (12/11)**

- Trang `frontend/src/features/ai/AiImportPage.tsx`, route `/ai-import`; `?type=PRODUCT|SUPPLIER|CUSTOMER` chọn sẵn loại dữ liệu, `?id={extractionId}` được ghi sau khi trích xuất để tải lại trang lấy lại bảng qua `GET /api/ai/extractions/{id}` (chỉnh sửa chỉ nằm trên trình duyệt nên mất, màn hình báo rõ). Mục menu "Nhập dữ liệu bằng AI" (trường `anyOf` của `navigation.tsx`) và nút "Nhập bằng AI" trên trang hàng hóa, nhà cung cấp, khách hàng chỉ hiện theo quyền `aiImport.productSupplier`, `aiImport.customer`; loại dữ liệu chọn được cũng lọc theo hai quyền này.
- Bước 1: kiểm tra phần mở rộng (xlsx, xls, csv, pdf, png, jpg, jpeg) và 10 MB trước khi gửi; hiện lượt AI tháng (`GET /api/ai/usage`, sau khi trích xuất lấy `usage` của kết quả); chờ tối đa 210 giây (`EXTRACTION_TIMEOUT_MS`), đếm giây, nút "Hủy" (AbortController; máy chủ vẫn có thể chạy xong và tính lượt, màn hình nói rõ). Lỗi 400 hiện nguyên thông báo của máy chủ; `PLAN_LIMIT_EXCEEDED`, `SUBSCRIPTION_EXPIRED` chỉ hiện hộp thoại dùng chung.
- Bước 2: mỗi cột một phần tử `columns` (dấu * bắt buộc, tooltip tiêu đề có cột nguồn và độ tin cậy ghép cột); ô có `confidence` dưới 0,8 (`LOW_CONFIDENCE_THRESHOLD`) nền vàng cho tới khi được sửa; bấm ô để sửa theo kiểu (chữ có đếm `maxLength`, số đọc theo đúng quy tắc `NumberParser`, ô chọn khi có `options`). Lỗi trước khi nhập: thiếu trường bắt buộc, ô số không đọc được, giá trị ngoài `options`, quá `maxLength`; dòng còn lỗi không chọn được, dòng khác chọn sẵn. Xóa dòng, lọc "Chỉ hiện dòng cần xem lại", thông báo `truncated`, `warnings`, chế độ giả lập, đồng hồ tới `expiresAt` (hết hạn hoặc nhập nhận 404 thì khóa nút nhập, yêu cầu tải lại file). Rời trang khi còn chỉnh sửa chưa nhập hoặc đang chờ trích xuất thì hỏi lại (`useBlocker` và `beforeunload`).
- Bước 3: hộp xác nhận số dòng sẽ gửi; nút nhập có `data-write-action` (nút trích xuất cũng vậy, vì gateway chặn POST khi gói hết hạn), khóa khi đang chạy; 409 báo "đang nhập"; 400 `VALIDATION_ERROR` đổi `rows[i]` về đúng ô. Kết quả: ba con số, bảng từng dòng đối chiếu `results[k].row` với thứ tự đã gửi, nút mở danh mục, "Sửa và nhập tiếp" (dòng lỗi giữ giá trị và lý do, cùng các dòng chưa gửi), "Tải danh sách dòng lỗi" (xlsx).
- Chưa chạy với backend thật; logic thuần (`aiImport.ts`) được kiểm bằng script Node tạm.

**Tiêu chí nghiệm thu**

Chạy trọn bước 2 của kịch bản demo: tải file danh mục 50 dòng, sửa vài ô, nhập, mở danh mục thấy đủ hàng hóa.

### WMS-24.4 [Backend] Trợ lý AI

Người làm: Toàn. Ngày: 13/11. Jira: TASK-154. Phụ thuộc: WMS-24.1.

**Mô tả chức năng**

- `POST /api/ai/chat`: body `message`, `conversationId` (không bắt buộc); trả câu trả lời bằng tiếng Việt và `conversationId`. Lịch sử hội thoại giữ 1 giờ.
- Trợ lý trả lời hai nhóm câu hỏi: hướng dẫn sử dụng (dựa trên tài liệu của hệ thống) và tra cứu dữ liệu (tồn kho theo SKU hoặc kho, trạng thái đơn mua, đơn bán theo số chứng từ).
- Trợ lý chỉ đọc dữ liệu của doanh nghiệp đang đăng nhập và theo đúng quyền của người hỏi; không thực hiện thao tác ghi.
- Mỗi câu hỏi tính một lượt AI.

**Tiêu chí nghiệm thu**

Hỏi "tạo kho thế nào" nhận hướng dẫn đúng các bước trên giao diện; hỏi tồn của một SKU nhận đúng số trong hệ thống; người dùng của B hỏi về SKU chỉ có ở A nhận câu trả lời là không tìm thấy.

**Đã làm (nhánh `t-24-4`, gộp WMS-18.2 "API trợ lý AI")**

`POST /api/ai/chat`, bọc `ApiResponse`, cần `X-Company-Id` (thiếu thì `403 COMPANY_REQUIRED`). Mọi vai trò đã đăng nhập đều hỏi được; tra cứu được gì do service đích quyết định theo vai trò và kho của người hỏi.

- Body `{ "message": "...", "conversationId": "..." }`: `message` từ 1 đến 2000 ký tự (sai: `400 VALIDATION_ERROR`, `data.message`); `conversationId` không bắt buộc.
- Một câu hỏi tính đúng một lượt AI như `POST /api/ai/ask`: kiểm tra hạn mức trước (hết lượt: `403 PLAN_LIMIT_EXCEEDED`, `limit = AI_REQUESTS`), chỉ tăng bộ đếm khi có câu trả lời, dù Claude gọi bao nhiêu lần trong câu đó. Lỗi, hết thời gian (`503 SERVICE_UNAVAILABLE`) và câu bị từ chối (`refused = true`) không bị tính. Câu trả lời giả lập vẫn bị tính trừ khi `AI_MOCK_COUNTS_USAGE=false`.
- Lịch sử: khóa Redis `wms:ai:chat:{companyId}:{tên đăng nhập mã hóa base64url}:{conversationId}`, giữ 1 giờ kể từ câu hỏi gần nhất, tối đa 10 lượt hỏi đáp gần nhất (câu trả lời lưu tối đa 4000 ký tự). Mã hội thoại sai dạng, hết hạn, của người khác hay doanh nghiệp khác đều bắt đầu hội thoại mới (`newConversation = true`, mã mới), không đọc được gì của người khác. Redis lỗi chỉ làm mất lịch sử, không làm hỏng câu trả lời. Câu bị từ chối không lưu vào lịch sử.
- Hướng dẫn sử dụng: `docs/USER_GUIDE.md`, bản chép ở `backend/ai-service/src/main/resources/assistant/USER_GUIDE.md` (image Docker build từ `backend/` nên không đọc được `docs/`); test `UserGuideTest` báo lỗi khi hai bản khác nhau. Sửa tài liệu thì chép lại.
- Chế độ LIVE: lời dặn cố định cộng toàn văn hướng dẫn (trong thẻ `<user_guide>`) đứng đầu và đánh dấu bộ nhớ đệm prompt, không chứa người hỏi hay doanh nghiệp; điểm đánh dấu tự động ở cấp yêu cầu để các vòng công cụ sau đọc lại phần hội thoại đã gửi. Cùng mô hình, effort, quy tắc không tham số lấy mẫu, không đặt `thinking`, `fallbacks` của WMS-24.1. Vòng công cụ tự viết bằng SDK (`client.beta().messages()`): tối đa 5 vòng công cụ, hết vòng thì lần gọi cuối đặt `tool_choice: none` để Claude trả lời bằng dữ liệu đã có; tổng thời gian một câu hỏi 90 giây (nginx chờ `/api/ai/` 200 giây, gateway và Caddy không đặt giới hạn); mỗi lần gọi xét `stop_reason` trước: `refusal` trả lời xin lỗi, chỉ `tool_use` mới chạy công cụ (lượt bị cắt vì `max_tokens` không chạy công cụ dở).
- Công cụ (chỉ đọc, ai-service thực thi qua client cân bằng tải với nguyên `X-Company-Id`, `X-User-Name`, `X-User-Roles`, `X-User-Warehouse-Ids`, `X-Plan` của người hỏi; kết quả rút gọn, không gửi giá, mô tả, ghi chú):

| Công cụ | Gọi API | Trả cho Claude |
| --- | --- | --- |
| `find_product(keyword)` | `GET /api/products?keyword=&size=10` | `sku`, `name`, `uom`, `active` |
| `get_stock(sku, warehouse?)` | `GET /api/products/sku/{sku}` rồi `GET /api/inventory/stock/atp?productId=&warehouseId=` | `onHandQuantity`, `reservedQuantity`, `availableQuantity` trên mọi kho hoặc một kho |
| `get_warehouse_stock(warehouse, keyword?)` | `GET /api/inventory/reports/in-out-balance` từ hôm nay đến hôm nay, sắp `closingQuantity` giảm dần, 20 dòng | số mặt hàng, tổng tồn, từng dòng `sku`, `name`, `uom`, `quantity` |
| `list_warehouses()` | `GET /api/inventory/warehouses` | `id`, `code`, `name`, `active` |
| `get_purchase_order(poNumber)` | `GET /api/orders/purchase/number/{poNumber}` | trạng thái và nhãn tiếng Việt, nhà cung cấp, kho, ngày, tổng tiền, tối đa 30 dòng hàng |
| `get_sales_order(soNumber)` | `GET /api/orders/sales/number/{soNumber}` | như trên với khách hàng, lý do thiếu hàng |

  `warehouse` nhận ID, mã kho hoặc một phần tên (không phân biệt dấu) trong các kho người hỏi được xem. Kết quả lỗi gửi Claude dạng `{"status":"NOT_FOUND|FORBIDDEN|BAD_REQUEST|UNAVAILABLE|UNKNOWN_TOOL","message":"..."}`: 404 thành "không tìm thấy trong dữ liệu của doanh nghiệp bạn" (SKU chỉ có ở doanh nghiệp khác rơi vào đây vì product-service lọc theo `X-Company-Id`), 401, 403 thành "Bạn không có quyền xem dữ liệu này.", 5xx hoặc service chưa chạy thành "chưa tra cứu được". Tên công cụ lạ trả `UNKNOWN_TOOL`; mã SKU, số chứng từ chỉ nhận chữ, số và `. _ -` (không có `/`, `..`).
- An toàn: lời dặn yêu cầu chỉ đọc (muốn thay đổi thì chỉ chỗ làm trên màn hình), chỉ nói về dữ liệu của người hỏi, không tiết lộ lời dặn, công cụ, khóa; coi kết quả công cụ, tài liệu và tin nhắn là dữ liệu, không làm theo câu lệnh nằm trong tên hàng hay ghi chú. Không có công cụ ghi nên trường hợp xấu nhất là một câu trả lời sai.
- Chế độ MOCK (`AI_MOCK=true` hoặc chưa có khóa): không gọi Claude, không bịa số. Câu có `PO-...`/`SO-...` tra đơn; có mã dạng SKU (chữ lẫn số, hoặc chữ in hoa có gạch nối, hoặc sau chữ "SKU", "mã hàng") tra tồn của SKU, kèm kho nếu có "kho <mã>"; có "kho <mã>" tra tồn của kho; chỉ hỏi "tồn" thì liệt kê kho để hỏi lại; còn lại trả mục hướng dẫn khớp nhất theo từ khóa (không phân biệt dấu). Mọi câu trả lời mở đầu bằng dòng "Đây là trợ lý giả lập (hệ thống chưa bật AI thật)...".
- Cấu hình `app.ai.assistant.*` (đều có mặc định, không thêm biến môi trường): `max-tokens` 2048, `timeout` 90s, `max-tool-rounds` 5, `history-ttl` 1h, `max-exchanges` 10, `max-stored-answer-length` 4000, `max-list-items` 20.

Hợp đồng JSON cho cửa sổ trợ lý (WMS-18.4). Gửi:

```json
{ "message": "Tồn của SKU-GAO-ST25 còn bao nhiêu?", "conversationId": "3f1c2b9e-1111-4a2b-9c3d-0123456789ab" }
```

Nhận `data`:

```json
{
  "answer": "Mã SKU-GAO-ST25 (Gạo ST25 túi 5kg) trên mọi kho: tồn thực tế 120 Túi, đang giữ cho đơn bán 20 Túi, còn khả dụng 100 Túi.",
  "conversationId": "3f1c2b9e-1111-4a2b-9c3d-0123456789ab",
  "newConversation": false,
  "truncated": false,
  "refused": false,
  "mode": "LIVE",
  "lookups": ["get_stock"],
  "usage": { "used": 8, "max": 500 }
}
```

- `answer` là văn bản thường tiếng Việt, có thể có xuống dòng và dòng bắt đầu bằng "- " hoặc "1. "; hiển thị giữ xuống dòng, không render HTML.
- Lưu `conversationId` và gửi lại ở câu sau. `newConversation = true` khi hội thoại cũ đã hết hạn (quá 1 giờ) hay không hợp lệ: giao diện nên báo "Đã bắt đầu cuộc trò chuyện mới". Muốn bắt đầu lại thì bỏ `conversationId`.
- `mode = "MOCK"`: có thể hiện nhãn "Trợ lý giả lập". `lookups` là các phép tra cứu đã chạy (`find_product`, `get_stock`, `get_warehouse_stock`, `list_warehouses`, `get_purchase_order`, `get_sales_order`), rỗng khi trả lời từ hướng dẫn. `usage.max = -1` là không giới hạn, `null` khi chưa đọc được thuê bao.
- Lỗi: `PLAN_LIMIT_EXCEEDED` dùng hộp thoại chung của WMS-23.4; `SERVICE_UNAVAILABLE` hiện `message` và cho gửi lại; `VALIDATION_ERROR` không xảy ra nếu giao diện chặn ô trống và quá 2000 ký tự. Một câu hỏi có thể mất tới 90 giây: hiện trạng thái đang trả lời và khóa nút gửi.
- Giao diện đã làm (WMS-18.4, `frontend/src/features/ai/`): nút nổi góc phải dưới mọi trang trong `MainLayout` mở khung chat (điện thoại: hết chiều ngang); `conversationId` chỉ giữ trong bộ nhớ (mất khi tải lại trang, đăng xuất); lời gọi chờ tối đa 120 giây (axios dùng chung chỉ 15 giây); không tự gửi lại khi lỗi. Khi thuê bao `EXPIRED`, gateway chặn `POST /api/ai/chat` bằng `403 SUBSCRIPTION_EXPIRED` (đường dẫn này không nằm trong `write-allowed-paths`): khung chat không bị chế độ chỉ đọc chặn bấm nhưng hiện ghi chú và hộp thoại hết hạn dùng chung.

Chưa kiểm chứng: chưa gọi Claude thật (máy phát triển và CI không có khóa), nên chất lượng câu trả lời, việc Claude chọn đúng công cụ, thời gian thực tế và tỷ lệ đọc lại bộ nhớ đệm chưa đo; vòng công cụ được kiểm thử bằng phản hồi JSON mẫu đọc qua SDK.

### WMS-18.1 [Backend] Tóm tắt số liệu điều hành bằng AI (khối "AI Summary" của dashboard)

Người làm: Toàn. Jira: TASK-85. Phụ thuộc: WMS-24.1, WMS-16.1 (8 thẻ KPI), WMS-17.1 (báo cáo Xuất - Nhập - Tồn). Giao diện làm ở WMS-18.3.

**Đã làm (nhánh `t-24-4`)**

`GET /api/ai/summary?warehouseId=&refresh=`, bọc `ApiResponse`, cần `X-Company-Id` (thiếu thì `403 COMPANY_REQUIRED`).

- Tham số: `warehouseId` (không bắt buộc, sai kiểu: `400`), `refresh` (mặc định `false`; `true` bỏ qua bộ nhớ đệm và tạo bản mới).
- Số liệu lấy bằng danh tính người xem (cùng client chuyển tiếp header với trợ lý): `GET /api/notifications/dashboard/kpi-summary?warehouseId=` (bắt buộc) và `GET /api/inventory/reports/in-out-balance` từ ngày 1 của tháng tới hôm nay (giờ Việt Nam), `groupBy=PRODUCT`, `size=1`, cùng kho mà notification-service đã dùng (`meta.warehouseId`; vai trò theo kho không chọn kho thì là kho được gán đầu tiên), chỉ đọc `totals`. KPI trả 403 thì trả `403 FORBIDDEN` (vai trò theo kho chưa được gán kho, kho không được gán); KPI không đọc được thì `503`. Báo cáo nhập xuất không đọc được (ví dụ `SALES`, `PURCHASING` bị 403) thì bản tóm tắt ghi "chưa có số liệu" kèm lý do, không lỗi.
- Ai xem được: người notification-service cho xem KPI. Thẻ tài chính (công nợ phải thu, phải trả, giá trị tồn kho, doanh số) có `visible = false` với vai trò không phải `ADMIN`, `ACCOUNTANT`: giá trị của chúng (notification-service điền 0) không được đưa cho Claude, chỉ ghi tên trong `hiddenForRole`. Giá trị `null` (giá trị tồn kho chưa có giá vốn, tăng trưởng khi tháng trước không có doanh số, nhập xuất trước mốc sổ kho) giữ `null` kèm lời giải thích, Claude được dặn nói "chưa có" chứ không đoán.
- Chế độ LIVE: `structured outputs` với lược đồ `{ bullets: string[], actions: string[] }`, lời dặn cố định đứng đầu và đánh dấu bộ nhớ đệm prompt, số liệu nằm trong tin nhắn người dùng (thẻ `<kpi_data>`), cùng mô hình, effort, quy tắc không tham số lấy mẫu của WMS-24.1; `max_tokens` 1500, chờ tối đa 60 giây. Giữ tối đa 5 ý và 2 gợi ý. `refusal`, `max_tokens`, JSON hỏng hoặc không có ý nào: `503`, không tính lượt, không lưu.
- Chế độ MOCK: 4 hoặc 5 ý dựng bằng câu soạn sẵn từ đúng các số trên (đơn bán chờ xuất và đơn gấp, đơn mua chờ nhận, tồn khả dụng và tỷ lệ lấp đầy, nhập xuất từ đầu tháng, công nợ và doanh số khi được xem); gợi ý theo quy tắc (có đơn gấp, có lô đến hôm nay, kho lấp đầy từ 85%, có khách nợ đến hạn).
- Bộ nhớ đệm: Redis `wms:ai:summary:{companyId}:{FIN|OPS}:{phạm vi}` sống 30 phút (`app.ai.summary.cache-ttl`). `FIN` cho `ADMIN`, `ACCOUNTANT`, `OPS` cho vai trò khác, nên bản viết từ số liệu tài chính không đến tay vai trò bị che. Phạm vi: `w{warehouseId}` khi chọn kho; không chọn kho thì `all` (vai trò xem mọi kho) hoặc `a{các kho được gán, sắp tăng, nối bằng -}`. Lấy từ bộ nhớ đệm không gọi service khác, không kiểm tra, không tính lượt (`fromCache = true`, `generatedAt` là lúc tạo). Redis lỗi thì coi như không có bộ nhớ đệm.
- Đếm lượt: mỗi lần tạo bản mới (lần đầu, hết 30 phút, `refresh=true`) kiểm tra hạn mức sau khi đọc được KPI (hết lượt: `403 PLAN_LIMIT_EXCEEDED`) và tính một lượt khi thành công; bản giả lập vẫn bị tính trừ khi `AI_MOCK_COUNTS_USAGE=false`.
- Cấu hình `app.ai.summary.*` (có mặc định, không thêm biến môi trường): `cache-ttl` 30m, `max-tokens` 1500, `timeout` 60s.

Hợp đồng JSON cho khối "AI Summary" (WMS-18.3), `data`:

```json
{
  "summary": "- Có 7 đơn bán chờ xuất kho, 2 đơn cần lấy hàng gấp.\n- Kho đã lấp đầy 91,5% (183/200 vị trí).\n- Từ đầu tháng nhập 500, xuất 320,25.\n\nGợi ý:\n- Ưu tiên điều phối lấy hàng cho 2 đơn gấp.",
  "bullets": [
    "Có 7 đơn bán chờ xuất kho, 2 đơn cần lấy hàng gấp.",
    "Kho đã lấp đầy 91,5% (183/200 vị trí).",
    "Từ đầu tháng nhập 500, xuất 320,25."
  ],
  "actions": ["Ưu tiên điều phối lấy hàng cho 2 đơn gấp."],
  "generatedAt": "2026-11-12T03:00:00Z",
  "fromCache": false,
  "mode": "LIVE",
  "warehouseId": null,
  "financialsIncluded": false,
  "usage": { "used": 10, "max": 500 }
}
```

- Hiển thị `bullets` thành danh sách và `actions` dưới tiêu đề "Gợi ý" (hoặc dùng `summary` là văn bản thường có xuống dòng). `generatedAt` là UTC, hiển thị "Cập nhật lúc ..." theo giờ máy; `fromCache = true` nghĩa là bản đã lưu, nút "Làm mới" gọi lại với `refresh=true` (tốn một lượt AI). `mode = "MOCK"` có thể hiện nhãn "Tóm tắt tự động (chưa dùng AI thật)".
- Gọi với cùng `warehouseId` mà bộ lọc kho của dashboard đang chọn. Gọi khi mở dashboard, không gọi lại theo chu kỳ.
- Lỗi: `PLAN_LIMIT_EXCEEDED` hiện lời nhắc nâng gói trong khối (không chặn cả dashboard); `FORBIDDEN` ẩn khối; `SERVICE_UNAVAILABLE` hiện `message` và nút thử lại.
- Giao diện đã làm (WMS-18.3, `frontend/src/features/ai/components/AiSummaryCard.tsx`, chèn một dòng dưới thanh công cụ của `DashboardPage`): chờ bộ lọc kho của dashboard chọn xong rồi mới gọi (tránh hai lần tạo bản tóm tắt), mỗi kho gọi một lần trong một lần mở trang; "Làm mới" hỏi xác nhận vì tốn một lượt AI; mọi `403` khác `PLAN_LIMIT_EXCEEDED` ẩn khối. Lời gọi đặt `skipPlanLimitDialog: true` (cờ mới của axios dùng chung trong `frontend/src/services/api.ts`) để hết lượt AI chỉ báo trong khối, không mở hộp thoại khi vừa mở dashboard.

Chưa kiểm chứng: chưa gọi Claude thật nên chất lượng câu chữ và thời gian thực tế chưa đo; notification-service hiện tính KPI từ số liệu thật nhưng thẻ "Giá trị tồn kho" luôn `null` (chưa có giá vốn).

### WMS-25.1 [Backend] API key cho hệ thống ngoài

Người làm: Toàn. Ngày: 17/11 (làm sớm ngày 11/11). Jira: TASK-156. Phụ thuộc: WMS-22.3.

**Mô tả chức năng**

- Bảng `api_keys` ở auth-service: `company_id`, `name`, `key_prefix` (8 ký tự đầu để nhận diện), `key_hash`, `last_used_at`, `revoked_at`, người tạo.
- `POST /api/auth/api-keys` (`ADMIN`): body `name`; trả key đầy đủ đúng một lần. `GET /api/auth/api-keys`: danh sách không kèm key. `DELETE /api/auth/api-keys/{id}`: thu hồi.
- Chỉ gói có `apiAccess` mới tạo được key (gói `FREE` nhận `PLAN_LIMIT_EXCEEDED` với `limit = API_ACCESS`). Mỗi doanh nghiệp tối đa 5 key đang hoạt động.
- Gateway nhận header `X-Api-Key` thay cho JWT: tra key, gắn `X-Company-Id` và vai trò `INTEGRATION`. Vai trò này chỉ gọi được `GET` của hàng hóa, tồn kho, đơn hàng.
- Key sai hoặc đã thu hồi: `401`. Thuê bao hết hạn hoặc đã về gói `FREE`: `403`. Giới hạn tốc độ tính theo key, mặc định 20 request mỗi giây, vượt thì `429`.
- Trang `/settings/api-keys` cho `ADMIN`: tạo, sao chép key lúc vừa tạo, xem danh sách, thu hồi.

**Tiêu chí nghiệm thu**

Gọi `curl` với API key đọc được tồn kho của đúng doanh nghiệp; gọi `POST` bằng key bị từ chối; key đã thu hồi nhận `401`.

**Đã làm (nhánh `t-25-1`)**

*Dạng key.* `wms_` cộng 43 ký tự base64url (không đệm) của 32 byte ngẫu nhiên sinh bằng `SecureRandom`, tổng 47 ký tự. `key_prefix` là 8 ký tự đầu (gồm cả `wms_`). Cơ sở dữ liệu và Redis chỉ có SHA-256 của key (hex chữ thường 64 ký tự, tính trên chuỗi key mã hóa UTF-8); key gốc chỉ có trong kết quả tạo, không ghi log, không lưu ở đâu.

*Dữ liệu.* Migration V7 của auth-service tạo bảng `api_keys`: `id`, `company_id` (khóa ngoại `companies`), `name` (tối đa 100 ký tự), `key_prefix` (8), `key_hash` (64, duy nhất toàn hệ thống), `last_used_at`, `revoked_at` (khác NULL là đã thu hồi), `created_by_user` (khóa ngoại `users`), bốn cột audit; chỉ mục `(company_id, revoked_at)`. auth-service lọc tường minh theo `companyId` trong JWT, không dùng `@TenantId`. Key không bị xóa khỏi bảng, chỉ thu hồi.

*API của auth-service.* Mọi API cần JWT của `ADMIN`; tài khoản khác nhận `403 FORBIDDEN`, chưa đăng nhập nhận `401 UNAUTHORIZED`.

| API | Mô tả |
| --- | --- |
| `POST /api/auth/api-keys` | Body `{ "name": "Website bán hàng" }` (2 đến 100 ký tự). Trả `201`, `data`: `id`, `name`, `keyPrefix`, `key` (key đầy đủ, chỉ lần này), `createdAt`. Lỗi: gói không có `apiAccess` (gói `FREE`) trả `403 PLAN_LIMIT_EXCEEDED`, `data = { limit: "API_ACCESS", current: <số key đang hoạt động>, max: 0, plan: "FREE" }`; thuê bao `EXPIRED` trả `403 SUBSCRIPTION_EXPIRED` (gateway chặn trước); đã có 5 key đang hoạt động trả `409 CONFLICT`; tên trống trả `400 VALIDATION_ERROR`. Gói và trạng thái đọc từ cơ sở dữ liệu của auth-service, không qua bộ nhớ đệm |
| `GET /api/auth/api-keys` | `data` là mảng, mới nhất trước, gồm cả key đã thu hồi: `id`, `name`, `keyPrefix`, `active`, `createdAt`, `createdBy` (tên đăng nhập người tạo), `lastUsedAt` (cập nhật mỗi phút, có thể `null`), `revokedAt` |
| `DELETE /api/auth/api-keys/{id}` | Thu hồi, trả một phần tử như trong danh sách với `active = false`. Gọi lại với key đã thu hồi vẫn trả `200`, không đổi `revokedAt`. Key của doanh nghiệp khác hoặc không tồn tại trả `404 NOT_FOUND`. Gọi được cả khi thuê bao đã hết hạn (`/api/auth/api-keys/*` nằm trong `gateway.subscription.write-allowed-paths`) |

*Cách gateway kiểm tra key.* Tên khóa nằm ở `vn.wms.common.plan.ApiKeyCacheKeys` (gateway khai báo lại vì không phụ thuộc `common`):

| Khóa Redis | Kiểu | Ai ghi | Nội dung |
| --- | --- | --- | --- |
| `wms:apikey:{sha256}` | hash | auth-service | `companyId`, `keyId`, `prefix`. Ghi sau khi giao dịch tạo key commit, xóa sau khi giao dịch thu hồi commit. Tác vụ định kỳ (`app.api-key.sync-interval-ms`, mặc định 5 phút) ghi lại mọi key đang hoạt động và xóa khóa của key đã thu hồi, nên Redis bị xóa dữ liệu hoặc một lần ghi lỗi tự lành trong một chu kỳ |
| `wms:apikey:last-used` | hash | gateway | Trường `{keyId}`, giá trị epoch mili giây của request hợp lệ gần nhất (một lệnh HSET mỗi request). auth-service đổ vào cột `last_used_at` mỗi `app.api-key.last-used-flush-ms` (mặc định 1 phút), chỉ ghi khi mới hơn; không có lần ghi cơ sở dữ liệu nào theo từng request |
| `wms:apikey:rate:{keyId}:{epochSecond}` | chuỗi số | gateway | Bộ đếm request của key trong một giây (INCR, tự hết hạn sau 2 giây) |

Bộ lọc `ApiKeyAuthenticationGlobalFilter` (order -101, ngay trước bộ lọc JWT) xử lý request có `X-Api-Key` và không có `Authorization`, theo thứ tự:

1. Key sai dạng (`wms_` cộng 20 đến 200 ký tự base64url) hoặc không có `wms:apikey:{sha256}`: `401 UNAUTHORIZED` ("API key không hợp lệ hoặc đã bị thu hồi"). Key đã thu hồi bị từ chối ngay khi auth-service commit việc thu hồi. Không đọc được Redis: `503 SERVICE_UNAVAILABLE` (không xác thực được thì không cho qua).
2. Chỉ `GET` trên `gateway.api-key.allowed-paths` (mặc định `/api/products/**`, `/api/inventory/**`, `/api/orders/**`); phương thức hoặc đường dẫn khác: `403 FORBIDDEN`.
3. Đọc `wms:subscription:{companyId}`: `status = EXPIRED` trả `403 SUBSCRIPTION_EXPIRED`; `apiAccess` khác `true` (gói `FREE`) trả `403 PLAN_LIMIT_EXCEEDED` với `data = { limit: "API_ACCESS", plan }`. Thiếu hash hoặc Redis lỗi thì cho qua (fail open, cùng quy tắc mục 2.7; auth-service ghi lại hash mỗi 5 phút nên khoảng thiếu ngắn).
4. Giới hạn tốc độ: quá `gateway.api-key.rate-limit-per-second` (biến `API_KEY_RATE_LIMIT_PER_SECOND`, mặc định 20) request trong cùng một giây trả `429 TOO_MANY_REQUESTS` kèm header `Retry-After: 1`. Redis lỗi thì cho qua. Bộ lọc `RequestRateLimiter` chung của gateway (theo `X-User-Name`) vẫn áp dụng thêm với ngưỡng 20 mỗi giây, sức chứa 40.
5. Gỡ mọi header `X-User-*`, `X-Company-Id`, `X-Plan`, `X-Warehouse-Ids` và chính `X-Api-Key` do client gửi; gắn `X-Company-Id` của key, `X-User-Roles: INTEGRATION`, `X-User-Name: apikey-{keyId}` (không gắn kho). `SubscriptionGlobalFilter` sau đó gắn `X-Plan` như với request JWT.

Request có cả `Authorization` lẫn `X-Api-Key` được xử lý như request JWT (key bị gỡ, không chuyển xuống service). Hành vi của request JWT không đổi. Tắt cả cơ chế bằng `GATEWAY_API_KEY_ENABLED=false`. Bộ lọc chạy cả khi `GATEWAY_JWT_ENABLED=false`. Thân lỗi cùng khung `{ success: false, code, message }`, thêm `data` với `PLAN_LIMIT_EXCEEDED`.

*Vai trò `INTEGRATION` ở service nghiệp vụ.* Hằng số `Roles.INTEGRATION` không nằm trong `Roles.ALL` nên không gán được cho tài khoản. `UserContext.isGlobalReadScope` coi `INTEGRATION` là đọc được mọi kho của doanh nghiệp (giống `ADMIN`, `ACCOUNTANT` khi đọc); `requireWarehouseWriteAccess` vẫn từ chối. Bộ lọc `IntegrationAccessFilter` của module `common` (tự đăng ký ở mọi service servlet, chạy ngay sau `TenantFilter`) trả `403 FORBIDDEN` cho mọi request mang vai trò `INTEGRATION` mà không phải `GET`/`HEAD` hoặc nằm ngoài ba nhóm đường dẫn trên, để vai trò này chỉ đọc kể cả khi request không qua gateway. Từ WMS-20.3 các service bật `@PreAuthorize`: mọi API `GET` dưới ba nhóm đường dẫn trên khai báo thêm `INTEGRATION`, trừ kiểm kê, phiếu điều chỉnh tồn, báo cáo kho và danh sách lấy hàng của đơn bán (bảng vai trò, cột INTEGRATION ở [SECURITY.md](SECURITY.md)); dữ liệu vẫn lọc theo `X-Company-Id`.

*Ví dụ cho tài liệu tích hợp (WMS-25.2).*

```bash
# Tồn khả dụng của một sản phẩm trên mọi kho của doanh nghiệp (thêm &warehouseId= để lọc một kho)
curl -H "X-Api-Key: $WMS_API_KEY" "https://<gateway>/api/inventory/stock/atp?productId=15"
# Danh mục hàng hóa và đơn bán
curl -H "X-Api-Key: $WMS_API_KEY" "https://<gateway>/api/products?page=0&size=20"
curl -H "X-Api-Key: $WMS_API_KEY" "https://<gateway>/api/orders/sales"
# Ghi bằng key: 403 FORBIDDEN; key đã thu hồi: 401 UNAUTHORIZED
curl -X POST -H "X-Api-Key: $WMS_API_KEY" "https://<gateway>/api/products"
```

*Màn hình.* `/settings/api-keys` (mục "API key" trên menu, sau "Người dùng"); tài khoản không phải `ADMIN` thấy trang 403. Tạo key theo tên gợi nhớ; hộp thoại hiện key đúng một lần với nút sao chép và cảnh báo không xem lại được; danh sách; thu hồi có xác nhận. `PLAN_LIMIT_EXCEEDED` và `SUBSCRIPTION_EXPIRED` hiện lời nhắc kèm nút sang `/subscription`.

### WMS-25.2 [Docs] Hướng dẫn tích hợp và Swagger công khai

Người làm: Toàn. Ngày: 20/11. Jira: TASK-157. Phụ thuộc: WMS-25.1.

Tài liệu `docs/INTEGRATION.md`: cách tạo API key, danh sách API được gọi, ví dụ `curl` cho hàng hóa, tồn kho, đơn hàng, bảng mã lỗi, giới hạn tốc độ. Swagger của các service truy cập được qua gateway (gộp WMS-21.1).

### WMS-26.1 [Frontend] Rà soát giao diện toàn hệ thống

Người làm: Toàn. Ngày: 18/11 đến 19/11. Jira: TASK-159. Phụ thuộc: mọi màn hình đã có.

Toàn dùng thử toàn bộ hệ thống, ghi danh sách góp ý (bố cục, luồng thao tác, câu chữ, hiển thị trên điện thoại), sửa trực tiếp hoặc giao lại cho người phụ trách màn hình. Kết quả: danh sách góp ý đã xử lý kèm ảnh trước và sau.

## 4. Ảnh hưởng tới phần đã làm

| Phần đã có | Thay đổi sau khi tách doanh nghiệp |
| --- | --- |
| Mọi danh sách, tìm kiếm, báo cáo | Chỉ còn dữ liệu của doanh nghiệp đang đăng nhập |
| Mã SKU, mã kho, mã đối tác, số chứng từ | Duy nhất trong một doanh nghiệp, không còn duy nhất toàn hệ thống |
| Vai trò `ADMIN` | Là quản trị của một doanh nghiệp |
| Test của các service | Phải đặt doanh nghiệp (header `X-Company-Id` hoặc `TenantContext`) trước khi gọi |
| WebSocket | Kênh đổi sang dạng theo doanh nghiệp ở WMS-22.4c, Toàn sửa hook dùng chung |
| Cache Redis (WMS-19.1) | Tên khóa phải có `companyId`. Đã làm: `wms:stock:{companyId}:...` (chi tiết ở [PERFORMANCE.md](PERFORMANCE.md)) |
| Báo cáo WMS-15, WMS-16, WMS-17 | Bảng và truy vấn mới phải có `company_id` ngay từ đầu; truy vấn SQL gốc phải tự lọc |
| Task nghiệp vụ làm từ 09/11 (WMS-10, 12, 13, 14) | Thực thể mới kế thừa `TenantEntity` ngay khi tạo |

## 5. Điểm còn chờ chốt

1. Giá gói Pro (499.000 đ mỗi tháng, 4.790.000 đ mỗi năm) đang lấy theo trang bảng giá, Toàn xác nhận trước ngày 10/11.
2. Khóa PayOS và khóa AI do Toàn đặt trực tiếp vào `.env` trên máy chủ. Trước đó mọi người phát triển với `PAYOS_MOCK=true` và `AI_MOCK=true`.
