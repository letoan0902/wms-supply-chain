package vn.wms.common.plan;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Đọc hash {@code wms:subscription:{companyId}} thành {@link SubscriptionSnapshot}. Lỗi kết nối Redis được ném ra
 * nguyên vẹn để bên gọi tự quyết định (xem quy tắc cho qua khi lỗi ở {@link PlanLimitChecker}).
 */
public class SubscriptionSnapshotReader {

    private final StringRedisTemplate redisTemplate;

    public SubscriptionSnapshotReader(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Ảnh chụp thuê bao của doanh nghiệp, rỗng khi khóa không tồn tại hoặc thiếu trường {@code plan}. */
    public Optional<SubscriptionSnapshot> read(long companyId) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        Map<String, String> entries = hash.entries(SubscriptionCacheKeys.subscriptionKey(companyId));
        return SubscriptionSnapshot.fromHash(entries);
    }
}
