package vn.wms.auth.account.entity;

/** Loại mã dùng một lần trong bảng {@code account_tokens} (WMS-23.6). */
public enum AccountTokenType {
    /** Kích hoạt doanh nghiệp vừa đăng ký, gắn với doanh nghiệp (và ADMIN đầu tiên). */
    COMPANY_ACTIVATION,
    /** Đặt lại mật khẩu, gắn với một tài khoản. */
    PASSWORD_RESET
}
