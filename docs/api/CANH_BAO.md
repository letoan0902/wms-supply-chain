# Hợp đồng API cảnh báo tồn kho cho WMS-15.3 và WMS-15.4

Nhánh backend: `t-15` (commit `9b45311` WMS-15.1, `9ea3408` WMS-15.2). Mọi API đi qua gateway, bọc trong `ApiResponse` (`success`, `code`, `message`, `data`, `timestamp`). Doanh nghiệp lấy từ JWT, phạm vi kho như các API kho khác: `ADMIN`, `ACCOUNTANT` thấy mọi kho của doanh nghiệp; vai trò khác chỉ thấy kho được phân công.

## 1. Bốn loại cảnh báo và màu thẻ

| `kind` | Ý nghĩa | `severity` | Màu thẻ gợi ý | Nhãn gợi ý |
| --- | --- | --- | --- | --- |
| `EXPIRED` | Lô còn hàng, hạn dùng nhỏ hơn hoặc bằng hôm nay | `CRITICAL` | đỏ | HẾT HẠN |
| `NEAR_EXPIRY` | Lô còn hàng, hạn dùng trong 30 ngày tới | `WARNING` | vàng viền cam | CẬN DATE |
| `LOW_STOCK` | Tồn dùng được nhỏ hơn hoặc bằng mức an toàn | `CRITICAL` | đỏ | CHÁY HÀNG |
| `LOW_STOCK` | Tồn dùng được nhỏ hơn hoặc bằng điểm đặt hàng lại | `WARNING` | vàng viền cam | SẮP HẾT |
| `DEAD_STOCK` | Còn tồn, không biến động từ 90 ngày trở lên | `INFO` | xanh dương | TỒN LÂU |

"Hôm nay" tính theo giờ Việt Nam. Ngưỡng 30 và 90 ngày trả về trong `GET /summary` (`nearExpiryDays`, `deadStockDays`), giao diện không tự viết cứng.

## 2. `GET /api/inventory/alerts` (bảng tồn kho, WMS-15.3)

Tính trực tiếp từ tồn hiện tại mỗi lần gọi, không phải chờ tác vụ 00:00.

Tham số (đều không bắt buộc): `kind` (một trong bốn giá trị trên), `severity` (`CRITICAL`, `WARNING`, `INFO`), `warehouseId`, `productId`, `keyword` (tìm trong SKU, tên sản phẩm, số lô, mã ô kệ, mã và tên kho), `page` (từ 0), `size` (mặc định 20, tối đa 100). Thứ tự cố định: mức nghiêm trọng, rồi loại (`EXPIRED`, `LOW_STOCK`, `NEAR_EXPIRY`, `DEAD_STOCK`), rồi hạn gần trước; tham số `sort` bị bỏ qua.

`data` là trang chuẩn: `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`. Mỗi phần tử:

| Trường | Kiểu | Ghi chú |
| --- | --- | --- |
| `id` | string | Khóa ổn định, dùng làm `rowKey`: `EXPIRED:15`, `NEAR_EXPIRY:15` (theo dòng tồn), `LOW_STOCK:2:100`, `DEAD_STOCK:2:100` (theo kho và sản phẩm) |
| `kind`, `severity` | string | Như mục 1 |
| `warehouseId`, `warehouseCode`, `warehouseName` | number, string, string | |
| `productId` | number | |
| `skuCode`, `productName` | string hoặc null | null khi backend không đọc được product-service |
| `stockItemId` | number hoặc null | Có với `EXPIRED`, `NEAR_EXPIRY`; null với `LOW_STOCK`, `DEAD_STOCK` |
| `lotNo`, `binLocationId`, `binCode` | | Có với `EXPIRED`, `NEAR_EXPIRY` |
| `quantity` | number | Tồn của lô (`EXPIRED`, `NEAR_EXPIRY`); tồn dùng được của mặt hàng tại kho (`LOW_STOCK`); tổng tồn của mặt hàng tại kho (`DEAD_STOCK`) |
| `availableQuantity` | number | Tồn trừ phần đã giữ chỗ |
| `expDate` | `yyyy-MM-dd` hoặc null | |
| `daysLeft` | number hoặc null | Số ngày còn tới hạn; 0 hoặc âm là đã hết hạn |
| `lastMovementAt` | ISO 8601 UTC hoặc null | Chỉ có với `DEAD_STOCK` |
| `daysWithoutMovement` | number hoặc null | Chỉ có với `DEAD_STOCK` |
| `safetyStock`, `reorderPoint` | number hoặc null | Chỉ có với `LOW_STOCK` |
| `message` | string | Câu tiếng Việt hiển thị thẳng (tooltip hoặc dòng mô tả) |

Gắn thẻ vào bảng tồn kho: thẻ theo lô khớp bằng `stockItemId` (hoặc `warehouseId` + `productId` + `lotNo`); thẻ theo mặt hàng khớp bằng `warehouseId` + `productId`. Một dòng có thể có nhiều thẻ (ví dụ vừa `LOW_STOCK` vừa có lô `NEAR_EXPIRY`). Muốn lấy đủ thẻ của một kho: `?warehouseId=...&size=100`, đọc tiếp các trang khi `last = false`.

Lỗi: `400` giá trị `kind` hoặc `severity` sai; `403` hỏi kho không được phân công hoặc tài khoản chưa được gán kho nào; `503 SERVICE_UNAVAILABLE` khi lọc `kind=LOW_STOCK` mà backend không đọc được product-service (không lọc loại thì vẫn trả các loại khác, không có `LOW_STOCK`).

