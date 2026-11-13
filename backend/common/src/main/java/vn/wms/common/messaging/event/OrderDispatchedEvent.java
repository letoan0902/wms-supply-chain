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
 * @param dispatchKey  khóa trừ tồn của phiếu xuất (WMS-20.4, dạng {@code GDN-<id phiếu>}): order-service đã trừ tồn
 *                     đồng bộ với khóa này trước khi phát, inventory-service thấy khóa đã có trong
 *                     {@code stock_dispatches} thì không trừ lần nữa; null với sự kiện phát trước WMS-20.4
 */
public record OrderDispatchedEvent(Long orderId, String orderNumber, OrderType orderType, Long warehouseId,
                                   String dispatchedBy, Instant dispatchedAt, List<OrderLine> items,
                                   String dispatchKey) {

    public OrderDispatchedEvent {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** Sự kiện không mang khóa trừ tồn (dạng trước WMS-20.4); bên nhận chống trùng theo messageId. */
    public OrderDispatchedEvent(Long orderId, String orderNumber, OrderType orderType, Long warehouseId,
                                String dispatchedBy, Instant dispatchedAt, List<OrderLine> items) {
        this(orderId, orderNumber, orderType, warehouseId, dispatchedBy, dispatchedAt, items, null);
    }
}
