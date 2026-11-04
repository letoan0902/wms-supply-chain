# Trao đổi sự kiện qua RabbitMQ

Tài liệu cho task WMS-02 (Sprint 2 và 3): exchange, routing key, queue, chính sách dead-letter, cách thêm sự kiện mới (WMS-02.1) và cách phát, nhận sự kiện trong code bằng `WmsEventPublisher`, converter JSON dùng chung và OpenFeign (WMS-02.2, mục 7). Phần WebSocket (WMS-02.3, WMS-02.4) sẽ bổ sung khi làm.

## 1. Tổng quan

- Một exchange kiểu **topic** duy nhất `wms.events`: mọi service publish sự kiện vào đây kèm routing key.
- Mỗi service tiêu thụ có queue riêng, bind vào `wms.events` bằng routing key hoặc mẫu (`order.#`). Một sự kiện có thể tới nhiều queue (fan-out theo binding), mỗi queue chỉ có một service đọc.
- Tin nhắn bị consumer từ chối không requeue (ví dụ JSON hỏng, lỗi nghiệp vụ không thể xử lý) được chuyển sang exchange `wms.dlx` và nằm chờ ở queue `wms.dead-letter` để xem lại, không bị mất và không lặp vô hạn.
- Topology được nạp vào broker từ [docker/rabbitmq/definitions.json](../docker/rabbitmq/definitions.json) qua API quản trị bởi container `rabbitmq-init` ([docker/rabbitmq/import-definitions.sh](../docker/rabbitmq/import-definitions.sh)) mỗi lần `docker compose up`; các service nghiệp vụ chỉ khởi động sau khi container này kết thúc thành công. Service không phải tự tạo queue; chỉ tham chiếu hằng số trong `vn.wms.common.messaging.WmsEvents` (module `backend/common`).

Sơ đồ:

```text
order-service ----order.created / order.dispatched----> [wms.events] ----> inventory.order-events     (inventory-service)
                                                             |------------> notification.order-events  (notification-service)
inventory-service --inventory.updated---------------------->  |------------> notification.inventory-events (notification-service)
inventory-service --alert.low-stock------------------------->  '------------> notification.alerts       (notification-service)

queue nghiệp vụ --(reject, không requeue)--> [wms.dlx] --> wms.dead-letter (giữ 7 ngày, tối đa 10000 tin)
```

## 2. Quy ước đặt tên

| Thành phần | Quy ước | Ví dụ |
| --- | --- | --- |
| Routing key | `<domain>.<action>`, chữ thường, động từ ở thì quá khứ (sự kiện đã xảy ra) | `order.created`, `inventory.updated` |
| Mẫu binding | `<domain>.#` nhận mọi sự kiện của domain | `order.#` |
| Queue | `<service-nhận>.<nội-dung>`, chữ thường, nối bằng dấu gạch ngang | `inventory.order-events` |
| Exchange | tiền tố `wms.` | `wms.events`, `wms.dlx` |

Không đặt `x-arguments` trực tiếp trên queue (dead-letter đi qua policy, mục 4). Nhờ vậy service nào lỡ khai báo lại queue bằng Spring AMQP cũng không bị lỗi lệch tham số (`PRECONDITION_FAILED`).

## 3. Danh sách sự kiện

| Routing key | Service phát | Queue nhận (service) | Ý nghĩa và nội dung dự kiến |
| --- | --- | --- | --- |
| `order.created` | order-service | `inventory.order-events` (inventory), `notification.order-events` (notification) | Đơn hàng mới (PO hoặc SO) đã tạo: mã đơn, loại đơn, kho, danh sách dòng hàng (SKU, số lượng) |
| `order.dispatched` | order-service | `inventory.order-events`, `notification.order-events` | Đơn bán đã xuất kho (GDN xác nhận): mã đơn, mã GDN, dòng hàng đã xuất |
| `inventory.updated` | inventory-service | `notification.inventory-events` (notification) | Tồn kho thay đổi sau nhập, xuất, điều chỉnh: SKU, kho, tồn trước và sau |
| `alert.low-stock` | inventory-service | `notification.alerts` (notification, đã có consumer) | Tồn chạm ngưỡng an toàn: SKU, kho, tồn hiện tại, ngưỡng; notification-service gửi email |

