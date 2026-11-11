package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record GeneratePickingListRequest(
        @Schema(description = "ID đơn hàng", example = "101")
        Long orderId,

        @Schema(description = "Mã đơn hàng", example = "SO-20261111-0001")
        String orderNumber,

        @Schema(description = "ID kho xuất hàng", example = "1")
        Long warehouseId,

        @Schema(description = "Danh sách mặt hàng và số lượng cần lấy")
        List<PickingItemDemand> items
) {
}
