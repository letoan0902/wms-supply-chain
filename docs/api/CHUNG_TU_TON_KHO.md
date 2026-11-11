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
