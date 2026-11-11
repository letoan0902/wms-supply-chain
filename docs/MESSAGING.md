# Trao đổi sự kiện qua RabbitMQ

Tài liệu cho task WMS-02 (Sprint 2 và 3): exchange, routing key, queue, chính sách dead-letter, cách thêm sự kiện mới (WMS-02.1) và cách phát, nhận sự kiện trong code bằng `WmsEventPublisher`, converter JSON dùng chung và OpenFeign (WMS-02.2, mục 7), hub WebSocket STOMP `/ws-wms` đẩy thông báo thời gian thực tới giao diện (WMS-02.3, mục 8).

## 1. Tổng quan

- Một exchange kiểu **topic** duy nhất `wms.events`: mọi service publish sự kiện vào đây kèm routing key.
- Mỗi service tiêu thụ có queue riêng, bind vào `wms.events` bằng routing key hoặc mẫu (`order.#`). Một sự kiện có thể tới nhiều queue (fan-out theo binding), mỗi queue chỉ có một service đọc.
- Tin nhắn bị consumer từ chối không requeue (ví dụ JSON hỏng, lỗi nghiệp vụ không thể xử lý) được chuyển sang exchange `wms.dlx` và nằm chờ ở queue `wms.dead-letter` để xem lại, không bị mất và không lặp vô hạn.
- Topology được nạp vào broker từ [docker/rabbitmq/definitions.json](../docker/rabbitmq/definitions.json) qua API quản trị bởi container `rabbitmq-init` ([docker/rabbitmq/import-definitions.sh](../docker/rabbitmq/import-definitions.sh)) mỗi lần `docker compose up`; các service nghiệp vụ chỉ khởi động sau khi container này kết thúc thành công. Service không phải tự tạo queue; chỉ tham chiếu hằng số trong `vn.wms.common.messaging.WmsEvents` (module `backend/common`).

Sơ đồ:

```text
order-service --order.created/.dispatched/.received---> [wms.events] ----> inventory.order-events     (inventory-service)
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
| `order.received` | order-service | `inventory.order-events`, `notification.order-events` (qua `order.#`) | Phiếu nhập kho GRN đã lưu (WMS-10.3): inventory-service cộng OnHand tại ô và lô đã chọn, trừ Incoming của PO; mỗi GRN chỉ cộng một lần (bảng `stock_receipts`, khóa `(company_id, grn_id)`) |
| `inventory.updated` | inventory-service | `notification.inventory-events` (notification) | Tồn kho thay đổi sau nhập, xuất, điều chuyển, điều chỉnh: SKU, kho, tồn trước và sau. Điều chỉnh kiểm kê (WMS-14.2): phát sau khi Kế toán duyệt phiếu điều chỉnh, mỗi sản phẩm của phiếu một sự kiện, `reason = ADJUSTMENT`, `referenceNumber` là mã phiếu `ADJ-yyyyMM-NN`, `quantityBefore`/`quantityAfter` là tổng tồn thực tế của sản phẩm trong kho trước và sau, `skuCode` để null (inventory-service không lưu SKU, bên nhận hiển thị theo `productId`), doanh nghiệp ở header `x-wms-company-id` |
| `alert.low-stock` | inventory-service | `notification.alerts` (notification) | Tồn chạm ngưỡng an toàn: SKU, kho, tồn hiện tại, ngưỡng; notification-service đẩy cảnh báo lên WebSocket `/topic/company/{companyId}/alerts` (mục 8) |

Nội dung message là JSON (Jackson). Payload của mỗi routing key là một record trong gói `vn.wms.common.messaging.event` của module `common`, service phát và service nhận dùng chung một kiểu:

