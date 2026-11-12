package vn.wms.order.client;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yêu cầu gửi {@code POST /internal/inventory/reservations} khi xác nhận đơn bán (WMS-20.4): giữ chỗ toàn bộ đơn,
 * tất cả hoặc không, idempotent theo {@code orderId}.
 *
 * @param orderId     ID đơn bán
 * @param orderNumber mã đơn bán
 * @param warehouseId kho xuất (null thì inventory-service giữ ở mọi kho của doanh nghiệp)
 * @param items       các dòng hàng
 */
public record ReserveOrderRequest(Long orderId, String orderNumber, Long warehouseId, List<Item> items) {

    public ReserveOrderRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * @param productId sản phẩm
     * @param skuCode   mã SKU
     * @param quantity  số lượng cần giữ
     */
    public record Item(Long productId, String skuCode, BigDecimal quantity) {
    }
}
