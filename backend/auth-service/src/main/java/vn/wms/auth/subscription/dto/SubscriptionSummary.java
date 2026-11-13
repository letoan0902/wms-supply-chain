package vn.wms.auth.subscription.dto;

import vn.wms.auth.subscription.entity.Subscription;

import java.time.Instant;

/**
 * Tóm tắt thuê bao trả kèm kết quả đăng nhập và {@code GET /api/auth/me} (docs/SAAS_SPEC.md mục 2.1).
 *
 * @param plan      FREE, TRIAL hoặc PRO
 * @param status    ACTIVE hoặc EXPIRED
 * @param expiresAt mốc hết hạn: TRIAL là ngày hết dùng thử, PRO là ngày hết hạn, FREE là null
 */
public record SubscriptionSummary(String plan, String status, Instant expiresAt) {

    public static SubscriptionSummary from(Subscription subscription) {
        if (subscription == null) {
            return null;
        }
        String plan = subscription.getPlan().getCode();
        Instant expiresAt = switch (plan) {
            case "FREE" -> null;
            case "TRIAL" -> subscription.getTrialEndsAt();
            default -> subscription.getExpiresAt();
        };
        return new SubscriptionSummary(plan, subscription.getStatus().name(), expiresAt);
    }
}
