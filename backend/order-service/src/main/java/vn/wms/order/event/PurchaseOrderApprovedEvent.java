package vn.wms.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Sự kiện Đơn mua hàng đã được phê duyệt (APPROVED), dùng để phát tán sự kiện sang RabbitMQ sau commit (WMS-09.2).
 *
 * @param companyId doanh nghiệp của đơn, để sự kiện RabbitMQ phát sau commit mang đúng header
 *                  {@code x-wms-company-id} (WMS-22.4b)
 */
public record PurchaseOrderApprovedEvent(
        Long companyId,
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
