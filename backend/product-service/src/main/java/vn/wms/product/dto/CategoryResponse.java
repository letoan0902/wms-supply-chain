package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.product.entity.Category;

@Schema(description = "Thông tin nhóm hàng / ngành hàng")
public record CategoryResponse(
        @Schema(description = "ID danh mục", example = "1")
        Long id,

        @Schema(description = "Mã danh mục", example = "BEV")
        String code,

        @Schema(description = "Tên danh mục", example = "Đồ uống & Giải khát")
        String name,

        @Schema(description = "Mô tả chi tiết")
        String description
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName(), category.getDescription());
    }
}
