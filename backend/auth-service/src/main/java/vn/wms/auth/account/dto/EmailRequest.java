package vn.wms.auth.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của {@code POST /api/auth/companies/resend-activation} và {@code POST /api/auth/password/forgot}. */
public record EmailRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email
) {
}
