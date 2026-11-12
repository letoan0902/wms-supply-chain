# Hợp đồng API báo cáo kho (WMS-17.1, WMS-17.2, WMS-17.3)

Dịch vụ: **inventory-service**, đi qua API Gateway với tiền tố `/api/inventory/reports`. Mọi phản hồi bọc trong
`ApiResponse` chuẩn của dự án: `{ success, code, message, data, timestamp }`. Lỗi có `success = false`, `code` là mã
lỗi (frontend xử lý theo `code`, không theo `message`).

## 0. Quy ước chung

| Mục | Quy ước |
| --- | --- |
| Header (gateway tự gắn) | `X-Company-Id`, `X-User-Name`, `X-User-Roles`, `X-User-Warehouse-Ids`. Frontend chỉ gửi `Authorization: Bearer ...` như mọi API khác |
| Vai trò được xem | `ADMIN`, `ACCOUNTANT`: mọi kho của doanh nghiệp. `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF`: chỉ các kho được gán. Vai trò khác (`SALES`, `PURCHASING`): `403 FORBIDDEN` |
| Doanh nghiệp | Luôn lọc theo doanh nghiệp của token. Kho của doanh nghiệp khác: `404 NOT_FOUND` |
| Ngày | `fromDate`, `toDate` dạng `YYYY-MM-DD`, tính cả hai đầu, cắt ngày theo giờ Việt Nam (`Asia/Ho_Chi_Minh`). Khoảng tối đa 366 ngày |
| Thời điểm | Các trường `...At` là chuỗi ISO-8601 UTC, ví dụ `2026-11-05T03:15:00Z`. Frontend tự đổi sang giờ địa phương khi hiển thị |
| Số lượng | Số thập phân 3 chữ số (`DECIMAL(18,3)`), ví dụ `100.250` |
| Tiền | Các trường `...Value` luôn `null`: tồn kho chưa có giá vốn (giống thẻ "Giá trị tồn kho" trên Dashboard). Hiển thị "Chưa có giá vốn" và nội dung `valuationNote`, không tự tính |
| Phân trang | `page` từ 0, `size` từ 1 đến 200. Kết quả phân trang dạng `PageResponse`: `{ content, page, size, totalElements, totalPages, first, last }` |
| Số liệu không xác định | Trường số lượng `null` nghĩa là không xác định được (không phải 0). Lời giải thích nằm ở `dataNote` |

### Nguồn số liệu: sổ kho

Từ migration V13, inventory-service ghi bảng `stock_movements`: mỗi lần tồn kho thay đổi có đúng một dòng sổ, ghi cùng
transaction với thao tác (nhập GRN, xuất bán, xuất điều chuyển, nhận điều chuyển, xuất kho khác qua API trừ tồn; sau
này là điều chỉnh kiểm kê). Vì vậy `tồn đầu kỳ + nhập - xuất = tồn cuối kỳ` luôn đúng tuyệt đối.

Lịch sử trước khi có sổ kho không được lưu và không được dựng lại. Migration V13 ghi cho mỗi dòng tồn đang có một
dòng **số dư mở sổ** (`OPENING_BALANCE`) đúng bằng số lượng lúc chạy migration, và lưu mốc đó (`ledgerStartedAt`).
Kỳ báo cáo bắt đầu trước mốc này: tồn đầu kỳ, nhập, xuất là `null`, `dataNote` giải thích; tồn cuối kỳ vẫn đúng.
Doanh nghiệp tạo sau migration không có mốc (`ledgerStartedAt = null`), số liệu luôn đầy đủ.

### Mã lỗi

| HTTP | `code` | Khi nào |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | Thiếu `fromDate`/`toDate`/`productId`/`warehouseId`/`lotNo`, sai định dạng ngày, `toDate` trước `fromDate`, khoảng quá 366 ngày, `page < 0`, `size` ngoài 1..200, `sort`/`direction`/`groupBy` không hợp lệ, bộ lọc khớp quá 2000 sản phẩm |
| 401 | `UNAUTHORIZED` | Không có thông tin người dùng (gọi không qua gateway) |
| 403 | `FORBIDDEN` | Vai trò không được xem báo cáo; tài khoản kho chưa được gán kho; xem kho không được gán |
| 403 | `COMPANY_REQUIRED` | Không xác định được doanh nghiệp |
| 404 | `NOT_FOUND` | Kho không tồn tại hoặc thuộc doanh nghiệp khác; số lô không có trong phạm vi |
| 409 | `CONFLICT` | Số lô dùng cho nhiều sản phẩm mà không truyền `productId` (danh sách sản phẩm trong `data.candidates`) |
| 503 | `SERVICE_UNAVAILABLE` | Lọc theo `categoryId`/`keyword` khi product-service không trả lời (không lặng lẽ bỏ bộ lọc) |

