package vn.wms.product.dto;

import vn.wms.product.entity.Product;

/** Thông tin sản phẩm trả về cho client. */
public record ProductResponse(Long id, String sku, String barcode, String name, String description,
                              Long categoryId, String categoryName, Long uomId, String uomCode, boolean active) {

    /** Chuyển entity sang DTO. Gọi trong transaction vì category và uom được nạp lazy. */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                product.getDescription(),
                product.getCategory() == null ? null : product.getCategory().getId(),
                product.getCategory() == null ? null : product.getCategory().getName(),
                product.getUom().getId(),
                product.getUom().getCode(),
                product.isActive());
    }
}
