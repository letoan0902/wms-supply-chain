package vn.wms.order.client;

import java.util.List;

/**
 * Yêu cầu gửi {@code POST /internal/inventory/receipts/location-check} trước khi lưu phiếu nhập kho (WMS-20.4):
 * kho nhận và các cặp sản phẩm, vị trí sẽ được cộng tồn.
 *
 * @param warehouseId kho nhận hàng
 * @param lines       các dòng có số lượng đạt lớn hơn 0
 */
public record ReceiptLocationCheckRequest(Long warehouseId, List<Line> lines) {

    public ReceiptLocationCheckRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /**
     * @param productId    sản phẩm
     * @param locationId   ID vị trí ô kệ trên phiếu (có thể null)
     * @param locationCode mã vị trí ô kệ trên phiếu (có thể null)
     */
    public record Line(Long productId, Long locationId, String locationCode) {
    }
}
