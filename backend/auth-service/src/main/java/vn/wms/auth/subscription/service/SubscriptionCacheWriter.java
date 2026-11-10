package vn.wms.auth.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.wms.auth.subscription.entity.Plan;
import vn.wms.auth.subscription.entity.Subscription;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Ghi bộ nhớ đệm thuê bao trên Redis theo docs/SAAS_SPEC.md mục 2.7: hash {@code wms:subscription:{companyId}}.
 * auth-service là nơi duy nhất ghi khóa này; Gateway và PlanLimitChecker (WMS-23.2) chỉ đọc.
 *
 * <p>Mọi giá trị là chuỗi: {@code expiresAt} dạng ISO-8601 (ví dụ {@code 2026-12-09T03:00:00Z}), rỗng với gói FREE,
 * với TRIAL là ngày hết dùng thử; các giới hạn {@code -1} là không giới hạn; {@code apiAccess} là {@code true}/{@code false}.
 *
 * <p>Nếu đang trong một giao dịch, giá trị được chụp lại ngay nhưng chỉ ghi sau khi giao dịch commit thành công,
 * để Redis không bao giờ mang trạng thái của một giao dịch đã rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionCacheWriter {

    public static final String KEY_PREFIX = "wms:subscription:";
    public static final String FIELD_PLAN = "plan";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_EXPIRES_AT = "expiresAt";
    public static final String FIELD_MAX_USERS = "maxUsers";
    public static final String FIELD_MAX_WAREHOUSES = "maxWarehouses";
    public static final String FIELD_MAX_SKUS = "maxSkus";
    public static final String FIELD_MAX_AI_REQUESTS = "maxAiRequests";
    public static final String FIELD_API_ACCESS = "apiAccess";

    private final RedisTemplate<String, String> redisTemplate;

    /** Khóa Redis của thuê bao một doanh nghiệp. */
    public static String keyOf(Long companyId) {
        return KEY_PREFIX + companyId;
    }

    /** Ghi lại toàn bộ hash của thuê bao (sau khi giao dịch hiện tại commit, nếu có). */
    public void write(Subscription subscription) {
        if (subscription == null || subscription.getCompany() == null || subscription.getPlan() == null) {
            return;
        }
        String key = keyOf(subscription.getCompany().getId());
        Map<String, String> fields = toFields(subscription);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    put(key, fields);
                }
            });
        } else {
            put(key, fields);
        }
    }

    /** Các trường của hash theo mục 2.7. */
    static Map<String, String> toFields(Subscription subscription) {
        Plan plan = subscription.getPlan();
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_PLAN, plan.getCode());
        fields.put(FIELD_STATUS, subscription.getStatus().name());
        fields.put(FIELD_EXPIRES_AT, toIso(effectiveExpiry(subscription)));
        fields.put(FIELD_MAX_USERS, String.valueOf(plan.getMaxUsers()));
        fields.put(FIELD_MAX_WAREHOUSES, String.valueOf(plan.getMaxWarehouses()));
        fields.put(FIELD_MAX_SKUS, String.valueOf(plan.getMaxSkus()));
        fields.put(FIELD_MAX_AI_REQUESTS, String.valueOf(plan.getMaxAiRequests()));
        fields.put(FIELD_API_ACCESS, String.valueOf(Boolean.TRUE.equals(plan.getApiAccess())));
        return fields;
    }

    /** Mốc hết hạn hiệu lực: FREE không có, TRIAL là trial_ends_at, gói trả phí là expires_at. */
    static Instant effectiveExpiry(Subscription subscription) {
        String planCode = subscription.getPlan().getCode();
        if (SubscriptionService.PLAN_FREE.equals(planCode)) {
            return null;
        }
        if (SubscriptionService.PLAN_TRIAL.equals(planCode)) {
            return subscription.getTrialEndsAt();
        }
        return subscription.getExpiresAt();
    }

    private static String toIso(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private void put(String key, Map<String, String> fields) {
        try {
            redisTemplate.opsForHash().putAll(key, fields);
        } catch (Exception e) {
            log.warn("Không thể ghi trạng thái thuê bao vào Redis (khóa {}): {}", key, e.getMessage());
        }
    }
}
