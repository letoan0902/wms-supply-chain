package vn.wms.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Yêu cầu cập nhật lệnh điều chuyển hàng hóa khi còn ở trạng thái DRAFT (WMS-13.1).
 */
public record UpdateStockTransferRequest(
        Instant expectedDeliveryDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String notes,

        @NotEmpty(message = "Danh sách hàng điều chuyển không được để trống")
        @Valid
        List<CreateStockTransferItemRequest> items
) {}

