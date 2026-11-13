# Hướng dẫn tích hợp bằng API key

Tài liệu dành cho lập trình viên của hệ thống ngoài (website bán hàng, ERP, kế toán, ứng dụng báo cáo) cần đọc dữ liệu hàng hóa, tồn kho và đơn hàng của một doanh nghiệp trên WMS Supply Chain mà không dùng tài khoản đăng nhập của người.

Đặc tả chi tiết và các quyết định thiết kế nằm ở mục "WMS-25.1" của [SAAS_SPEC.md](SAAS_SPEC.md); quy ước định dạng phản hồi và mã lỗi chung nằm ở [CONVENTIONS.md](CONVENTIONS.md).

## 1. Tích hợp làm được gì

- Chỉ đọc: mọi request `GET` tới hàng hóa (`/api/products/**`), tồn kho (`/api/inventory/**`) và đơn hàng (`/api/orders/**`). Các nhóm đường dẫn này lấy từ `gateway.api-key.allowed-paths` trong `backend/api-gateway/src/main/resources/application.yml`.
- Không ghi được: `POST`, `PUT`, `PATCH`, `DELETE` bằng API key đều bị từ chối `403 FORBIDDEN`, kể cả khi đường dẫn thuộc ba nhóm trên.
- Không gọi được các nhóm khác (`/api/auth/**`, `/api/users/**`, `/api/notifications/**`, `/api/ai/**`).
- Mỗi key thuộc đúng một doanh nghiệp và chỉ thấy dữ liệu của doanh nghiệp đó. Key có quyền đọc mọi kho của doanh nghiệp, không bị giới hạn theo kho.
- Lưu ý dữ liệu nhạy cảm: các API đọc hàng hóa trả cả giá nhập, các API đơn hàng trả thông tin khách hàng, nhà cung cấp (liên hệ, công nợ). Hãy xem key như quyền đọc toàn bộ nhóm dữ liệu này.

## 2. Tạo API key

Chỉ tài khoản có vai trò `ADMIN` của doanh nghiệp tạo được key.

1. Đăng nhập, vào trang `/settings/api-keys` (menu "API key" thuộc nhóm "Cài đặt").
2. Bấm "Tạo API key", nhập "Tên gợi nhớ" (2 đến 100 ký tự, ví dụ tên hệ thống sẽ dùng key) rồi xác nhận.
3. Hộp thoại "API key đã được tạo" hiện key đầy đủ **đúng một lần**. Sao chép và lưu ngay vào nơi cất bí mật của hệ thống ngoài. Hệ thống chỉ lưu giá trị băm SHA-256 của key nên không thể xem lại; nếu làm mất, thu hồi key và tạo key mới.
4. Danh sách trên trang hiển thị tên, 8 ký tự đầu của key (`keyPrefix`, để nhận diện), trạng thái, người tạo, ngày tạo, lần dùng gần nhất (cập nhật khoảng mỗi phút) và thời điểm thu hồi.

Quy tắc:

| Quy tắc | Giá trị |
| --- | --- |
| Dạng key | `wms_` cộng 43 ký tự (tổng 47 ký tự) |
| Số key đang hoạt động tối đa mỗi doanh nghiệp | 5 (tạo thêm nhận `409 CONFLICT`) |
| Gói dịch vụ được dùng API key | `TRIAL` (Dùng thử) và `PRO` (Chuyên Nghiệp). Gói `FREE` (Khởi Đầu) không có API key |

Có thể thao tác bằng API với token đăng nhập của `ADMIN` (không dùng được bằng API key): `POST /api/auth/api-keys` với body `{ "name": "Website bán hàng" }`, `GET /api/auth/api-keys`, `DELETE /api/auth/api-keys/{id}`.

## 3. Gửi key

Đặt key vào header `X-Api-Key` của mọi request, gọi qua API Gateway. Địa chỉ gốc là địa chỉ công khai của hệ thống (trên môi trường triển khai là `https://<DOMAIN>`, `/api/...` đi qua Caddy và Nginx tới gateway). Máy phát triển chạy Docker Compose dùng `http://localhost:8080`.

