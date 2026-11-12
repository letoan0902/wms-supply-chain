# Bảo mật và phân quyền

Tài liệu mô tả cách hệ thống xác thực, phân quyền theo vai trò và theo kho, các giới hạn chống lạm dụng và những điểm
còn hở. Bản này viết theo mã nguồn sau WMS-20.3 (Jira TASK-125). Khi thêm hoặc đổi API, cập nhật bảng ở mục 3 và
bảng quyền của giao diện `frontend/src/features/auth/permissions.ts` (giao diện dùng bảng này để ẩn nút và hiện trang
403, hook `useCan()` ở `useCan.ts`) trong cùng một thay đổi.

## 1. Danh tính đi từ JWT tới service

```
Trình duyệt --Bearer JWT--> api-gateway --X-User-Name, X-User-Roles, X-User-Warehouse-Ids, X-Company-Id, X-Plan--> service
Hệ thống ngoài --X-Api-Key--> api-gateway --X-User-Name: apikey-{id}, X-User-Roles: INTEGRATION, X-Company-Id--> service
```

1. auth-service cấp access token HS256 (15 phút) với claim `sub`, `roles`, `warehouseIds`, `companyId`, `plan`.
2. api-gateway (`JwtAuthenticationGlobalFilter`) luôn gỡ các header `X-User-*`, `X-Company-Id`, `X-Plan` do client tự
   gửi, kiểm tra chữ ký và hạn của token, rồi gắn lại các header từ claim. `ApiKeyAuthenticationGlobalFilter` làm việc
   tương tự với `X-Api-Key` (tra SHA-256 của key trên Redis), gắn vai trò `INTEGRATION` và chỉ cho `GET` dưới
   `/api/products/**`, `/api/inventory/**`, `/api/orders/**`. `SubscriptionGlobalFilter` gắn `X-Plan`.
3. Ở mỗi service servlet, theo thứ tự bộ lọc:
   - `TenantFilter` đặt doanh nghiệp từ `X-Company-Id` (xem [MULTI_TENANT.md](MULTI_TENANT.md)).
   - `IntegrationAccessFilter` trả `403 FORBIDDEN` cho mọi request mang vai trò `INTEGRATION` mà không phải
     `GET`/`HEAD` hoặc nằm ngoài ba nhóm đường dẫn trên.
   - Chuỗi bộ lọc Spring Security dùng chung (`WmsSecurityAutoConfiguration` trong module `common`) với
     `GatewayIdentityFilter`: header thành `Authentication`, principal là tên đăng nhập, authority là
     `ROLE_<vai trò>`, `details` là `GatewayAuthenticationDetails` (kho được gán, doanh nghiệp, gói).
   - `@PreAuthorize("hasAnyRole(...)")` trên từng API (product, inventory, order, notification bật
     `@EnableMethodSecurity`). Vai trò không có trong danh sách nhận `403 FORBIDDEN`.
   - Phạm vi kho vẫn kiểm tra trong service qua `UserContext` (`resolveAllowedWarehouses`,
     `requireWarehouseWriteAccess`, `StocktakePermissions`, `ReportAccess`): ADMIN, ACCOUNTANT và INTEGRATION đọc
     mọi kho của doanh nghiệp; WAREHOUSE_MANAGER và WAREHOUSE_STAFF chỉ các kho được gán; chỉ ADMIN và
     WAREHOUSE_MANAGER của kho mới ghi dữ liệu kho.
4. auth-service tự kiểm tra JWT (`JwtAuthenticationFilter`, có danh sách token đã đăng xuất trên Redis) và có chuỗi bộ
   lọc riêng, nên chuỗi dùng chung không áp dụng ở đó. ai-service dùng chuỗi dùng chung nhưng không có
   `@PreAuthorize`: vai trò theo loại dữ liệu của trích xuất kiểm tra trong `ExtractionAccess`, trợ lý dành cho mọi
   tài khoản.

Lỗi 401 và 403 trả theo khung chung `{ "success": false, "code": "UNAUTHORIZED" | "FORBIDDEN", "message": ... }`.
Lỗi 403 phát sinh trong controller được `SecurityExceptionPassthroughAdvice` chuyển tiếp tới ExceptionTranslationFilter
rồi tới `ApiResponseSecurityErrorHandler`, không bị `GlobalExceptionHandler` gom thành 500. Lỗi không lường trước chỉ
trả `500 INTERNAL_ERROR` với thông báo chung; Spring Boot giữ mặc định không đưa stack trace hay thông điệp ngoại lệ
vào phản hồi (không service nào đặt `server.error.include-*`).

