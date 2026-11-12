package vn.wms.order.client;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yêu cầu gửi {@code POST /internal/inventory/dispatches} khi hoàn tất phiếu xuất kho (WMS-20.4): trừ tồn đồng bộ,
 * mỗi {@code dispatchKey} chỉ trừ một lần.
 *
 * @param dispatchKey  khóa của phiếu xuất ({@code GDN-<id phiếu>}), gửi lại cùng khóa thì không trừ thêm
 * @param orderId      ID đơn bán
 * @param orderNumber  mã đơn bán
 * @param warehouseId  kho xuất
 * @param dispatchedBy người xác nhận xuất
 * @param items        các dòng xuất
 */
public record DocumentDispatchRequest(String dispatchKey, Long orderId, String orderNumber, Long warehouseId,
                                      String dispatchedBy, List<Item> items) {

    public DocumentDispatchRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * @param productId sản phẩm
     * @param skuCode   mã SKU
     * @param quantity  số lượng xuất
     */
    public record Item(Long productId, String skuCode, BigDecimal quantity) {
    }
}
