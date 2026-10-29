package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Yêu cầu quy đổi số lượng giữa hai đơn vị tính bất kỳ")
public record CalculateConversionRequest(
        @Schema(description = "ID đơn vị tính nguồn", example = "6")
        @NotNull(message = "Đơn vị tính nguồn không được để trống")
        Long fromUomId,

        @Schema(description = "ID đơn vị tính đích", example = "4")
        @NotNull(message = "Đơn vị tính đích không được để trống")
        Long toUomId,

        @Schema(description = "Số lượng tại đơn vị nguồn", example = "2.000")
        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.0", message = "Số lượng không được âm")
        BigDecimal quantity
) {
}
