# Hiệu năng

Tài liệu của WMS-19: bộ nhớ đệm Redis cho tổng tồn và tồn khả dụng (WMS-19.1), chỉ mục theo doanh nghiệp và sửa truy
vấn lặp cho các API danh sách (WMS-19.3). Yêu cầu gốc nằm ở SRS (API nghiệp vụ phản hồi trong 500 ms, trừ tồn chịu 30
đến 50 request đồng thời, Dashboard tải trong 2 giây có phân trang) và ở `docs/project-management/KE_HOACH_JIRA.md`
(WMS-19, WMS-19.1 đến WMS-19.3). Kiểm thử tải đồng thời (WMS-19.2) là task riêng; lỗi cạn nhóm kết nối nó phát hiện
và cách định cỡ nhóm kết nối nằm ở mục 5 (WMS-20.4).

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

## 2. Chỉ mục theo doanh nghiệp (WMS-19.3)

### 2.1. Vì sao phải đổi

Từ WMS-22 mọi truy vấn JPQL đều có thêm `company_id = ?` (TenantEntity). Chỉ mục cũ tạo trước đó bắt đầu bằng cột
khác (`status`, `exp_date`, `is_locked`...), nên MySQL hoặc đọc cả chỉ mục của mọi doanh nghiệp rồi lọc, hoặc ghép hai
chỉ mục (`index_merge`). Chỉ mục mới bắt đầu bằng `company_id` rồi mới tới cột lọc và cột sắp xếp. InnoDB tự nối khóa
chính vào cuối mỗi chỉ mục phụ, nên `(company_id, id)` cho danh sách sắp `id` giảm dần đọc đúng 20 dòng của trang, và
`(company_id, status)` vẫn giữ thứ tự `id` trong cùng trạng thái.

Đã rà mọi repository, Specification, truy vấn của tác vụ định kỳ và listener ở auth, product, inventory, order,
notification, đối chiếu với toàn bộ migration. Chỉ inventory-service và order-service cần chỉ mục mới; product-service,
auth-service và notification-service đã có chỉ mục bắt đầu bằng `company_id` cho các truy vấn đang dùng (đo ở mục 2.3),
nên không có migration (V5, V9, V4 không dùng).

### 2.2. Chỉ mục thêm và bỏ

inventory-service `V15__company_indexes_for_lists.sql`:

| Bảng | Thêm | Phục vụ truy vấn | Bỏ (lý do) |
| --- | --- | --- | --- |
| `stock_items` | `(company_id, exp_date)` | Danh sách lô sắp theo hạn dùng (`GET /api/inventory/lots`), lô cận hạn, tự khóa lô hết hạn hằng đêm | `(exp_date)` (mọi truy vấn theo hạn dùng đều kèm `company_id`), `(company_id)` (tiền tố của `(company_id, product_id)`) |
| `stock_reservations` | `(company_id, message_id)` | Chống xử lý trùng `order.created` (`existsByMessageId`, mỗi lần giữ chỗ) | `(company_id)` (tiền tố của chỉ mục mới) |
| `stock_transfers` | `(company_id, created_at)`, `(company_id, status, created_at)` | Danh sách lệnh điều chuyển (sắp `createdAt` giảm dần, lọc trạng thái) | `(company_id)` (tiền tố), `(status)` (thay bằng chỉ mục có `company_id`) |
| `inventory_lots` | | | `(company_id)` (tiền tố của `(company_id, product_id)`) |
| `bin_locations` | | | `(warehouse_id, zone, rack)` (tiền tố của ràng buộc duy nhất `(warehouse_id, zone, rack, bin)`) |

order-service `V10__company_indexes_for_lists.sql`:

| Bảng | Thêm | Phục vụ truy vấn | Bỏ (lý do) |
| --- | --- | --- | --- |
| `sales_orders` | `(company_id, id)`, `(company_id, status, order_date)` | Danh sách đơn bán (sắp `id` giảm dần), lọc trạng thái, đếm theo trạng thái và doanh thu theo khoảng ngày của KPI | `(status)` |
| `purchase_orders` | `(company_id, id)`, `(company_id, status)` | Danh sách đơn mua, lọc trạng thái, đếm KPI | `(status)` |
| `goods_receipt_notes` | `(company_id, id)`, `(company_id, status)` | Danh sách phiếu nhập, đếm và cộng KPI theo trạng thái | `(status)` |
| `goods_delivery_notes` | `(company_id, id)`, `(company_id, status)` | Danh sách phiếu xuất, đếm và cộng KPI theo trạng thái | `(status)` |
| `purchase_bills` | `(company_id, id)`, `(company_id, status)` | Danh sách hóa đơn mua, đếm theo trạng thái | `(status)` |
| `suppliers`, `customers` | `(company_id, active)` | Lọc đối tác đang hoạt động, đếm và cộng công nợ đối tác đang hoạt động (KPI) | `(active)` |

