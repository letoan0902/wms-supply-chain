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
- Khi `EXPIRED`: mọi request `POST`, `PUT`, `PATCH`, `DELETE` trả `SUBSCRIPTION_EXPIRED`, trừ đăng nhập, làm mới token, đăng xuất, đổi mật khẩu và các API dưới `/api/auth/subscription/**`, `/api/auth/payments/**`. Request `GET` vẫn chạy bình thường.

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
- Trạng thái doanh nghiệp sau đăng ký là `ACTIVE` cho tới khi WMS-23.6 thêm bước kích hoạt qua email.
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
  - `assertCanCreateIfTenant(limit, supplier)`: bỏ qua khi chưa có doanh nghiệp trong ngữ cảnh, dùng cho service chưa tách dữ liệu (product-service).
  - `reportUsage(limit, count)`, `incrementMonthlyAiUsage()`, `assertAiQuota()` cho ai-service.
- `ApiException.withData(code, message, data)`: `GlobalExceptionHandler` trả `data` kèm lỗi.
- Gateway: `SubscriptionGlobalFilter` (order -99, ngay sau bộ lọc JWT) gắn `X-Plan` và chặn ghi khi `EXPIRED`. Danh sách ngoại lệ ở `gateway.subscription.write-allowed-paths`; tắt cả bộ lọc bằng `GATEWAY_SUBSCRIPTION_ENABLED=false`. Bộ lọc chỉ có tác dụng khi bật kiểm tra JWT (mới có `X-Company-Id`).
- inventory-service kiểm tra số kho khi tạo kho đang hoạt động và khi bật lại kho đã ngưng (PATCH trạng thái hoặc PUT có `active: true`); product-service kiểm tra số SKU khi tạo và khi bật lại SKU đã xóa mềm. product-service hiện đếm SKU trên toàn bảng cho tới khi xong WMS-22.4b.
- Phần 2 (auth-service, nhánh `wms-23-2b`):
  - `UserPlanLimitGuard` kiểm tra `USERS` (số tài khoản `enabled = true` của doanh nghiệp) trước khi tạo tài khoản (`POST /api/auth/users`, `POST /api/auth/register`) và trước khi mở khóa (`PATCH .../status` hoặc `PUT` có `enabled: true`); sau mỗi lần tạo, mở khóa, khóa thì ghi lại `users` vào `wms:usage:{companyId}` sau khi commit. auth-service không dùng `TenantContext` nên gọi bản nhận doanh nghiệp trực tiếp `assertCanCreate(companyId, limit, currentCount)`, `reportUsage(companyId, limit, count)` (thêm vào `PlanLimitChecker`, bản cũ giữ nguyên). Đăng ký doanh nghiệp mới không bị chặn.
  - `GET /api/auth/subscription` trả đủ bốn số `usage` (cách đọc ở mục API của WMS-23.1); Redis lỗi thì ba số đọc từ Redis là `null`.
  - `SubscriptionCacheWriter` dùng tên khóa, tên trường của `SubscriptionCacheKeys`; test hợp đồng kiểm tra hash ghi ra được `SubscriptionSnapshot.fromHash` đọc lại đúng.
  - Tác vụ định kỳ 5 phút, sau khi chuyển `EXPIRED`, ghi lại hash của mọi thuê bao để Redis mất dữ liệu thì tự lành trong 5 phút; Redis không chạy chỉ ghi cảnh báo.

### WMS-23.3a [Frontend] Trang giới thiệu và bảng giá

Người làm: Linh. Đã xong ngày 05/11. Jira: TASK-144.

Trang `/landing` và `/pricing` đã có, dữ liệu gói lấy từ `pricingConfig.ts`. Việc còn lại: khi WMS-23.3b có trang `/register`, các nút "Bắt đầu" trỏ sang trang đó; số liệu trong `pricingConfig.ts` giữ khớp mục 2.5.

### WMS-23.3b [Frontend] Đăng ký doanh nghiệp và đăng nhập theo doanh nghiệp

Người làm: Phương Anh. Ngày: 17/11. Jira: TASK-145. Phụ thuộc: WMS-23.1.

**Mô tả chức năng**

- Trang `/register`: các trường tên doanh nghiệp, mã số thuế, số điện thoại, họ tên người quản trị, tên đăng nhập, email, mật khẩu, nhập lại mật khẩu, ô đồng ý điều khoản. Kiểm tra dữ liệu ngay trên form theo đúng quy tắc của API.
- Gửi thành công: thông báo đăng ký thành công và 30 ngày dùng thử, chuyển sang `/login` (sau WMS-23.6 thì hiện hướng dẫn mở email để kích hoạt). Lỗi `409` hiển thị ngay dưới trường bị trùng.
- Đăng nhập: lưu `company` và `subscription` cùng thông tin người dùng; hiển thị tên doanh nghiệp ở thanh tiêu đề và trang hồ sơ. Lỗi `COMPANY_INACTIVE` hiện thông báo riêng, kèm nút gửi lại email kích hoạt khi WMS-23.6 xong.
- Trang đăng nhập có liên kết "Đăng ký doanh nghiệp" và "Quên mật khẩu".

**Tiêu chí nghiệm thu**

Từ trang giới thiệu bấm đăng ký, điền form, đăng nhập và vào được trang tổng quan của doanh nghiệp mới với dữ liệu rỗng, thanh tiêu đề hiện đúng tên doanh nghiệp.

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

