# Hiệu năng

Tài liệu của WMS-19: bộ nhớ đệm Redis cho tổng tồn và tồn khả dụng (WMS-19.1). Yêu cầu gốc nằm ở SRS (yêu cầu phi chức
năng về thời gian phản hồi và truy cập đồng thời) và ở `docs/project-management/KE_HOACH_JIRA.md` (WMS-19, WMS-19.1 đến
WMS-19.3).

## 1. Bộ nhớ đệm tổng tồn và tồn khả dụng (WMS-19.1)

### 1.1. Đệm cái gì

Đơn vị đệm là tổng tồn của một sản phẩm trong một kho (hoặc cộng mọi kho) của một doanh nghiệp: tồn thực tế (`onHand`)
và phần đã giữ chỗ (`reserved`), chỉ tính dòng tồn chưa khóa và chưa hết hạn. Tồn khả dụng ATP = max(0, onHand -
reserved) được tính lại khi trả về, nên kết quả API giống hệt khi đọc cơ sở dữ liệu.

| API đọc | Ai gọi | Đi qua bộ nhớ đệm |
| --- | --- | --- |
| `GET /api/inventory/stock/atp?productId=&warehouseId=` | order-service (OpenFeign) khi lập và xác nhận đơn bán; màn hình đơn bán và điều chuyển | Có |
| `POST /api/inventory/stock/atp/batch` | Kiểm tra ATP nhiều sản phẩm một lần | Có, chỉ nạp các sản phẩm trượt trong một truy vấn |
| `GET /internal/inventory/kpi-metrics?warehouseId=` | notification-service (Dashboard KPI) | Ba số theo tồn (tồn khả dụng, tổng tồn, số ô đang chứa hàng); số ô của kho luôn đọc cơ sở dữ liệu |

Không đi qua bộ nhớ đệm, cố ý:

- Mọi quyết định thay đổi tồn: giữ chỗ (`StockReservationService`), trừ tồn và xuất hàng (`StockDeductionService`),
  lập và duyệt xuất điều chuyển (`StockTransferService` gọi thẳng `StockService.getAtp`), duyệt điều chỉnh kiểm kê. Các
  đường này đọc cơ sở dữ liệu dưới khóa bi quan như trước. Lớp `StockReadService` chỉ dùng cho API đọc.
- Danh sách lô, thẻ kho, báo cáo nhập xuất tồn: kết quả phân trang và lọc nhiều chiều, không đáng đệm; tối ưu bằng chỉ mục.
- `GET /api/inventory/warehouses/summary`: phạm vi kho khác nhau theo từng người dùng.

### 1.2. Khóa Redis

Mọi khóa có ID doanh nghiệp ngay sau tiền tố, nên hai doanh nghiệp trùng ID sản phẩm hay ID kho cũng không bao giờ đọc
số của nhau (test `companiesNeverShareCachedFigures`, `companiesNeverReadEachOthersCachedFigures`). Hằng số nằm ở
`vn.wms.inventory.cache.StockCacheKeys` (chỉ inventory-service dùng nên không đưa vào `common`).

| Khóa | Giá trị |
| --- | --- |
| `wms:stock:{companyId}:p:{productId}:gen` | Thế hệ của sản phẩm, tăng (INCR) sau mỗi thay đổi tồn của sản phẩm; sống 1 ngày |
| `wms:stock:{companyId}:p:{productId}:w:{warehouseId}` | `thế hệ\|ngày\|onHand\|reserved` của sản phẩm trong một kho, sống `STOCK_CACHE_TTL` |
| `wms:stock:{companyId}:p:{productId}:w:all` | Như trên, cộng mọi kho của doanh nghiệp |
| `wms:stock:{companyId}:gen` | Thế hệ của doanh nghiệp, tăng khi bất kỳ tồn nào của doanh nghiệp đổi |
| `wms:stock:{companyId}:kpi:{warehouseId hoặc all}` | `thế hệ\|ngày\|tồn khả dụng\|tổng tồn\|số ô đang chứa hàng` |

### 1.3. Vô hiệu sau mỗi thay đổi tồn

Cách làm: giá trị đệm mang theo thế hệ đã đọc TRƯỚC khi truy vấn cơ sở dữ liệu. Khi đọc, giá trị chỉ được dùng nếu thế
hệ của nó bằng thế hệ hiện tại và ngày tính bằng hôm nay (lô hết hạn bị loại theo ngày). Sau khi giao dịch thay đổi tồn
commit, thế hệ của sản phẩm và của doanh nghiệp tăng lên, nên mọi số cũ (mọi kho, cả số "mọi kho" và số KPI) mất hiệu
lực bằng vài lệnh INCR, không cần KEYS hay SCAN. Một request đọc chạy song song lỡ ghi số cũ sau lệnh INCR thì số đó
mang thế hệ cũ nên không bao giờ được dùng (test `lateWriteOfStaleValueIsIgnored`).

