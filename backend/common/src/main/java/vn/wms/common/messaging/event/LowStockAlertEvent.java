package vn.wms.common.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sự kiện {@code alert.low-stock}: tồn kho của một SKU chạm hoặc xuống dưới ngưỡng tối thiểu.
 *
 * @param warehouseId     kho
 * @param warehouseName   tên kho
 * @param productId       id sản phẩm
 * @param skuCode         mã SKU
 * @param productName     tên sản phẩm
 * @param quantityOnHand  tồn hiện tại
 * @param minimumQuantity ngưỡng tối thiểu đã cấu hình
 * @param detectedAt      thời điểm phát hiện
 */
public record LowStockAlertEvent(Long warehouseId, String warehouseName, Long productId, String skuCode,
                                 String productName, BigDecimal quantityOnHand, BigDecimal minimumQuantity,
                                 Instant detectedAt) {
}
