# Hợp đồng API quản trị nền tảng cho màn hình `/platform`

Dịch vụ: auth-service, đi qua api-gateway với tiền tố `/api/auth/platform`. Mô hình và lý do ở
[SAAS_SPEC.md](../SAAS_SPEC.md) mục 6. Mọi phản hồi theo `ApiResponse` (`success`, `code`, `message`, `data`,
`timestamp`); danh sách theo `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`,
`page` bắt đầu từ 0, `size` mặc định 10, lớn hơn 100 thì lấy 100). Thời điểm là chuỗi ISO 8601 UTC (`Instant`, có thể
có phần lẻ của giây), ngày dạng `yyyy-MM-dd`. Số tiền là số nguyên đồng (VND).

## 1. Phân quyền

Chỉ vai trò `SUPER_ADMIN` (quản trị nền tảng). Chưa đăng nhập: `401 UNAUTHORIZED`. Vai trò khác, kể cả `ADMIN` của
doanh nghiệp: `403 FORBIDDEN`. Doanh nghiệp hệ thống mã `PLATFORM` (chứa tài khoản `SUPER_ADMIN`) không bao giờ có
trong kết quả và không được tính vào số liệu; xem, đổi trạng thái, duyệt hoặc từ chối nó trả `404 NOT_FOUND`.

Giá trị enum: trạng thái doanh nghiệp `PENDING | ACTIVE | SUSPENDED`; gói `FREE | TRIAL | PRO`; trạng thái thuê bao
`ACTIVE | EXPIRED`; trạng thái thanh toán `PENDING | PAID | CANCELLED | EXPIRED`; chu kỳ `MONTHLY | YEARLY`.

## 2. Đối tượng dùng chung

`PlatformCompanySummary`:

```json
{
  "id": 3, "code": "CTY000003", "name": "Công ty TNHH Ví dụ", "taxCode": "0101234567",
  "email": "lienhe@vidu.vn", "phone": "0900000000", "status": "ACTIVE",
  "plan": "TRIAL", "planName": "Dùng thử", "subscriptionStatus": "ACTIVE",
  "expiresAt": "2026-12-10T17:00:00Z", "userCount": 4,
  "createdAt": "2026-11-10T03:12:45.123456Z", "activatedAt": "2026-11-10T03:20:01.654321Z"
}
```

- `plan`, `planName`, `subscriptionStatus`: `null` khi doanh nghiệp chưa có thuê bao.
- `expiresAt`: hạn đang áp dụng (hạn dùng thử với `TRIAL`, hạn gói với `PRO`), `null` với `FREE` hoặc khi chưa có thuê bao.
- `userCount`: tổng số tài khoản của doanh nghiệp, kể cả tài khoản đang khóa.
- `activatedAt`: `null` khi doanh nghiệp chưa từng kích hoạt.

`PlatformPayment`:

```json
{
  "orderCode": 1731400000123, "companyId": 3, "companyCode": "CTY000003", "companyName": "Công ty TNHH Ví dụ",
  "plan": "PRO", "cycle": "MONTHLY", "amount": 499000, "status": "PAID",
  "createdAt": "2026-11-12T02:00:00Z", "paidAt": "2026-11-12T02:03:10Z"
}
```

`paidAt` là `null` khi chưa thanh toán. Không có liên kết thanh toán, mã QR hay mã giao dịch ngân hàng.

## 3. Endpoint

### 3.1 `GET /overview`

```json
{
  "companies": { "total": 12, "active": 8, "pending": 3, "suspended": 1 },
  "subscriptions": { "free": 2, "trial": 5, "pro": 4, "expired": 1, "expiringSoon": 2 },
  "users": { "total": 57, "active": 51 },
  "revenue": { "total": 5980000, "thisMonth": 1196000, "paidCount": 9 },
  "registrationsByMonth": [ { "month": "2026-06", "count": 0 }, "...", { "month": "2026-11", "count": 7 } ],
  "revenueByMonth": [ { "month": "2026-06", "amount": 0 }, "...", { "month": "2026-11", "amount": 1196000 } ],
  "recentCompanies": [ "PlatformCompanySummary, tối đa 5, mới tạo trước" ],
  "recentPayments": [ "PlatformPayment, tối đa 5 thanh toán PAID, mới thanh toán trước" ]
}
```

