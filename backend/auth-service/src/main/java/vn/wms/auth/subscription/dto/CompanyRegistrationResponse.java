package vn.wms.auth.subscription.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CompanyRegistrationResponse {
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
