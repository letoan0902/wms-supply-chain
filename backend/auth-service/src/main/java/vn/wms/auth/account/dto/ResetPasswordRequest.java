package vn.wms.auth.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /api/auth/password/reset}. Mật khẩu mới theo cùng quy tắc với đổi mật khẩu
 * ({@code ChangePasswordRequest}: 6 đến 72 ký tự); giao diện tự kiểm tra ô nhập lại.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Mã đặt lại mật khẩu không được để trống")
        @Size(max = 128, message = "Mã đặt lại mật khẩu không hợp lệ")
        String token,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 6, max = 72, message = "Mật khẩu mới phải từ 6 đến 72 ký tự")
        String newPassword
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, newPassword=***]";
    }
}