---

## 1. Báo cáo Xuất - Nhập - Tồn

`GET /api/inventory/reports/in-out-balance`

### Tham số

| Tham số | Bắt buộc | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `fromDate` | Có | | Ngày đầu kỳ |
| `toDate` | Có | | Ngày cuối kỳ |
| `warehouseId` | Không | mọi kho trong phạm vi | Một kho cụ thể |
| `categoryId` | Không | | Lọc theo danh mục (tra product-service) |
| `productId` | Không | | Một sản phẩm |
| `keyword` | Không | | Mã SKU hoặc tên sản phẩm (tra product-service) |
| `groupBy` | Không | `PRODUCT_WAREHOUSE` | `PRODUCT_WAREHOUSE`: mỗi dòng một sản phẩm tại một kho. `PRODUCT`: gộp mọi kho trong phạm vi, `warehouseId` của dòng là `null` |
| `sort` | Không | `productId` | `productId`, `warehouseId` (chỉ khi `PRODUCT_WAREHOUSE`), `openingQuantity`, `quantityIn`, `quantityOut`, `closingQuantity` |
| `direction` | Không | `asc` | `asc` hoặc `desc` |
| `page` | Không | 0 | |
| `size` | Không | 20 | 1 đến 200 |

Dòng chỉ xuất hiện khi có tồn đầu kỳ khác 0 hoặc có phát sinh trong kỳ (sản phẩm đã hết hàng từ trước kỳ và không
phát sinh thì không có dòng).

### `data`

| Trường | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `fromDate`, `toDate` | ngày | Kỳ đã yêu cầu |
| `timezone` | chuỗi | `Asia/Ho_Chi_Minh` |
| `warehouseId` | số hoặc null | Kho đã yêu cầu |
| `warehouseIds` | mảng số hoặc null | Các kho thực sự được tổng hợp; `null` là mọi kho của doanh nghiệp |
| `groupBy` | chuỗi | `PRODUCT_WAREHOUSE` hoặc `PRODUCT` |
| `rows` | PageResponse của dòng | Xem bảng dưới |
| `totals` | đối tượng | Tổng trên **mọi** dòng khớp bộ lọc (không chỉ trang hiện tại): `openingQuantity`, `quantityIn`, `quantityOut`, `closingQuantity`, `openingValue`, `valueIn`, `valueOut`, `closingValue` |
| `ledgerStartedAt` | thời điểm hoặc null | Mốc bắt đầu sổ kho của doanh nghiệp |
| `quantitiesComplete` | boolean | `true` nếu tồn đầu, nhập, xuất của kỳ đều xác định được |
| `dataNote` | chuỗi hoặc null | Giải thích khi có số liệu `null` |
| `valuationMethod` | null | Chưa có phương pháp tính giá |
| `valuationNote` | chuỗi | Vì sao cột tiền để trống |
| `generatedAt` | thời điểm | Lúc lập báo cáo |

Dòng (`rows.content[]`):

| Trường | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `productId` | số | ID sản phẩm |
| `skuCode`, `productName`, `uom` | chuỗi hoặc null | Theo dòng sổ gần nhất có mã; thiếu thì lấy từ product-service; vẫn không có thì `null` (hiển thị theo `productId`) |
| `warehouseId`, `warehouseCode`, `warehouseName` | | `null` khi `groupBy = PRODUCT` |
| `openingQuantity` | số hoặc null | Tồn đầu kỳ |
| `quantityIn` | số hoặc null | Tổng nhập trong kỳ (nhập GRN, nhận điều chuyển, điều chỉnh tăng) |
| `quantityOut` | số hoặc null | Tổng xuất trong kỳ (xuất bán, xuất điều chuyển, xuất khác, điều chỉnh giảm) |
| `closingQuantity` | số hoặc null | Tồn cuối kỳ |
| `openingValue`, `valueIn`, `valueOut`, `closingValue` | null | Chưa có giá vốn |

### Ví dụ

`GET /api/inventory/reports/in-out-balance?fromDate=2026-11-01&toDate=2026-11-30&warehouseId=1&page=0&size=20`