Không đụng ràng buộc duy nhất nào, không đổi dữ liệu. Chỉ mục khóa ngoại vẫn còn (MySQL bắt buộc có chỉ mục bắt đầu
bằng cột khóa ngoại). `CREATE INDEX` của MySQL 8 chạy trực tuyến (không khóa ghi), `DROP INDEX` chỉ đổi siêu dữ liệu:
trên dữ liệu sinh ở mục 2.3, V15 chạy 8 giây và V10 chạy 13 giây.

### 2.3. Đo trước và sau

Không có bộ dữ liệu cỡ thật của khách hàng. Đã đo trên một MySQL 8.0.43 dựng tạm trên máy phát triển (đang chạy song
song nhiều bản build khác nên số tuyệt đối dao động, chỉ nên so tương đối), dữ liệu sinh bằng SQL gồm ba doanh nghiệp
xen kẽ theo ID: doanh nghiệp 1 nhỏ (2% số dòng), doanh nghiệp 3 vừa (18%), doanh nghiệp 2 lớn (80%):

| Bảng | Số dòng |
| --- | --- |
| `sales_orders`, `so_items` | 200 000, 400 000 |
| `purchase_orders` | 100 000 |
| `customers`, `suppliers` | 3 000, 1 000 |
| `stock_items`, `stock_reservations`, `stock_transfers`, `bin_locations` | 300 000, 100 000, 50 000, 6 000 |
| `products`, `categories` | 60 000, 300 |

Cách đo: áp migration cũ, sinh dữ liệu, `ANALYZE TABLE`, chạy `EXPLAIN` và `EXPLAIN ANALYZE` cho câu SQL tương đương
câu Hibernate sinh ra (lần chạy thứ hai, bộ đệm đã nóng); áp V15 và V10, đo lại.

| Truy vấn | Trước | Sau |
| --- | --- | --- |
| Chống trùng `order.created`: `stock_reservations WHERE company_id = 2 AND message_id = ?` | `ref` trên `(company_id)`, đọc 80 000 dòng, 2 370 ms | `ref` trên `(company_id, message_id)`, chỉ đọc chỉ mục, 1 dòng, 0,03 ms |
| Tự khóa lô hết hạn của doanh nghiệp 1: `company_id = 1 AND exp_date <= hôm nay AND is_locked = 0 AND quantity > 0` | `index_merge` giao `(company_id)` với `(is_locked)`, quét 296 908 mục của `(is_locked)`, 3 131 ms | `range` trên `(company_id, exp_date)`, 264 dòng, 1,7 ms |
| KPI doanh thu tháng của doanh nghiệp 1: `SUM(total_amount) WHERE company_id = 1 AND status IN (...) AND order_date BETWEEN ...` | `ref` trên ràng buộc `(company_id, so_number)` rồi đọc 4 000 dòng để lọc, 180 ms | `range` trên `(company_id, status, order_date)`, 124 dòng, 0,9 ms |
| Đếm đơn bán `CONFIRMED` của doanh nghiệp 2 (câu đếm của trang có lọc trạng thái) | `ref` trên `(status)` rồi đọc 33 334 dòng để lọc doanh nghiệp, 995 ms | Chỉ đọc chỉ mục `(company_id, status, order_date)`, 51 ms |
| Danh sách lệnh điều chuyển của doanh nghiệp 1, trang đầu | Đọc 1 000 dòng rồi sắp xếp (`filesort`), 129 ms | Đọc ngược `(company_id, created_at)`, 20 mục, 0,07 ms |
| Danh sách lô của doanh nghiệp 1 sắp theo hạn dùng | Quét `(exp_date)` của mọi doanh nghiệp, 967 dòng, 3,1 ms | `(company_id, exp_date)`, 20 dòng, 0,34 ms |
| Danh sách đơn bán, đơn mua của doanh nghiệp 1, trang đầu | Quét ngược khóa chính 951 dòng (1,3 ms); đơn mua `filesort` 2 000 dòng (1,8 ms) | Đọc ngược `(company_id, id)`, 20 dòng: 0,38 ms và 0,05 ms |

