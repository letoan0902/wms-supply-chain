package vn.wms.inventory.client;

/** Phần thông tin sản phẩm mà inventory-service cần từ product-service (các trường dư sẽ bị bỏ qua khi đọc JSON). */
public record ProductSummary(Long id, String sku, String name) {
}
