package vn.wms.auth.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RegisterRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 3, max = 50, message = "Tên đăng nhập từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Tên đăng nhập chỉ chứa chữ cái, chữ số, dấu chấm, gạch dưới hoặc gạch ngang")
        String username,
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,
        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 72, message = "Mật khẩu từ 6 đến 72 ký tự")
        String password,
        @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
        String fullName,
        @NotEmpty(message = "Người dùng phải có ít nhất một vai trò")
        Set<String> roles // <-- Có thể truyền 1 role hoặc nhiều role!

) {
}
