package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu tạo mới danh mục nhóm hàng")
public record CreateCategoryRequest(
        @Schema(description = "Mã nhóm hàng", example = "ELEC")
        @NotBlank(message = "Mã danh mục không được để trống")
        @Size(max = 50, message = "Mã danh mục tối đa 50 ký tự")
        String code,

        @Schema(description = "Tên nhóm hàng", example = "Thiết bị điện tử")
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 150, message = "Tên danh mục tối đa 150 ký tự")
        String name,

        @Schema(description = "Mô tả danh mục", example = "Điện thoại, phụ kiện, linh kiện máy tính")
        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description
) {
}