```json
{
  "success": true,
  "code": "OK",
  "message": "Thành công",
  "data": {
    "fromDate": "2026-11-01",
    "toDate": "2026-11-30",
    "timezone": "Asia/Ho_Chi_Minh",
    "warehouseId": 1,
    "warehouseIds": [1],
    "groupBy": "PRODUCT_WAREHOUSE",
    "rows": {
      "content": [
        {
          "productId": 101, "skuCode": "SKU-GAO-ST25", "productName": "Gạo ST25 túi 5kg", "uom": "Túi",
          "warehouseId": 1, "warehouseCode": "WH01", "warehouseName": "Kho Hà Nội",
          "openingQuantity": 10.000, "quantityIn": 5.500, "quantityOut": 3.250, "closingQuantity": 12.250,
          "openingValue": null, "valueIn": null, "valueOut": null, "closingValue": null
        }
      ],
      "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true
    },
    "totals": {
      "openingQuantity": 10.000, "quantityIn": 5.500, "quantityOut": 3.250, "closingQuantity": 12.250,
      "openingValue": null, "valueIn": null, "valueOut": null, "closingValue": null
    },
    "ledgerStartedAt": null,
    "quantitiesComplete": true,
    "dataNote": null,
    "valuationMethod": null,
    "valuationNote": "Tồn kho chưa có giá vốn (...) nên chưa tính được giá trị; ...",
    "generatedAt": "2026-11-30T09:00:00Z"
  },
  "timestamp": "2026-11-30T09:00:00Z"
}
```

Kỳ bắt đầu trước mốc sổ kho: `openingQuantity`, `quantityIn`, `quantityOut` của mọi dòng và của `totals` là `null`,
`quantitiesComplete = false`, `dataNote` dạng "Sổ kho của doanh nghiệp bắt đầu ghi từ 12:00:00 ngày 10/11/2026.
Biến động trước thời điểm này không được lưu nên tồn đầu kỳ, nhập và xuất trong kỳ không xác định và để trống; tồn
cuối kỳ vẫn chính xác vì đã gồm số dư mở sổ. Chọn ngày đầu kỳ từ 11/11/2026 trở đi để có đủ số liệu."

---

## 2. Thẻ kho chi tiết

`GET /api/inventory/reports/stock-card`

| Tham số | Bắt buộc | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `productId` | Có | | Sản phẩm |
| `warehouseId` | Có | | Kho (phải thuộc phạm vi của người xem) |
| `fromDate`, `toDate` | Có | | Kỳ |
| `page` | Không | 0 | |
| `size` | Không | 50 | 1 đến 200 |

Các dòng luôn theo thứ tự thời gian tăng dần (thời điểm, rồi ID dòng sổ). `balanceAfter` là tồn lũy kế sau dòng đó,
đúng cả ở trang thứ hai trở đi.

### `data`

| Trường | Ý nghĩa |
| --- | --- |
| `productId`, `skuCode`, `productName`, `uom` | Sản phẩm |
| `warehouseId`, `warehouseCode`, `warehouseName` | Kho |
| `fromDate`, `toDate`, `timezone` | Kỳ |
| `openingQuantity` | Tồn đầu kỳ (null nếu kỳ bắt đầu trước mốc sổ kho) |
| `totalIn`, `totalOut` | Tổng nhập, xuất trong kỳ (null như trên) |
| `closingQuantity` | Tồn cuối kỳ |
| `lines` | PageResponse của dòng thẻ kho |
| `ledgerStartedAt`, `dataNote`, `generatedAt` | Như báo cáo Xuất - Nhập - Tồn |

Dòng (`lines.content[]`):