## 2. Chế độ phát triển và chế độ máy chủ

Công tắc `app.security.allow-anonymous-without-gateway` (biến `SECURITY_ALLOW_ANONYMOUS`):

| Giá trị | Ở đâu | Request không có `X-User-Name` |
| --- | --- | --- |
| `true` (mặc định trong `application.yml` của từng service) | Máy phát triển, chạy từ IDE hoặc `docker-compose.yml` với `GATEWAY_JWT_ENABLED=false` | Được coi là principal `dev-anonymous` mang đủ sáu vai trò tài khoản. `UserContext` vẫn trả rỗng nên các kiểm tra viết tay giữ quy ước cũ: cho qua, trừ chỗ bắt buộc đăng nhập (kiểm kê, báo cáo kho, KPI dashboard trả 401 như trước) |
| `false` | `deploy/docker-compose.prod.yml` đặt cho product, order, inventory, notification, ai | `401 UNAUTHORIZED` ngay tại service, kể cả khi ai đó gọi thẳng vào cổng của service mà không qua gateway |

Request có header danh tính luôn bị kiểm tra vai trò ở cả hai chế độ. Trên máy chủ, nếu tạm đặt
`GATEWAY_JWT_ENABLED=false` để chẩn đoán thì gateway không gắn danh tính và mọi API nghiệp vụ trả 401, trừ khi đặt tạm
`SECURITY_ALLOW_ANONYMOUS=true`.

Đường dẫn luôn mở: `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/error`, preflight
`OPTIONS`, và `app.security.public-paths` riêng của service (notification-service: `/ws-wms/**`, vì trình duyệt không
gửi được header Bearer khi bắt tay WebSocket; JWT được kiểm tra ở frame STOMP CONNECT).

## 3. Ma trận vai trò

Ký hiệu: `R` đọc, `W` ghi, `-` không được gọi (403). Cột INTEGRATION là API key của hệ thống ngoài (chỉ đọc).
ADMIN là quản trị của một doanh nghiệp nên có mọi quyền nghiệp vụ, kể cả những ô SRS mục 2.5.1 ghi "chỉ xem"
(xem mục 7). Phạm vi kho áp dụng thêm cho WAREHOUSE_MANAGER và WAREHOUSE_STAFF như mục 1.

### product-service

| API | ADMIN | MANAGER | STAFF | PURCHASING | SALES | ACCOUNTANT | INTEGRATION |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `GET` hàng hóa, danh mục, đơn vị tính, quy đổi, mã vạch | R | R | R | R | R | R | R |
| `POST /api/products`, `PUT /api/products/{id}`, `POST /api/products/bulk`, tạo danh mục, đơn vị tính, thêm/sửa/xóa quy đổi | W | W | W | W | - | - | - |
| `DELETE /api/products/{id}` (ngưng hoạt động) | W | W | - | - | - | - | - |
| `POST .../conversions/convert-to-base`, `.../calculate` (tính toán, không ghi) | R | R | R | R | R | R | - |
| `POST /api/products/{id}/barcode/batch` (in tem lô) | W | W | W | - | - | - | - |

Đường dẫn bí danh `/api/categories`, `/api/uoms` cùng vai trò nhưng nằm ngoài `/api/products/**` nên API key không gọi được.

### inventory-service

