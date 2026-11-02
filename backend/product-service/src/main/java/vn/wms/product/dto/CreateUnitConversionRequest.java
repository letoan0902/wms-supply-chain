package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Yêu cầu cấu hình quy đổi đơn vị tính cho sản phẩm")
public record CreateUnitConversionRequest(
        @Schema(description = "ID đơn vị tính nguồn (đóng gói lớn hơn)", example = "6")
        @NotNull(message = "Đơn vị nguồn không được để trống")
        Long fromUomId,

        @Schema(description = "ID đơn vị tính đích (đóng gói nhỏ hơn hoặc đơn vị cơ sở)", example = "4")
        @NotNull(message = "Đơn vị đích không được để trống")
        Long toUomId,

        @Schema(description = "Hệ số quy đổi (1 đơn vị nguồn = factor * đơn vị đích)", example = "4.000000")
        @NotNull(message = "Hệ số quy đổi không được để trống")
        @DecimalMin(value = "0.000001", message = "Hệ số quy đổi phải lớn hơn 0")
        @Digits(integer = 12, fraction = 6, message = "Hệ số quy đổi không hợp lệ")
        BigDecimal conversionFactor,

        @Schema(description = "Mã vạch riêng cho quy cách này (nếu có)", example = "8938501234999")
        @Size(max = 64, message = "Mã vạch tối đa 64 ký tự")
        String barcode,

        @Schema(description = "Mô tả quy cách đóng gói", example = "1 Thùng = 4 Lốc")
        @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
        String description
) {
}
