package vn.wms.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Sự kiện Đơn mua hàng đã được phê duyệt (APPROVED), dùng để phát tán sự kiện sang RabbitMQ sau commit (WMS-09.2).
 */
public record PurchaseOrderApprovedEvent(
        Long orderId,
        String poNumber,
        Long supplierId,
        Long warehouseId,
        String warehouseName,
        BigDecimal totalAmount,
        String status,
        List<Item> items,
        Instant timestamp
) {
    public record Item(
            Long productId,
            String skuCode,
            String productName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String uom
    ) {
    }
}
