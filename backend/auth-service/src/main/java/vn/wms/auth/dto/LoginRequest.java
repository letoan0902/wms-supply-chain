package vn.wms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Yêu cầu đăng nhập. */
public record LoginRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(max = 50, message = "Tên đăng nhập tối đa 50 ký tự")
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(max = 100, message = "Mật khẩu tối đa 100 ký tự")
        String password) {
}
