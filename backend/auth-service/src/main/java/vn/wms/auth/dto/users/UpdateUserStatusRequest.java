package vn.wms.auth.dto.users;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO khóa / mở khóa trạng thái tài khoản.
 *
 * @param enabled      trạng thái mới của tài khoản
 * @param reasonChange lý do thay đổi do quản trị viên nhập; không bắt buộc vì backend chưa lưu lý do,
 *                     client cũ chỉ gửi {@code enabled} vẫn gọi được
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "Trạng thái kích hoạt không được để trống")
        Boolean enabled,
        @Size(max = 255, message = "Lý do thay đổi trạng thái tối đa 255 ký tự")
        String reasonChange
) {
}
