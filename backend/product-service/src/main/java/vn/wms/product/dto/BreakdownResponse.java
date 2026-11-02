package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Kết quả phân rã tồn kho cơ sở thành các cấp đóng gói")
public record BreakdownResponse(
        @Schema(description = "ID sản phẩm", example = "1")
        Long productId,

        @Schema(description = "Mã SKU", example = "BEER-TIGER-330")
        String productSku,

        @Schema(description = "Tổng số lượng theo đơn vị cơ sở", example = "50.000")
        BigDecimal totalBaseQuantity,

        @Schema(description = "Đơn vị tính cơ sở", example = "Lon")
        String baseUomName,

        @Schema(description = "Danh sách các cấp đóng gói quy đổi từ lớn đến nhỏ")
        List<BreakdownItem> breakdown,

        @Schema(description = "Chuỗi tóm tắt hiển thị trực quan", example = "2 Thùng, 2 Lon")
        String summary
) {
}