- `subscriptions.free|trial|pro`: số doanh nghiệp theo gói của thuê bao, kể cả đã hết hạn. `expired`: số thuê bao
  `EXPIRED`. `expiringSoon`: thuê bao `ACTIVE` có hạn trong 7 ngày tới.
- `users.active`: số tài khoản đang bật.
- `revenue`: chỉ thanh toán `PAID`, theo `paidAt`; `thisMonth` theo tháng hiện tại giờ Việt Nam.
- `registrationsByMonth`, `revenueByMonth`: đúng 6 phần tử, 6 tháng gần nhất kể cả tháng hiện tại, tăng dần, tháng
  theo giờ Việt Nam, tháng trống có giá trị 0.

### 3.2 `GET /companies`

Tham số: `keyword` (không phân biệt hoa thường, tìm trong tên, mã, mã số thuế, email), `status`, `plan`, `page`, `size`.
Sắp xếp: mới tạo trước. Trả `PageResponse<PlatformCompanySummary>`. `status` sai giá trị hoặc `plan` không phải gói có
trong hệ thống: `400`.

### 3.3 `GET /companies/{id}`

```json
{
  "company": { "...các trường của PlatformCompanySummary...": "", "address": "1 Tràng Tiền, Hà Nội" },
  "limits": { "maxUsers": 50, "maxWarehouses": 20, "maxSkus": -1, "maxAiRequests": 500, "apiAccess": true },
  "usage": { "users": 4, "warehouses": 1, "skus": 37, "aiRequests": 5 },
  "admins": [ { "id": 9, "username": "chu.cty", "fullName": "Nguyễn Văn A", "email": "a@vidu.vn", "enabled": true } ],
  "payments": [ "PlatformPayment, tối đa 10, mới tạo trước" ]
}
```

- `limits`, `usage`: `null` khi doanh nghiệp chưa có thuê bao. Giới hạn `-1` là không giới hạn.
- `usage.users`: số tài khoản đang bật (như trang Gói dịch vụ của doanh nghiệp). `usage.warehouses`, `usage.skus`:
  service nghiệp vụ ghi lên Redis, `null` khi chưa ghi hoặc đọc không được. `usage.aiRequests`: lượt AI tháng này, `0`
  khi chưa có bộ đếm, `null` khi Redis lỗi.
- `admins`: tài khoản có vai trò `ADMIN` của doanh nghiệp, theo id tăng dần.
- Không có hoặc là `PLATFORM`: `404 NOT_FOUND`.

### 3.4 `PUT /companies/{id}/status`

Thân: `{ "status": "SUSPENDED" | "ACTIVE", "reason": "tùy chọn, tối đa 255 ký tự" }`. Trả `PlatformCompanySummary` sau
khi đổi.

| Trạng thái hiện tại | `SUSPENDED` | `ACTIVE` |
| --- | --- | --- |
| `ACTIVE` | Tạm khóa | `400` "Doanh nghiệp đang hoạt động, không cần mở khóa" |
| `SUSPENDED` | `400` "Doanh nghiệp đang bị tạm khóa" | Mở khóa |
| `PENDING` | `400` (chỉ tạm khóa được doanh nghiệp đang hoạt động) | `400` (kích hoạt qua liên kết trong email đăng ký hoặc mục 3.6) |

- Giá trị khác (`PENDING`): `400 BAD_REQUEST`. Giá trị không phải enum: `400 BAD_REQUEST` "Nội dung yêu cầu không đúng
  định dạng". Thiếu `status` hoặc `reason` quá 255 ký tự: `400 VALIDATION_ERROR` kèm lỗi từng trường trong `data`.
- `PLATFORM` hoặc không tồn tại: `404`.
- Người dùng của doanh nghiệp bị khóa đăng nhập hoặc làm mới token nhận `403 COMPANY_INACTIVE` với thông báo
  "Doanh nghiệp đã bị tạm khóa. Vui lòng liên hệ quản trị nền tảng.". Access token đã cấp còn dùng được tới khi hết hạn
  (tối đa 15 phút).
- `reason` chỉ ghi vào log của auth-service, không lưu và không trả lại.

### 3.5 `GET /payments`

Tham số: `status`, `companyId`, `from`, `to` (ngày `yyyy-MM-dd` theo giờ Việt Nam, lọc theo ngày tạo, gồm cả hai đầu),
`page`, `size`. Sắp xếp: mới tạo trước. Trả `PageResponse<PlatformPayment>`. `from` sau `to` hoặc ngày sai dạng: `400`.
Giao dịch `PENDING` đã quá hạn được chuyển `EXPIRED` trước khi trả (giống lịch sử thanh toán của doanh nghiệp).

