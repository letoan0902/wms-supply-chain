package vn.wms.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Chi tiết dòng sản phẩm khi tạo/cập nhật lệnh điều chuyển kho (WMS-13.1).
 */
public record CreateStockTransferItemRequest(
        @NotNull(message = "ID sản phẩm không được để trống")
        Long productId,

        @NotBlank(message = "Mã SKU không được để trống")
        String skuCode,

        String productName,

        String uom,

        Long sourceBinLocationId,

        @NotNull(message = "Số lượng điều chuyển không được để trống")
        @DecimalMin(value = "0.001", message = "Số lượng điều chuyển phải lớn hơn 0")
        BigDecimal quantity,

        String notes
) {}

