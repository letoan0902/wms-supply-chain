package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO yêu cầu xác nhận nhận hàng và xử lý biên bản tổn thất tại kho đích (WMS-13.2 - FR08).
 */
@Schema(description = "Yêu cầu nhận hàng tại kho đích và lập biên bản thiếu/hỏng")
public record ReceiveStockTransferRequest(
        @Schema(description = "Danh sách mặt hàng kiểm đếm thực nhận tại kho đích")
        @NotEmpty(message = "Danh sách mặt hàng nhận không được để trống")
        List<@Valid ReceiveStockTransferItemRequest> items,

        @Schema(description = "Ghi chú chung hoặc nội dung biên bản sự cố giao nhận", example = "Biên bản kiểm đếm ngày 10/11: Đã nhận đủ hàng")
        String notes
) {
}

