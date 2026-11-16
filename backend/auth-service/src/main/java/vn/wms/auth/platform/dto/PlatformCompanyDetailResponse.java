package vn.wms.auth.platform.dto;

import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.entity.User;
import vn.wms.auth.subscription.entity.Plan;

import java.time.Instant;
import java.util.List;

/**
 * Chi tiết một doanh nghiệp khách hàng ({@code GET /api/auth/platform/companies/{id}}).
 *
 * @param limits   giới hạn của gói, {@code null} khi doanh nghiệp chưa có thuê bao
 * @param usage    mức dùng hiện tại, {@code null} khi doanh nghiệp chưa có thuê bao
 * @param admins   tài khoản có vai trò ADMIN của doanh nghiệp
 * @param payments 10 thanh toán gần nhất của doanh nghiệp, mới tạo trước
 */
public record PlatformCompanyDetailResponse(
        CompanyInfo company,
        Limits limits,
        Usage usage,
        List<Admin> admins,
        List<PlatformPayment> payments) {

    /** Các trường của {@link PlatformCompanySummary} cộng địa chỉ. */
    public record CompanyInfo(
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
            Instant activatedAt,
            String address) {

        public static CompanyInfo of(PlatformCompanySummary summary, String address) {
            return new CompanyInfo(summary.id(), summary.code(), summary.name(), summary.taxCode(), summary.email(),
                    summary.phone(), summary.status(), summary.plan(), summary.planName(),
                    summary.subscriptionStatus(), summary.expiresAt(), summary.userCount(), summary.createdAt(),
                    summary.activatedAt(), address);
        }
    }

    /** Giới hạn của gói; {@code -1} là không giới hạn. */
    public record Limits(Integer maxUsers, Integer maxWarehouses, Integer maxSkus, Integer maxAiRequests,
                         Boolean apiAccess) {

        public static Limits of(Plan plan) {
            return new Limits(plan.getMaxUsers(), plan.getMaxWarehouses(), plan.getMaxSkus(),
                    plan.getMaxAiRequests(), plan.getApiAccess());
        }
    }

    /**
     * Mức dùng: {@code users} là số tài khoản đang bật (đếm trong cơ sở dữ liệu); {@code warehouses}, {@code skus},
     * {@code aiRequests} đọc từ Redis do service nghiệp vụ ghi, {@code null} khi không đọc được.
     */
    public record Usage(Long users, Long warehouses, Long skus, Long aiRequests) {
    }

    /** Một tài khoản ADMIN của doanh nghiệp. */
    public record Admin(Long id, String username, String fullName, String email, boolean enabled) {

        public static Admin of(User user) {
            return new Admin(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.isEnabled());
        }
    }
}
