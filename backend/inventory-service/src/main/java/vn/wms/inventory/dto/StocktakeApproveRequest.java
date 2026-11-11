package vn.wms.inventory.dto;

import jakarta.validation.constraints.Size;

/**
 * Ghi chú khi Quản lý kho duyệt kết quả kiểm kê (ghi vào phiếu điều chỉnh nếu có chênh lệch).
 */
public record StocktakeApproveRequest(
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String notes
) {
}