```bash
export WMS_BASE_URL="https://<DOMAIN>"
export WMS_API_KEY="<api-key>"

curl -H "X-Api-Key: $WMS_API_KEY" "$WMS_BASE_URL/api/products?page=0&size=20"
```

Không cần và không nên gửi kèm `Authorization`: request có cả hai header được xử lý như request đăng nhập thường, `X-Api-Key` bị bỏ qua.

## 4. API đọc được bằng key

Mọi API trả khung `ApiResponse` (mục 6). Tham số danh sách có phân trang dùng chung `page` (từ 0), `size` (mặc định 20, tối đa 100) và `sort` (dạng `tênTrường,asc` hoặc `tênTrường,desc`).

### 4.1. Hàng hóa (product-service)

| API | Tham số chính | Trả về (trường chính) |
| --- | --- | --- |
| `GET /api/products` | `keyword` (SKU, tên hoặc barcode), `categoryId`, `active`, phân trang | Trang `ProductResponse` |
| `GET /api/products/{id}` | | `ProductResponse` |
| `GET /api/products/sku/{sku}` | | `ProductResponse` |
| `GET /api/products/categories`, `GET /api/products/categories/{id}` | | Danh mục: `id`, `code`, `name`, `description` |
| `GET /api/products/uoms`, `GET /api/products/uoms/{id}` | | Đơn vị tính: `id`, `code`, `name` |
| `GET /api/products/{productId}/conversions` | | Quy đổi đơn vị: `fromUomCode`, `toUomCode`, `conversionFactor`, `factorToBase`, `barcode` |
| `GET /api/products/{productId}/conversions/breakdown` | `baseQuantity` (bắt buộc) | Số lượng cơ sở tách theo các đơn vị quy đổi |
| `GET /api/products/{id}/barcode` | `type` (`CODE_128`, `EAN_13`, `QR_CODE`), `width`, `height`, `includeLabel` | Ảnh mã vạch dạng JSON Base64; `.../barcode/image` trả PNG |

Các đường dẫn `/api/categories` và `/api/uoms` (bí danh trong product-service) không đi qua gateway; dùng dạng có tiền tố `/api/products/`.

Trường chính của `ProductResponse`: `id`, `sku`, `barcode`, `name`, `description`, `categoryId`, `categoryName`, `uomId`, `uomCode`, `uomName`, `purchasePrice`, `sellingPrice`, `vatRate`, `imageUrl`, `manageByLot`, `manageByExpiration`, `safetyStock`, `reorderPoint`, `maxStock`, `active`, `createdAt`, `updatedAt`.

### 4.2. Tồn kho (inventory-service)

| Nhóm | API | Tham số chính | Trả về (trường chính) |
| --- | --- | --- | --- |
| Tồn kho | `GET /api/inventory/stock/atp` | `productId` (bắt buộc), `warehouseId` (bỏ trống là mọi kho) | `productId`, `warehouseId`, `onHandQuantity`, `reservedQuantity`, `atpQuantity` (khả dụng = tồn thực trừ đã giữ chỗ, không âm) |
| Lô hàng | `GET /api/inventory/lots` | `warehouseId`, `productId`, `lotNo`, `locked`, `hasStock`, `expiryStatus`, `expiringDays` (mặc định 30), phân trang (mặc định sắp theo hạn dùng tăng dần) | Trang `LotResponse`: `warehouseId`, `warehouseCode`, `binLocationCode`, `zone`, `rack`, `bin`, `productId`, `lotNo`, `mfgDate`, `expDate`, `quantity`, `reservedQuantity`, `availableQuantity`, `locked`, `daysUntilExpiry`, `expiryStatus` |
| Lô hàng | `GET /api/inventory/lots/summary` | `warehouseId`, `expiringDays` | `totalLots`, `activeLots`, `expiringSoonLots`, `expiredLots`, `lockedLots`, `totalQuantityInLots` |
| Lô hàng | `GET /api/inventory/lots/expiring-soon` | `warehouseId`, `expiringDays` | Danh sách `LotResponse` cận hạn |
| Kho | `GET /api/inventory/warehouses` | `keyword`, `active` | Danh sách (không phân trang): `id`, `code`, `name`, `address`, `managerName`, `contactPhone`, `capacity`, `active`, `totalLocations`, `occupiedLocations`, `utilizationRate` |
| Kho | `GET /api/inventory/warehouses/{id}`, `GET /api/inventory/warehouses/summary` | | Chi tiết một kho; thẻ tóm tắt toàn hệ thống kho |
| Vị trí | `GET /api/inventory/locations`, `/zones`, `/{id}` | `warehouseId`, `zone`, `rack`, `status`, `active`; `/zones` cần `warehouseId` | Vị trí ô kệ: `code`, `zone`, `rack`, `bin`, `status`, `itemCount`, `totalQuantity` |
| Hàng đang về | `GET /api/inventory/incoming-stocks`, `/by-product/{productId}`, `/warehouses/{warehouseId}/summary` | `warehouseId` (bắt buộc ở hai API đầu), `status` (mặc định `INCOMING`) | Hàng đã đặt mua đang trên đường về kho |
| Điều chuyển | `GET /api/inventory/transfers`, `/{id}` | `status`, `sourceWarehouseId`, `destinationWarehouseId`, `keyword`, phân trang | Lệnh điều chuyển giữa kho |

