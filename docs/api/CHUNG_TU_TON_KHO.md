# Hợp đồng API chứng từ làm thay đổi tồn kho sau WMS-20.4

WMS-20.4 (Jira TASK-126) sửa các chỗ order-service và inventory-service có thể lệch nhau về tồn kho. Tài liệu này ghi
những thay đổi giao diện phải biết ở các API phiếu nhập, đơn bán và phiếu xuất. Mọi API đi qua gateway, bọc trong
`ApiResponse` (`success`, `code`, `message`, `data`, `timestamp`); giao diện hiển thị `message` của lỗi cho người dùng.
Cơ chế phía sau ở [MESSAGING.md](../MESSAGING.md) mục 7.4.

## 1. Lập phiếu nhập kho `POST /api/orders/goods-receipt-notes`

Vai trò không đổi (ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF), thân yêu cầu không đổi.

**Header mới, tùy chọn: `Idempotency-Key`.** Chuỗi 1 đến 100 ký tự do giao diện sinh (ví dụ UUID), duy nhất trong
doanh nghiệp. Form lập phiếu nhập sinh **một khóa mỗi lần mở form** và gửi cùng khóa đó cho mọi lần bấm Lưu và mọi
lần gửi lại sau lỗi mạng hoặc hết thời gian chờ; mở form mới (lập phiếu khác) thì sinh khóa mới.

| Tình huống | Kết quả |
| --- | --- |
| Lần đầu với khóa (hoặc không gửi khóa) | `201`, phiếu mới, như trước |
| Gửi lại cùng khóa cho cùng đơn mua | `200`, `data` là phiếu đã lập lần đầu, header `Idempotent-Replayed: true`; không tạo phiếu, hóa đơn, công nợ, không cộng tồn lần nữa |
| Cùng khóa nhưng đơn mua khác | `409 CONFLICT`, thông báo nêu mã phiếu và mã đơn mua đã dùng khóa |
| Khóa dài quá 100 ký tự | `400 BAD_REQUEST` |

Không gửi khóa thì hai request giống nhau vẫn tạo hai phiếu (mỗi phiếu hợp lệ nếu tổng nhận không vượt số đặt), nên
giao diện nên luôn gửi khóa. Quy tắc số lượng giữ nguyên: số lượng đạt cộng dồn của một dòng không vượt số lượng đặt
(không có dung sai nhận vượt), lập phiếu đồng thời cho cùng một đơn mua được xếp hàng bằng khóa dòng đơn mua.

**Lỗi mới:** `409 CONFLICT` khi có dòng (số lượng đạt lớn hơn 0) cộng tồn vào ô đang đóng băng kiểm kê, kể cả dòng
không chọn ô mà kho cất mặc định vào ô đầu tiên đang đóng băng. Thông báo nêu mã ô và mã đợt kiểm kê; `data` là mảng
`sessionId`, `sessionCode`, `binLocationId`, `binLocationCode`, `productId`. Phiếu, hóa đơn mua, công nợ không được
ghi. Kho chưa có ô nào nhận `400` với thông báo của inventory-service. Không gọi được inventory-service: `503
SERVICE_UNAVAILABLE`, không ghi gì, bấm lại được (cùng `Idempotency-Key`).

## 2. Xác nhận đơn bán

Áp dụng cho `POST /api/orders/sales/{id}/confirm`, `POST /api/orders/sales` khi đơn được xác nhận ngay (`status`
CONFIRMED hoặc bỏ trống) và `PUT /api/orders/sales/{id}` khi đơn BACKORDER đủ hàng. Đường dẫn, vai trò, thân yêu cầu
và phản hồi không đổi; giữ chỗ nay là một phần của xác nhận nên đơn CONFIRMED luôn đã được giữ đủ hàng.

| Tình huống | Kết quả |
| --- | --- |
| `confirm` khi đơn khác vừa giữ mất phần tồn (ATP đọc lúc trước vẫn đủ) | `400 BAD_REQUEST` "Không thể xác nhận đơn do thiếu tồn khả dụng: Không đủ tồn khả dụng để giữ chỗ cho đơn ...: SKU ... thiếu ..."; `data` là mảng `productId`, `skuCode`, `requestedQuantity`, `availableQuantity`, `shortageQuantity`; đơn giữ nguyên DRAFT/BACKORDER |
| Bấm `confirm` hai lần cùng lúc | Một lần `200`, lần sau `400` (đơn đã CONFIRMED) |
| Lập đơn xác nhận ngay, không giữ đủ, `allowBackorder = true` | `201`, `status` BACKORDER, `backorderReason` là thông báo thiếu hàng |
| Lập đơn xác nhận ngay, không giữ đủ, `allowBackorder = false` | `400`, đơn không được lưu |
| Sửa đơn BACKORDER, ATP đủ nhưng không giữ đủ | `200`, đơn vẫn BACKORDER, `backorderReason` cập nhật |
| Không gọi được inventory-service hoặc hết thời gian chờ | `503 SERVICE_UNAVAILABLE`, đơn không đổi; bấm xác nhận lại là an toàn: trước khi giữ, order-service giải phóng giữ chỗ còn sót của đơn rồi giữ lại theo dòng hàng hiện tại (kể cả khi đơn đã được sửa) |
| inventory-service vẫn báo đơn đã có giữ chỗ sau khi đã giải phóng | `503 SERVICE_UNAVAILABLE` "Dịch vụ kho chưa giải phóng giữ chỗ cũ của đơn ...", đơn không đổi, bấm lại sau |
| Đơn chưa chọn kho (`warehouseId` trống): lập đơn với `status` CONFIRMED hoặc bỏ trống, `confirm`, sửa đơn BACKORDER | `400 VALIDATION_ERROR` "Phải chọn kho xuất hàng trước khi xác nhận đơn bán", `data` là `{"warehouseId": "..."}`; không gọi inventory-service. Đơn DRAFT vẫn lưu được khi chưa chọn kho |

