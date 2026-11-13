# Hợp đồng API biểu đồ dashboard (WMS-16.4)

Ba API đọc phục vụ các khối dưới hàng thẻ KPI của dashboard: biểu đồ cột đôi mua hàng và bán hàng theo thời gian,
biểu đồ donut tỷ trọng tồn kho theo nhóm hàng, bảng giao dịch kho gần nhất. Không có bảng hay migration mới. Mọi phản
hồi bọc trong `ApiResponse` chuẩn `{ success, code, message, data, timestamp }`; lỗi xử lý theo `code`.

## 0. Quy ước chung

| Mục | Quy ước |
| --- | --- |
| `warehouseId` | Tùy chọn. Bỏ trống: mọi kho trong phạm vi của người xem |
| `dateRange` | `TODAY`, `7_DAYS` (mặc định, gồm hôm nay), `THIS_MONTH`, `THIS_QUARTER`, `ALL_TIME`, không phân biệt hoa thường; giá trị khác trả 400. Cùng tập giá trị với `GET /api/orders/reports/trending-items`. Mọi khoảng kết thúc ở hôm nay |
| Phạm vi kho | ADMIN, ACCOUNTANT: mọi kho của doanh nghiệp. Vai trò khác: chỉ các kho được gán; chưa được gán kho nào hoặc xin kho ngoài phạm vi trả `403 FORBIDDEN` |
| Doanh nghiệp | Luôn lọc theo doanh nghiệp của token (JPQL với `@TenantId`). Kho của doanh nghiệp khác hoặc không tồn tại: `404 NOT_FOUND` (hai API kho) |
| Số tiền | Chỉ ADMIN, ACCOUNTANT nhận số tiền, giống các thẻ KPI tài chính; vai trò khác nhận `null` kèm cờ và lời giải thích |
| Số lượng | Cộng gộp các đơn vị tính khác nhau (giống biểu đồ mặt hàng bán chạy), nên chỉ dùng để so sánh tương đối |
| Thời điểm | Các trường `...At`, `asOf`, `fromTime` là ISO-8601 UTC; trường ngày là `YYYY-MM-DD` |

Mã lỗi: `400 BAD_REQUEST` (sai `dateRange`, `limit`), `401 UNAUTHORIZED` (không có danh tính), `403 FORBIDDEN`,
`403 COMPANY_REQUIRED`, `404 NOT_FOUND`, `503 SERVICE_UNAVAILABLE` (chỉ tỷ trọng theo nhóm hàng, khi product-service
không trả lời).

## 1. Mua hàng và bán hàng theo thời gian

`GET /api/orders/reports/purchase-sales-trend?warehouseId=&dateRange=` (order-service)

Vai trò: ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF, PURCHASING, SALES, ACCOUNTANT (như `trending-items`).

### Số liệu được tính

| Chuỗi | Chứng từ | Trạng thái | Ngày xếp kỳ | Số tiền |
| --- | --- | --- | --- | --- |
| Mua hàng | Đơn mua (PO) | `APPROVED`, `CONFIRMED`, `PARTIALLY_RECEIVED`, `COMPLETED` (đã duyệt trở đi; không gồm nháp, chờ duyệt, bị từ chối, đã hủy) | `order_date` | `total_amount` của đơn (sau chiết khấu, thuế) |
| Bán hàng | Đơn bán (SO) | `CONFIRMED`, `COMPLETED` (không gồm nháp, chờ hàng `BACKORDER`, đã hủy) | `order_date` | `total_amount` của đơn |

Bán hàng dùng đúng quy ước của thẻ "Doanh số xuất bán tháng" và biểu đồ mặt hàng bán chạy: với `THIS_MONTH`,
`totals.salesAmount` bằng số trên thẻ khi cùng phạm vi kho. Thẻ "Đơn mua chờ tiếp nhận" chỉ đếm phần `APPROVED` nên nhỏ
hơn hoặc bằng `totals.purchaseOrderCount`. Đơn đặt ngày trong tương lai không được tính. Ngày hôm nay lấy theo giờ của
máy chủ order-service, giống `trending-items`.

