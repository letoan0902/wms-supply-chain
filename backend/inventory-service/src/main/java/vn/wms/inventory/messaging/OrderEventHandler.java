package vn.wms.inventory.messaging;

import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderDispatchedEvent;

/**
 * Điểm nối cho nghiệp vụ kho xử lý sự kiện đơn hàng. Khai báo một {@code @Component} implement interface này
 * (ví dụ cập nhật tồn chờ nhập cho đơn mua ở WMS-09.2, giữ hàng cho đơn bán ở WMS-11.2) là
 * {@link OrderEventsListener} tự gọi tới; có thể có nhiều handler cùng lúc.
 *
 * <p>Handler ném exception thì message được thử lại theo cấu hình {@code spring.rabbitmq.listener.simple.retry},
 * hết số lần thử sẽ sang queue {@code wms.dead-letter}. Dùng {@code messageId} để bỏ qua message đã xử lý
 * (RabbitMQ có thể giao lại cùng một message khi consumer mất kết nối giữa chừng).
 */
public interface OrderEventHandler {

    /** Đơn hàng vừa được phê duyệt ({@code order.created}). */
    default void onOrderCreated(OrderCreatedEvent event, String messageId) {
    }

    /** Đơn hàng đã được kho xử lý xong ({@code order.dispatched}). */
    default void onOrderDispatched(OrderDispatchedEvent event, String messageId) {
    }
}