| Trường | Ý nghĩa |
| --- | --- |
| `movementId` | ID dòng sổ |
| `occurredAt` | Thời điểm phát sinh (UTC) |
| `movementType` | `OPENING_BALANCE`, `RECEIPT`, `TRANSFER_IN`, `ADJUSTMENT_IN`, `SALES_DISPATCH`, `TRANSFER_OUT`, `ADJUSTMENT_OUT`, `ISSUE_OTHER` |
| `movementTypeLabel` | Tên tiếng Việt: "Số dư mở sổ kho", "Nhập kho theo phiếu nhập", "Nhận hàng điều chuyển", "Điều chỉnh tăng", "Xuất bán theo đơn bán", "Xuất điều chuyển", "Điều chỉnh giảm", "Xuất kho khác" |
| `documentType` | `GOODS_RECEIPT` (phiếu nhập GRN), `SALES_ORDER` (đơn bán; sự kiện xuất kho chưa mang mã phiếu xuất GDN), `STOCK_TRANSFER`, `OPENING_BALANCE`, `OTHER` |
| `documentId`, `documentNumber` | ID và số chứng từ (ID phiếu nhập, ID đơn bán, ID phiếu điều chuyển) |
| `referenceType`, `referenceId`, `referenceNumber` | Chứng từ tham chiếu: `PURCHASE_ORDER` với dòng nhập kho |
| `partnerId` | ID nhà cung cấp của phiếu nhập (tham chiếu order-service; tên chưa lưu ở inventory) |
| `counterpartWarehouseId` | Kho bên kia của phiếu điều chuyển |
| `lotNo`, `expDate` | Số lô, hạn dùng |
| `binLocationId`, `binLocationCode` | Vị trí tại thời điểm phát sinh |
| `quantityIn`, `quantityOut` | Số lượng nhập, xuất |
| `balanceAfter` | Tồn lũy kế sau dòng |
| `performedBy` | Người thực hiện (người xác nhận GRN, người xác nhận xuất, người duyệt hoặc nhận điều chuyển) |
| `reason` | Diễn giải |

### Ví dụ

`GET /api/inventory/reports/stock-card?productId=101&warehouseId=1&fromDate=2026-11-01&toDate=2026-11-30`

```json
{
  "success": true, "code": "OK", "message": "Thành công",
  "data": {
    "productId": 101, "skuCode": "SKU-GAO-ST25", "productName": "Gạo ST25 túi 5kg", "uom": "Túi",
    "warehouseId": 1, "warehouseCode": "WH01", "warehouseName": "Kho Hà Nội",
    "fromDate": "2026-11-01", "toDate": "2026-11-30", "timezone": "Asia/Ho_Chi_Minh",
    "openingQuantity": 0.000, "totalIn": 100.250, "totalOut": 50.125, "closingQuantity": 50.125,
    "lines": {
      "content": [
        { "movementId": 1, "occurredAt": "2026-11-05T03:15:00Z", "movementType": "RECEIPT",
          "movementTypeLabel": "Nhập kho theo phiếu nhập", "documentType": "GOODS_RECEIPT", "documentId": 9001,
          "documentNumber": "GRN-20261105-0001", "referenceType": "PURCHASE_ORDER", "referenceId": 801,
          "referenceNumber": "PO-20261101-0001", "partnerId": 7, "counterpartWarehouseId": null,
          "lotNo": "LOT-A", "expDate": "2027-10-01", "binLocationId": 15, "binLocationCode": "WH01-ZA-R01-B01",
          "quantityIn": 100.250, "quantityOut": 0.000, "balanceAfter": 100.250, "performedBy": "thukho01", "reason": null },
        { "movementId": 2, "occurredAt": "2026-11-06T02:00:00Z", "movementType": "SALES_DISPATCH",
          "movementTypeLabel": "Xuất bán theo đơn bán", "documentType": "SALES_ORDER", "documentId": 501,
          "documentNumber": "SO-20261106-0001", "lotNo": "LOT-A", "binLocationCode": "WH01-ZA-R01-B01",
          "quantityIn": 0.000, "quantityOut": 30.125, "balanceAfter": 70.125, "performedBy": "thukho02" },
        { "movementId": 3, "occurredAt": "2026-11-07T04:00:00Z", "movementType": "TRANSFER_OUT",
          "movementTypeLabel": "Xuất điều chuyển", "documentType": "STOCK_TRANSFER", "documentId": 12,
          "documentNumber": "TRF-20261107-0001", "counterpartWarehouseId": 2, "lotNo": "LOT-A",
          "quantityIn": 0.000, "quantityOut": 20.000, "balanceAfter": 50.125, "performedBy": "quanly01" }
      ],
      "page": 0, "size": 50, "totalElements": 3, "totalPages": 1, "first": true, "last": true
    },
    "ledgerStartedAt": null, "dataNote": null, "generatedAt": "2026-11-30T09:00:00Z"
  },
  "timestamp": "2026-11-30T09:00:00Z"
}
```

(Ví dụ lược bớt các trường null cho gọn; API luôn trả đủ trường.)

---

## 3. Truy vết nguồn gốc theo số lô

`GET /api/inventory/reports/lot-trace`

