package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PickingItemDemand(
        @Schema(description = "ID sản phẩm", example = "10")
        Long productId,

        @Schema(description = "Mã SKU", example = "SKU-MILK-001")
        String skuCode,

        @Schema(description = "Tên sản phẩm", example = "Sữa tươi tiệt trùng 1L")
        String productName,

        @Schema(description = "Đơn vị tính", example = "Hộp")
        String uom,

        @Schema(description = "Số lượng cần lấy", example = "20.000")
        BigDecimal quantity
) {
}