| Routing key | Record payload | Trường chính |
| --- | --- | --- |
| `order.created` | `OrderCreatedEvent` | `orderId`, `orderNumber`, `orderType` (PURCHASE hoặc SALES), `status`, `supplierId` hoặc `customerId`, `warehouseId`, `warehouseName`, `totalAmount`, `createdBy`, `createdAt`, `items[]` (`OrderLine`: `productId`, `skuCode`, `productName`, `quantity`, `uom`, `unitPrice`) |
| `order.dispatched` | `OrderDispatchedEvent` | `orderId`, `orderNumber`, `orderType`, `warehouseId`, `dispatchedBy`, `dispatchedAt`, `items[]` |
| `order.received` | `OrderReceivedEvent` | `grnId`, `grnNumber`, `poId`, `poNumber`, `warehouseId`, `supplierId`, `receivedBy`, `receiptDate`, `receivedAt`, `items[]` (`ReceivedItemLine`: `productId`, `skuCode`, `productName`, `acceptedQuantity`, `rejectedQuantity`, `uom`, `locationId`, `locationCode`, `batchNumber`, `mfgDate`, `expDate`) |
| `inventory.updated` | `InventoryUpdatedEvent` | `warehouseId`, `productId`, `skuCode`, `quantityBefore`, `quantityAfter`, `reason`, `referenceNumber`, `updatedAt` |
| `alert.low-stock` | `LowStockAlertEvent` | `warehouseId`, `warehouseName`, `productId`, `skuCode`, `productName`, `quantityOnHand`, `minimumQuantity`, `detectedAt` |

Thông tin kỹ thuật không nằm trong payload mà ở thuộc tính message do `WmsEventPublisher` gắn: `messageId` (UUID, consumer dùng để bỏ qua message giao lại), `timestamp`, header `x-wms-event` (trùng routing key), `x-wms-source` (tên service phát) và `x-wms-company-id` (ID doanh nghiệp phát sinh sự kiện, bên nhận dùng để đặt lại ngữ cảnh doanh nghiệp trước khi xử lý, xem [MULTI_TENANT.md](MULTI_TENANT.md)). Thời gian ghi dạng ISO 8601 UTC, ví dụ `2026-11-04T08:00:00Z` (xem nguyên tắc idempotent trong [ARCHITECTURE.md](ARCHITECTURE.md)).

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
| `WmsEventPublisher` | `WmsEventPublisherAutoConfiguration` | Gửi lên `wms.events` kèm `messageId`, `timestamp`, header `x-wms-event`, `x-wms-source`, `x-wms-company-id`; có `publishAfterCommit` để chỉ gửi khi transaction đã commit |

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

Queue chỉ chứa một loại sự kiện: khai báo tham số đúng kiểu record, converter tự đọc JSON:

```java
@RabbitListener(queues = "<queue chỉ có một loại sự kiện>")
public void onEvent(LowStockAlertEvent event) { ... }
```

Queue chứa nhiều loại sự kiện (`inventory.order-events` nhận `order.#`, cả ba queue của notification-service): nhận `Message` thô, phân loại theo routing key rồi đọc JSON, như `vn.wms.inventory.messaging.OrderEventsListener`. Mỗi queue chỉ được có **một** `@RabbitListener`: hai listener cùng queue sẽ bị broker chia tin luân phiên, mỗi bên chỉ nhận một phần. Nghiệp vụ kho không viết trong listener mà implement `OrderEventHandler` (một `@Component`, có thể nhiều cái):

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

## 8. WebSocket STOMP: thông báo thời gian thực (WMS-02.3)

notification-service là hub WebSocket: nhận sự kiện ở mục 3 từ ba queue của mình, dựng phong bì JSON tiếng Việt rồi đẩy tới trình duyệt qua STOMP. Hub chỉ đẩy một chiều: không có `@MessageMapping`, frame SEND từ client bị từ chối.

### 8.1. Kết nối

| Thành phần | Giá trị |
| --- | --- |
| Endpoint | `/ws-wms` qua api-gateway (route `notification-websocket`, `lb://notification-service`). Bản SockJS (`sockjs-client` nối `http(s)://<host>/ws-wms`) và bản WebSocket thuần (`ws(s)://<host>/ws-wms`) dùng chung đường dẫn |
| Broker | Simple broker trong bộ nhớ, tiền tố `/topic`, nhịp tim 10000/10000 ms |
| Client được làm | CONNECT, SUBSCRIBE vào kênh của doanh nghiệp mình `/topic/company/{companyId}/alerts`, `/orders`, `/inventory`, `/kpi` (WMS-22.4c), UNSUBSCRIBE, DISCONNECT. SUBSCRIBE đích khác, kênh của doanh nghiệp khác hoặc SEND đều nhận frame ERROR |
| Origin | `setAllowedOriginPatterns` lấy từ `CORS_ALLOWED_ORIGINS` (mặc định `http://localhost:5173`; compose dev thêm `http://localhost`, production là `https://<DOMAIN>`) |

