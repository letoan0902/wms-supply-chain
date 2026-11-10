package vn.wms.auth.subscription.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SubscriptionResponse {
    private String plan;
    private String planName;
    private String status;
    private Instant trialEndsAt;
    private Instant expiresAt;
    private Long daysRemaining;
    private boolean readOnly;
    private LimitsDto limits;
    private UsageDto usage;

    @Getter
    @Builder
    public static class LimitsDto {
        private Integer maxUsers;
        private Integer maxWarehouses;
        private Integer maxSkus;
        private Integer maxAiRequests;
        private Boolean apiAccess;
    }

    @Getter
    @Builder
    public static class UsageDto {
        private Long users;
        private Long warehouses;
        private Long skus;
        private Long aiRequests;
    }
}