| Tham số | Bắt buộc | Ý nghĩa |
| --- | --- | --- |
| `lotNo` | Có | Số lô (khớp chính xác, bỏ khoảng trắng hai đầu) |
| `productId` | Không | Bắt buộc khi số lô dùng cho nhiều sản phẩm |

Phạm vi: chỉ các dòng sổ và dòng tồn thuộc kho người xem được phép. Không có gì trong phạm vi: `404 NOT_FOUND`.
Số lô thuộc nhiều sản phẩm và không truyền `productId`: `409 CONFLICT`, `data.candidates` là mảng
`{ productId, skuCode, productName }` để người dùng chọn rồi gọi lại kèm `productId`.

### `data`

| Trường | Ý nghĩa |
| --- | --- |
| `lotNo`, `productId`, `skuCode`, `productName`, `uom` | Lô và sản phẩm |
| `mfgDate`, `expDate` | Ngày sản xuất, hạn dùng (theo dòng tồn, thiếu thì theo phiếu nhập) |
| `warehouseIds` | Kho được xem; `null` là mọi kho |
| `summary` | `totalReceived` (nhập GRN), `openingBalance` (số dư mở sổ), `totalAdjustedIn`, `totalSalesDispatched`, `totalTransferredOut`, `totalOtherOut`, `ledgerBalance` (tồn của lô theo sổ), `currentOnHand` (tồn thực tế), `currentReserved` |
| `origins[]` | Lô từ đâu tới, kiểu `Flow` (bên dưới): `RECEIPT` (phiếu nhập kèm đơn mua, ID nhà cung cấp), `OPENING_BALANCE` (tồn có trước sổ kho, không có chứng từ gốc), `ADJUSTMENT_IN` |
| `currentLocations[]` | Lô đang ở đâu: `stockItemId`, `warehouseId`, `warehouseCode`, `warehouseName`, `binLocationId`, `binLocationCode`, `quantity`, `reservedQuantity`, `availableQuantity`, `locked`, `lockedReason`, `expDate`, `expired`, `daysUntilExpiry` (chỉ dòng có số lượng khác 0) |
| `outflows[]` | Lô đã đi đâu, kiểu `Flow`: `TRANSFER_OUT` (kèm `transfer`), `SALES_DISPATCH` (số đơn bán), `ISSUE_OTHER`, `ADJUSTMENT_OUT` |
| `timeline[]` | Mọi biến động của lô theo thời gian: `movementId`, `occurredAt`, `movementType`, `movementTypeLabel`, `documentType`, `documentId`, `documentNumber`, `referenceType`, `referenceNumber`, `supplierId`, `warehouseId`, `warehouseCode`, `warehouseName`, `binLocationCode`, `counterpartWarehouseId`, `quantityIn`, `quantityOut`, `lotBalanceAfter`, `performedBy` |
| `tree` | Cây để vẽ (xem dưới) |
| `notes[]` | Các giới hạn dữ liệu bằng tiếng Việt, hiển thị nguyên văn dưới cây |
| `ledgerStartedAt`, `generatedAt` | Như các báo cáo khác |

`Flow` (một chứng từ, gộp các dòng sổ cùng chứng từ và cùng kho): `movementType`, `movementTypeLabel`, `occurredAt`,
`documentType`, `documentId`, `documentNumber`, `referenceType`, `referenceId`, `referenceNumber`, `supplierId`,
`warehouseId`, `warehouseCode`, `warehouseName`, `binLocationCodes[]`, `quantity`, `performedBy`, `transfer`
(chỉ với `TRANSFER_OUT`, còn lại `null`).

`transfer`: `transferId`, `transferNumber`, `status` (`IN_TRANSIT`, `COMPLETED`, `PARTIAL_RECEIVED`),
`destinationWarehouseId`, `destinationWarehouseCode`, `destinationWarehouseName`, `lotQuantityShipped` (số lượng của
lô này đã xuất theo phiếu), `itemReceivedQuantity`, `itemDamagedQuantity` (của cả dòng sản phẩm trên phiếu, có thể gồm
nhiều lô; `null` khi chưa nhận), `destinationBinLocationCode`, `receivedAt`, `receivedBy`, `lotTrackedAtDestination`
(luôn `false` hiện nay).

`tree` (nút `TraceNode`, lồng nhau qua `children`, hợp với `Tree` của Ant Design dùng `key`, `title`, `children`):
`key`, `nodeType`, `direction`, `title` (tiếng Việt, hiển thị được ngay), `quantity`, `occurredAt`, `documentType`,
`documentId`, `documentNumber`, `warehouseId`, `warehouseName`, `binLocationCode`, `children[]`.

