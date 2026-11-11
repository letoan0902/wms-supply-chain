package vn.wms.inventory.dto;

import jakarta.validation.constraints.Size;

/**
 * Lý do kèm theo thao tác hủy, từ chối đợt kiểm kê hoặc ghi lý do chênh lệch.
 */
public record StocktakeReasonRequest(
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