Cách gom kỳ (`bucket`): `DAY` với `TODAY` (1 kỳ), `7_DAYS` (7 kỳ), `THIS_MONTH` (số ngày đã qua của tháng); `MONTH` với
`THIS_QUARTER` (tháng đầu quý tới tháng hiện tại) và `ALL_TIME` (từ tháng có đơn được tính sớm nhất, không có đơn thì
một kỳ là tháng này). Kỳ không phát sinh vẫn có điểm với giá trị 0.

### `data`

| Trường | Ý nghĩa |
| --- | --- |
| `dateRange`, `fromDate`, `toDate` | Khoảng đã chuẩn hóa, ngày đầu và ngày cuối (hôm nay) |
| `bucket` | `DAY` hoặc `MONTH` |
| `amountVisible` | `true` với ADMIN, ACCOUNTANT |
| `currency` | `VNĐ` |
| `purchaseDefinition`, `salesDefinition`, `quantityNote` | Mô tả bằng lời để hiển thị dưới biểu đồ |
| `amountNote` | Lý do số tiền để trống, `null` khi được xem |
| `points[]` | `{ periodStart, periodEnd, label, figures }`; `label` dạng `dd/MM` (ngày) hoặc `MM/yyyy` (tháng); kỳ tháng hiện tại có `periodEnd` là hôm nay |
| `figures`, `totals` | `{ purchaseOrderCount, purchaseQuantity, purchaseAmount, salesOrderCount, salesQuantity, salesAmount }`; hai trường `...Amount` là `null` khi `amountVisible = false` |
| `meta` | `{ companyId, warehouseId, warehouseIds, calculatedAt }`; `warehouseIds = null` là mọi kho của doanh nghiệp |

## 2. Tỷ trọng tồn kho theo nhóm hàng

`GET /api/inventory/dashboard/stock-by-category?warehouseId=&dateRange=` (inventory-service)

Vai trò: cả sáu vai trò tài khoản (như thẻ tồn kho khả dụng). Không có số tiền.

- Số liệu: tồn thực tế hiện tại `SUM(stock_items.quantity)` theo sản phẩm, gồm cả phần đã giữ chỗ, lô bị khóa, lô hết
  hạn (vì vậy khác số trên thẻ "Tồn kho khả dụng", vốn trừ phần giữ chỗ). `dateRange` chỉ được kiểm tra và trả lại:
  mọi khoảng đều kết thúc hôm nay nên tồn cuối kỳ chính là tồn hiện tại.
- Giá trị: thẻ "Giá trị tồn kho" chưa tính được vì tồn kho chưa có giá vốn, nên API tính theo số lượng
  (`basis = QUANTITY`, `valuationMethod = NOT_AVAILABLE`, mọi trường giá trị `null`) và nói rõ trong `basisNote`.
  Khi có giá vốn, chỉ vai trò `valueVisible = true` (ADMIN, ACCOUNTANT) mới nhận giá trị.
- Nhóm hàng: đọc danh mục hàng hóa qua `GET /internal/products` của product-service (100 sản phẩm mỗi trang, tối đa
  100 trang). Sản phẩm chưa gán nhóm hoặc không còn trong danh mục vào nhóm "Chưa phân nhóm hàng"
  (`kind = UNCATEGORIZED`). Tối đa 8 lát cắt: nhiều nhóm hơn thì 7 nhóm lớn nhất giữ nguyên, phần còn lại gom vào
  "Các nhóm khác" (`kind = OTHERS`). product-service không trả lời: `503`, không đoán nhóm. Không có tồn thì không gọi
  product-service và trả `slices = []`.

### `data`

| Trường | Ý nghĩa |
| --- | --- |
| `dateRange`, `asOf` | Khoảng đã chuẩn hóa, thời điểm chụp tồn |
| `basis`, `valuationMethod`, `valueVisible`, `basisNote`, `unit` | Như trên; `unit` là `đơn vị` |
| `totalQuantity`, `totalValue` | Tổng tồn thực tế; `totalValue` luôn `null` |
| `productCount`, `categoryCount` | Số sản phẩm còn tồn, số nhóm (kể cả "Chưa phân nhóm hàng") trước khi gom |
| `slices[]` | `{ key, kind, categoryId, name, quantity, value, percentage, productCount }`, lớn trước; `key` là `category-{id}`, `uncategorized` hoặc `others`; `percentage` một chữ số thập phân |
| `warehouseId`, `warehouseIds` | Kho đã yêu cầu, các kho được tổng hợp (`null` là mọi kho) |
| `dataNote` | Lưu ý khi danh mục quá lớn để đọc hết, `null` khi đầy đủ |

