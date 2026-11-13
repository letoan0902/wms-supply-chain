package vn.wms.inventory.alert;

import java.math.BigDecimal;

/**
 * Phần thông tin sản phẩm mà cảnh báo tồn kho cần từ product-service ({@code GET /api/products}); các trường khác của
 * JSON bị bỏ qua. Mức tồn an toàn và điểm đặt hàng lại nằm ở bảng {@code products} của product-service.
 */
public record AlertProductInfo(Long id, String sku, String name, BigDecimal safetyStock, BigDecimal reorderPoint,
                               Boolean active) {
}