Hàng ở lô khóa, lô hết hạn và ô đang đóng băng kiểm kê không được giữ, nên có thể thiếu dù ATP trên màn hình đủ.

## 3. Phiếu xuất kho

Áp dụng cho `POST /api/orders/goods-delivery-notes` khi `status` là COMPLETED hoặc bỏ trống, và
`POST /api/orders/goods-delivery-notes/{id}/dispatch`. Đường dẫn, vai trò, thân yêu cầu và phản hồi thành công không
đổi; phiếu chỉ COMPLETED khi tồn đã được trừ thật.

| Tình huống | Kết quả |
| --- | --- |
| Trừ tồn thành công | Như trước: `201` (lập) hoặc `200` (`dispatch`), `status` COMPLETED |
| Không đủ tồn thực tế, lô khóa, kho ngừng hoạt động | Mã và thông báo của inventory-service (thường `400 BAD_REQUEST` "Không đủ tồn kho để xuất..."). Lập phiếu: không có phiếu nào được lưu. `dispatch`: phiếu giữ nguyên DRAFT/PICKING |
| Xuất từ ô đang đóng băng kiểm kê | `409 CONFLICT` với `data` của đợt kiểm kê; phiếu không hoàn tất |
| Lập phiếu mà hết thời gian chờ hoặc mất kết nối tới inventory-service | `503 SERVICE_UNAVAILABLE`, thông báo nêu mã phiếu: phiếu **đã được lưu ở PICKING**. Giao diện tải lại danh sách phiếu của đơn và cho người dùng bấm Xuất kho (`dispatch`) trên phiếu đó; không lập phiếu mới (số lượng của phiếu PICKING đã tính vào phần đã xuất của đơn) |
| `dispatch` mà hết thời gian chờ | `503`, phiếu giữ nguyên; bấm lại an toàn, tồn chỉ bị trừ một lần |
| `dispatch` khi đơn bán đã hủy (CANCELLED) hoặc còn DRAFT | `400 BAD_REQUEST` "Đơn bán hàng ... đã bị hủy, không thể xuất kho phiếu ..."; không gọi inventory-service, phiếu và đơn giữ nguyên. Đơn CONFIRMED, BACKORDER, COMPLETED xuất được |

Phiếu lập ở DRAFT hoặc PICKING không gọi inventory-service, như trước. `dispatch` khóa đơn bán rồi mới khóa phiếu
(cùng thứ tự với lập phiếu và hủy đơn).

## 4. Hủy, sửa, xóa đơn bán

| Thao tác | Ảnh hưởng tới tồn và phiếu xuất |
| --- | --- |
| `POST /api/orders/sales/{id}/cancel` | Giải phóng giữ chỗ như trước. Phiếu xuất DRAFT, PICKING của đơn chuyển CANCELLED, số lượng của chúng được trừ khỏi `deliveredQuantity` của dòng đơn; phiếu COMPLETED giữ nguyên. Phản hồi không đổi |
| `PUT /api/orders/sales/{id}` (đơn DRAFT, BACKORDER) | Giải phóng giữ chỗ còn sót của một lần xác nhận hết thời gian chờ trước khi đổi dòng hàng. Phản hồi không đổi |
| `DELETE /api/orders/sales/{id}` (đơn DRAFT, CANCELLED) | Giải phóng giữ chỗ còn sót của đơn. Phản hồi không đổi |

Giải phóng giữ chỗ gọi `POST /api/inventory/stock/reservations/release`, idempotent; lỗi khi gọi chỉ ghi log, không
chặn thao tác.

## 5. Dòng đơn mua phải có mã hàng

`POST /api/orders/purchase` và `PUT /api/orders/purchase/{id}`: `items[].productId` bắt buộc (trước đây tùy chọn). Thiếu
thì `400 VALIDATION_ERROR`, `data` là `{"items[0].productId": "ID sản phẩm không được để trống"}`. Dòng không có mã hàng
trước đây vẫn nhận hàng được, ghi hóa đơn mua và công nợ nhưng inventory-service không cộng được tồn. Dòng đơn bán vốn
đã bắt buộc `productId`.

Số lượng trên dòng đơn mua, đơn bán và phiếu nhập, phiếu xuất được hiểu theo đơn vị tính gốc của hàng hóa; `uom` chỉ
là nhãn hiển thị, không có quy đổi (xem `docs/TEST_PLAN.md` mục 2.5).
