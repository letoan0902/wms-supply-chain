package vn.wms.inventory.dto;

import vn.wms.inventory.entity.StocktakeLine;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dòng chênh lệch kiểm kê cho màn hình đối soát (chỉ người duyệt được xem).
 *
 * @param systemQuantity         tồn hệ thống lúc chụp
 * @param systemReservedQuantity số đang giữ chỗ cho đơn lúc chụp
 * @param countedQuantity        số đếm thực tế
 * @param varianceQuantity       chênh lệch = số đếm - tồn hệ thống
 * @param reservedConflict       số đếm nhỏ hơn số giữ chỗ: không duyệt được cho tới khi xử lý
 */
public record StocktakeVarianceLineResponse(
        Long lineId,
        Long binLocationId,
        String binLocationCode,
        String zone,
        Long productId,
        String lotNo,
        BigDecimal systemQuantity,
        BigDecimal systemReservedQuantity,
        BigDecimal countedQuantity,
        BigDecimal varianceQuantity,
        boolean counted,
        boolean unexpected,
        boolean recountRequested,
        boolean reservedConflict,
        int countTimes,
        String lastCountedBy,
        Instant lastCountedAt,
        String reason
) {
    public static StocktakeVarianceLineResponse from(StocktakeLine line) {
        boolean conflict = line.getCountedQuantity() != null
                && line.getCountedQuantity().compareTo(line.getSystemReservedQuantity()) < 0;
        return new StocktakeVarianceLineResponse(
                line.getId(),
                line.getBinLocation().getId(),
                line.getBinLocation().getCode(),
                line.getBinLocation().getZone(),
                line.getProductId(),
                line.getLotNo(),
                line.getSystemQuantity(),
                line.getSystemReservedQuantity(),
                line.getCountedQuantity(),
                line.getVarianceQuantity(),
                line.isCounted(),
                line.isUnexpected(),
                line.isRecountRequested(),
                conflict,
                line.getCountTimes(),
                line.getLastCountedBy(),
                line.getLastCountedAt(),
                line.getReason()
        );
    }
}