Các truy vấn không đổi vì đã đúng: ATP một sản phẩm trong một kho (`ref` trên `(product_id)`, 15 dòng, rồi khóa chính
`bin_locations`); danh sách sản phẩm (quét khóa chính theo `id`, dừng sau 20 dòng của doanh nghiệp, khoảng 1 ms);
`notification_logs` đã có `(company_id, status)`, `(company_id, created_at)` từ WMS-22.4c; auth-service lọc theo
`(company_id)` của bảng `users`, `payments`, `api_keys` vốn đã có chỉ mục.

Kiểm tra migration: trên cơ sở dữ liệu trống của MySQL thử nghiệm, chạy inventory-service và order-service bản đã đóng
gói với `ddl-auto: validate`: Flyway áp đủ V1 đến V15 (inventory, chưa có V14 của task song song) và V1 đến V10 (order),
Hibernate kiểm tra entity khớp schema, service khởi động bình thường. MySQL thử nghiệm đã tắt và xóa sau khi đo.

## 3. Truy vấn lặp (N+1) và danh sách không giới hạn

| Chỗ | Trước | Sửa | Test |
| --- | --- | --- | --- |
| Danh sách đơn bán (`GET /api/orders/sales`, `findAll(Pageable)` và `search`) | `@EntityGraph` nạp kèm `items` khi phân trang: Hibernate đọc MỌI đơn khớp điều kiện rồi cắt trang trong bộ nhớ (cảnh báo HHH90003004) | Chỉ nạp kèm `customer`; dòng hàng nạp theo lô | `ListQueryCountIntegrationTest.salesOrderPageLoadsOnlyThePage`: nạp đúng 5 đơn của trang, tối đa 4 câu SQL |
| Danh sách đơn mua, phiếu nhập, phiếu xuất, hóa đơn mua (order-service) | Mỗi chứng từ của trang thêm một câu lấy dòng hàng | `hibernate.default_batch_fetch_size: 50`: dòng hàng và quan hệ lazy của cả trang nạp bằng một câu `IN` | `ListQueryCountIntegrationTest.purchaseOrderPageHasNoNPlusOne`: tối đa 4 câu cho trang 5 đơn (trước là 7) |
| Danh sách tài khoản (auth-service), sản phẩm (product-service), lệnh điều chuyển, ô kệ (inventory-service) | Vai trò, kho được gán, nhóm hàng, đơn vị tính, kho của ô kệ nạp lazy từng bản ghi | Cùng thiết lập `default_batch_fetch_size: 50` | Test có sẵn của từng service |

Nội dung API không đổi: thiết lập trên chỉ đổi cách Hibernate nạp quan hệ. Kích thước trang đã giới hạn ở mọi service
(`spring.data.web.pageable.max-page-size: 100`; kiểm kê và lịch sử thanh toán tự giới hạn 100). Hai danh sách không phân
trang được giữ nguyên vì giao diện đang dùng nguyên danh sách và số dòng có giới hạn tự nhiên: danh sách ô kệ của các kho
trong phạm vi người dùng và danh sách kho (giới hạn theo gói dịch vụ).

## 4. Những gì cố ý không làm

- **Phân vùng bảng (partitioning).** Kế hoạch WMS-19.3 có nhắc nhưng không làm: dữ liệu mỗi bảng ở mức vài trăm nghìn
  dòng, chỉ mục bắt đầu bằng `company_id` đã đưa các truy vấn trên về dưới 2 ms. MySQL bắt buộc cột phân vùng nằm trong
  mọi khóa chính và ràng buộc duy nhất, nên phân vùng theo doanh nghiệp hay theo ngày sẽ phải đổi khóa chính `id` thành
  khóa ghép và bỏ khóa ngoại (bảng phân vùng không có khóa ngoại), làm phức tạp các khóa `(company_id, mã)` vừa dựng ở
  WMS-22. Chỉ nên cân nhắc lại khi một bảng vượt hàng chục triệu dòng.
- **Bộ nhớ đệm cấp hai của Hibernate.** Không bật: dữ liệu thay đổi liên tục từ nhiều instance và qua sự kiện, bộ đệm
  cấp hai theo entity khó vô hiệu đúng theo doanh nghiệp; phần đọc nhiều nhất (tồn khả dụng) đã có bộ nhớ đệm Redis có
  kiểm soát ở mục 1.
- **Đệm danh sách và báo cáo.** Kết quả lọc nhiều chiều và phân trang, tỷ lệ trúng thấp; tối ưu bằng chỉ mục.
- **Tìm kiếm theo từ khóa** (`LIKE '%từ khóa%'`) không dùng được chỉ mục B-tree; trong phạm vi một doanh nghiệp số dòng
  nhỏ nên chấp nhận. Cần thì dùng FULLTEXT ở một task riêng.