Gateway chuyển request có header `Upgrade: websocket` sang `ws://` sau khi load balancer chọn instance (`WebsocketRoutingFilter` của Spring Cloud Gateway 4.1 đổi `http` thành `ws`), nên route dùng `lb://` như các route khác; các request HTTP của SockJS (`/ws-wms/info`, xhr-streaming) đi như request thường. Origin được kiểm tra ở notification-service (gateway không giữ Host gốc nên mọi kết nối qua gateway đều là khác origin), vì vậy `CORS_ALLOWED_ORIGINS` của notification-service phải chứa origin của giao diện. Nginx của frontend phải chuyển tiếp `/ws-wms` tới api-gateway kèm header `Upgrade` và `Connection` như khối `/api/` (Caddy tự hỗ trợ WebSocket).

### 8.2. Xác thực

- Trình duyệt không gắn được header vào handshake, nên gateway để `/ws-wms/**` trong `gateway.jwt.public-paths`; JWT được kiểm tra ở frame STOMP CONNECT: client gửi header `Authorization: Bearer <accessToken>` trong frame.
- `StompAuthChannelInterceptor` (kênh inbound) kiểm tra chữ ký HS256 và hạn giống `JwtAuthenticationGlobalFilter`; token hợp lệ thì gắn `Principal` là `StompPrincipal` gồm username và doanh nghiệp lấy từ claim `companyId` (kèm vai trò trong claim `roles`); thiếu, sai chữ ký, hết hạn hoặc không có `companyId` thì trả frame ERROR và đóng kết nối.
- SUBSCRIBE (WMS-22.4c): chỉ chấp nhận đích đúng dạng `/topic/company/{companyId}/alerts|orders|inventory|kpi` với `{companyId}` bằng doanh nghiệp của phiên lấy ở bước CONNECT. Phiên chưa xác thực, phiên không có doanh nghiệp, kênh của doanh nghiệp khác, topic dùng chung cũ (`/topic/alerts`...) đều nhận frame ERROR. Doanh nghiệp không bao giờ lấy từ giá trị client tự khai báo.
- Cấu hình `app.websocket.auth-required: ${GATEWAY_JWT_ENABLED:false}` (cùng công tắc với gateway; production bật qua `deploy/docker-compose.prod.yml`) và `app.websocket.jwt-secret: ${JWT_SECRET:...}` (phải trùng auth-service và gateway, compose truyền qua `x-backend-env`). Tắt (máy phát triển) thì CONNECT không bắt buộc token: token hợp lệ vẫn được dùng để lấy doanh nghiệp, không có token thì phiên nhận `app.tenant.default-company-id` (`TENANT_DEFAULT_COMPANY_ID`, compose dev là 1); không có cả hai thì không đăng ký được kênh nào. Giới hạn SUBSCRIBE và SEND luôn áp dụng.
- notification-service bật `app.tenant.required`, nên `TenantFilter` bỏ qua `/ws-wms/**` (`app.tenant.public-paths`): handshake của trình duyệt không mang `X-Company-Id`, doanh nghiệp của phiên đến từ frame CONNECT.

### 8.3. Topic và phong bì

Mỗi doanh nghiệp có bốn kênh riêng (WMS-22.4c, thay cho bốn topic dùng chung `/topic/alerts`, `/topic/orders`, `/topic/inventory`, `/topic/kpi` trước đây, ánh xạ một đổi một). Tin của sự kiện nào chỉ đẩy lên kênh của doanh nghiệp phát sinh sự kiện đó (header `x-wms-company-id`, mục 3).

| Topic | `type` | `severity` | Nguồn | Chỉ số KPI tăng |
| --- | --- | --- | --- | --- |
| `/topic/company/{companyId}/alerts` | `LOW_STOCK` | `warning` | `alert.low-stock` | `lowStockAlerts` |
| `/topic/company/{companyId}/orders` | `ORDER_CREATED` | `info` | `order.created` | `purchaseOrders` hoặc `salesOrders` theo `orderType` |
| `/topic/company/{companyId}/orders` | `ORDER_DISPATCHED` | `success` | `order.dispatched` | `dispatchedOrders` |
| `/topic/company/{companyId}/inventory` | `INVENTORY_UPDATED` | `info` | `inventory.updated` | `inventoryMovements` |
| `/topic/company/{companyId}/kpi` | `KPI_UPDATED` | `info` | sau mỗi lần tăng bộ đếm | |

Mọi tin là một phong bì JSON (`vn.wms.notification.realtime.RealtimeNotification`):

