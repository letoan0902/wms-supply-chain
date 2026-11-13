package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO yêu cầu xác nhận trừ tồn kho với Khóa bi quan (PESSIMISTIC_WRITE) chống âm kho (WMS-12.2).
 */
@Schema(description = "Yêu cầu trừ tồn kho xuất hàng")
public record DeductStockRequest(
        @Schema(description = "ID đơn bán hàng SO hoặc lệnh xuất kho liên quan (nếu có)", example = "1001")
        Long orderId,

        @Schema(description = "Mã chứng từ / mã đơn hàng SO liên quan (nếu có)", example = "SO-20261106-0001")
        String orderNumber,

        @Schema(description = "ID kho thực hiện xuất hàng", example = "1")
        @NotNull(message = "warehouseId không được để trống")
        Long warehouseId,

        @Schema(description = "Lý do / loại nghiệp vụ xuất kho (SALES_DISPATCH, STOCK_TRANSFER, ADJUSTMENT, MANUAL)", example = "SALES_DISPATCH")
        String reason,

        @Schema(description = "Ghi chú bổ sung", example = "Xuất kho giao hàng khách hàng công ty A")
        String note,

        @Schema(description = "Danh sách chi tiết các mặt hàng cần trừ tồn kho")
        @NotEmpty(message = "Danh sách mặt hàng cần xuất không được rỗng")
        @Valid
        List<DeductStockItemRequest> items
) {
}