Báo cáo và chứng từ kiểm kê: gateway cho đường dẫn đi qua, nhưng inventory-service từ chối vai trò của API key bằng `403 FORBIDDEN` ở các API sau, vì chỉ cho `ADMIN`, `ACCOUNTANT`, quản lý và nhân viên kho:

- `GET /api/inventory/reports/in-out-balance`, `/stock-card`, `/lot-trace`
- `GET /api/inventory/stocktakes/**`, `GET /api/inventory/stock-adjustments/**`

Nếu hệ thống ngoài cần các báo cáo này, hãy liên hệ nhóm phát triển để mở thêm quyền cho vai trò `INTEGRATION`.

Lưu ý: các API `POST` của nhóm tồn kho (ví dụ `POST /api/inventory/stock/atp/batch`) không dùng được bằng key; để hỏi tồn nhiều sản phẩm, gọi `/stock/atp` lần lượt từng `productId` và tôn trọng giới hạn tốc độ (mục 8).

### 4.3. Đơn hàng (order-service)

Mọi API dưới `/api/orders/**` đọc được bằng key. Nhóm chính:

| API | Tham số chính |
| --- | --- |
| `GET /api/orders/sales`, `/sales/{id}`, `/sales/number/{soNumber}`, `/sales/summary` | Danh sách: `keyword`, `customerId`, `warehouseId`, `status`, `fromOrderDate`, `toOrderDate` (định dạng `yyyy-MM-dd`), phân trang (mặc định mới nhất trước) |
| `GET /api/orders/purchase`, `/purchase/{id}`, `/purchase/number/{poNumber}`, `/purchase/summary` | `keyword`, `supplierId`, `warehouseId`, `status`, `fromOrderDate`, `toOrderDate`, phân trang |
| `GET /api/orders/goods-delivery-notes`, `/{id}`, `/number/{gdnNumber}`, `/so/{soId}`, `/summary` | `keyword`, `status`, `customerId`, `warehouseId`, `soId`, `fromDeliveryDate`, `toDeliveryDate` |
| `GET /api/orders/goods-receipt-notes`, `/{id}`, `/number/{grnNumber}`, `/po/{poId}`, `/po/{poId}/progress`, `/summary` | `keyword`, `status`, `warehouseId`, `supplierId`, `poId`, `fromReceiptDate`, `toReceiptDate` |
| `GET /api/orders/purchase-bills`, `/{id}`, `/number/{billNumber}`, `/grn/{grnId}`, `/po/{poId}`, `/summary` | Hóa đơn mua hàng |
| `GET /api/orders/customers`, `/{id}`, `/code/{code}`, `/{id}/transactions`, `/summary` | `keyword`, `type`, `active`, `debtLocked`, `overLimit`, phân trang |
| `GET /api/orders/suppliers`, `/{id}`, `/code/{code}`, `/{id}/transactions`, `/summary` | Nhà cung cấp |

`status` của đơn nhận một trong: `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`, `CONFIRMED`, `PARTIALLY_RECEIVED`, `BACKORDER`, `COMPLETED`, `CANCELLED`.

