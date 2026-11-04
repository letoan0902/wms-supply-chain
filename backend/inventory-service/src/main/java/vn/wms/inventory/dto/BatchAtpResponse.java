package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Kết quả kiểm tra tồn khả dụng ATP theo danh sách (WMS-11.1).
 */
@Schema(description = "Kết quả kiểm tra tồn ATP hàng loạt")
public record BatchAtpResponse(
        @Schema(description = "ID kho hàng đã lọc")
        Long warehouseId,

        @Schema(description = "Danh sách tồn khả dụng từng sản phẩm")
        List<StockAtpResponse> items
) {
}