## 5. Nhóm kết nối cơ sở dữ liệu (WMS-20.4)

### 5.1. Sự cố khi chạy tải

Chạy `qa/load/concurrency.mjs` trên bộ Docker thật (40 thao tác mỗi giây, trộn tạo đơn bán, xác nhận, lập phiếu xuất và
đọc của một doanh nghiệp): order-service ngừng trả lời. 604 trên 637 lần `POST /api/orders/sales` hết thời gian chờ 30
giây, các API đọc của order-service cũng treo, inventory-service vẫn nhanh. Log order-service có 562 dòng
`SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms` và
khoảng chục dòng `Lock wait timeout exceeded`.

### 5.2. Nguyên nhân và cách sửa

`DocumentNumberService` cấp số chứng từ trong một transaction `REQUIRES_NEW` trên nhóm kết nối chính, trong khi phương
thức tạo chứng từ gọi nó (`@Transactional` của đơn bán, đơn mua, phiếu nhập, phiếu xuất, hóa đơn mua) đang giữ một kết
nối của cùng nhóm. Mỗi request tạo chứng từ vì vậy cần HAI kết nối. Khi số request tạo chứng từ cùng lúc bằng kích thước
nhóm (mặc định 10), mỗi luồng giữ một kết nối và chờ kết nối thứ hai: không luồng nào đi tiếp được cho tới khi hết 30
giây chờ, và mọi request khác của service (kể cả API đọc) chờ chung nhóm đó.

Quy tắc: **không bao giờ mở transaction mới trên cùng một nhóm kết nối khi đang giữ một kết nối của nhóm đó**
(`@Transactional(propagation = REQUIRES_NEW)`, `TransactionTemplate` với `PROPAGATION_REQUIRES_NEW`, hay lấy thêm kết
nối từ cùng `DataSource`). Cần một transaction độc lập bên trong transaction đang mở thì dùng nhóm kết nối riêng mà luồng
giữ nó không chờ gì khác. `REQUIRES_NEW` chỉ an toàn khi người gọi chắc chắn không giữ kết nối (không có transaction bao
ngoài; `spring.jpa.open-in-view: false` ở mọi service nên controller không giữ kết nối).

Cách sửa: cấp số qua nhóm kết nối riêng `document-sequence` (lớp `DocumentSequencePool`, cùng URL, tài khoản, driver với
nhóm chính, mặc định 3 kết nối, biến `DOCUMENT_SEQUENCE_POOL_SIZE`), bằng JDBC thuần trong một transaction ngắn:
`SELECT ... FOR UPDATE` dòng (company_id, tiền tố), `UPDATE` hoặc `INSERT` khi chưa có dòng, commit. Đồ thị chờ không
còn vòng: luồng giữ kết nối chính có thể chờ kết nối cấp số; luồng giữ kết nối cấp số chỉ chờ khóa dòng bộ đếm, được trả
sau vài mili giây. Truy vấn số lớn nhất đã có (khởi tạo bộ đếm) và kiểm tra số đã dùng vẫn chạy qua JPA trên kết nối của
người gọi, lúc không giữ kết nối cấp số. Bảo đảm cũ giữ nguyên: số duy nhất theo doanh nghiệp và tiền tố kể cả khi chạy
nhiều phiên bản service, khóa chỉ giữ vài mili giây, chứng từ lập thất bại chỉ bỏ qua một số. Nhóm này cố ý không đăng ký
thành bean `DataSource` để Spring Boot vẫn tự cấu hình nhóm chính cho JPA và Flyway.

Kiểm chứng: `DocumentNumberPoolStarvationTest` thu nhóm chính còn 4 kết nối (chờ tối đa 3 giây) và cấp số từ 16 luồng
cùng lúc trong transaction đã giữ kết nối, cho hai doanh nghiệp. Trên mã cũ test hỏng vì
`HikariPool-1 - Connection is not available, request timed out after 3017ms (total=4, active=4, idle=0, waiting=10)`;
với bản sửa mọi lần cấp thành công, mỗi doanh nghiệp nhận đúng `...-0001` đến `...-0008`. Cùng kịch bản, thêm 64 luồng
chia cho hai phiên bản service (mỗi phiên bản một nhóm cấp số riêng), đã chạy trên MySQL 8.0.43 thử nghiệm sau khi Flyway
áp V1 đến V11 và Hibernate kiểm tra schema: số liên tiếp, không trùng, không deadlock, không `Lock wait timeout`.