Trường chính của đơn bán (`SalesOrderResponse`): `id`, `soNumber`, `customerId`, `customerCode`, `customerName`, `warehouseId`, `warehouseName`, `deliveryAddress`, `recipientName`, `recipientPhone`, `status`, `orderDate`, `expectedDate`, `subtotalAmount`, `discountAmount`, `taxAmount`, `totalAmount`, `note`, `confirmedBy`, `confirmedAt`, `backorderReason`, `items`, `createdAt`, `updatedAt`. Mỗi phần tử của `items`: `productId`, `skuCode`, `productName`, `uom`, `quantity`, `unitPrice`, `discountPercent`, `discountAmount`, `taxRate`, `taxAmount`, `lineTotal`, `onHandQuantity`, `reservedQuantity`, `atpQuantity`, `isBackorder`, `note`.

Đơn mua (`PurchaseOrderResponse`) có cấu trúc tương tự: `poNumber`, `supplierId`, `supplierName`, `warehouseId`, `status`, `orderDate`, `expectedDate`, các trường tiền, `approvedBy`, `approvedAt`, `rejectedReason`, `items`.

## 5. Ví dụ curl

Danh sách hàng hóa đang hoạt động, 20 dòng đầu:

```bash
curl -s -H "X-Api-Key: $WMS_API_KEY" \
  "$WMS_BASE_URL/api/products?active=true&page=0&size=20"
```

Tồn khả dụng của sản phẩm có `id = 15` trên mọi kho (thêm `&warehouseId=<id kho>` để lọc một kho):

```bash
curl -s -H "X-Api-Key: $WMS_API_KEY" \
  "$WMS_BASE_URL/api/inventory/stock/atp?productId=15"
```

Đơn bán theo số đơn:

```bash
curl -s -H "X-Api-Key: $WMS_API_KEY" \
  "$WMS_BASE_URL/api/orders/sales/number/<soNumber>"
```

Đơn bán đã xác nhận trong một khoảng ngày:

```bash
curl -s -H "X-Api-Key: $WMS_API_KEY" \
  "$WMS_BASE_URL/api/orders/sales?status=CONFIRMED&fromOrderDate=2026-11-01&toOrderDate=2026-11-30&page=0&size=50"
```

Thử ghi để kiểm tra key chỉ đọc (phải nhận `403 FORBIDDEN`):

```bash
curl -i -X POST -H "X-Api-Key: $WMS_API_KEY" "$WMS_BASE_URL/api/products"
```

## 6. Định dạng phản hồi

Mọi API trả khung `ApiResponse`:

```json
{
  "success": true,
  "code": "OK",
  "message": "Thành công",
  "data": { },
  "timestamp": "2026-11-11T03:00:00Z"
}
```

Khi lỗi, `success` là `false`, `code` là tên mã lỗi, `message` là mô tả tiếng Việt, `data` có thể có thêm chi tiết (bị bỏ khỏi JSON nếu rỗng). Hãy xử lý theo `code`, không theo nội dung `message`.

Danh sách có phân trang đặt trong `data` đối tượng:

```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 135,
  "totalPages": 7,
  "first": true,
  "last": false
}
```

`page` bắt đầu từ 0. Riêng `GET /api/inventory/transfers` trả đối tượng `Page` nguyên bản của Spring Data (trường `content`, `totalElements`, `totalPages`, `number`, `size`, `first`, `last`...) thay vì dạng trên.

Số lượng và tiền là số thập phân (JSON number); thời điểm là chuỗi ISO 8601 UTC; ngày (`orderDate`, `expDate`...) dạng `yyyy-MM-dd`.

## 7. Bảng lỗi cho tích hợp