Nội dung message là JSON (Jackson). Payload của mỗi routing key là một record trong gói `vn.wms.common.messaging.event` của module `common`, service phát và service nhận dùng chung một kiểu:

| Routing key | Record payload | Trường chính |
| --- | --- | --- |
| `order.created` | `OrderCreatedEvent` | `orderId`, `orderNumber`, `orderType` (PURCHASE hoặc SALES), `status`, `supplierId` hoặc `customerId`, `warehouseId`, `warehouseName`, `totalAmount`, `createdBy`, `createdAt`, `items[]` (`OrderLine`: `productId`, `skuCode`, `productName`, `quantity`, `uom`, `unitPrice`) |
| `order.dispatched` | `OrderDispatchedEvent` | `orderId`, `orderNumber`, `orderType`, `warehouseId`, `dispatchedBy`, `dispatchedAt`, `items[]` |
| `inventory.updated` | `InventoryUpdatedEvent` | `warehouseId`, `productId`, `skuCode`, `quantityBefore`, `quantityAfter`, `reason`, `referenceNumber`, `updatedAt` |
| `alert.low-stock` | `LowStockAlertEvent` | `warehouseId`, `warehouseName`, `productId`, `skuCode`, `productName`, `quantityOnHand`, `minimumQuantity`, `detectedAt` |

Thông tin kỹ thuật không nằm trong payload mà ở thuộc tính message do `WmsEventPublisher` gắn: `messageId` (UUID, consumer dùng để bỏ qua message giao lại), `timestamp`, header `x-wms-event` (trùng routing key) và `x-wms-source` (tên service phát). Thời gian ghi dạng ISO 8601 UTC, ví dụ `2026-11-04T08:00:00Z` (xem nguyên tắc idempotent trong [ARCHITECTURE.md](ARCHITECTURE.md)).

## 4. Dead-letter

Hai policy trong `definitions.json`:

| Policy | Áp dụng cho | Nội dung |
| --- | --- | --- |
| `wms-dead-letter` | queue có tên bắt đầu bằng `inventory.`, `notification.`, `order.` | `dead-letter-exchange = wms.dlx`: tin bị reject (không requeue) hoặc hết hạn được chuyển sang `wms.dlx`, giữ nguyên routing key, kèm header `x-death` ghi queue gốc và lý do |
| `wms-dead-letter-retention` | `wms.dead-letter` | giữ tối đa 7 ngày (`message-ttl` 604800000 ms) và 10000 tin, đầy thì bỏ tin cũ nhất (`overflow = drop-head`) |

Consumer cần đặt `spring.rabbitmq.listener.simple.default-requeue-rejected=false` (order-service, inventory-service và notification-service đã đặt trong `application.yml`, kèm `listener.simple.retry` thử lại 3 lần trong tiến trình trước khi từ chối) để tin lỗi đi vào dead-letter thay vì quay lại queue vô hạn. Tin có nội dung hỏng (không đọc được JSON) thì consumer ném `AmqpRejectAndDontRequeueException` để bỏ qua bước thử lại.

Xem và xử lý tin chết: vào giao diện quản trị (mục 6), queue `wms.dead-letter`, Get messages để đọc nội dung và header `x-death`. Sau khi sửa lỗi, có thể publish lại tin vào `wms.events` với routing key cũ (nút Publish message) hoặc dùng Shovel. Không có consumer tự động cho queue này.

## 5. Thêm sự kiện mới

1. Thêm routing key (và queue nếu service nhận chưa có queue phù hợp) vào `docker/rabbitmq/definitions.json`: queue durable, không arguments; binding từ `wms.events`.
2. Thêm hằng số tương ứng vào `WmsEvents` (`RoutingKeys`, `Queues`) và cập nhật danh sách `ALL`.
3. Chạy `./gradlew :common:test` trong `backend/`: `WmsEventsDefinitionsTest` kiểm tra hằng số khớp file định nghĩa, routing key tới ít nhất một queue và queue mới được policy dead-letter bao phủ.
4. Nạp lại file: `docker compose up -d rabbitmq-init` (hoặc `docker compose up -d`); queue cũ giữ nguyên, queue mới được tạo thêm.
5. Cập nhật bảng ở mục 3.

