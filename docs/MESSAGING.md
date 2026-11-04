# Trao đổi sự kiện qua RabbitMQ

Tài liệu cho task WMS-02 (Sprint 2 và 3): exchange, routing key, queue, chính sách dead-letter và cách thêm sự kiện mới. Phần producer, consumer trong từng service (WMS-02.2) và WebSocket (WMS-02.3, WMS-02.4) sẽ bổ sung vào đây khi làm.

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

Nội dung message là JSON (Jackson), mỗi sự kiện nên có `eventId` (UUID) và `occurredAt` để consumer xử lý idempotent (xem nguyên tắc trong [ARCHITECTURE.md](ARCHITECTURE.md)). Cấu trúc chi tiết từng message sẽ chốt ở WMS-02.2.

## 4. Dead-letter

Hai policy trong `definitions.json`:

| Policy | Áp dụng cho | Nội dung |
| --- | --- | --- |
| `wms-dead-letter` | queue có tên bắt đầu bằng `inventory.`, `notification.`, `order.` | `dead-letter-exchange = wms.dlx`: tin bị reject (không requeue) hoặc hết hạn được chuyển sang `wms.dlx`, giữ nguyên routing key, kèm header `x-death` ghi queue gốc và lý do |
| `wms-dead-letter-retention` | `wms.dead-letter` | giữ tối đa 7 ngày (`message-ttl` 604800000 ms) và 10000 tin, đầy thì bỏ tin cũ nhất (`overflow = drop-head`) |

Consumer cần đặt `spring.rabbitmq.listener.simple.default-requeue-rejected=false` (notification-service đã đặt) để tin lỗi đi vào dead-letter thay vì quay lại queue vô hạn.

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

## 7. Việc tiếp theo

- WMS-02.2: `RabbitTemplate` với `Jackson2JsonMessageConverter` trong order-service và inventory-service, consumer trong inventory-service và notification-service, OpenFeign cho gọi đồng bộ; chốt cấu trúc JSON từng sự kiện.
- WMS-02.3, WMS-02.4: WebSocket STOMP (`/ws-wms`) đẩy cảnh báo và KPI tới giao diện; nguồn dữ liệu là các sự kiện ở mục 3.
- Vận hành: đổi tài khoản `guest` trên máy chủ production sang tài khoản riêng theo mục 6.
