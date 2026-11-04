package vn.wms.order.client;

/**
 * Phần thông tin kho mà order-service cần từ inventory-service (WarehouseResponse có nhiều trường hơn,
 * các trường không khai báo ở đây bị bỏ qua khi đọc JSON).
 */
public record WarehouseSummary(Long id, String code, String name, String address, boolean active) {
}
