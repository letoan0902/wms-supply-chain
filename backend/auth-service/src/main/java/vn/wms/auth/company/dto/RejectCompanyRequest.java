package vn.wms.auth.company.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectCompanyRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        String reason
) {
}
