package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Thống kê tổng quan đơn bán hàng (SO Metrics Summary) (FR15 - WMS-11.1).
 */
public record SalesOrderSummaryResponse(
        @Schema(description = "Tổng số lượng đơn bán hàng", example = "50")
        long totalOrders,

        @Schema(description = "Số lượng đơn nháp (DRAFT)", example = "5")
        long draftOrders,

        @Schema(description = "Số lượng đơn thiếu hàng / chờ bù (BACKORDER)", example = "3")
        long backorders,

        @Schema(description = "Số lượng đơn đã xác nhận (CONFIRMED)", example = "20")
        long confirmedOrders,

        @Schema(description = "Số lượng đơn đã hoàn tất giao (COMPLETED)", example = "20")
        long completedOrders,

        @Schema(description = "Số lượng đơn đã hủy (CANCELLED)", example = "2")
        long cancelledOrders,

        @Schema(description = "Tổng doanh thu bán hàng các đơn đã xác nhận và hoàn tất (VNĐ)", example = "150000000.00")
        BigDecimal totalRevenue
) {
}
