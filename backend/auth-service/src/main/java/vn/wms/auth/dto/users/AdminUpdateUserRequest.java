package vn.wms.auth.dto.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * DTO cập nhật thông tin người dùng dành cho Quản trị viên (Admin).
 */
public record AdminUpdateUserRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
        String fullName,

        @NotEmpty(message = "Người dùng phải có ít nhất một vai trò")
        Set<String> roles,

        Set<Long> warehouseIds,

        Boolean enabled
) {
}
