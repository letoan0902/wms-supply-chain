package vn.wms.inventory.alert;

/** Khóa (kho, sản phẩm) dùng để gộp tồn và biến động của một mặt hàng trong một kho. */
public record WarehouseProductKey(Long warehouseId, Long productId) {
}
