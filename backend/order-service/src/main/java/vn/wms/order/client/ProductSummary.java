package vn.wms.order.client;

/** Phần thông tin sản phẩm mà order-service cần từ product-service (các trường dư sẽ bị bỏ qua khi đọc JSON). */
public record ProductSummary(Long id, String sku, String name) {
}