| HTTP | `code` | Nguyên nhân | Cách xử lý |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | Key sai dạng, không tồn tại hoặc đã bị thu hồi (thu hồi có hiệu lực ngay) | Kiểm tra key; nếu đã thu hồi, xin key mới từ `ADMIN` |
| 403 | `FORBIDDEN` | Phương thức không phải `GET`, hoặc đường dẫn ngoài `/api/products/**`, `/api/inventory/**`, `/api/orders/**`, hoặc API chỉ dành cho vai trò người dùng (mục 4.2) | Chỉ gọi `GET` vào API đã liệt kê ở mục 4 |
| 403 | `SUBSCRIPTION_EXPIRED` | Gói của doanh nghiệp đã hết hạn | `ADMIN` gia hạn gói ở trang `/subscription` |
| 403 | `PLAN_LIMIT_EXCEEDED` | Gói hiện tại không có API key (gói `FREE`); `data` có `limit = "API_ACCESS"` và `plan` | Nâng cấp gói |
| 429 | `TOO_MANY_REQUESTS` | Vượt giới hạn tốc độ của key; kèm header `Retry-After: 1` | Chờ rồi gửi lại (mục 8) |
| 503 | `SERVICE_UNAVAILABLE` | Gateway không đọc được kho dữ liệu kiểm tra key (Redis) | Thử lại sau; báo người vận hành nếu kéo dài |
| 404 | `NOT_FOUND` | Không có bản ghi (hoặc bản ghi thuộc doanh nghiệp khác) | Kiểm tra mã hoặc id |
| 400 | `VALIDATION_ERROR` hoặc `BAD_REQUEST` | Thiếu tham số bắt buộc hoặc sai định dạng (ví dụ `productId`, ngày) | Sửa tham số |

Ngoài ra bộ giới hạn chung của gateway (theo định danh người gọi) cũng có thể trả `429`; cách xử lý như nhau.

## 8. Giới hạn tốc độ

- Mặc định mỗi key được 20 request mỗi giây. Vượt ngưỡng trong cùng một giây nhận `429 TOO_MANY_REQUESTS` với `Retry-After: 1`.
- Người vận hành đổi ngưỡng bằng biến môi trường `API_KEY_RATE_LIMIT_PER_SECOND` của api-gateway (xem [OPERATIONS.md](OPERATIONS.md)).
- Gợi ý: gom dữ liệu bằng `size` lớn hơn (tối đa 100), lưu đệm kết quả ít thay đổi như danh mục hàng hóa, thử lại theo kiểu lùi dần khi nhận `429` hoặc `503`.

## 9. Thu hồi key

- `ADMIN` vào `/settings/api-keys`, bấm "Thu hồi" ở dòng của key rồi xác nhận. Key bị từ chối `401` ngay sau đó. Thu hồi vẫn làm được khi gói đã hết hạn.
- Thu hồi không xóa được lịch sử: key vẫn hiện trong danh sách với trạng thái đã thu hồi. Muốn dùng lại phải tạo key mới.

## 10. Khuyến nghị bảo mật

- Chỉ dùng key ở phía máy chủ của hệ thống ngoài. Không nhúng vào mã trình duyệt, ứng dụng di động, kho mã nguồn hay log.
- Mỗi hệ thống tích hợp dùng một key riêng, đặt tên gợi nhớ theo hệ thống, để thu hồi riêng khi cần và theo dõi "lần dùng gần nhất".
- Xoay key định kỳ: tạo key mới (còn chỗ trong 5 key), cập nhật hệ thống ngoài, kiểm tra key mới chạy tốt rồi thu hồi key cũ. Thu hồi ngay khi nghi ngờ lộ key hoặc khi người có quyền xem key rời dự án.
- Luôn gọi qua HTTPS. Key đi trong header, đừng đặt vào đường dẫn hay tham số truy vấn.
- Theo dõi cột "Lần dùng gần nhất": key không còn dùng nên thu hồi.

## 11. Tài liệu API tương tác (Swagger)

- Gateway gộp mô tả OpenAPI của các service tại `/swagger-ui.html` (danh sách thả xuống: Auth, Products, Inventory, Orders, Notifications, AI). Bấm Authorize, nhập `X-Api-Key` rồi dùng "Try it out"; request đi qua gateway.
- Tính năng bật tắt bằng biến `SWAGGER_ENABLED` của gateway: máy phát triển mặc định bật, máy chủ production mặc định tắt (các đường dẫn Swagger không có route nên trả 404). Cách bật tạm trên máy chủ xem [OPERATIONS.md](OPERATIONS.md).
- Trên máy chủ, tên miền công khai chỉ chuyển `/api/` tới gateway nên không mở được `/swagger-ui.html` qua tên miền đó; xem cách mở trong OPERATIONS.md.