## 6. Kết nối, tài khoản và giao diện quản trị

- Service đọc `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` (mặc định `localhost`, `5672`, `guest`, `guest`); trong Docker Compose các biến này được đặt sẵn qua khối `x-backend-env`.
- Tài khoản broker đặt bằng `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` trong `.env` (compose truyền thành `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`). Lưu ý: RabbitMQ chỉ tạo tài khoản mặc định khi volume còn trống. Với volume đã có dữ liệu (máy chủ đang chạy), tạo tài khoản bằng tay rồi mới đổi `.env`:

```bash
docker compose exec rabbitmq rabbitmqctl add_user wms '<mật khẩu>'
docker compose exec rabbitmq rabbitmqctl set_user_tags wms administrator
docker compose exec rabbitmq rabbitmqctl set_permissions -p / wms ".*" ".*" ".*"
```

- Topology được nạp sau khi broker chạy (container `rabbitmq-init`) chứ không nạp lúc khởi động bằng `definitions.import_backend`, vì với cách đó RabbitMQ 3.13 bỏ qua việc tạo tài khoản mặc định (log `Will not seed default virtual host and user`) và volume mới sẽ không có user nào để service kết nối.
- Giao diện quản trị: môi trường dev mở tại http://localhost:15672 (cổng chỉ publish trên 127.0.0.1). Trên VPS cổng không mở ra ngoài, dùng SSH tunnel như hướng dẫn mục 8.2 của [deploy/README.md](../deploy/README.md) rồi truy cập http://localhost:15672.
- Kiểm tra nhanh bằng dòng lệnh:

```bash
docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers
docker compose exec rabbitmq rabbitmqctl list_bindings source_name destination_name routing_key
docker compose exec rabbitmq rabbitmqctl list_policies
```

## 7. Phát và nhận sự kiện trong code (WMS-02.2)

Module `common` tự cấu hình hai thứ cho mọi service có `spring-boot-starter-amqp` (đăng ký trong `META-INF/spring/...AutoConfiguration.imports`, không cần `scanBasePackages`):

| Bean | Lớp | Vai trò |
| --- | --- | --- |
| `MessageConverter` | `WmsMessagingAutoConfiguration` | `Jackson2JsonMessageConverter` dùng `ObjectMapper` của Spring Boot: gửi và nhận JSON, kiểu đích lấy từ tham số của `@RabbitListener` (không phụ thuộc header `__TypeId__` của bên gửi). Service tự khai báo `MessageConverter` riêng thì bean này lùi lại |
| `WmsEventPublisher` | `WmsEventPublisherAutoConfiguration` | Gửi lên `wms.events` kèm `messageId`, `timestamp`, header `x-wms-event`, `x-wms-source`; có `publishAfterCommit` để chỉ gửi khi transaction đã commit |

### 7.1. Phát sự kiện (producer)

```java
@Service
public class PurchaseOrderService {
    private final WmsEventPublisher eventPublisher;   // inject, không cần cấu hình thêm

    @Transactional
    public PurchaseOrderResponse approve(Long id, String approver) {
        PurchaseOrder po = ...;                        // đổi trạng thái, lưu
        OrderCreatedEvent event = new OrderCreatedEvent(po.getId(), po.getPoNumber(), OrderType.PURCHASE,
                po.getStatus().name(), po.getSupplier().getId(), null, po.getWarehouseId(), po.getWarehouseName(),
                po.getTotalAmount(), approver, Instant.now(), lines);
        eventPublisher.publishAfterCommit(WmsEvents.RoutingKeys.ORDER_CREATED, event);
        return PurchaseOrderResponse.from(po);
    }
}
```

