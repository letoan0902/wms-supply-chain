package vn.wms.order.dto;

import java.math.BigDecimal;

/**
 * DTO Thống kê tổng quan tình hình Nhập kho (GRN Metrics Summary - FR11).
 */
public record GrnSummaryResponse(
        long totalGrn,
        long completedGrn,
        long draftGrn,
        long cancelledGrn,
        BigDecimal totalAcceptedQuantity,
        BigDecimal totalRejectedQuantity,
        BigDecimal totalReceivedAmount
) {
}