## 3. Giao dịch kho gần nhất

`GET /api/inventory/dashboard/recent-transactions?warehouseId=&dateRange=&limit=` (inventory-service)

Vai trò: ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF, ACCOUNTANT (cùng quyền và phạm vi với báo cáo kho, vì thẻ kho cho
thấy cùng dữ liệu). PURCHASING, SALES nhận 403. `limit` từ 1 đến 50, mặc định 10.

- Nguồn: sổ kho `stock_movements` (xem [BAO_CAO_KHO.md](BAO_CAO_KHO.md)). Mỗi dòng gom các dòng sổ cùng
  (`documentType`, `documentId`, `documentNumber`, `movementType`, `warehouseId`) phát sinh từ đầu ngày đầu khoảng (giờ
  Việt Nam) tới hiện tại; mới nhất trước theo thời điểm phát sinh gần nhất. Bỏ dòng số dư mở sổ (`OPENING_BALANCE`).
- Một phiếu điều chuyển cho hai dòng: xuất ở kho nguồn (`TRANSFER_OUT`) và nhận ở kho đích (`TRANSFER_IN`).
- Xuất bán ghi theo đơn bán (`documentType = SALES_ORDER`, số chứng từ là số SO) vì sổ kho chưa mang số phiếu xuất
  GDN; nhiều lần xuất của cùng một đơn trong khoảng được gộp một dòng với tổng số lượng.
- Phiếu điều chỉnh kiểm kê có `documentType = STOCKTAKE`, `documentNumber` là mã phiếu điều chỉnh, `stocktakeId` là
  đợt kiểm kê để mở màn hình kiểm kê.

### `data`

| Trường | Ý nghĩa |
| --- | --- |
| `dateRange`, `fromTime`, `limit` | `fromTime` là `null` với `ALL_TIME` |
| `rows[]` | Xem bảng dưới |
| `warehouseId`, `warehouseIds`, `generatedAt` | Như các API trên |

| Trường của dòng | Ý nghĩa |
| --- | --- |
| `documentType`, `documentId`, `documentNumber` | `GOODS_RECEIPT` (ID phiếu nhập GRN), `SALES_ORDER` (ID đơn bán), `STOCK_TRANSFER`, `STOCKTAKE` (ID phiếu điều chỉnh), `OTHER` |
| `movementType`, `movementTypeLabel`, `direction` | Loại biến động, tên tiếng Việt, `IN` hoặc `OUT` |
| `warehouseId`, `warehouseCode`, `warehouseName` | Kho phát sinh (tên `null` nếu kho đã bị xóa) |
| `counterpartWarehouseId`, `counterpartWarehouseName` | Kho bên kia của điều chuyển |
| `quantity` | Tổng nhập (với `IN`) hoặc tổng xuất (với `OUT`) của chứng từ tại kho, cộng gộp các mặt hàng |
| `productCount`, `lineCount` | Số mặt hàng, số dòng sổ được gom |
| `occurredAt` | Thời điểm phát sinh gần nhất |
| `performedBy` | Người xác nhận phiếu nhập, xuất, nhận điều chuyển, duyệt điều chỉnh (nếu chứng từ có nhiều người thì lấy một tên) |
| `stocktakeId` | Chỉ với `STOCKTAKE` |

## 4. Giao diện

`frontend/src/pages/DashboardPage.tsx` gọi ba API với cùng bộ chọn kho và khoảng thời gian như thẻ KPI
(`features/dashboard/dashboardApi.ts`). Biểu đồ mua bán cho chọn xem theo giá trị (chỉ khi `amountVisible` và quyền
`dashboard.amounts`), số đơn hoặc số lượng. Bảng giao dịch chỉ hiện khi có quyền `dashboard.recentTransactions`; số
chứng từ dẫn tới màn hình Mua hàng và nhập kho, Bán hàng và xuất kho, Điều chuyển (chưa có trang chi tiết riêng) hoặc
trang đợt kiểm kê, chỉ khi vai trò được mở màn hình đó.
