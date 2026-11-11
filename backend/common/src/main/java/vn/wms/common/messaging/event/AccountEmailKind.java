package vn.wms.common.messaging.event;

/** Loại email tài khoản trong {@link AccountEmailRequestedEvent} (WMS-23.6). */
public enum AccountEmailKind {
    /** Liên kết kích hoạt doanh nghiệp vừa đăng ký, hiệu lực 24 giờ. */
    ACTIVATION,
    /** Liên kết đặt lại mật khẩu, hiệu lực 30 phút. */
    PASSWORD_RESET
}
