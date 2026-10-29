package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu tạo mới đơn vị tính")
public record CreateUomRequest(
        @Schema(description = "Mã đơn vị tính", example = "TUBE")
        @NotBlank(message = "Mã đơn vị tính không được để trống")
        @Size(max = 20, message = "Mã đơn vị tính tối đa 20 ký tự")
        @Pattern(regexp = "^[A-Z0-9-_]+$", message = "Mã đơn vị tính chỉ gồm chữ in hoa, số và dấu gạch")
        String code,

        @Schema(description = "Tên đơn vị tính", example = "Tuýp")
        @NotBlank(message = "Tên đơn vị tính không được để trống")
        @Size(max = 100, message = "Tên đơn vị tính tối đa 100 ký tự")
        String name
) {
}
