package vn.wms.order.client;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả giữ chỗ từ inventory-service (WMS-20.4).
 *
 * @param orderId         ID đơn bán
 * @param alreadyReserved true nếu đơn đã có giữ chỗ từ lần gọi trước (gọi lại không giữ thêm)
 * @param lines           tổng số lượng đang giữ theo sản phẩm
 */
public record ReserveOrderResponse(Long orderId, boolean alreadyReserved, List<Line> lines) {

    /**
     * @param productId        sản phẩm
     * @param reservedQuantity tổng số lượng đang giữ cho đơn
     */
    public record Line(Long productId, BigDecimal reservedQuantity) {
    }
}
