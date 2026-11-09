package vn.wms.auth.company.entity;

/**
 * Trạng thái của doanh nghiệp. Chỉ doanh nghiệp {@link #ACTIVE} mới đăng nhập và làm mới token được.
 */
public enum CompanyStatus {
    /** Đã đăng ký nhưng chưa kích hoạt qua email. */
    PENDING,
    /** Đang hoạt động bình thường. */
    ACTIVE,
    /** Bị tạm khóa. */
    SUSPENDED
}
