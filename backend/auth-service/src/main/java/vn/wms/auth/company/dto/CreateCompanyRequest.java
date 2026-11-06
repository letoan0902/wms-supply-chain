package vn.wms.auth.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
        @NotBlank(message = "Mã công ty không được để trống")
        @Size(max = 50, message = "Mã công ty không được vượt quá 50 ký tự")
        String code,

        @NotBlank(message = "Tên công ty không được để trống")
        @Size(max = 150, message = "Tên công ty không được vượt quá 150 ký tự")
        String name,

        @Size(max = 30, message = "Mã số thuế không được vượt quá 30 ký tự")
        String taxCode,

        @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
        String email,

        @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
        String phone,

        @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
        String address
) {}
