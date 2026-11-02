package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Kết quả quy đổi số lượng về đơn vị cơ sở")
public record ConvertToBaseResponse(
        @Schema(description = "ID sản phẩm", example = "1")
        Long productId,

        @Schema(description = "Mã SKU", example = "BEER-TIGER-330")
        String productSku,

        @Schema(description = "ID đơn vị tính đầu vào", example = "6")
        Long inputUomId,

        @Schema(description = "Mã đơn vị tính đầu vào", example = "CTN")
        String inputUomCode,

        @Schema(description = "Tên đơn vị tính đầu vào", example = "Thùng")
        String inputUomName,

        @Schema(description = "Số lượng đầu vào", example = "5.000")
        BigDecimal inputQuantity,

        @Schema(description = "ID đơn vị tính cơ sở của SKU", example = "3")
        Long baseUomId,

        @Schema(description = "Mã đơn vị tính cơ sở", example = "CAN")
        String baseUomCode,

        @Schema(description = "Tên đơn vị tính cơ sở", example = "Lon")
        String baseUomName,

        @Schema(description = "Số lượng đã quy đổi về đơn vị cơ sở (để tính tồn kho)", example = "120.000")
        BigDecimal baseQuantity,

        @Schema(description = "Tỷ lệ quy đổi (1 inputUom = conversionRate * baseUom)", example = "24.000000")
        BigDecimal conversionRate
) {
}
