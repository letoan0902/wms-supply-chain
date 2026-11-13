package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO yêu cầu trừ số lượng tồn của một mặt hàng cụ thể (WMS-12.2).
 */
@Schema(description = "Thông tin dòng mặt hàng cần trừ tồn kho khi xuất xưởng")
public record DeductStockItemRequest(
        @Schema(description = "ID sản phẩm bên product-service", example = "101")
        @NotNull(message = "productId không được để trống")
        Long productId,

        @Schema(description = "Mã SKU của sản phẩm", example = "SKU-MILK-100")
        String skuCode,

        @Schema(description = "Số lượng cần trừ tồn kho", example = "10.000")
        @NotNull(message = "Số lượng trừ không được để trống")
        @DecimalMin(value = "0.001", message = "Số lượng trừ phải lớn hơn 0")
        BigDecimal quantity,

        @Schema(description = "ID vị trí ô kệ chỉ định xuất (nếu bỏ trống, hệ thống tự động trừ theo vị trí có sẵn)", example = "15")
        Long binLocationId,

        @Schema(description = "Mã số lô hàng (nếu có)", example = "LOT-202611-01")
        String lotNo
) {
}
