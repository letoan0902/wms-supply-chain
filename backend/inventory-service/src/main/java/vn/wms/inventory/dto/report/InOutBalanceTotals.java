package vn.wms.inventory.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Dòng tổng cộng của báo cáo Xuất - Nhập - Tồn, tính trên mọi dòng khớp bộ lọc (không chỉ trang hiện tại). */
@Schema(description = "Tổng cộng báo cáo Xuất - Nhập - Tồn")
public record InOutBalanceTotals(
        @Schema(description = "Tổng tồn đầu kỳ; null nếu kỳ bắt đầu trước lúc có sổ kho", example = "1000.000")
        BigDecimal openingQuantity,
        @Schema(description = "Tổng nhập trong kỳ; null nếu kỳ bắt đầu trước lúc có sổ kho", example = "500.000")
        BigDecimal quantityIn,
        @Schema(description = "Tổng xuất trong kỳ; null nếu kỳ bắt đầu trước lúc có sổ kho", example = "300.000")
        BigDecimal quantityOut,
        @Schema(description = "Tổng tồn cuối kỳ; null nếu kỳ kết thúc trước lúc có sổ kho", example = "1200.000")
        BigDecimal closingQuantity,
        @Schema(description = "Tổng giá trị tồn đầu kỳ, luôn null vì chưa có giá vốn")
        BigDecimal openingValue,
        @Schema(description = "Tổng giá trị nhập, luôn null vì chưa có giá vốn")
        BigDecimal valueIn,
        @Schema(description = "Tổng giá trị xuất, luôn null vì chưa có giá vốn")
        BigDecimal valueOut,
        @Schema(description = "Tổng giá trị tồn cuối kỳ, luôn null vì chưa có giá vốn")
        BigDecimal closingValue
) {
}
