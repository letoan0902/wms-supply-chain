package vn.wms.auth.internal;

/**
 * Người nhận email cảnh báo tồn kho. Chỉ có ba trường cần cho việc gửi thư, không bao giờ có mật khẩu băm, tên đăng
 * nhập hay ID.
 *
 * @param fullName họ tên (có thể null)
 * @param email    địa chỉ email
 * @param role     ADMIN hoặc WAREHOUSE_MANAGER
 */
public record AlertRecipientResponse(String fullName, String email, String role) {
}
