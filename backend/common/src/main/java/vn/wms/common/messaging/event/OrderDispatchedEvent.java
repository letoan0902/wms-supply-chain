package vn.wms.common.messaging.event;

import java.time.Instant;
import java.util.List;

/**
 * Sự kiện {@code order.dispatched}: đơn hàng đã được kho xử lý xong (xuất kho giao hàng hoặc nhập kho hoàn tất).
 *
 * @param orderId      id đơn hàng trong order-service
 * @param orderNumber  mã đơn hiển thị
 * @param orderType    đơn mua hay đơn bán
 * @param warehouseId  kho thực hiện
 * @param dispatchedBy người xác nhận
 * @param dispatchedAt thời điểm hoàn tất
 * @param items        các dòng hàng đã xử lý
 */
public record OrderDispatchedEvent(Long orderId, String orderNumber, OrderType orderType, Long warehouseId,
                                   String dispatchedBy, Instant dispatchedAt, List<OrderLine> items) {

    public OrderDispatchedEvent {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
