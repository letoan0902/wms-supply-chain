package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yêu cầu kiểm tra tồn khả dụng ATP trước khi tạo đơn (FR15 - WMS-11.1).
 */
public record AtpCheckRequest(
        @Schema(description = "ID kho hàng cần kiểm tra (để trống nếu kiểm tra toàn hệ thống)", example = "1")
        Long warehouseId,

        @NotEmpty(message = "Danh sách sản phẩm kiểm tra không được rỗng")
        @Schema(description = "Danh sách mặt hàng và số lượng cần kiểm tra tồn")
        List<ItemCheck> items
) {
    public record ItemCheck(
            @NotNull(message = "ID sản phẩm không được rỗng")
            @Schema(description = "ID sản phẩm", example = "10")
            Long productId,

            @Schema(description = "Mã SKU", example = "MILK-VINA-100")
            String skuCode,

            @Schema(description = "Tên sản phẩm", example = "Sữa tươi tiệt trùng Vinamilk 100ml")
            String productName,

            @NotNull(message = "Số lượng yêu cầu không được rỗng")
            @Schema(description = "Số lượng yêu cầu xuất bán", example = "10.000")
            BigDecimal quantity
    ) {
    }
}
