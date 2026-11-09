package vn.wms.inventory.dto;

import vn.wms.inventory.entity.InventoryLot;
import vn.wms.inventory.entity.StockItem;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Thông tin chi tiết lô hàng (Batch/Lot) theo vị trí ô/kệ lưu trữ (WMS-10.2 - FR12).
 */
public record LotResponse(
        Long stockItemId,
        Long warehouseId,
        String warehouseName,
        String warehouseCode,
        Long binLocationId,
        String binLocationCode,
        String zone,
        String rack,
        String bin,
        Long productId,
        String lotNo,
        LocalDate mfgDate,
        LocalDate expDate,
        BigDecimal quantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        boolean locked,
        String lockedReason,
        Long daysUntilExpiry,
        String expiryStatus
) {
    public static LotResponse from(StockItem item, LocalDate today) {
        LocalDate ref = today != null ? today : LocalDate.now();
        Long days = item.getDaysUntilExpiry(ref);
        String status;
        if (item.getExpDate() == null) {
            status = "NO_EXPIRY";
        } else if (item.isExpired(ref)) {
            status = "EXPIRED";
        } else if (days != null && days <= 30) {
            status = "EXPIRING_SOON";
        } else {
            status = "GOOD";
        }

        return new LotResponse(
                item.getId(),
                item.getBinLocation() != null && item.getBinLocation().getWarehouse() != null
                        ? item.getBinLocation().getWarehouse().getId() : null,
                item.getBinLocation() != null && item.getBinLocation().getWarehouse() != null
                        ? item.getBinLocation().getWarehouse().getName() : null,
                item.getBinLocation() != null && item.getBinLocation().getWarehouse() != null
                        ? item.getBinLocation().getWarehouse().getCode() : null,
                item.getBinLocation() != null ? item.getBinLocation().getId() : null,
                item.getBinLocation() != null ? item.getBinLocation().getCode() : null,
                item.getBinLocation() != null ? item.getBinLocation().getZone() : null,
                item.getBinLocation() != null ? item.getBinLocation().getRack() : null,
                item.getBinLocation() != null ? item.getBinLocation().getBin() : null,
                item.getProductId(),
                item.getLotNo(),
                item.getMfgDate(),
                item.getExpDate(),
                item.getQuantity(),
                item.getReservedQuantity(),
                item.getAvailableQuantity(),
                item.isLocked(),
                item.getLockedReason(),
                days,
                status
        );
    }

    public static LotResponse from(InventoryLot lot, LocalDate today) {
        LocalDate ref = today != null ? today : LocalDate.now();
        Long days = lot.getDaysUntilExpiry(ref);
        String status;
        if (lot.getExpDate() == null) {
            status = "NO_EXPIRY";
        } else if (lot.isExpired(ref)) {
            status = "EXPIRED";
        } else if (days != null && days <= 30) {
            status = "EXPIRING_SOON";
        } else {
            status = "GOOD";
        }

        return new LotResponse(
                lot.getId(),
                lot.getBinLocation() != null && lot.getBinLocation().getWarehouse() != null
                        ? lot.getBinLocation().getWarehouse().getId() : null,
                lot.getBinLocation() != null && lot.getBinLocation().getWarehouse() != null
                        ? lot.getBinLocation().getWarehouse().getName() : null,
                lot.getBinLocation() != null && lot.getBinLocation().getWarehouse() != null
                        ? lot.getBinLocation().getWarehouse().getCode() : null,
                lot.getBinLocation() != null ? lot.getBinLocation().getId() : null,
                lot.getBinLocation() != null ? lot.getBinLocation().getCode() : null,
                lot.getBinLocation() != null ? lot.getBinLocation().getZone() : null,
                lot.getBinLocation() != null ? lot.getBinLocation().getRack() : null,
                lot.getBinLocation() != null ? lot.getBinLocation().getBin() : null,
                lot.getProductId(),
                lot.getLotNumber(),
                lot.getMfgDate(),
                lot.getExpDate(),
                lot.getQuantity(),
                lot.getReservedQuantity(),
                lot.getAvailableQuantity(),
                lot.isLocked(),
                lot.getLockedReason(),
                days,
                status
        );
    }
}
