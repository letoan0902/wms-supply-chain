package vn.wms.inventory.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Thông tin sản phẩm mà báo cáo kho cần từ product-service (WMS-17.1): mã, tên, danh mục, đơn vị tính.
 * Các trường khác trong JSON của product-service bị bỏ qua.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductInfo(Long id, String sku, String name, Long categoryId, String categoryName, String uomCode,
                          String uomName) {
}
