package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Yêu cầu quy đổi số lượng về đơn vị cơ sở để tính tồn kho")
public record ConvertToBaseRequest(
        @Schema(description = "ID đơn vị tính đầu vào (ví dụ: Thùng, Lốc)", example = "6")
        @NotNull(message = "Đơn vị tính không được để trống")
        Long uomId,

        @Schema(description = "Số lượng cần quy đổi", example = "5.000")
        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.0", message = "Số lượng không được âm")
        BigDecimal quantity
) {
}
