package vn.wms.inventory.dto;

import vn.wms.inventory.entity.Warehouse;

import java.time.Instant;

/**
 * Dữ liệu trả về cho kho hàng (WMS-05.1, WMS-05.2).
 */
public record WarehouseResponse(
        Long id,
        String code,
        String name,
        String address,
        String managerName,
        String contactPhone,
        Integer capacity,
        boolean active,
        long totalLocations,
        long occupiedLocations,
        double utilizationRate,
        Instant createdAt,
        Instant updatedAt
) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return from(warehouse, 0L, 0L);
    }

    public static WarehouseResponse from(Warehouse warehouse, long totalLocations, long occupiedLocations) {
        double rate = 0.0;
        if (warehouse.getCapacity() != null && warehouse.getCapacity() > 0) {
            rate = Math.round(((double) occupiedLocations / warehouse.getCapacity()) * 1000.0) / 10.0;
        } else if (totalLocations > 0) {
            rate = Math.round(((double) occupiedLocations / totalLocations) * 1000.0) / 10.0;
        }
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getManagerName(),
                warehouse.getContactPhone(),
                warehouse.getCapacity() != null ? warehouse.getCapacity() : 0,
                warehouse.isActive(),
                totalLocations,
                occupiedLocations,
                rate,
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt()
        );
    }
}
