package vn.wms.inventory.dto;

import vn.wms.inventory.entity.Warehouse;

/**
 * Thông tin tối thiểu của một kho để chọn trong danh sách (kho đích của lệnh điều chuyển):
 * chỉ mã, tên và trạng thái, không kèm địa chỉ, người phụ trách hay số liệu tồn kho.
 */
public record WarehouseOptionResponse(
        Long id,
        String code,
        String name,
        boolean active
) {
    public static WarehouseOptionResponse from(Warehouse warehouse) {
        return new WarehouseOptionResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName(), warehouse.isActive());
    }
}
