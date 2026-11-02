package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.product.entity.UnitConversion;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Thông tin chi tiết quy đổi đơn vị tính")
public record UnitConversionResponse(
        @Schema(description = "ID bản ghi quy đổi", example = "1")
        Long id,

        @Schema(description = "ID sản phẩm", example = "10")
        Long productId,

        @Schema(description = "Mã SKU", example = "BEER-TIGER-330")
        String productSku,

        @Schema(description = "Tên sản phẩm", example = "Bia Tiger Lon 330ml")
        String productName,

        @Schema(description = "ID đơn vị tính nguồn", example = "6")
        Long fromUomId,

        @Schema(description = "Mã đơn vị tính nguồn", example = "CTN")
        String fromUomCode,

        @Schema(description = "Tên đơn vị tính nguồn", example = "Thùng")
        String fromUomName,

        @Schema(description = "ID đơn vị tính đích", example = "4")
        Long toUomId,

        @Schema(description = "Mã đơn vị tính đích", example = "PACK")
        String toUomCode,

        @Schema(description = "Tên đơn vị tính đích", example = "Lốc/Gói")
        String toUomName,

        @Schema(description = "Hệ số quy đổi trực tiếp (1 from = factor * to)", example = "4.000000")
        BigDecimal conversionFactor,

        @Schema(description = "Tỷ lệ tích lũy tự động về đơn vị cơ sở của SKU (1 from = factorToBase * baseUom)", example = "24.000000")
        BigDecimal factorToBase,

        @Schema(description = "Mã vạch riêng cho quy cách đóng gói này")
        String barcode,

        @Schema(description = "Mô tả quy cách")
        String description,

        @Schema(description = "Thời điểm tạo")
        Instant createdAt,

        @Schema(description = "Thời điểm cập nhật")
        Instant updatedAt
) {
    public static UnitConversionResponse from(UnitConversion uc, BigDecimal factorToBase) {
        return new UnitConversionResponse(
                uc.getId(),
                uc.getProduct().getId(),
                uc.getProduct().getSku(),
                uc.getProduct().getName(),
                uc.getFromUom().getId(),
                uc.getFromUom().getCode(),
                uc.getFromUom().getName(),
                uc.getToUom().getId(),
                uc.getToUom().getCode(),
                uc.getToUom().getName(),
                uc.getConversionFactor(),
                factorToBase,
                uc.getBarcode(),
                uc.getDescription(),
                uc.getCreatedAt(),
                uc.getUpdatedAt()
        );
    }
}
