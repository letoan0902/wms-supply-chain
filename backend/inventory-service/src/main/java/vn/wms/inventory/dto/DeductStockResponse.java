package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO kết quả sau khi xác nhận trừ tồn kho thành công (WMS-12.2).
 */
@Schema(description = "Kết quả xác nhận trừ tồn kho thành công")
public record DeductStockResponse(
        @Schema(description = "ID đơn hàng liên quan", example = "1001")
        Long orderId,

        @Schema(description = "Mã đơn hàng", example = "SO-20261106-0001")
        String orderNumber,

        @Schema(description = "ID kho xuất hàng", example = "1")
        Long warehouseId,

        @Schema(description = "Lý do xuất", example = "SALES_DISPATCH")
        String reason,

        @Schema(description = "Tổng số lượng đã trừ thành công trên toàn bộ các dòng hàng", example = "25.000")
        BigDecimal totalDeductedQuantity,

        @Schema(description = "Danh sách chi tiết các vị trí ô kệ đã bị trừ tồn kho")
        List<DeductedItemDetail> deductedItems,

        @Schema(description = "Thời điểm hoàn tất giao dịch xuất kho")
        Instant dispatchedAt
) {
}
