package vn.wms.inventory.dto;

import vn.wms.inventory.entity.StockAdjustment;
import vn.wms.inventory.entity.StockAdjustmentStatus;
import vn.wms.inventory.entity.StocktakeScopeType;
import vn.wms.inventory.entity.StocktakeSession;
import vn.wms.inventory.entity.StocktakeStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Thông tin đợt kiểm kê. Không chứa tồn hệ thống của từng dòng (xem API chênh lệch, chỉ dành cho người duyệt).
 * {@code locations} chỉ có ở API chi tiết (null ở API danh sách); với phạm vi WAREHOUSE và ZONES, danh sách vị trí
 * được chốt khi bắt đầu kiểm kê nên ở trạng thái DRAFT danh sách này rỗng.
 */
public record StocktakeSessionResponse(
        Long id,
        String code,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        StocktakeStatus status,
        StocktakeScopeType scopeType,
        List<String> zones,
        List<Long> productIds,
        LocalDate plannedDate,
        String notes,
        long totalLocations,
        long totalLines,
        long countedLines,
        long uncountedLines,
        String startedBy,
        Instant startedAt,
        String finishedBy,
        Instant finishedAt,
        String managerApprovedBy,
        Instant managerApprovedAt,
        String approvedBy,
        Instant approvedAt,
        String closedBy,
        Instant closedAt,
        String closeReason,
        Long adjustmentId,
        String adjustmentCode,
        StockAdjustmentStatus adjustmentStatus,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<StocktakeLocationResponse> locations
) {
    public static StocktakeSessionResponse of(StocktakeSession session, long totalLocations, long totalLines,
                                              long uncountedLines, List<StocktakeLocationResponse> locations,
                                              StockAdjustment adjustment) {
        return new StocktakeSessionResponse(
                session.getId(),
                session.getCode(),
                session.getWarehouse().getId(),
                session.getWarehouse().getCode(),
                session.getWarehouse().getName(),
                session.getStatus(),
                session.getScopeType(),
                session.getZoneList(),
                new ArrayList<>(session.getProductIdSet()),
                session.getPlannedDate(),
                session.getNotes(),
                totalLocations,
                totalLines,
                totalLines - uncountedLines,
                uncountedLines,
                session.getStartedBy(),
                session.getStartedAt(),
                session.getFinishedBy(),
                session.getFinishedAt(),
                session.getManagerApprovedBy(),
                session.getManagerApprovedAt(),
                session.getApprovedBy(),
                session.getApprovedAt(),
                session.getClosedBy(),
                session.getClosedAt(),
                session.getCloseReason(),
                adjustment != null ? adjustment.getId() : null,
                adjustment != null ? adjustment.getCode() : null,
                adjustment != null ? adjustment.getStatus() : null,
                session.getCreatedBy(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                locations
        );
    }
}