### WMS-23.6 [Backend] Kích hoạt doanh nghiệp qua email và đặt lại mật khẩu

Người làm: Phương Anh. Ngày: 18/11. Jira: TASK-149. Phụ thuộc: WMS-23.1.

**API**

| API | Mô tả |
| --- | --- |
| `POST /api/auth/companies/register` (sửa) | Doanh nghiệp tạo ra ở trạng thái `PENDING`; gửi email chứa liên kết `{địa chỉ frontend}/activate?token=...` tới email đăng ký |
| `POST /api/auth/companies/activate` | Body: `token`. Hợp lệ: doanh nghiệp thành `ACTIVE`, ghi `activated_at`. Sai, đã dùng hoặc quá 24 giờ: `400 TOKEN_INVALID` |
| `POST /api/auth/companies/resend-activation` | Body: `email`. Luôn trả `200` dù email có tồn tại hay không. Mỗi email tối đa một lần mỗi phút |
| `POST /api/auth/password/forgot` | Body: `email`. Luôn trả `200`. Nếu email thuộc một tài khoản đang bật thì gửi liên kết `{địa chỉ frontend}/reset-password?token=...`, hiệu lực 30 phút |
| `POST /api/auth/password/reset` | Body: `token`, `newPassword`. Hợp lệ: đổi mật khẩu, vô hiệu mọi refresh token của tài khoản. Không hợp lệ: `400 TOKEN_INVALID` |

**Quy tắc nghiệp vụ**

- Mã trong liên kết sinh ngẫu nhiên, chỉ lưu bản băm, dùng một lần.
- 30 ngày dùng thử vẫn tính từ lúc đăng ký.
- Đăng nhập khi doanh nghiệp còn `PENDING` nhận `403 COMPANY_INACTIVE`.
- auth-service không gửi mail trực tiếp: phát sự kiện qua RabbitMQ để notification-service gửi bằng `EmailService` đã có. Tên sự kiện và cấu trúc bổ sung vào `docs/MESSAGING.md` trong cùng Pull Request.
- Môi trường không cấu hình SMTP: notification-service ghi liên kết ra log để nhóm test.
- Kèm hai trang frontend nhỏ `/activate` (gọi API rồi báo kết quả) và `/reset-password` (form mật khẩu mới), và nối trang `/forgot-password` đã có với API.

**Tiêu chí nghiệm thu**

1. Đăng ký xong chưa đăng nhập được; bấm liên kết trong email thì đăng nhập được.
2. Liên kết kích hoạt dùng lần hai nhận `TOKEN_INVALID`.
3. Quên mật khẩu: nhận email, đặt mật khẩu mới, đăng nhập bằng mật khẩu mới được, mật khẩu cũ không còn dùng được.
4. Nhập email không tồn tại ở form quên mật khẩu: vẫn hiện thông báo như bình thường.

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

### WMS-25.1 [Backend] API key cho hệ thống ngoài

Người làm: Toàn. Ngày: 17/11. Jira: TASK-156. Phụ thuộc: WMS-22.3.

**Mô tả chức năng**

- Bảng `api_keys` ở auth-service: `company_id`, `name`, `key_prefix` (8 ký tự đầu để nhận diện), `key_hash`, `last_used_at`, `revoked_at`, người tạo.
- `POST /api/auth/api-keys` (`ADMIN`): body `name`; trả key đầy đủ đúng một lần. `GET /api/auth/api-keys`: danh sách không kèm key. `DELETE /api/auth/api-keys/{id}`: thu hồi.
- Chỉ gói có `apiAccess` mới tạo được key (gói `FREE` nhận `PLAN_LIMIT_EXCEEDED` với `limit = API_ACCESS`). Mỗi doanh nghiệp tối đa 5 key đang hoạt động.
- Gateway nhận header `X-Api-Key` thay cho JWT: tra key, gắn `X-Company-Id` và vai trò `INTEGRATION`. Vai trò này chỉ gọi được `GET` của hàng hóa, tồn kho, đơn hàng.
- Key sai hoặc đã thu hồi: `401`. Thuê bao hết hạn hoặc đã về gói `FREE`: `403`. Giới hạn tốc độ tính theo key, mặc định 20 request mỗi giây, vượt thì `429`.
- Trang `/settings/api-keys` cho `ADMIN`: tạo, sao chép key lúc vừa tạo, xem danh sách, thu hồi.

**Tiêu chí nghiệm thu**

Gọi `curl` với API key đọc được tồn kho của đúng doanh nghiệp; gọi `POST` bằng key bị từ chối; key đã thu hồi nhận `401`.

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
| Cache Redis (WMS-19.1) | Tên khóa phải có `companyId` |
| Báo cáo WMS-15, WMS-16, WMS-17 | Bảng và truy vấn mới phải có `company_id` ngay từ đầu; truy vấn SQL gốc phải tự lọc |
| Task nghiệp vụ làm từ 09/11 (WMS-10, 12, 13, 14) | Thực thể mới kế thừa `TenantEntity` ngay khi tạo |

## 5. Điểm còn chờ chốt

1. Giá gói Pro (499.000 đ mỗi tháng, 4.790.000 đ mỗi năm) đang lấy theo trang bảng giá, Toàn xác nhận trước ngày 10/11.
2. Khóa PayOS và khóa AI do Toàn đặt trực tiếp vào `.env` trên máy chủ. Trước đó mọi người phát triển với `PAYOS_MOCK=true` và `AI_MOCK=true`.
