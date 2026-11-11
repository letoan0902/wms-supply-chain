package vn.wms.auth.account.dto;

import java.time.Instant;

/**
 * Kết quả {@code POST /api/auth/companies/activate}.
 *
 * @param companyCode mã doanh nghiệp
 * @param companyName tên doanh nghiệp
 * @param status      trạng thái sau khi kích hoạt, luôn {@code ACTIVE}
 * @param activatedAt thời điểm kích hoạt
 * @param username    tên đăng nhập của ADMIN nhận liên kết, để trang đăng nhập điền sẵn
 */
public record ActivationResponse(String companyCode, String companyName, String status, Instant activatedAt,
                                 String username) {
}