### 5.3. Rà soát cả backend và định cỡ nhóm kết nối

| Chỗ | Có giữ kết nối khi lấy kết nối thứ hai? | Kết luận |
| --- | --- | --- |
| `DocumentNumberService` (order-service) | Có: gọi từ năm phương thức tạo chứng từ `@Transactional` | Lỗi thật, đã sửa ở mục 5.2 |
| `PartnerBulkImportService` (order-service, `REQUIRES_NEW` mỗi dòng) | Không: controller gọi thẳng, lớp không có transaction bao ngoài | Không phải lỗi |
| `ProductBulkRowImporter` (product-service, `REQUIRES_NEW` mỗi dòng) | Không: `ProductBulkService` cố ý không có transaction | Không phải lỗi |
| `StockAlertRunGuard` (inventory-service, `REQUIRES_NEW`) | Không: `StockAlertScanService` không có transaction, phần đọc của `StockAlertDetector` là transaction chỉ đọc đã đóng trước khi giữ chỗ | Không phải lỗi |
| `TransactionTemplate` của `PaymentService` (auth-service) | Không: propagation mặc định, gọi PayOS ngoài transaction | Không phải lỗi |
| Open-session-in-view | `spring.jpa.open-in-view: false` ở mọi service có JPA và ở `config-repo/application.yml` | Controller không giữ kết nối, không cần đổi |
| Callback sau commit (`afterCommit`, `@TransactionalEventListener`) | Chỉ gửi RabbitMQ, xóa Redis hoặc gọi inventory-service giải phóng giữ chỗ, không mở transaction | Không phải lỗi |

Giữ một kết nối trong lúc gọi đồng bộ sang service khác là thiết kế có chủ đích (lỗi bên kia thì rollback cả thao tác):
order-service xác nhận đơn bán và tạo đơn ở trạng thái đã xác nhận (kiểm tra tồn khả dụng từng dòng rồi giữ chỗ), lập và
xuất phiếu xuất kho (trừ tồn), lập và xác nhận phiếu nhập (kiểm tra ô kệ đang kiểm kê); inventory-service lập báo cáo tồn
trong transaction chỉ đọc có gọi product-service. Thời gian giữ bị chặn bởi thời gian chờ của Feign:

| Service | Feign kết nối / đọc | Một lần gọi tối đa | Lần gọi GET, nếu Spring Cloud LoadBalancer thử lại một lần (mặc định khi có spring-retry) |
| --- | --- | --- | --- |
| order-service (đã đặt từ trước) | 3 s / 5 s | 8 s | 16 s |
| inventory-service (trước: mặc định 10 s / 60 s) | 2 s / 5 s | 7 s | 14 s |

Trường hợp xấu nhất của order-service khi inventory-service treo: xác nhận đơn N dòng giữ kết nối tối đa khoảng
16 x N + 8 giây (mỗi dòng một lần đọc tồn khả dụng, cộng một lần giữ chỗ); lập phiếu xuất, xuất kho, lập phiếu nhập
khoảng 8 giây cho lần gọi POST. Trên thực tế lần gọi đầu tiên lỗi là cả thao tác dừng và rollback, nên một request thường
giữ kết nối tối đa 16 giây, thấp hơn 30 giây chờ kết nối của Hikari.

Định cỡ (biến `DB_POOL_SIZE` của từng container):

| Service | Nhóm chính | Lý do |
| --- | --- | --- |
| order-service | 20 (mặc định của Hikari là 10) + 3 kết nối cấp số | Giữ kết nối trong lúc gọi inventory-service |
| inventory-service | 20 | Nhận các lần gọi trên dưới khóa bi quan, báo cáo gọi product-service |
| auth-service, product-service, notification-service | 10 (mặc định) | Transaction ngắn, không giữ kết nối qua lời gọi mạng |

Tổng một bộ service: 10 + 10 + 10 + 20 + 23 = 73 kết nối, nhỏ hơn `max_connections` mặc định 151 của MySQL 8 (compose
không đổi giá trị này); chạy thêm một bản order-service và inventory-service vẫn còn dưới (116). Tomcat giữ mặc định
(tối đa 200 luồng, hàng đợi 100): luồng vượt số kết nối xếp hàng chờ Hikari tối đa 30 giây (`connection-timeout` mặc
định), nên kết nối phải được trả nhanh như trên chứ không tăng nhóm vô hạn.
