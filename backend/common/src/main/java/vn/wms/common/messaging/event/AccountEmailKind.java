package vn.wms.common.messaging.event;

/** Loại email tài khoản trong {@link AccountEmailRequestedEvent} (WMS-23.6). */
public enum AccountEmailKind {
    /** Liên kết kích hoạt doanh nghiệp vừa đăng ký, hiệu lực 24 giờ. */
    ACTIVATION,
    /** Liên kết đặt lại mật khẩu, hiệu lực 30 phút. */
    PASSWORD_RESET,
    /** Quản trị nền tảng đã duyệt bản đăng ký: doanh nghiệp hoạt động, liên kết tới trang đăng nhập, không hết hạn. */
    COMPANY_APPROVED,
    /** Quản trị nền tảng đã từ chối bản đăng ký: liên kết tới trang chủ, không hết hạn, không kèm lý do. */
    COMPANY_REJECTED
}
