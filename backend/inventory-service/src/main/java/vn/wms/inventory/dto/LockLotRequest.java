package vn.wms.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu khóa hoặc phong tỏa lô hàng thủ công (WMS-10.2 - FR12).
 */
public record LockLotRequest(
        @NotBlank(message = "Lý do khóa lô không được để trống")
        @Size(max = 255, message = "Lý do khóa tối đa 255 ký tự")
        String reason
) {
}
