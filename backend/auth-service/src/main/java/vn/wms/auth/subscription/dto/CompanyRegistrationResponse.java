package vn.wms.auth.subscription.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Kết quả {@code POST /api/auth/companies/register}. Không có token: giao diện hiển thị hướng dẫn mở email khi
 * {@link #activationRequired} là {@code true} (doanh nghiệp {@code PENDING}, WMS-23.6), ngược lại chuyển sang đăng nhập.
 */
@Getter
@Builder
public class CompanyRegistrationResponse {
    /** {@code true}: phải bấm liên kết kích hoạt trong email trước khi đăng nhập được. */
    private boolean activationRequired;
    private CompanyDto company;
    private AdminDto admin;
    private SubscriptionDto subscription;

    @Getter
    @Builder
    public static class CompanyDto {
        private Long id;
        private String code;
        private String name;
        private String status;
    }

    @Getter
    @Builder
    public static class AdminDto {
        private String username;
        private String email;
    }

    @Getter
    @Builder
    public static class SubscriptionDto {
        private String plan;
        private String status;
        private Instant trialEndsAt;
    }
}
