package vn.wms.auth.company.dto;

import lombok.Builder;
import vn.wms.auth.company.entity.CompanyStatus;

import java.time.Instant;

@Builder
public record AdminCompanyResponse(
        Long id,
        String code,
        String name,
        String taxCode,
        String email,
        String phone,
        String address,
        CompanyStatus status,
        Instant activatedAt,
        Instant createdAt,
        AdminUserSummary adminUser,
        CompanySubscriptionSummary subscription
) {
    @Builder
    public record AdminUserSummary(
            Long id,
            String username,
            String email,
            String fullName,
            boolean enabled
    ) {}

    @Builder
    public record CompanySubscriptionSummary(
            String plan,
            String status,
            Instant trialEndsAt,
            Instant expiresAt
    ) {}
}
