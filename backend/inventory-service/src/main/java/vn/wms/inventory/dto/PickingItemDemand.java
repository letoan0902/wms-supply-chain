package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Nhu cầu mặt hàng cần lấy trong đơn hàng (WMS-12.1).
 */
public record PickingItemDemand(
        @NotNull(message = "productId không được để trống")
        @Schema(description = "ID sản phẩm", example = "10")
        Long productId,

        @Schema(description = "Mã SKU", example = "SKU-MILK-001")
        String skuCode,

        @Schema(description = "Tên sản phẩm", example = "Sữa tươi tiệt trùng 1L")
        String productName,

        @Schema(description = "Đơn vị tính", example = "Hộp")
        String uom,

        @NotNull(message = "Số lượng cần lấy không được để trống")
        @DecimalMin(value = "0.001", message = "Số lượng cần lấy phải lớn hơn 0")
        @Schema(description = "Số lượng cần lấy", example = "20.000")
        BigDecimal quantity
) {
}
