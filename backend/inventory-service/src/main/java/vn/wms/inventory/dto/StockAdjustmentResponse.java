package vn.wms.inventory.dto;

import vn.wms.inventory.entity.StockAdjustment;
import vn.wms.inventory.entity.StockAdjustmentLine;
import vn.wms.inventory.entity.StockAdjustmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Phiếu điều chỉnh tồn kho. {@code lines} chỉ có ở API chi tiết (null ở danh sách).
 */
public record StockAdjustmentResponse(
        Long id,
        String code,
        Long sessionId,
        String sessionCode,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        StockAdjustmentStatus status,
        BigDecimal totalIncreaseQuantity,
        BigDecimal totalDecreaseQuantity,
        int totalLines,
        String notes,
        String requestedBy,
        Instant requestedAt,
        String approvedBy,
        Instant approvedAt,
        String rejectedBy,
        Instant rejectedAt,
        String rejectReason,
        Instant createdAt,
        List<Line> lines
) {

    /**
     * Dòng phiếu điều chỉnh; {@code quantityBefore}, {@code quantityAfter} có giá trị khi phiếu đã duyệt.
     */
    public record Line(Long id, Long stocktakeLineId, Long binLocationId, String binLocationCode, Long productId,
                       String lotNo, BigDecimal systemQuantity, BigDecimal countedQuantity,
                       BigDecimal differenceQuantity, BigDecimal quantityBefore, BigDecimal quantityAfter,
                       String reason) {

        static Line from(StockAdjustmentLine line) {
            return new Line(line.getId(), line.getStocktakeLineId(), line.getBinLocation().getId(),
                    line.getBinLocation().getCode(), line.getProductId(), line.getLotNo(), line.getSystemQuantity(),
                    line.getCountedQuantity(), line.getDifferenceQuantity(), line.getQuantityBefore(),
                    line.getQuantityAfter(), line.getReason());
        }
    }

    public static StockAdjustmentResponse from(StockAdjustment adjustment, boolean withLines) {
        return new StockAdjustmentResponse(
                adjustment.getId(),
                adjustment.getCode(),
                adjustment.getSession().getId(),
                adjustment.getSession().getCode(),
                adjustment.getWarehouse().getId(),
                adjustment.getWarehouse().getCode(),
                adjustment.getWarehouse().getName(),
                adjustment.getStatus(),
                adjustment.getTotalIncreaseQuantity(),
                adjustment.getTotalDecreaseQuantity(),
                adjustment.getLines().size(),
                adjustment.getNotes(),
                adjustment.getRequestedBy(),
                adjustment.getRequestedAt(),
                adjustment.getApprovedBy(),
                adjustment.getApprovedAt(),
                adjustment.getRejectedBy(),
                adjustment.getRejectedAt(),
                adjustment.getRejectReason(),
                adjustment.getCreatedAt(),
                withLines ? adjustment.getLines().stream().map(Line::from).toList() : null
        );
    }
}
