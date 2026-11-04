package vn.wms.common.messaging.event;

import java.math.BigDecimal;

/**
 * Một dòng hàng trong sự kiện đơn hàng, dùng chung cho đơn mua và đơn bán.
 *
 * @param productId   id sản phẩm trong product-service
 * @param skuCode     mã SKU
 * @param productName tên sản phẩm tại thời điểm lập đơn
 * @param quantity    số lượng đặt
 * @param uom         đơn vị tính
 * @param unitPrice   đơn giá
 */
public record OrderLine(Long productId, String skuCode, String productName, BigDecimal quantity, String uom,
                        BigDecimal unitPrice) {
}
