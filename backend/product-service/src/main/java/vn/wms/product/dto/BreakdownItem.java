package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Một thành phần đóng gói sau khi phân rã tồn kho")
public record BreakdownItem(
        @Schema(description = "ID đơn vị tính", example = "6")
        Long uomId,

        @Schema(description = "Mã đơn vị tính", example = "CTN")
        String uomCode,

        @Schema(description = "Tên đơn vị tính", example = "Thùng")
        String uomName,

        @Schema(description = "Số lượng đóng gói", example = "2")
        BigDecimal quantity,

        @Schema(description = "Hệ số so với đơn vị cơ sở", example = "24.000000")
        BigDecimal factorToBase
) {
}
