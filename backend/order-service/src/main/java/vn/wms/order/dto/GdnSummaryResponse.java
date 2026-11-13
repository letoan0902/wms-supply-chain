package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Thống kê tổng quan phiếu xuất kho (GDN Metrics Summary - WMS-12.1).
 */
public record GdnSummaryResponse(
        @Schema(description = "Tổng số phiếu xuất kho", example = "50")
        long totalGdns,

        @Schema(description = "Số phiếu đang nháp / chuẩn bị", example = "5")
        long draftGdns,

        @Schema(description = "Số phiếu đang thực hiện nhặt hàng", example = "8")
        long pickingGdns,

        @Schema(description = "Số phiếu đã xuất kho hoàn tất", example = "35")
        long completedGdns,

        @Schema(description = "Số phiếu đã hủy", example = "2")
        long cancelledGdns,

        @Schema(description = "Tổng số lượng hàng hóa đã xuất", example = "12500.000")
        BigDecimal totalQuantityDispatched,

        @Schema(description = "Tổng giá trị hàng hóa đã xuất (VNĐ)", example = "350000000.00")
        BigDecimal totalAmountDispatched
) {
}
