package vn.wms.auth.platform.dto;

import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.service.SubscriptionCacheWriter;

import java.time.Instant;

/**
 * Một doanh nghiệp khách hàng trong danh sách của quản trị nền tảng (docs/SAAS_SPEC.md mục 6).
 *
 * @param plan               mã gói của thuê bao, {@code null} khi doanh nghiệp chưa có thuê bao
 * @param planName           tên gói, {@code null} khi chưa có thuê bao
 * @param subscriptionStatus {@code ACTIVE} hoặc {@code EXPIRED}, {@code null} khi chưa có thuê bao
 * @param expiresAt          hạn đang áp dụng: hạn dùng thử với TRIAL, hạn gói với gói trả phí, {@code null} với FREE
 *                           hoặc khi chưa có thuê bao
 * @param userCount          tổng số tài khoản của doanh nghiệp (cả tài khoản đang khóa)
 */
public record PlatformCompanySummary(
        Long id,
        String code,
        String name,
        String taxCode,
        String email,
        String phone,
        CompanyStatus status,
        String plan,
        String planName,
        String subscriptionStatus,
        Instant expiresAt,
        long userCount,
        Instant createdAt,
        Instant activatedAt) {

    public static PlatformCompanySummary of(Company company, Subscription subscription, long userCount) {
        return new PlatformCompanySummary(
                company.getId(),
                company.getCode(),
                company.getName(),
                company.getTaxCode(),
                company.getEmail(),
                company.getPhone(),
                company.getStatus(),
                subscription != null ? subscription.getPlan().getCode() : null,
                subscription != null ? subscription.getPlan().getName() : null,
                subscription != null ? subscription.getStatus().name() : null,
                subscription != null ? SubscriptionCacheWriter.effectiveExpiry(subscription) : null,
                userCount,
                company.getCreatedAt(),
                company.getActivatedAt());
    }
}