- `direction`: `ROOT` (lô), `UPSTREAM` (nguồn gốc), `CURRENT` (đang ở), `DOWNSTREAM` (đã đi).
- `nodeType`: `LOT`; `GOODS_RECEIPT` -> con `PURCHASE_ORDER` -> con `SUPPLIER` (với nút `SUPPLIER`, `documentId` là ID
  nhà cung cấp); `OPENING_BALANCE`; `ADJUSTMENT_IN`; `LOCATION`; `TRANSFER` -> con `TRANSFER_DESTINATION`;
  `SALES_ORDER`; `OTHER_ISSUE`; `ADJUSTMENT_OUT`.
- Thứ tự con của gốc: nguồn gốc, vị trí hiện tại, nơi đã đi (theo thời gian).

### Giới hạn dữ liệu (đã nêu trong `notes`)

- Tên nhà cung cấp và khách hàng không lưu ở inventory-service, chưa có client sang order-service: trả ID nhà cung
  cấp, số đơn mua, số đơn bán. Màn hình có thể tra tên qua API đơn hàng hiện có.
- Xuất bán ghi theo số đơn bán (`SALES_ORDER`) vì sự kiện `order.dispatched` chưa mang mã phiếu xuất GDN.
- Phiếu điều chuyển chưa lưu số lô: hàng nhận tại kho đích được cộng vào dòng tồn không theo lô, nên chuỗi dừng ở
  nút `TRANSFER_DESTINATION`; không truy tiếp các lần xuất sau tại kho đích.
- Lô có trước khi có sổ kho xuất hiện dưới dạng `OPENING_BALANCE`, không có phiếu nhập gốc.

### Ví dụ

`GET /api/inventory/reports/lot-trace?lotNo=LOT-T`

