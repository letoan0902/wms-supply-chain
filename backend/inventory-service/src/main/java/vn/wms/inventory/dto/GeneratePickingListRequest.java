package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Yêu cầu tự động sinh lộ trình lấy hàng (Picking List) theo Zone -> Rack -> Bin và FEFO/FIFO (WMS-12.1).
 */
public record GeneratePickingListRequest(
        @Schema(description = "ID đơn hàng (Sales Order ID nếu có)", example = "101")
        Long orderId,

        @Schema(description = "Mã đơn hàng hiển thị", example = "SO-20261111-0001")
        String orderNumber,

        @NotNull(message = "warehouseId không được để trống")
        @Schema(description = "ID kho xuất hàng", example = "1")
        Long warehouseId,

        @NotEmpty(message = "Danh sách mặt hàng cần nhặt không được để trống")
        @Valid
        @Schema(description = "Danh sách mặt hàng và số lượng cần lấy")
        List<PickingItemDemand> items
) {
}