```json
{
  "id": "messageId của sự kiện gốc (UUID), không có thì sinh UUID mới",
  "type": "LOW_STOCK",
  "severity": "warning",
  "title": "Cảnh báo tồn kho thấp",
  "message": "SKU-001 Thùng carton tại Kho Hà Nội còn 5, dưới ngưỡng 20",
  "occurredAt": "2026-11-05T08:00:00Z",
  "payload": { "...": "nguyên record sự kiện gốc ở mục 3" }
}
```

- `severity` thuộc `info | success | warning | error`, chữ thường. `title`, `message` tiếng Việt có dấu, dùng thẳng cho toast. Tiêu đề: `Cảnh báo tồn kho thấp`, `Đơn mua hàng mới` hoặc `Đơn bán hàng mới`, `Đơn bán hàng đã xuất kho` hoặc `Đơn mua hàng đã nhập kho`, `Tồn kho thay đổi`, `Cập nhật chỉ số KPI`.
- `occurredAt` lấy thời điểm trong payload (`detectedAt`, `createdAt`, `dispatchedAt`, `updatedAt`), không có thì lấy timestamp của message RabbitMQ, cuối cùng là giờ hệ thống.
- Broker giao lại một sự kiện thì thông báo được đẩy lại với cùng `id`; frontend bỏ tin trùng theo `id`. Tin `KPI_UPDATED` luôn có `id` mới.

### 8.4. Bộ đếm KPI

`payload` của `KPI_UPDATED` và `data` của `GET /api/notifications/realtime/kpi` (trả `ApiResponse<KpiSnapshot>`, giao diện gọi khi vừa mở trang):

```json
{ "date": "2026-11-05", "purchaseOrders": 3, "salesOrders": 5, "dispatchedOrders": 2, "lowStockAlerts": 1, "inventoryMovements": 12, "updatedAt": "2026-11-05T08:00:00Z" }
```

- Đếm riêng từng doanh nghiệp, trong ngày theo giờ Việt Nam (Asia/Ho_Chi_Minh), lưu Redis: hash `wms:notification:kpi:<companyId>:<yyyy-MM-dd>`, hết hạn sau 2 ngày. `updatedAt` là null khi trong ngày chưa có sự kiện.
- Chống đếm trùng khi broker giao lại: `SET NX` khóa `wms:notification:kpi:<companyId>:processed:<messageId>` giữ 1 ngày; sự kiện không có `messageId` thì cứ đếm.
- `GET /api/notifications/realtime/kpi` trả bộ đếm của doanh nghiệp trong `X-Company-Id` (gateway gắn theo JWT); thiếu doanh nghiệp trả 403 `COMPANY_REQUIRED`.
- Redis lỗi không làm hỏng việc tiêu thụ sự kiện: ghi log warn, thông báo chính vẫn được đẩy, chỉ bỏ lượt đẩy `/topic/company/{companyId}/kpi`. API `GET .../kpi` khi Redis lỗi trả 503 `SERVICE_UNAVAILABLE` thay vì báo số 0 sai.

### 8.5. Queue `notification.alerts`: hai định dạng, một listener

Queue này có từ trước với `NotificationConsumer` đọc `NotificationMessage` (`eventType`, `recipient`, `subject`, `content`) để gửi email. Vì một queue chỉ được có một listener, `AlertEventsListener` thay cho consumer cũ và phân loại theo loại sự kiện (routing key nhận được, không có thì header `x-wms-event`):

| Routing key | Payload | Xử lý |
| --- | --- | --- |
| `alert.low-stock` | `LowStockAlertEvent` (mục 3) | Đẩy `/topic/company/{companyId}/alerts`, tăng `lowStockAlerts` của doanh nghiệp đó. Chưa gửi email vì sự kiện không có người nhận |
| `alert.*` khác, ví dụ `alert.email` | `NotificationMessage` | Gửi email và ghi `notification_logs` (có `company_id` của sự kiện) như trước (`NotificationService`). Tin không có `recipient` (loại cảnh báo chưa hỗ trợ) thì ghi log và bỏ qua |

Bên phát muốn gửi email thì dùng routing key `alert.*` khác `alert.low-stock` với payload `NotificationMessage`, phát bằng `WmsEventPublisher` trong ngữ cảnh doanh nghiệp của mình và chỉ chọn người nhận thuộc doanh nghiệp đó (notification-service không tự tra người nhận). JSON hỏng ở cả hai nhánh chuyển dead-letter.