| API | ADMIN | MANAGER | STAFF | PURCHASING | SALES | ACCOUNTANT | INTEGRATION |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `GET /api/inventory/warehouses/**` | R | R | R | R | R | R | R |
| `POST`, `DELETE /api/inventory/warehouses` | W | - | - | - | - | - | - |
| `PUT /api/inventory/warehouses/{id}`, `PATCH .../status` | W | W (kho được gán) | - | - | - | - | - |
| `GET /api/inventory/locations/**` (kể cả tem mã vạch vị trí) | R | R | R | - | - | - | R |
| Tạo, tạo hàng loạt, sửa, xóa vị trí | W | W (kho được gán) | - | - | - | - | - |
| `GET /api/inventory/incoming-stocks/**` | R | R | R | R | R | R | R |
| `GET /api/inventory/lots/**` | R | R | R | R | R | R | R |
| Khóa, mở khóa lô, khóa lô hết hạn | W | W (kho được gán) | - | - | - | - | - |
| `GET /api/inventory/stock/atp` | R | R | R | R | R | R | R |
| `POST /api/inventory/stock/atp/batch` (đọc) | R | R | R | R | R | R | - |
| `POST /api/inventory/stock/picking-list` (order-service gọi khi lập phiếu xuất, xem lấy hàng) | R | R | R | - | R | - | - |
| `POST /api/inventory/stock/reservations/release` (order-service gọi khi hủy, xóa đơn bán) | W | W | - | - | W | - | - |
| `POST /api/inventory/stock/deduct`, `/dispatch` (trừ tồn trực tiếp, giao diện không gọi) | W | W | - | - | - | - | - |
| `GET /api/inventory/reports/**` (nhập-xuất-tồn, thẻ kho, truy vết lô, kèm `/export` tải file Excel của nhập-xuất-tồn và thẻ kho: cùng quyền và phạm vi kho) | R | R (kho được gán) | R (kho được gán) | - | - | R | - |
| `GET /api/inventory/alerts`, `/alerts/summary` (cảnh báo tồn kho, theo phạm vi kho của người gọi) | R | R | R | R | R | R | R |
| `POST /api/inventory/alerts/scan` (chạy quét cảnh báo ngay) | W | - | - | - | - | - | - |
| `GET /api/inventory/stocktakes/**` (danh sách, chi tiết, dòng đếm) | R | R | R | - | - | R | - |
| `GET /api/inventory/stocktakes/{id}/variances` | R | R | - | - | - | R | - |
| Lập, bắt đầu, kết thúc, đếm lại, hủy, ghi lý do, duyệt cấp quản lý | W | W (kho được gán) | - | - | - | - | - |
| `POST /api/inventory/stocktakes/{id}/counts` (kiểm đếm) | W | W | W (kho được gán) | - | - | - | - |
| `POST /api/inventory/stocktakes/{id}/adjustment/approve` | W | - | - | - | - | W | - |
| `POST /api/inventory/stocktakes/{id}/reject` | W | W | - | - | - | W | - |
| `GET /api/inventory/stock-adjustments/**` | R | R | - | - | - | R | - |
| `GET /api/inventory/transfers/**` | R | R | R | - | - | R | R |
| `POST /api/inventory/transfers` (lập lệnh điều chuyển) | W | W | W (kho nguồn được gán) | - | - | - | - |
| Sửa, hủy, xuất, nhận lệnh điều chuyển | W | W (kho nguồn/đích được gán) | - | - | - | - | - |

### order-service

| API | ADMIN | MANAGER | STAFF | PURCHASING | SALES | ACCOUNTANT | INTEGRATION |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `GET /api/orders/customers/**` (kể cả công nợ, kiểm tra hạn mức) | R | R | - | - | R | R | R |
| Tạo, nhập hàng loạt, sửa, xóa khách hàng | W | - | - | - | W | W | - |
| Ghi giao dịch công nợ khách hàng | W | - | - | - | - | W | - |
| `GET /api/orders/suppliers`, `/{id}`, `/code/{code}` | R | R | R | R | - | R | R |
| `GET /api/orders/suppliers/summary`, `/{id}/transactions` (công nợ) | R | R | - | R | - | R | R |
| Tạo, nhập hàng loạt, sửa nhà cung cấp | W | W | - | W | - | W | - |
| Xóa nhà cung cấp | W | - | - | W | - | W | - |
| Ghi giao dịch công nợ nhà cung cấp | W | - | - | - | - | W | - |
| `GET /api/orders/purchase/**` | R | R | R | R | - | R | R |
| Tạo, sửa, xóa, hủy đơn mua | W | - | - | W | - | - | - |
| Duyệt, từ chối đơn mua | W | W | - | W | - | - | - |
| `GET /api/orders/sales/**` (trừ picking-list) | R | R | R | - | R | R | R |
| `POST /api/orders/sales/check-atp` | R | R | - | - | R | - | - |
| Tạo, sửa, xóa, hủy đơn bán | W | - | - | - | W | - | - |
| Xác nhận đơn bán | W | W | - | - | W | - | - |
| `GET /api/orders/sales/{id}/picking-list` | R | R | R | - | R | - | - |
| `GET /api/orders/goods-receipt-notes/**` | R | R | R | R | - | R | R |
| Lập, xác nhận phiếu nhập | W | W | W | - | - | - | - |
| `GET /api/orders/goods-delivery-notes/**` | R | R | R | - | R | R | R |
| Lập, xác nhận xuất phiếu xuất | W | W | W | - | - | - | - |
| `GET /api/orders/purchase-bills/**` | R | R | - | R | - | R | R |
| Ghi thanh toán hóa đơn mua | W | - | - | - | - | W | - |
| `GET /api/orders/reports/trending-items` (mặt hàng bán chạy trên dashboard, chỉ số lượng, kho lọc theo phạm vi) | R | R | R | R | R | R | - |

