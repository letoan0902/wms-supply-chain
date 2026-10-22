package vn.wms.inventory.dto;

import vn.wms.inventory.entity.Warehouse;

/** Thông tin kho trả về cho client. */
public record WarehouseResponse(Long id, String code, String name, String address, boolean active) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName(),
                warehouse.getAddress(), warehouse.isActive());
    }
}
