# Hợp đồng API kiểm kê kho (WMS-14.1, WMS-14.2) cho màn hình WMS-14.3 và WMS-14.4

Dịch vụ: inventory-service, đi qua api-gateway với tiền tố `/api/inventory`. Mọi phản hồi theo `ApiResponse`
(`success`, `code`, `message`, `data`, `timestamp`); danh sách theo `PageResponse` (`content`, `page`, `size`,
`totalElements`, `totalPages`, `first`, `last`, trang bắt đầu từ 0, `size` tối đa 100). Số lượng là số thập phân
tối đa 3 chữ số sau dấu phẩy (`DECIMAL(18,3)`). Thời gian ISO 8601 UTC, ngày dạng `yyyy-MM-dd`.
Mọi API kiểm kê bắt buộc đăng nhập (thiếu người dùng trả 401).

## 1. Trạng thái

Đợt kiểm kê (`status`):

| Mã | Ý nghĩa | Vị trí đóng băng |
|----|---------|------------------|
| `DRAFT` | Mới lập kế hoạch | Không |
| `COUNTING` | Đang đếm mù | Có |
| `COUNTED` | Đã kết thúc đếm, đã tính chênh lệch, chờ Quản lý kho duyệt | Có |
| `PENDING_ACCOUNTING` | Quản lý kho đã duyệt, phiếu điều chỉnh chờ Kế toán duyệt | Có |
| `COMPLETED` | Đã duyệt, tồn kho đã điều chỉnh (nếu có lệch) | Không |
| `REJECTED` | Bị từ chối, tồn kho giữ nguyên | Không |
| `CANCELLED` | Đã hủy, tồn kho giữ nguyên | Không |

Phiếu điều chỉnh (`adjustmentStatus`): `PENDING_APPROVAL`, `APPROVED`, `REJECTED`.

Phạm vi (`scopeType`): `WAREHOUSE` (toàn kho), `ZONES` (theo khu), `LOCATIONS` (theo danh sách vị trí);
`productIds` (không bắt buộc) giới hạn sản phẩm.

## 2. Phân quyền

| Thao tác | Vai trò |
|----------|---------|
| Lập, bắt đầu, kết thúc đếm, đếm lại, ghi lý do lệch, hủy, Quản lý kho duyệt | `ADMIN`, `WAREHOUSE_MANAGER` phụ trách kho |
| Đếm (quét, nhập tay) | `ADMIN`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF` của kho |
| Xem đợt, danh sách dòng đếm (không có số hệ thống) | `ADMIN`, `ACCOUNTANT`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF` của kho |
| Xem chênh lệch, phiếu điều chỉnh | `ADMIN`, `ACCOUNTANT`, `WAREHOUSE_MANAGER` của kho (nhân viên kho bị 403) |
| Kế toán duyệt phiếu điều chỉnh | `ACCOUNTANT`, `ADMIN`; phải khác người đã duyệt cấp Quản lý kho |
| Từ chối | ở `COUNTED`: `ADMIN`, `WAREHOUSE_MANAGER` của kho; ở `PENDING_ACCOUNTING`: thêm `ACCOUNTANT` |

## 3. Trình tự gọi

1. `POST /api/inventory/stocktakes` lập đợt (DRAFT).
2. `POST /api/inventory/stocktakes/{id}/start` đóng băng và chụp tồn (COUNTING).
3. Màn hình đếm mù (WMS-14.3): `GET /{id}` lấy danh sách vị trí, `GET /{id}/count-lines` lấy dòng cần đếm,
   mỗi lần quét gọi `POST /{id}/counts` với `mode=ADD`, `quantity=1` (hoặc số trên thùng); sửa tay gọi `mode=SET`.
4. `POST /{id}/finish` kết thúc đếm (COUNTED). Còn dòng chưa đếm thì nhận 409 kèm `data.uncountedLines`;
   hỏi xác nhận rồi gọi lại với `confirmUncountedAsZero=true` để tính các dòng đó bằng 0.