```json
{
  "success": true, "code": "OK", "message": "Thành công",
  "data": {
    "lotNo": "LOT-T", "productId": 7101, "skuCode": "SKU-7101", "productName": "Sữa tươi 1L", "uom": "Thùng",
    "mfgDate": "2026-10-01", "expDate": "2027-10-01", "warehouseIds": null,
    "summary": { "totalReceived": 100.000, "openingBalance": 0.000, "totalAdjustedIn": 0.000,
      "totalSalesDispatched": 50.000, "totalTransferredOut": 30.000, "totalOtherOut": 0.000,
      "ledgerBalance": 20.000, "currentOnHand": 20.000, "currentReserved": 0.000 },
    "origins": [ { "movementType": "RECEIPT", "movementTypeLabel": "Nhập kho theo phiếu nhập",
      "occurredAt": "2026-11-05T03:15:00Z", "documentType": "GOODS_RECEIPT", "documentId": 9101,
      "documentNumber": "GRN-20261105-0001", "referenceType": "PURCHASE_ORDER", "referenceId": 811,
      "referenceNumber": "PO-20261101-0001", "supplierId": 17, "warehouseId": 1, "warehouseCode": "WH01",
      "warehouseName": "Kho Hà Nội", "binLocationCodes": ["WH01-ZA-R01-B01"], "quantity": 100.000,
      "performedBy": "thukho01", "transfer": null } ],
    "currentLocations": [ { "stockItemId": 55, "warehouseId": 1, "warehouseCode": "WH01", "warehouseName": "Kho Hà Nội",
      "binLocationId": 15, "binLocationCode": "WH01-ZA-R01-B01", "quantity": 20.000, "reservedQuantity": 0.000,
      "availableQuantity": 20.000, "locked": false, "lockedReason": null, "expDate": "2027-10-01",
      "expired": false, "daysUntilExpiry": 324 } ],
    "outflows": [
      { "movementType": "TRANSFER_OUT", "documentType": "STOCK_TRANSFER", "documentId": 12,
        "documentNumber": "TRF-20261106-0001", "warehouseId": 1, "quantity": 30.000,
        "transfer": { "transferId": 12, "transferNumber": "TRF-20261106-0001", "status": "COMPLETED",
          "destinationWarehouseId": 2, "destinationWarehouseCode": "WH02", "destinationWarehouseName": "Kho Đà Nẵng",
          "lotQuantityShipped": 30.000, "itemReceivedQuantity": 30.000, "itemDamagedQuantity": 0.000,
          "destinationBinLocationCode": "WH02-ZB-R01-B01", "receivedAt": "2026-11-07T02:00:00Z",
          "receivedBy": "quanly02", "lotTrackedAtDestination": false } },
      { "movementType": "SALES_DISPATCH", "documentType": "SALES_ORDER", "documentId": 601,
        "documentNumber": "SO-20261108-0601", "warehouseId": 1, "quantity": 50.000, "transfer": null }
    ],
    "timeline": [ "... 3 sự kiện với lotBalanceAfter 100.000, 70.000, 20.000 ..." ],
    "tree": { "key": "lot:LOT-T:7101", "nodeType": "LOT", "direction": "ROOT",
      "title": "Lô LOT-T (SKU-7101 - Sữa tươi 1L)", "quantity": 20.000,
      "children": [
        { "key": "origin:1", "nodeType": "GOODS_RECEIPT", "direction": "UPSTREAM",
          "title": "Phiếu nhập GRN-20261105-0001 vào Kho Hà Nội", "quantity": 100.000,
          "children": [ { "key": "po:1", "nodeType": "PURCHASE_ORDER", "title": "Đơn mua PO-20261101-0001",
            "children": [ { "key": "supplier:17:1", "nodeType": "SUPPLIER", "title": "Nhà cung cấp ID 17", "children": [] } ] } ] },
        { "key": "location:55", "nodeType": "LOCATION", "direction": "CURRENT",
          "title": "Đang ở WH01-ZA-R01-B01 - Kho Hà Nội", "quantity": 20.000, "children": [] },
        { "key": "outflow:1", "nodeType": "TRANSFER", "direction": "DOWNSTREAM",
          "title": "Điều chuyển TRF-20261106-0001 từ Kho Hà Nội", "quantity": 30.000,
          "children": [ { "key": "transfer-dest:1", "nodeType": "TRANSFER_DESTINATION",
            "title": "Kho đích Kho Đà Nẵng: đã nhận 30.000 (cả dòng sản phẩm của phiếu); số lô không được giữ tại kho đích",
            "children": [] } ] },
        { "key": "outflow:2", "nodeType": "SALES_ORDER", "direction": "DOWNSTREAM",
          "title": "Xuất bán theo đơn SO-20261108-0601 từ Kho Hà Nội", "quantity": 50.000, "children": [] }
      ] },
    "notes": [ "Tên nhà cung cấp và khách hàng không lưu ở dịch vụ kho; ...", "Xuất bán ghi theo số đơn bán ...",
      "Phiếu điều chuyển chưa lưu số lô: ..." ],
    "ledgerStartedAt": null, "generatedAt": "2026-11-11T09:00:00Z"
  }
}
```

(Ví dụ lược bớt một số trường cho gọn; API luôn trả đủ trường.)

---

## 4. Xuất Excel (WMS-17.3)

Hai endpoint trả file `.xlsx` do backend lập bằng Apache POI (`SXSSFWorkbook`, ghi theo luồng nên 20000 dòng không làm
đầy bộ nhớ). Cùng bộ lọc, cùng kiểm tra tham số, vai trò, phạm vi kho và doanh nghiệp như API xem (xem phần 0); không
có `page`, `size`.

| Endpoint | Tham số | Tên file |
| --- | --- | --- |
| `GET /api/inventory/reports/in-out-balance/export` | `fromDate`, `toDate` (bắt buộc), `warehouseId`, `categoryId`, `productId`, `keyword`, `groupBy`, `sort`, `direction` | `bao-cao-xuat-nhap-ton_{fromDate}_{toDate}.xlsx` |
| `GET /api/inventory/reports/stock-card/export` | `productId`, `warehouseId`, `fromDate`, `toDate` (bắt buộc) | `the-kho_{SKU}_{mã kho}_{fromDate}_{toDate}.xlsx` (SKU, mã kho chỉ giữ chữ, số, `.`, `_`, `-`) |

Phản hồi 200:

```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="bao-cao-xuat-nhap-ton_2026-11-01_2026-11-12.xlsx"; filename*=UTF-8''bao-cao-xuat-nhap-ton_2026-11-01_2026-11-12.xlsx
Cache-Control: no-store
```

Tên file chỉ gồm ký tự ASCII nên `filename` và `filename*` giống nhau. API Gateway cho phép trình duyệt đọc
`Content-Disposition` (`exposed-headers` của CORS). Lỗi (400, 401, 403, 404, 503) vẫn là JSON `ApiResponse` như API xem,
vì file chỉ bắt đầu ghi sau khi mọi kiểm tra đã qua; frontend đọc body lỗi dạng Blob rồi `JSON.parse`.