- Dùng `publishAfterCommit` trong phương thức `@Transactional`: rollback thì không phát sự kiện, không có "sự kiện ma". Lỗi broker lúc này chỉ ghi log (dữ liệu đã lưu); nếu cần chắc chắn tuyệt đối thì chuyển sang mẫu outbox.
- Dùng `publish` khi không có transaction; phương thức này ném `AmqpException` để caller tự quyết định.
- Payload luôn là record trong `vn.wms.common.messaging.event` (mục 3), không gửi `Map` hay entity JPA.
- order-service và inventory-service khai báo lại exchange (và queue mình đọc) trong `config/RabbitMqConfig` với đúng tham số của `definitions.json`, để chạy service rời trong IDE với broker trống vẫn được.

### 7.2. Nhận sự kiện (consumer)

Queue chỉ chứa một loại sự kiện (`notification.alerts`): khai báo tham số đúng kiểu record, converter tự đọc JSON:

```java
@RabbitListener(queues = WmsEvents.Queues.NOTIFICATION_ALERTS)
public void onLowStock(LowStockAlertEvent event) { ... }
```

Queue chứa nhiều loại sự kiện (`inventory.order-events` nhận `order.#`): nhận `Message` thô, phân loại theo routing key rồi đọc JSON, như `vn.wms.inventory.messaging.OrderEventsListener`. Nghiệp vụ kho không viết trong listener mà implement `OrderEventHandler` (một `@Component`, có thể nhiều cái):

```java
@Component
public class IncomingStockHandler implements OrderEventHandler {
    @Override
    public void onOrderCreated(OrderCreatedEvent event, String messageId) {
        if (event.orderType() != OrderType.PURCHASE) return;
        // tạo tồn chờ nhập cho từng event.items(), dùng messageId để bỏ qua message giao lại
    }
}
```

Quy tắc xử lý lỗi (đã cấu hình trong `application.yml` của order-service, inventory-service, notification-service):

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false   # hết lượt thử thì sang wms.dlx, không quay lại queue
        retry:
          enabled: true                   # thử lại 3 lần trong tiến trình, cách 1s rồi 2s
          max-attempts: 3
```

- Handler ném exception nghiệp vụ: thử lại 3 lần rồi vào `wms.dead-letter`.
- JSON hỏng hoặc không đọc được thành record: ném `AmqpRejectAndDontRequeueException` để vào dead-letter ngay.
- Test unit cho listener và publisher không cần broker (xem `OrderEventsListenerTest`, `WmsEventPublisherTest`); khi chạy test Spring Boot đặt `spring.rabbitmq.listener.simple.auto-startup=false` trong `application-test.yml`.

### 7.3. Gọi đồng bộ bằng OpenFeign

Sự kiện dùng cho những việc "đã xảy ra" và không cần kết quả ngay. Khi lập đơn cần kiểm tra ngay (kho có tồn tại, đang hoạt động không) thì gọi REST qua OpenFeign với Eureka: `vn.wms.order.client.InventoryClient` (`GET /api/inventory/warehouses/{id}`) bọc bởi `InventoryGateway`, quy lỗi về `ApiException`: không có kho thì `NOT_FOUND` (404), kho ngừng hoạt động thì `BAD_REQUEST`, inventory-service không trả lời thì `SERVICE_UNAVAILABLE` (503). Timeout Feign đặt trong `spring.cloud.openfeign.client.config.default` (3 giây kết nối, 5 giây đọc) để request của người dùng không bị treo. Tương tự `ProductClient` cho product-service.

## 8. Việc tiếp theo

- WMS-02.3, WMS-02.4: WebSocket STOMP (`/ws-wms`) trong notification-service đẩy cảnh báo và KPI tới giao diện; consumer cho `notification.order-events` và `notification.inventory-events` làm cùng lúc vì nguồn dữ liệu chính là các sự kiện ở mục 3.
- WMS-09.1, WMS-09.2, WMS-11.1, WMS-11.2: order-service chuyển sang `WmsEventPublisher.publishAfterCommit` với `OrderCreatedEvent`; inventory-service implement `OrderEventHandler` để cập nhật tồn chờ nhập và giữ hàng.
- Khi hệ thống chạy nhiều doanh nghiệp (EPIC 10): thêm `companyId` vào từng record sự kiện.
- Cân nhắc mẫu outbox nếu cần đảm bảo không mất sự kiện khi broker ngừng đúng lúc commit.