5. Màn hình đối soát (WMS-14.4): `GET /{id}/variances` (tổng hợp và dòng chênh lệch), có thể
   `POST /{id}/recount` (quay lại COUNTING cho các dòng chọn), `PUT /{id}/lines/{lineId}/reason` ghi lý do.
6. Quản lý kho `POST /{id}/approve`: không có lệch thì COMPLETED ngay; có lệch thì sinh phiếu điều chỉnh,
   đợt sang PENDING_ACCOUNTING.
7. Kế toán `POST /{id}/adjustment/approve`: cập nhật tồn trong một transaction, mở đóng băng, đợt COMPLETED.
8. Bất cứ lúc nào trước khi xong: `POST /{id}/cancel` (DRAFT, COUNTING, COUNTED) hoặc `POST /{id}/reject`
   (COUNTED, PENDING_ACCOUNTING; bắt buộc lý do).

## 4. Endpoint

### 4.1 Đợt kiểm kê (WMS-14.1)

`POST /api/inventory/stocktakes` (201)
```json
{ "warehouseId": 1, "scopeType": "ZONES", "zones": ["ZA"], "binLocationIds": [], "productIds": [101],
  "plannedDate": "2026-11-14", "notes": "Kiểm kê cuối tháng" }
```
`zones` bắt buộc khi ZONES, `binLocationIds` bắt buộc khi LOCATIONS (vị trí phải thuộc kho). Mã đợt tự sinh
`ST-yyyyMM-NN`, duy nhất trong doanh nghiệp.

Phản hồi đợt (`StocktakeSessionResponse`): `id`, `code`, `warehouseId`, `warehouseCode`, `warehouseName`,
`status`, `scopeType`, `zones[]`, `productIds[]`, `plannedDate`, `notes`, `totalLocations`, `totalLines`,
`countedLines`, `uncountedLines`, `startedBy`, `startedAt`, `finishedBy`, `finishedAt`, `managerApprovedBy`,
`managerApprovedAt`, `approvedBy`, `approvedAt`, `closedBy`, `closedAt`, `closeReason`, `adjustmentId`,
`adjustmentCode`, `adjustmentStatus`, `createdBy`, `createdAt`, `updatedAt`,
`locations[]` (`binLocationId`, `binLocationCode`, `zone`, `rack`, `bin`, `frozen`; chỉ có ở API chi tiết,
null ở danh sách; với WAREHOUSE và ZONES danh sách vị trí chốt khi bắt đầu).

`GET /api/inventory/stocktakes?warehouseId=&status=&fromDate=&toDate=&keyword=&page=0&size=20`
lọc theo kho, trạng thái, khoảng `plannedDate`, mã đợt; Quản lý và Nhân viên kho chỉ thấy kho mình.

`GET /api/inventory/stocktakes/{id}` chi tiết.

`POST /api/inventory/stocktakes/{id}/start` DRAFT sang COUNTING: chốt vị trí, đóng băng, chụp tồn theo
(vị trí, sản phẩm, số lô) gồm tồn thực tế và giữ chỗ (bỏ qua bộ khóa có cả hai bằng 0).

`POST /api/inventory/stocktakes/{id}/counts` ghi số đếm mù
```json
{ "binLocationCode": "WH01-ZA-R01-B01", "productId": 101, "lotNo": "LOT-01", "quantity": 1, "mode": "ADD" }
```
Hoặc `{ "lineId": 55, "quantity": 12.5, "mode": "SET" }`. `binLocationId` dùng thay `binLocationCode`.
`mode`: `ADD` (mặc định, cộng dồn, `quantity` lớn hơn 0) hoặc `SET` (ghi đè, từ 0). Không có dòng khớp
(vị trí, sản phẩm, lô) thì tạo dòng hàng phát sinh `unexpected=true`. Vị trí phải thuộc phạm vi đợt, sản phẩm
thuộc phạm vi sản phẩm (nếu có). Màn hình tự tra sản phẩm theo mã vạch qua product-service để lấy `productId`.

