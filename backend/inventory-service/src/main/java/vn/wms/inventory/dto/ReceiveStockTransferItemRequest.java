package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO chi tiết nhận hàng từng mặt hàng trong lệnh điều chuyển tại kho đích (WMS-13.2 - FR08).
 */
@Schema(description = "Thông tin thực nhận và ghi nhận tổn thất cho từng mặt hàng điều chuyển")
public record ReceiveStockTransferItemRequest(
        @Schema(description = "ID dòng chi tiết lệnh điều chuyển (StockTransferItem)", example = "101")
        @NotNull(message = "itemId không được để trống")
        Long itemId,

        @Schema(description = "Số lượng thực nhận nguyên vẹn nhập kho đích", example = "20.000")
        @NotNull(message = "receivedQuantity không được để trống")
        @DecimalMin(value = "0.0", message = "Số lượng thực nhận không được âm")
        BigDecimal receivedQuantity,

        @Schema(description = "Số lượng hư hỏng hoặc mất mát phát hiện khi kiểm đếm", example = "0.000")
        @DecimalMin(value = "0.0", message = "Số lượng thiếu/hỏng không được âm")
        BigDecimal damagedQuantity,

        @Schema(description = "ID vị trí ô/kệ cất hàng tại kho đích (bắt buộc khi receivedQuantity > 0)", example = "15")
        Long destinationBinLocationId,

        @Schema(description = "Ghi chú sự cố / lý do hư hỏng cho mặt hàng này", example = "Bao bì nguyên vẹn")
        String notes
) {
    public BigDecimal damagedQuantityOrDefault() {
        return damagedQuantity != null ? damagedQuantity : BigDecimal.ZERO;
    }
}

