package vn.wms.auth.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.wms.auth.company.entity.CompanyStatus;

/**
 * Thân của {@code PUT /api/auth/platform/companies/{id}/status}.
 *
 * @param status {@code SUSPENDED} (tạm khóa, chỉ từ ACTIVE) hoặc {@code ACTIVE} (mở khóa, chỉ từ SUSPENDED)
 * @param reason lý do, tùy chọn, chỉ ghi vào log
 */
public record UpdateCompanyStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        CompanyStatus status,
        @Size(max = 255, message = "Lý do tối đa 255 ký tự")
        String reason) {
}
