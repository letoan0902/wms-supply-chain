package vn.wms.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Yêu cầu tạo mới lệnh điều chuyển hàng hóa nội bộ (WMS-13.1).
 */
public record CreateStockTransferRequest(
        @NotNull(message = "Kho xuất nguồn không được để trống")
        Long sourceWarehouseId,

        @NotNull(message = "Kho nhập đích không được để trống")
        Long destinationWarehouseId,

        Instant expectedDeliveryDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String notes,

        @NotEmpty(message = "Danh sách hàng điều chuyển không được để trống")
        @Valid
        List<CreateStockTransferItemRequest> items
) {}

