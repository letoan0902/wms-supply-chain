package vn.wms.inventory.cache;

import java.math.BigDecimal;

/**
 * Tổng tồn của một sản phẩm trong một kho (hoặc mọi kho): tồn thực tế và phần đã giữ chỗ, chỉ tính các dòng tồn
 * chưa khóa và chưa hết hạn, đúng như phép tính ATP của {@code StockService}. Tồn khả dụng = max(0, onHand - reserved)
 * do {@code StockAtpResponse.of} tính.
 */
public record StockTotals(BigDecimal onHand, BigDecimal reserved) {

    public static final StockTotals ZERO = new StockTotals(BigDecimal.ZERO, BigDecimal.ZERO);

    public StockTotals {
        onHand = onHand != null ? onHand : BigDecimal.ZERO;
        reserved = reserved != null ? reserved : BigDecimal.ZERO;
    }
}
