package vn.wms.common.messaging;

import java.util.List;

/**
 * Tên exchange, queue và routing key của RabbitMQ dùng chung cho mọi service (WMS-02.1).
 *
 * <p>Topology thật được nạp vào broker từ {@code docker/rabbitmq/definitions.json} (container rabbitmq-init chạy mỗi lần
 * {@code docker compose up}), nên service chỉ cần
 * tham chiếu hằng số ở đây khi publish hoặc đặt {@code @RabbitListener}; không cần tự khai báo queue. Hằng số và
 * file định nghĩa phải khớp nhau, test {@code WmsEventsDefinitionsTest} trong module common kiểm tra điều đó.
 *
 * <p>Quy ước đặt tên:
 * <ul>
 *   <li>Routing key: {@code <domain>.<action>}, chữ thường, ví dụ {@code order.created}.</li>
 *   <li>Queue: {@code <service-nhận>.<nội-dung>}, ví dụ {@code inventory.order-events} là queue của inventory-service
 *       nhận các sự kiện đơn hàng.</li>
 *   <li>Tin nhắn bị consumer từ chối (reject, không requeue) được chuyển sang exchange {@link #DEAD_LETTER_EXCHANGE}
 *       và nằm chờ ở queue {@link #DEAD_LETTER_QUEUE} để xem lại, nhờ chính sách {@code wms-dead-letter}.</li>
 * </ul>
 *
 * <p>Chi tiết và cách thêm sự kiện mới: docs/MESSAGING.md.
 */
public final class WmsEvents {

    /** Exchange kiểu topic duy nhất cho sự kiện nghiệp vụ. Mọi service publish vào đây. */
    public static final String EXCHANGE = "wms.events";

    /** Exchange nhận tin nhắn chết (dead-letter) từ các queue nghiệp vụ. */
    public static final String DEAD_LETTER_EXCHANGE = "wms.dlx";

    /** Queue giữ tin nhắn chết tối đa 7 ngày hoặc 10000 tin (chính sách wms-dead-letter-retention). */
    public static final String DEAD_LETTER_QUEUE = "wms.dead-letter";

    private WmsEvents() {
    }

    /** Routing key của từng sự kiện và các mẫu binding theo domain. */
    public static final class RoutingKeys {

        /** order-service: đơn hàng vừa được tạo. */
        public static final String ORDER_CREATED = "order.created";

        /** order-service: đơn hàng đã xuất kho (GDN xác nhận). */
        public static final String ORDER_DISPATCHED = "order.dispatched";

        /** inventory-service: tồn kho thay đổi (nhập, xuất, điều chỉnh). */
        public static final String INVENTORY_UPDATED = "inventory.updated";

        /** inventory-service: tồn kho chạm ngưỡng an toàn, cần cảnh báo. */
        public static final String ALERT_LOW_STOCK = "alert.low-stock";

        /** Danh sách mọi routing key sự kiện, dùng cho test và tài liệu. */
        public static final List<String> ALL = List.of(ORDER_CREATED, ORDER_DISPATCHED, INVENTORY_UPDATED, ALERT_LOW_STOCK);

        /** Mẫu binding: mọi sự kiện của domain đơn hàng. */
        public static final String ORDER_ALL = "order.#";

        /** Mẫu binding: mọi sự kiện của domain tồn kho. */
        public static final String INVENTORY_ALL = "inventory.#";

        /** Mẫu binding: mọi cảnh báo. */
        public static final String ALERT_ALL = "alert.#";

        private RoutingKeys() {
        }
    }

    /** Queue của từng service tiêu thụ sự kiện. */
    public static final class Queues {

        /** inventory-service nhận order.created và order.dispatched để cập nhật tồn. */
        public static final String INVENTORY_ORDER_EVENTS = "inventory.order-events";

        /** notification-service nhận mọi sự kiện đơn hàng (order.#) để thông báo. */
        public static final String NOTIFICATION_ORDER_EVENTS = "notification.order-events";

        /** notification-service nhận mọi sự kiện tồn kho (inventory.#). */
        public static final String NOTIFICATION_INVENTORY_EVENTS = "notification.inventory-events";

        /** notification-service nhận cảnh báo (alert.#) để gửi email. */
        public static final String NOTIFICATION_ALERTS = "notification.alerts";

        /** Danh sách mọi queue nghiệp vụ (không gồm queue dead-letter). */
        public static final List<String> ALL = List.of(
                INVENTORY_ORDER_EVENTS, NOTIFICATION_ORDER_EVENTS, NOTIFICATION_INVENTORY_EVENTS, NOTIFICATION_ALERTS);

        private Queues() {
        }
    }
}