### notification-service và ai-service

| API | ADMIN | MANAGER | STAFF | PURCHASING | SALES | ACCOUNTANT | INTEGRATION |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `GET /api/notifications/dashboard/kpi-summary` (số tiền bị ẩn với vai trò không xem tài chính, kho lọc theo phạm vi) | R | R | R | R | R | R | - |
| `GET /api/notifications/realtime/kpi` | R | R | R | R | R | R | - |
| `GET /api/notifications/health-info` (cấu hình RabbitMQ, SMTP) | R | - | - | - | - | - | - |
| `/api/ai/health`, `/usage`, `/ask` | R | R | R | R | R | R | - |
| `/api/ai/extractions/**` hàng hóa, nhà cung cấp | W | W | - | W | - | - | - |
| `/api/ai/extractions/**` khách hàng | W | - | - | - | W | - | - |

API key không gọi được notification-service và ai-service (gateway và `IntegrationAccessFilter` chặn).

Mọi API của product, inventory, order, notification đều có `@PreAuthorize`; test `everyApiDeclaresItsRoles` của từng
service thất bại nếu có API mới thiếu khai báo. Test `*AuthorizationTest` gọi API đại diện của mọi controller với từng
vai trò ở chế độ máy chủ; dùng `vn.wms.common.test.GatewayHeaders` (test fixtures của `common`) để gắn danh tính.

### Quyết định khi đặt vai trò

- WAREHOUSE_STAFF đọc được danh sách và chi tiết đơn bán, đơn mua (SRS 2.5.1: nhân viên kho "xem" đơn mua, đơn bán):
  màn hình lấy hàng, phiếu xuất và phiếu nhập cần chọn đơn.
- INTEGRATION đọc mọi API `GET` dưới `/api/products/**`, `/api/inventory/**`, `/api/orders/**` như trước WMS-20.3
  (khi `@PreAuthorize` chưa có tác dụng), trừ kiểm kê, phiếu điều chỉnh tồn, báo cáo kho (kiểm tra viết tay vốn đã từ
  chối) và danh sách lấy hàng của đơn bán (gọi tiếp một API `POST` của inventory-service mà API key không được gọi).
- KPI dashboard mở cho cả sáu vai trò (SRS UC-10: mọi tác nhân); service đã che số tiền và lọc theo kho. Biểu đồ
  mặt hàng bán chạy (`/api/orders/reports/trending-items`) cùng sáu vai trò: chỉ có số lượng, không có số tiền, vai trò
  không phải ADMIN, ACCOUNTANT chỉ thấy các kho được gán.
- Nhập hàng loạt nhà cung cấp gồm WAREHOUSE_MANAGER và PURCHASING để khớp màn hình nhập bằng AI (WMS-24.2 cho
  ADMIN, WAREHOUSE_MANAGER, PURCHASING nhập nhà cung cấp và hàng hóa).
- Hàng hóa: WAREHOUSE_STAFF thêm, sửa (SRS: "Xem, Thêm/Sửa"); PURCHASING thêm, sửa vì nhập hàng hóa bằng AI dành cho
  vai trò này; chỉ ADMIN, WAREHOUSE_MANAGER ngưng hoạt động sản phẩm.
- Vị trí ô kệ không mở cho PURCHASING, SALES, ACCOUNTANT (SRS: kế toán "Không").
- Trừ tồn trực tiếp (`/stock/deduct`, `/stock/dispatch`) chỉ ADMIN, WAREHOUSE_MANAGER; luồng chuẩn là phiếu xuất của
  order-service.

## 4. API nội bộ `/internal/**`