## 3. `GET /api/inventory/alerts/summary` (chuông thông báo và thanh tóm tắt)

Tham số: `warehouseId` (không bắt buộc). `data`:

```json
{
  "alertDate": "2026-11-11",
  "nearExpiryDays": 30,
  "deadStockDays": 90,
  "total": 5,
  "countsByKind": { "NEAR_EXPIRY": 3, "EXPIRED": 1, "DEAD_STOCK": 0, "LOW_STOCK": 1 },
  "countsBySeverity": { "CRITICAL": 2, "WARNING": 3, "INFO": 0 },
  "lowStockAvailable": true,
  "note": null,
  "topItems": [ "tối đa 5 phần tử, cùng cấu trúc với mục 2" ]
}
```

`countsByKind` và `countsBySeverity` luôn đủ khóa. `lowStockAvailable = false` kèm `note` (tiếng Việt) khi không tính được tồn thấp: hiển thị `note`, không hiển thị số 0 như thể không có cảnh báo. Lỗi `403` như mục 2.

## 4. `POST /api/inventory/alerts/scan` (nút "Quét ngay", chỉ hiện cho ADMIN)

Không có body. Chạy ngay tác vụ hằng ngày cho doanh nghiệp của người gọi. Mỗi loại chỉ gửi một lần mỗi ngày (kể cả đã chạy lúc 00:00). `data`:

```json
{
  "alertDate": "2026-11-11",
  "triggeredBy": "admin",
  "results": [
    { "kind": "NEAR_EXPIRY", "status": "PUBLISHED", "warehouseCount": 2, "itemCount": 3, "emailCount": 3,
      "message": "Đã gửi 2 bản tổng hợp theo kho, 3 email" },
    { "kind": "EXPIRED", "status": "ALREADY_SENT", "warehouseCount": 0, "itemCount": 1, "emailCount": 0,
      "message": "Hôm nay đã gửi cảnh báo loại này, không gửi lại" }
  ]
}
```

`status`: `PUBLISHED`, `ALREADY_SENT`, `NOTHING_FOUND`, `UNAVAILABLE` (tồn thấp khi product-service lỗi), `FAILED` (broker lỗi, bấm lại sau được). Lỗi: `401` thiếu thông tin người dùng, `403` không phải `ADMIN`. Nên disable nút khi đang gọi và hiển thị `message` của từng loại.

## 5. WebSocket (popover chuông, WMS-15.4)

Kênh đang có: `/topic/company/{companyId}/alerts` (`companyId` lấy từ JWT, cách nối như `frontend/src/features/realtime`). Ngoài loại `LOW_STOCK` cũ, kênh này có thêm loại `STOCK_ALERT`, phát sau tác vụ 00:00 hoặc sau khi ADMIN bấm quét, mỗi tin là một kho và một loại cảnh báo:

```json
{
  "id": "UUID của sự kiện, dùng để bỏ tin trùng",
  "type": "STOCK_ALERT",
  "severity": "error | warning | info",
  "title": "Hàng cận hạn sử dụng",
  "message": "Kho Hà Nội có 3 lô hàng cận hạn sử dụng trong 30 ngày tới. Ưu tiên xuất trước (FEFO)",
  "occurredAt": "2026-11-10T17:00:00Z",
  "payload": {
    "companyId": 1, "alertDate": "2026-11-11", "kind": "NEAR_EXPIRY", "severity": "WARNING",
    "warehouseId": 5, "warehouseCode": "WH-HN", "warehouseName": "Kho Hà Nội",
    "totalItems": 3, "totalQuantity": 12.75, "thresholdDays": 30,
    "items": [ { "productId": 100, "skuCode": "SKU-001", "productName": "Sữa tươi", "lotNo": "L01",
                 "binCode": "WH-HN-Z1-R1-B1", "quantity": 2.5, "availableQuantity": 2.5, "expDate": "2026-11-14",
                 "daysLeft": 3, "lastMovementAt": null, "daysWithoutMovement": null, "safetyStock": null,
                 "reorderPoint": null, "severity": "WARNING" } ],
    "detectedAt": "2026-11-10T17:00:00Z"
  }
}
```

`title` theo `payload.kind`: `Hàng cận hạn sử dụng`, `Hàng đã hết hạn sử dụng`, `Hàng tồn lâu không luân chuyển`, `Cảnh báo tồn kho thấp`. `severity` của phong bì: `error` (CRITICAL), `warning`, `info`. `items` tối đa 10 dòng nghiêm trọng nhất, `totalItems` là tổng. Tin không lọc theo kho được phân công: popover nên ẩn tin có `payload.warehouseId` ngoài danh sách kho của người dùng (trừ ADMIN, ACCOUNTANT). Khi mở trang (chưa có tin WebSocket), lấy số đếm và danh sách ban đầu từ mục 3 (`/summary`) rồi mục 2; bấm một thông báo thì mở bảng tồn kho lọc `kind` và `warehouseId` tương ứng.

## 6. Email (chỉ để biết, giao diện không gọi)

Sau mỗi lần quét, quản lý của kho có cảnh báo và admin của doanh nghiệp nhận MỘT email cho mỗi loại `LOW_STOCK`, `NEAR_EXPIRY`, `EXPIRED` trong ngày (admin gộp mọi kho trong một email). Máy chủ chưa cấu hình SMTP (`MAIL_HOST` rỗng) thì email không gửi mà nội dung được ghi vào log của notification-service.
