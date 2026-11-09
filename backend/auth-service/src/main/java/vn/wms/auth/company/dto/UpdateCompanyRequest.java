package vn.wms.auth.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ADMIN cập nhật thông tin doanh nghiệp của chính mình. Mã, email, trạng thái doanh nghiệp không sửa qua API này.
 */
public record UpdateCompanyRequest(
        @NotBlank(message = "Tên công ty không được để trống")
        @Size(max = 150, message = "Tên công ty không được vượt quá 150 ký tự")
        String name,

        @Size(max = 30, message = "Mã số thuế không được vượt quá 30 ký tự")
        String taxCode,

        @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
        String phone,

        @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
        String address
) {}
