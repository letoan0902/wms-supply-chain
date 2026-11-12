package vn.wms.inventory.dto.internal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả giữ chỗ cho đơn bán (WMS-20.4).
 *
 * @param orderId         ID đơn bán
 * @param alreadyReserved true nếu đơn đã có giữ chỗ đang hiệu lực từ lần gọi trước, lần này không đổi gì
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
