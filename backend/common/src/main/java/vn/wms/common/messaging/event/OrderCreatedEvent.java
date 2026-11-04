package vn.wms.common.messaging.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Sự kiện {@code order.created}: một đơn hàng vừa được phê duyệt và cần kho xử lý.
 * order-service phát; inventory-service nhận để chuẩn bị nhập hoặc giữ hàng; notification-service nhận để thông báo.
 *
 * @param orderId       id đơn hàng trong order-service
 * @param orderNumber   mã đơn hiển thị, ví dụ PO-20261104-0001
 * @param orderType     đơn mua hay đơn bán
 * @param status        trạng thái đơn tại thời điểm phát, ví dụ APPROVED
 * @param supplierId    id nhà cung cấp (đơn mua), null với đơn bán
 * @param customerId    id khách hàng (đơn bán), null với đơn mua
 * @param warehouseId   kho nhận hoặc kho xuất
 * @param warehouseName tên kho tại thời điểm lập đơn
 * @param totalAmount   tổng tiền sau chiết khấu và thuế
 * @param createdBy     người lập đơn
 * @param createdAt     thời điểm lập đơn
 * @param items         các dòng hàng
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        Long orderId,
        @JsonAlias({"poNumber", "soNumber", "orderNumber"}) String orderNumber,
        OrderType orderType,
        String status,
        Long supplierId,
        Long customerId,
        Long warehouseId,
        String warehouseName,
        BigDecimal totalAmount,
        String createdBy,
        @JsonAlias({"timestamp", "createdAt"}) Instant createdAt,
        List<OrderLine> items
) {

    public OrderCreatedEvent {
        items = items == null ? List.of() : List.copyOf(items);
        if (orderType == null) {
            orderType = (supplierId != null || (orderNumber != null && orderNumber.startsWith("PO")))
                    ? OrderType.PURCHASE : OrderType.SALES;
        }
    }
}
