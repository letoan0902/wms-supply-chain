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
| Không gọi được inventory-service hoặc hết thời gian chờ | `503 SERVICE_UNAVAILABLE`, đơn không đổi; bấm xác nhận lại là an toàn (inventory-service không giữ hai lần cho cùng đơn) |

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

Phiếu lập ở DRAFT hoặc PICKING không gọi inventory-service, như trước.
