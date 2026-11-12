package vn.wms.inventory.alert;

/** Người nhận email cảnh báo do auth-service trả về (chỉ họ tên, email, vai trò). */
public record AlertRecipient(String fullName, String email, String role) {
}
