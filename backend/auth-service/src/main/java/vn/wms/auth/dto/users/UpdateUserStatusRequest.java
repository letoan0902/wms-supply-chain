package vn.wms.auth.dto.users;

import jakarta.validation.constraints.NotNull;

/**
 * DTO khóa / mở khóa trạng thái tài khoản.
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "Trạng thái kích hoạt không được để trống")
        Boolean enabled,
        @NotNull(message = "Vui lòng nhập lý do muốn thay đổi trạng thái")
        String reasonChange
) {
}