Phản hồi dòng đếm (`StocktakeCountLineResponse`, KHÔNG có số hệ thống): `lineId`, `binLocationId`,
`binLocationCode`, `zone`, `productId`, `lotNo`, `counted`, `countedQuantity`, `countTimes`, `lastCountedBy`,
`lastCountedAt`, `unexpected`, `recountRequested`.

`GET /api/inventory/stocktakes/{id}/count-lines?counted=&binCode=&productId=&page=0&size=50`
danh sách dòng đếm (`counted=false` để xem dòng còn thiếu), sắp xếp theo mã vị trí.

`POST /api/inventory/stocktakes/{id}/cancel` body `{ "reason": "..." }` (không bắt buộc).

### 4.2 Chênh lệch và duyệt (WMS-14.2)

`POST /api/inventory/stocktakes/{id}/finish` body `{ "confirmUncountedAsZero": false }`: COUNTING sang COUNTED,
tính `varianceQuantity = countedQuantity - systemQuantity` cho mọi dòng.

`GET /api/inventory/stocktakes/{id}/variances?onlyDifferences=true&binCode=&productId=&page=0&size=50`
(chỉ khi đã kết thúc đếm):
```json
{ "summary": { "totalLines": 120, "countedLines": 120, "linesWithVariance": 4, "increaseLines": 1,
               "decreaseLines": 3, "totalIncreaseQuantity": 2.000, "totalDecreaseQuantity": 5.500,
               "netVarianceQuantity": -3.500, "reservedConflictLines": 0 },
  "lines": { "content": [ { "lineId": 55, "binLocationId": 7, "binLocationCode": "WH01-ZA-R01-B01",
               "zone": "ZA", "productId": 101, "lotNo": "LOT-01", "systemQuantity": 10.000,
               "systemReservedQuantity": 2.000, "countedQuantity": 9.995, "varianceQuantity": -0.005,
               "counted": true, "unexpected": false, "recountRequested": false, "reservedConflict": false,
               "countTimes": 3, "lastCountedBy": "staff01", "lastCountedAt": "...", "reason": null } ],
             "page": 0, "size": 50, "totalElements": 4, "totalPages": 1, "first": true, "last": true } }
```
`reservedConflict=true` khi số đếm nhỏ hơn số đang giữ chỗ cho đơn: không duyệt được cho tới khi xử lý.
Giá trị tiền của chênh lệch không tính ở API này vì inventory-service chưa có giá vốn sản phẩm; màn hình có thể
nhân với giá lấy từ product-service nếu cần.

`POST /api/inventory/stocktakes/{id}/recount` body `{ "lineIds": [55, 56] }`: xóa số đếm các dòng đó,
đánh dấu `recountRequested`, đợt quay lại COUNTING (vẫn đóng băng); sau đó đếm lại và gọi `finish`.

`PUT /api/inventory/stocktakes/{id}/lines/{lineId}/reason` body `{ "reason": "Hàng vỡ" }` (ở COUNTED).

`POST /api/inventory/stocktakes/{id}/approve` body `{ "notes": "..." }` (không bắt buộc): Quản lý kho duyệt.

`POST /api/inventory/stocktakes/{id}/adjustment/approve`: Kế toán duyệt phiếu điều chỉnh và cập nhật tồn.

`POST /api/inventory/stocktakes/{id}/reject` body `{ "reason": "..." }` (bắt buộc lý do).

`GET /api/inventory/stock-adjustments?warehouseId=&status=&fromDate=&toDate=&keyword=&page=0&size=20`
và `GET /api/inventory/stock-adjustments/{id}`: phiếu điều chỉnh (`id`, `code` dạng `ADJ-yyyyMM-NN`,
`sessionId`, `sessionCode`, `warehouseId`, `warehouseCode`, `warehouseName`, `status`, `totalIncreaseQuantity`,
`totalDecreaseQuantity`, `totalLines`, `notes`, `requestedBy`, `requestedAt`, `approvedBy`, `approvedAt`, `rejectedBy`,
`rejectedAt`, `rejectReason`, `createdAt`, `lines[]` gồm `id`, `stocktakeLineId`, `binLocationId`,
`binLocationCode`, `productId`, `lotNo`, `systemQuantity`, `countedQuantity`, `differenceQuantity`,
`quantityBefore`, `quantityAfter` (hai trường sau có giá trị khi đã duyệt), `reason`; `lines` null ở danh sách).
Lọc ngày theo ngày tạo phiếu.