`/internal/inventory/kpi-metrics` và `/internal/orders/kpi-metrics` chỉ được notification-service gọi qua mạng nội bộ;
`/internal/products` (product-service) cho tác vụ quét cảnh báo tồn kho hằng đêm của inventory-service đọc mức tồn an
toàn và điểm đặt hàng lại; `/internal/auth/users/alert-recipients` (auth-service) trả người nhận email cảnh báo.
order-service gọi đồng bộ các endpoint tồn kho nội bộ của inventory-service khi lập chứng từ (WMS-20.4,
[MESSAGING.md](MESSAGING.md) mục 7.4), chuyển tiếp nguyên danh tính của người đang thao tác; vai trò và phạm vi kho đã
được order-service kiểm tra trước khi gọi:

| Endpoint | Gọi từ | Việc làm |
| --- | --- | --- |
| `POST /internal/inventory/receipts/location-check` | lập, xác nhận phiếu nhập | Trả các dòng sẽ cộng tồn vào vị trí đang đóng băng kiểm kê (không đổi dữ liệu) |
| `POST /internal/inventory/reservations` | xác nhận đơn bán (cả lập đơn CONFIRMED, sửa đơn BACKORDER) | Giữ chỗ toàn bộ đơn, tất cả hoặc không, idempotent theo đơn; thiếu hàng trả 409 |
| `POST /internal/inventory/dispatches` | lập phiếu xuất COMPLETED, xác nhận xuất phiếu | Trừ tồn theo phiếu, mỗi `dispatchKey` một lần; thiếu tồn, ô đóng băng trả 4xx |

Endpoint nội bộ đổi tồn kho không kiểm tra vai trò của người được chuyển tiếp (khác `/api/inventory/stock/deduct` chỉ
cho ADMIN, WAREHOUSE_MANAGER), vì vai trò đã được kiểm tra ở API phiếu, đơn của order-service.

api-gateway không định tuyến tới (route chỉ có `/api/**` và `/ws-wms/**`). Quy tắc:

- Có người dùng chuyển tiếp (notification-service gắn lại `X-User-*` của người xem dashboard): xác thực như request
  thường.
- Không có người dùng nhưng có doanh nghiệp (`X-Company-Id` hoặc doanh nghiệp mặc định ở máy phát triển): principal
  `internal-service`, không có vai trò nghiệp vụ. Dành cho tác vụ định kỳ và listener.
- Không có cả hai: 401 ở chế độ máy chủ (và `TenantFilter` đã trả `403 COMPANY_REQUIRED` khi thiếu doanh nghiệp).

API nội bộ không có `@PreAuthorize`. Thêm API nội bộ mới chỉ cần đặt dưới `/internal/`.

## 5. Tách dữ liệu theo doanh nghiệp

Mỗi request mang đúng một doanh nghiệp lấy từ JWT hoặc API key (`X-Company-Id`, client không tự đặt được); Hibernate
`@TenantId` tự gán và tự lọc `company_id` cho mọi truy vấn JPA, đọc bản ghi của doanh nghiệp khác bằng ID trả 404, sự
kiện RabbitMQ và lời gọi OpenFeign mang theo doanh nghiệp, SQL gốc phải tự lọc. Chi tiết và cách kiểm thử ở
[MULTI_TENANT.md](MULTI_TENANT.md).

## 6. Mật khẩu, token, giới hạn tốc độ, CORS

**Lưu trữ bí mật.**

| Bí mật | Cách lưu |
| --- | --- |
| Mật khẩu tài khoản | BCrypt (`BCryptPasswordEncoder`) trong `users.password_hash` |
| API key | SHA-256 trong `api_keys.key_hash` và khóa Redis `wms:apikey:{sha256}`; key gốc chỉ trả một lần lúc tạo |
| Mã kích hoạt doanh nghiệp, mã đặt lại mật khẩu | SHA-256 trong `account_tokens.token_hash`, dùng một lần, có hạn |
| Refresh token | Lưu nguyên dạng ở Redis `refresh:{username}` (xem mục 7) |
| Access token đã đăng xuất | Khóa Redis `blacklist:{token}` đến khi token hết hạn (xem mục 7) |
| Khóa ký JWT, khóa PayOS, khóa AI | Chỉ trong biến môi trường trên máy chủ (`JWT_SECRET`, `PAYOS_*`, `ANTHROPIC_API_KEY`) |