### 3.6 `POST /companies/{id}/approve`

Duyệt một bản đăng ký đang chờ kích hoạt (`PENDING`). Thân tùy chọn; không gửi thân thì cấp gói dùng thử 30 ngày:

```json
{ "plan": "TRIAL", "trialDays": 30 }
{ "plan": "PRO", "cycle": "MONTHLY" }
```

- `plan`: `TRIAL` (mặc định) hoặc `PRO`, không phân biệt hoa thường. `trialDays`: 1 đến 90, mặc định 30, chỉ dùng với
  `TRIAL`. `cycle`: `MONTHLY` (30 ngày) hoặc `YEARLY` (365 ngày), bắt buộc với `PRO`, không dùng với `TRIAL`.
- Kết quả: doanh nghiệp `ACTIVE`, ghi `activatedAt`; thuê bao tạo lúc đăng ký được đặt lại một lần, hạn tính từ lúc duyệt
  (gói `PRO` không cộng dồn thời gian dùng thử còn lại và không tạo giao dịch thanh toán); bộ nhớ đệm Redis của thuê bao
  được ghi lại. Người đăng ký (ADMIN đầu tiên) đăng nhập được ngay và nhận email báo đã duyệt kèm liên kết đăng nhập.
  Liên kết kích hoạt trong email đăng ký vẫn mở được và chỉ báo doanh nghiệp đã hoạt động, không đổi gì.
- Trả `PlatformCompanySummary` sau khi duyệt.
- Gói khác `TRIAL`, `PRO`, chu kỳ sai, `PRO` thiếu `cycle`, `trialDays` đi với `PRO` hoặc `cycle` đi với `TRIAL`:
  `400 BAD_REQUEST`. `trialDays` ngoài 1 đến 90: `400 VALIDATION_ERROR`. Doanh nghiệp không còn `PENDING` (đã duyệt, đã
  kích hoạt qua email, đã từ chối, đang tạm khóa): `400 BAD_REQUEST`. `PLATFORM` hoặc không tồn tại: `404`.

### 3.7 `POST /companies/{id}/reject`

Từ chối một bản đăng ký đang chờ kích hoạt (`PENDING`). Thân: `{ "reason": "bắt buộc, tối đa 255 ký tự" }`.

- Kết quả: doanh nghiệp chuyển `SUSPENDED`, `activatedAt` vẫn `null` (cách nhận ra bản đăng ký bị từ chối trong danh
  sách); liên kết kích hoạt chưa dùng bị vô hiệu. Người đăng ký đăng nhập nhận `403 COMPANY_INACTIVE` "Doanh nghiệp đã bị
  tạm khóa. Vui lòng liên hệ quản trị nền tảng." và nhận email báo bản đăng ký chưa được chấp thuận (không kèm lý do).
- `reason` chỉ ghi vào log của auth-service, không lưu và không gửi đi. Thiếu hoặc quá 255 ký tự: `400 VALIDATION_ERROR`.
- Doanh nghiệp không còn `PENDING`: `400 BAD_REQUEST` (bản đăng ký đã từ chối không duyệt lại được). `PLATFORM` hoặc
  không tồn tại: `404`.
- Mở khóa (mục 3.4) vẫn đưa doanh nghiệp đã từ chối về `ACTIVE` như mọi doanh nghiệp đang tạm khóa, với thuê bao dùng
  thử tính từ lúc đăng ký; chỉ làm khi thật sự muốn nhận lại bản đăng ký.
- Trả `PlatformCompanySummary` sau khi từ chối.

## 4. API của doanh nghiệp khi đăng nhập bằng `SUPER_ADMIN`

Khu vực nền tảng không gọi các API này; nếu gọi nhầm, phản hồi luôn sạch: `GET /api/auth/subscription` trả `404`
(doanh nghiệp `PLATFORM` không có thuê bao); `/api/auth/payments/**`, `/api/auth/api-keys/**`, `/api/auth/users/**`
(quản lý người dùng), `GET` và `PUT /api/auth/company` trả `403`. `GET /api/auth/me`, hồ sơ cá nhân và đổi mật khẩu
dùng bình thường; `/me` và kết quả đăng nhập có `company.code = "PLATFORM"`, `subscription = null`.
