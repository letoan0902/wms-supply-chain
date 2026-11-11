package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record PickingListResponse(
        @Schema(description = "ID đơn hàng", example = "101")
        Long orderId,

        @Schema(description = "Mã đơn hàng", example = "SO-20261111-0001")
        String orderNumber,

        @Schema(description = "ID kho xuất hàng", example = "1")
        Long warehouseId,

        @Schema(description = "Tên kho xuất hàng", example = "Kho Tổng Tân Bình")
        String warehouseName,

        @Schema(description = "Tổng số chặng lấy hàng", example = "4")
        int totalLines,

        @Schema(description = "Tổng số lượng hàng cần nhặt", example = "35.000")
        BigDecimal totalQuantity,

        @Schema(description = "Danh sách các điểm dừng theo thứ tự tối ưu Zone -> Rack -> Bin")
        List<PickingItemResponse> items
) {
}
