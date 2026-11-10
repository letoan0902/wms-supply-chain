package vn.wms.auth.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import vn.wms.common.plan.SubscriptionCacheKeys;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Map;

/**
 * Đọc mức sử dụng do service nghiệp vụ ghi lên Redis (docs/SAAS_SPEC.md mục 2.7) để trả trong
 * {@code GET /api/auth/subscription}: số kho, số SKU ở hash {@code wms:usage:{companyId}} và lượt AI của tháng hiện
 * tại (giờ Việt Nam) ở {@code wms:usage:ai:{companyId}:{yyyyMM}}. Số người dùng đếm trực tiếp trong cơ sở dữ liệu
 * nên không đọc ở đây.
 *
 * <p>Không kết nối được Redis thì trả các số là {@code null} (chưa biết) và ghi cảnh báo, không làm hỏng API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionUsageReader {

    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock = Clock.system(SubscriptionCacheKeys.BUSINESS_ZONE);

    /**
     * Mức sử dụng trên Redis của một doanh nghiệp.
     *
     * @param warehouses số kho đang hoạt động, {@code null} khi inventory-service chưa ghi
     * @param skus       số SKU đang hoạt động, {@code null} khi product-service chưa ghi
     * @param aiRequests lượt AI trong tháng, {@code 0} khi chưa có bộ đếm
     */
    public record Usage(Long warehouses, Long skus, Long aiRequests) {
        static final Usage UNKNOWN = new Usage(null, null, null);
    }

    public Usage read(long companyId) {
        try {
            Map<Object, Object> usage = redisTemplate.opsForHash().entries(SubscriptionCacheKeys.usageKey(companyId));
            String aiKey = SubscriptionCacheKeys.aiUsageKey(companyId, YearMonth.now(clock));
            Long aiRequests = toLong(redisTemplate.opsForValue().get(aiKey));
            return new Usage(
                    toLong(usage == null ? null : usage.get(SubscriptionCacheKeys.USAGE_FIELD_WAREHOUSES)),
                    toLong(usage == null ? null : usage.get(SubscriptionCacheKeys.USAGE_FIELD_SKUS)),
                    aiRequests != null ? aiRequests : 0L);
        } catch (RuntimeException ex) {
            log.warn("Không đọc được mức sử dụng của doanh nghiệp {} trên Redis: {}", companyId, ex.getMessage());
            return Usage.UNKNOWN;
        }
    }

    private static Long toLong(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