### Bố cục sheet

Cả hai sheet có cùng cấu trúc: dòng 1 tiêu đề báo cáo (ô gộp hết chiều ngang), dòng 2 điều kiện lọc (ô gộp), dòng 3
tiêu đề cột (chữ đậm, cố định khi cuộn), các dòng dữ liệu, dòng tổng, một dòng trống rồi các ghi chú (ô gộp). Số lượng là
ô số định dạng `#,##0.000`; ngày là ô ngày `dd/MM/yyyy`; số lượng `null` để ô trống, không ghi 0.

- Sheet `Xuat_Nhap_Ton`: `STT`, `Mã SKU`, `Tên sản phẩm`, `ĐVT`, `Kho` (chỉ khi `groupBy = PRODUCT_WAREHOUSE`), `Tồn đầu kỳ`,
  `Nhập trong kỳ`, `Xuất trong kỳ`, `Tồn cuối kỳ`. Dòng `Tổng cộng` lấy từ `totals` của service (không cộng lại trong
  bộ xuất). Cột giá trị tiền không có vì luôn `null`; lý do nằm ở ghi chú `Giá trị tiền: ...`.
- Sheet `The_kho`: `STT`, `Thời điểm` (ô ngày giờ `dd/MM/yyyy HH:mm`, giờ Việt Nam), `Loại biến động`, `Số chứng từ`,
  `Chứng từ tham chiếu`, `Số lô`, `Hạn dùng` (ô ngày), `Vị trí`, `Nhập`, `Xuất`, `Tồn`, `Người thực hiện`, `Diễn giải`.
  Sau dòng `Tồn đầu kỳ` là các biến động, rồi `Cộng phát sinh trong kỳ` (tổng nhập, xuất) và `Tồn cuối kỳ`.
- Ghi chú cuối sheet: `Ghi chú số liệu: {dataNote}` (khi có), `Giá trị tiền: {valuationNote}` (báo cáo Xuất - Nhập -
  Tồn), `Lập lúc: dd/MM/yyyy HH:mm` (giờ Việt Nam).

### Giới hạn 20000 dòng

Quá 20000 dòng khớp thì file chứa 20000 dòng đầu (theo thứ tự của API xem) và có ghi chú "Báo cáo có N dòng, vượt giới
hạn 20000 dòng ... file chỉ gồm 20000 dòng đầu". Dòng tổng, tồn đầu kỳ, tồn cuối kỳ vẫn tính trên toàn bộ dòng khớp.
Không còn trả `400` khi vượt giới hạn.

### Dành cho code Java

`StockReportService.getInOutBalanceForExport(InOutBalanceQuery)` và `getStockCardForExport(...)` trả toàn bộ dòng (tối đa
20000) trong `rows.content` / `lines.content`; `totalElements` là tổng số dòng khớp thật nên lớn hơn số dòng trả về khi
bị cắt. Bộ lập file: `StockReportExportService` (gọi service, dựng điều kiện lọc, đặt tên file) và `StockReportExcelWriter`
(ghi POI).

## 5. Ghi chú cho người làm phần kiểm kê, điều chỉnh

Khi áp chênh lệch kiểm kê vào `stock_items.quantity`, gọi đúng một dòng trong cùng transaction:
`stockMovementRecorder.recordAdjustment(stockItem, delta, "STOCKTAKE", phieuId, soPhieu, lyDo, nguoiDuyet)`
(`delta > 0` điều chỉnh tăng, `delta < 0` điều chỉnh giảm). Không ghi thì báo cáo Xuất - Nhập - Tồn sẽ lệch với tồn thực.

## 6. Triển khai

- Migration inventory-service **V13** (`V13__create_stock_movements.sql`) tạo `stock_movements`, `stock_ledger_starts`
  và số dư mở sổ. V11, V12 do nhóm khác giữ: nếu V13 lên máy chủ trước V11/V12 thì khi thêm V11/V12 Flyway sẽ báo
  migration lệch thứ tự; cần merge V11, V12 trước hoặc bật `spring.flyway.out-of-order=true` một lần.
- Không có biến môi trường mới. Lọc `categoryId`/`keyword` và bổ sung tên sản phẩm gọi product-service qua Feign sẵn có.
