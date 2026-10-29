package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Kết quả quy đổi giữa hai đơn vị tính")
public record CalculateConversionResponse(
        @Schema(description = "ID sản phẩm", example = "1")
        Long productId,

        @Schema(description = "Mã SKU", example = "BEER-TIGER-330")
        String productSku,

        @Schema(description = "ID đơn vị nguồn", example = "6")
        Long fromUomId,

        @Schema(description = "Mã đơn vị nguồn", example = "CTN")
        String fromUomCode,

        @Schema(description = "ID đơn vị đích", example = "4")
        Long toUomId,

        @Schema(description = "Mã đơn vị đích", example = "PACK")
        String toUomCode,

        @Schema(description = "Số lượng tại đơn vị nguồn", example = "2.000")
        BigDecimal inputQuantity,

        @Schema(description = "Số lượng sau quy đổi tại đơn vị đích", example = "8.000")
        BigDecimal convertedQuantity,

        @Schema(description = "Tỷ lệ quy đổi (1 fromUom = conversionRate * toUom)", example = "4.000000")
        BigDecimal conversionRate
) {
}
