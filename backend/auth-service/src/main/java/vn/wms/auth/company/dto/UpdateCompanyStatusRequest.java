package vn.wms.auth.company.dto;

import jakarta.validation.constraints.NotNull;
import vn.wms.auth.company.entity.CompanyStatus;

public record UpdateCompanyStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        CompanyStatus status
) {
}