Điểm gắn: một callback JPA duy nhất trên `StockItem` (`@EntityListeners(StockItemCacheListener.class)`). Mỗi lần
Hibernate thêm, sửa hoặc xóa một dòng `stock_items`, callback báo cho `StockCacheInvalidator`; lớp này gom các sản phẩm
của giao dịch và vô hiệu trong `afterCommit`. Giao dịch rollback thì không vô hiệu gì. Vì gắn ở mức entity nên mọi
đường thay đổi tồn hiện có đều được bắt mà không sửa service nào, và đường mới viết sau này cũng tự được bắt:

| Đường thay đổi tồn | Lớp, phương thức | Test chứng minh số trả về là số mới |
| --- | --- | --- |
| Nhập kho theo phiếu GRN (`order.received`) | `StockReceiptService.processOrderReceived` | `receiptEvicts` |
| Trừ tồn thủ công, xuất hàng (`/deduct`, `/dispatch`) | `StockDeductionService.deductStock` | `deductionAndDispatchEvict` |
| Xuất hàng theo sự kiện `order.dispatched` | `StockDeductionService.deductStockForDispatchEvent` | `deductionAndDispatchEvict` |
| Giữ chỗ cho đơn bán, giải phóng khi hủy | `StockReservationService.reserveStock`, `releaseReservationsByOrderId` | `reservationAndReleaseEvict` |
| Duyệt xuất và nhận điều chuyển | `StockTransferService.dispatchTransfer`, `receiveTransfer` | `transferOutAndInEvict` |
| Duyệt điều chỉnh sau kiểm kê | `StocktakeApprovalService.approveAdjustment` | `stocktakeAdjustmentApprovalEvicts` |
| Khóa, mở khóa lô | `LotService.lockLot`, `unlockLot` | `lotLockAndUnlockEvict` |
| Tự khóa lô hết hạn (01:00 hằng ngày) | `LotService.autoLockExpiredLots` | không đổi ATP (lô hết hạn đã bị loại theo ngày), vẫn được vô hiệu |

Giới hạn cần nhớ: câu `UPDATE` JPQL hàng loạt hoặc SQL gốc trên `stock_items` không qua callback. Hiện inventory-service
không có câu nào như vậy; ai thêm thì phải tự gọi `StockCacheInvalidator.stockChanged` (Javadoc của listener có ghi).

### 1.4. Khi Redis lỗi

Không request nào thất bại vì Redis. Lỗi khi đọc hoặc ghi: trả số đọc thẳng từ cơ sở dữ liệu, bỏ qua bộ nhớ đệm trong
`app.stock-cache.retry-after` (30 giây) rồi mới thử lại, chỉ ghi một dòng cảnh báo mỗi khoảng đó. Lỗi khi vô hiệu
(không tăng được thế hệ): bỏ qua bộ nhớ đệm ít nhất một TTL để instance này không đọc lại số có thể đã cũ. Thời gian
chờ Redis của inventory-service giảm còn 2 giây (`REDIS_TIMEOUT`, `REDIS_CONNECT_TIMEOUT`). Trường hợp xấu nhất khi
Redis chập chờn đúng lúc vô hiệu và có nhiều instance inventory-service: instance khác có thể trả số cũ tối đa một TTL.

### 1.5. Cấu hình và theo dõi

| Thuộc tính | Biến môi trường | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `app.stock-cache.enabled` | `STOCK_CACHE_ENABLED` | `true` | Tắt thì mọi API đọc thẳng cơ sở dữ liệu, không gọi Redis lần nào |
| `app.stock-cache.ttl` | `STOCK_CACHE_TTL` | `60s` | Thời gian sống của một số đệm, chỉ là lưới an toàn |
| `app.stock-cache.retry-after` | `STOCK_CACHE_RETRY_AFTER` | `30s` | Thời gian bỏ qua Redis sau một lần lỗi |

Theo dõi: bộ đếm Micrometer `wms.stock.cache.requests` (thẻ `result` = `hit` hoặc `miss`), `wms.stock.cache.errors`,
`wms.stock.cache.evictions`; bật log `logging.level.vn.wms.inventory.cache.StockTotalsCache=DEBUG` để thấy từng lần
trúng, trượt và vô hiệu. Test chạy với `app.stock-cache.enabled=false` (không có Redis); `StockTotalsCacheTest` và
`StockCacheIntegrationTest` bật lại với Redis giả lập trong bộ nhớ.