### 8.5a. Doanh nghiệp của sự kiện (WMS-22.4c)

Cả ba listener (`AlertEventsListener`, `OrderEventsListener`, `InventoryEventsListener`) đọc doanh nghiệp theo thứ tự: header `x-wms-company-id` (do `WmsEventPublisher` gắn), không có thì trường `companyId` ở cấp ngoài cùng của JSON. Có doanh nghiệp thì toàn bộ xử lý chạy trong `TenantContext.runAs(companyId, ...)`: kênh WebSocket, bộ đếm KPI và bản ghi `notification_logs` đều thuộc doanh nghiệp đó. Không có doanh nghiệp thì ghi một dòng log cảnh báo và bỏ qua (ack): không ném lỗi, không thử lại, không vào dead-letter, không gửi email. Khác inventory-service (chuyển dead-letter để phát lại) vì thông báo bỏ lỡ không làm sai dữ liệu.

### 8.6. Thử nhanh không cần service phát

1. Chạy stack (`docker compose up -d`), mở giao diện đã nối WebSocket hoặc một client STOMP bất kỳ nối `ws://localhost:8080/ws-wms` (frame CONNECT kèm `Authorization: Bearer <accessToken>`, compose dev không bắt buộc) rồi SUBSCRIBE `/topic/company/1/alerts` và `/topic/company/1/kpi`.
2. Vào giao diện quản trị RabbitMQ (mục 6), Exchanges, `wms.events`, Publish message: Routing key `alert.low-stock`; Properties `content_type` = `application/json` và `message_id` = một UUID; Headers `x-wms-company-id` = `1` (thiếu thì tin bị bỏ qua); Payload:

```json
{ "warehouseId": 5, "warehouseName": "Kho Hà Nội", "productId": 100, "skuCode": "SKU-001", "productName": "Thùng carton", "quantityOnHand": 5, "minimumQuantity": 20, "detectedAt": "2026-11-05T08:00:00Z" }
```

3. Client nhận phong bì `LOW_STOCK` trên `/topic/company/1/alerts` và `KPI_UPDATED` trên `/topic/company/1/kpi`. Publish lại đúng `message_id` đó thì thông báo được đẩy lại nhưng KPI không tăng. Làm tương tự với `order.created` (payload `OrderCreatedEvent`) và `inventory.updated` (payload `InventoryUpdatedEvent`).
4. Xem số hiện tại: `GET http://localhost:8080/api/notifications/realtime/kpi` (kèm token, hoặc gọi thẳng cổng 8085 với header `X-Company-Id: 1`).

## 9. Việc tiếp theo

- WMS-02.4 (làm ở PR frontend riêng, thư mục `frontend/src/features/realtime/`): giao diện nối `/ws-wms` bằng SockJS, gửi header `Authorization` trong frame CONNECT, SUBSCRIBE bốn topic ở mục 8.3, bỏ tin trùng theo `id`, lấy KPI ban đầu từ `GET /api/notifications/realtime/kpi`; `frontend/nginx.conf` có khối chuyển tiếp `/ws-wms` tới api-gateway (mục 8.1). Sau khi cả hai phần được merge cần thử trọn luồng trên máy chủ.
- Gửi email cho `alert.low-stock` cần danh sách người nhận (ví dụ quản lý của kho); hiện cảnh báo này chỉ đẩy WebSocket (mục 8.5).
- WMS-09.1, WMS-09.2, WMS-11.1, WMS-11.2: order-service chuyển sang `WmsEventPublisher.publishAfterCommit` với `OrderCreatedEvent`; inventory-service implement `OrderEventHandler` để cập nhật tồn chờ nhập và giữ hàng.
- Đã làm ở WMS-22.4c: WebSocket tách kênh theo doanh nghiệp (`/topic/company/{companyId}/...`), interceptor chỉ cho SUBSCRIBE kênh của `companyId` trong JWT, bộ đếm KPI tách khóa Redis theo `companyId`. Còn lại: thêm `companyId` vào từng record sự kiện (hiện doanh nghiệp đi theo header, listener đã đọc được trường này nếu có).
- Lọc thông báo theo kho người dùng được phân quyền (WMS-04.2): thêm `warehouseId` vào đích hoặc lọc trước khi đẩy.
- Cân nhắc mẫu outbox nếu cần đảm bảo không mất sự kiện khi broker ngừng đúng lúc commit.
