package vn.wms.inventory.dto;

import vn.wms.inventory.entity.BinLocation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dữ liệu trả về cho vị trí ô/kệ (WMS-06.1).
 */
public record BinLocationResponse(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String zone,
        String rack,
        String bin,
        String code,
        BigDecimal maxWeight,
        String status,
        boolean active,
        long itemCount,
        BigDecimal totalQuantity,
        Instant createdAt
) {

    public static BinLocationResponse from(BinLocation loc) {
        return from(loc, 0L, BigDecimal.ZERO);
    }

    public static BinLocationResponse from(BinLocation loc, long itemCount, BigDecimal totalQuantity) {
        return new BinLocationResponse(
                loc.getId(),
                loc.getWarehouse().getId(),
                loc.getWarehouse().getCode(),
                loc.getWarehouse().getName(),
                loc.getZone(),
                loc.getRack(),
                loc.getBin(),
                loc.getCode(),
                loc.getMaxWeight(),
                loc.getStatus() != null ? loc.getStatus() : "EMPTY",
                loc.isActive(),
                itemCount,
                totalQuantity != null ? totalQuantity : BigDecimal.ZERO,
                loc.getCreatedAt()
        );
    }
}