## 5. Mã lỗi

| HTTP | `code` | Khi nào |
|------|--------|---------|
| 400 | `VALIDATION_ERROR`, `BAD_REQUEST` | Thiếu trường, số âm, quá 3 chữ số thập phân, ADD bằng 0, vị trí hoặc sản phẩm ngoài phạm vi, khu không tồn tại, thiếu lý do từ chối |
| 401 | `UNAUTHORIZED` | Thiếu người dùng |
| 403 | `FORBIDDEN` | Sai vai trò hoặc không phụ trách kho; Kế toán duyệt trùng người đã duyệt cấp Quản lý |
| 404 | `NOT_FOUND` | Không có đợt, dòng, phiếu (kể cả của doanh nghiệp khác) |
| 409 | `CONFLICT` | Sai trạng thái (bấm duyệt hai lần, đếm khi đã kết thúc, xem chênh lệch khi chưa kết thúc đếm); vị trí đang thuộc đợt khác (`data`: `sessionId`, `sessionCode`, `binLocationId`, `binLocationCode`, `productId`); còn dòng chưa đếm (`data.uncountedLines`); tồn sau điều chỉnh nhỏ hơn giữ chỗ hoặc âm (`data` là mảng `lineId`, `binLocationCode`, `productId`, `lotNo`, `newQuantity`, `reservedQuantity`) |

Khi đợt đang kiểm, các nghiệp vụ khác chạm vào vị trí đóng băng nhận 409 `CONFLICT` với thông báo
"Vị trí [mã] đang đóng băng để kiểm kê (đợt [mã đợt]), không thể thực hiện thao tác ...!" và `data` như trên:
nhập kho vào ô, xuất kho chỉ định ô, duyệt xuất điều chuyển từ ô, nhận hàng điều chuyển vào ô. Giữ chỗ cho đơn
mới và xuất kho không chỉ định ô thì bỏ qua các ô đang đóng băng và lấy hàng ở ô khác. Màn hình khác nên hiển thị
`message` của lỗi 409 cho người dùng; tag đỏ "Khóa kiểm kê" dùng `locations[].frozen` của đợt.

Gợi ý giao diện theo SRS: duyệt, từ chối, hủy đều có hộp thoại xác nhận lần hai; từ chối và điều chỉnh có ô lý do.
Màn hình đếm mù không gọi `/variances` (nhân viên kho nhận 403).

## 6. Quy tắc nghiệp vụ đã chọn

- Mỗi vị trí chỉ thuộc một đợt đang đóng băng tại một thời điểm (ràng buộc duy nhất trong CSDL).
- Dòng chưa đếm: chặn kết thúc đếm cho tới khi xác nhận tính bằng 0 (`confirmUncountedAsZero=true`).
- Duyệt hai cấp theo SRS: Quản lý kho rồi Kế toán, hai người khác nhau; không có lệch thì chỉ cần Quản lý kho.
- Điều chỉnh tồn: tồn mới = tồn hiện tại + chênh lệch, khóa bi quan các dòng tồn, một transaction; không cho tồn
  âm và không cho tồn mới nhỏ hơn số đang giữ chỗ (409, cần hủy hoặc đổi giữ chỗ của đơn trước, hoặc đếm lại).
  Giữ chỗ không bị thay đổi bởi điều chỉnh. Hàng phát sinh tạo dòng tồn mới (và dòng lô nếu có số lô).
- Duyệt hai lần hoặc đồng thời chỉ áp dụng một lần (khóa bi quan đợt kiểm kê, phiếu duy nhất theo đợt).
- Sau khi Kế toán duyệt: phát `inventory.updated` cho từng sản phẩm, `reason = ADJUSTMENT`,
  `referenceNumber` là mã phiếu, header `x-wms-company-id`.
