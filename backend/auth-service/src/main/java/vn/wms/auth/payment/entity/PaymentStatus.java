package vn.wms.auth.payment.entity;

/**
 * Trạng thái giao dịch thanh toán.
 * <ul>
 *     <li>{@code PENDING}: đã tạo liên kết, đang chờ PayOS báo đã nhận tiền.</li>
 *     <li>{@code PAID}: đã nhận tiền, gói đã được kích hoạt.</li>
 *     <li>{@code CANCELLED}: người dùng hủy, hoặc không tạo được liên kết ở PayOS.</li>
 *     <li>{@code EXPIRED}: quá 15 phút mà chưa thanh toán.</li>
 * </ul>
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    CANCELLED,
    EXPIRED
}
