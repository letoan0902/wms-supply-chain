package vn.wms.auth.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thân của {@code POST /api/auth/platform/companies/{id}/reject}.
 *
 * @param reason lý do từ chối, bắt buộc, tối đa 255 ký tự; chỉ ghi vào log, không gửi trong email
 */
public record RejectCompanyRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 255, message = "Lý do tối đa 255 ký tự")
        String reason) {
}