**Chống dò mật khẩu** (auth-service, `AuthService.login`): sai 5 lần thì khóa 30 phút theo cả địa chỉ IP
(`login:attempts:ip:*`, `login:lock:ip:*`) lẫn tên đăng nhập (`login:attempts:user:*`, `login:lock:user:*`, chỉ đếm khi
tài khoản tồn tại); bộ đếm hết hạn sau 30 phút, đăng nhập đúng thì xóa bộ đếm. Lần sai trả `401 UNAUTHORIZED` kèm số
lần thử còn lại; khi bị khóa trả `400 BAD_REQUEST` kèm số phút phải chờ. Khớp FR01 ("khóa tài khoản 30 phút khi sai quá
5 lần"), nên WMS-20.3 không thêm bộ giới hạn mới.

**Giới hạn tốc độ.**

- api-gateway `RequestRateLimiter` (token bucket trên Redis) theo `X-User-Name`, chưa đăng nhập thì theo IP (ưu tiên
  địa chỉ đầu của `X-Forwarded-For`): 20 request mỗi giây, bùng nổ 40 (`RATE_LIMIT_*`), vượt trả 429.
- API key: 20 request mỗi giây cho mỗi key (`API_KEY_RATE_LIMIT_PER_SECOND`), vượt trả `429 TOO_MANY_REQUESTS` kèm
  `Retry-After: 1`.
- Email kích hoạt, quên mật khẩu: giới hạn số lần gửi theo email (khóa `wms:auth:mail-throttle:*`).

**CORS.** Chỉ api-gateway trả header CORS: `allowed-origins` là danh sách cụ thể từ `CORS_ALLOWED_ORIGINS` (máy phát
triển `http://localhost:5173`, máy chủ `https://<DOMAIN>`), `allow-credentials: true`, phương thức
`GET, POST, PUT, PATCH, DELETE, OPTIONS`, mọi header, `max-age` 3600. Không có cấu hình `*` đi cùng credentials. Hub
WebSocket của notification-service dùng cùng danh sách origin.

## 7. Điểm còn hở (chưa xử lý trong WMS-20.3)

- Service tin header danh tính: ai vào được mạng Docker hoặc cổng `127.0.0.1:808x` trên máy chủ có thể tự gửi
  `X-User-Roles`. Công tắc mục 2 chỉ chặn request không có danh tính; chưa có chữ ký giữa gateway và service hay mTLS.
- API nội bộ chỉ cần `X-Company-Id`, dựa vào việc gateway không định tuyến tới và cổng service không mở ra Internet.
- Refresh token và access token đã đăng xuất lưu nguyên dạng trên Redis (nên lưu SHA-256 như API key).
- auth-service đọc IP đăng nhập từ `X-Forwarded-For` do client gửi, nên đổi header này né được khóa theo IP (khóa theo
  tài khoản vẫn còn). Khóa theo IP cũng có thể khóa chung nhiều người sau cùng một NAT.
- Ẩn trường tài chính với nhân viên kho (SRS 2.5.2: chiết khấu, công nợ) chỉ có ở KPI dashboard; đơn mua, đơn bán vẫn
  trả giá và thành tiền cho WAREHOUSE_STAFF.
- `GET /api/inventory/warehouses/{id}` kiểm tra phạm vi kho: tài khoản SALES hoặc PURCHASING chưa được gán kho nhận
  403, nên order-service không kiểm tra được kho khi họ lập đơn. Cần gán kho cho các tài khoản này hoặc nới quy tắc đọc
  kho ở WarehouseService.
- SRS 2.5.1 ghi ADMIN "chỉ xem" với vị trí ô kệ, hàng hóa, đơn hàng, phiếu kho; hệ thống cho ADMIN ghi vì ADMIN là chủ
  doanh nghiệp trong mô hình SaaS và các kiểm tra viết tay sẵn có đều cho ADMIN ghi.
- API trò chuyện và tóm tắt của ai-service (`/api/ai/chat`, `/api/ai/summary`) không dùng `@PreAuthorize`: mọi vai trò
  đã đăng nhập gọi được, quyền xem dữ liệu do service được tra cứu quyết định vì ai-service chuyển tiếp nguyên danh
  tính của người hỏi.
- Lời gọi giữa các service từ tác vụ định kỳ hoặc listener không có người dùng: trên máy chủ chỉ gọi được đường dẫn
  `/internal/**`. Thêm lời gọi nền mới tới `/api/**` của service khác sẽ nhận 401.
- Chưa kiểm thử xâm nhập; giao diện chưa ẩn nút theo đúng bảng trên ở mọi màn hình.
