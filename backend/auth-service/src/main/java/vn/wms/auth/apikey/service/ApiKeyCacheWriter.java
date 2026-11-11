package vn.wms.auth.apikey.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.wms.auth.apikey.entity.ApiKey;
import vn.wms.common.plan.ApiKeyCacheKeys;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghi khóa tra cứu API key lên Redis cho api-gateway (WMS-25.1). Tên khóa và tên trường lấy từ
 * {@link ApiKeyCacheKeys} của module common.
 *
 * <ul>
 *   <li>Key đang hoạt động: hash {@code wms:apikey:{sha256}} gồm {@code companyId}, {@code keyId}, {@code prefix}.</li>
 *   <li>Key đã thu hồi: xóa hash đó và xóa trường {@code keyId} trong {@code wms:apikey:last-used}.</li>
 * </ul>
 *
 * <p>Trong giao dịch thì chỉ ghi sau khi commit thành công (giống {@code SubscriptionCacheWriter}), để Redis không mang
 * key của một giao dịch đã rollback, và key vừa thu hồi chỉ biến mất khỏi Redis khi việc thu hồi đã được lưu. Lỗi
 * Redis chỉ ghi cảnh báo: {@link ApiKeyScheduler} ghi lại toàn bộ định kỳ nên Redis tự lành.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyCacheWriter {

    private final RedisTemplate<String, String> redisTemplate;

    /** Ghi hoặc xóa khóa tra cứu theo trạng thái hiện tại của key (sau commit nếu đang trong giao dịch). */
    public void sync(ApiKey apiKey) {
        if (apiKey == null || apiKey.getId() == null) {
            return;
        }
        String key = ApiKeyCacheKeys.lookupKey(apiKey.getKeyHash());
        String keyId = String.valueOf(apiKey.getId());
        Map<String, String> fields = apiKey.isActive() ? toFields(apiKey) : null;
        afterCommit(() -> {
            if (fields != null) {
                put(key, fields);
            } else {
                remove(key, keyId);
            }
        });
    }

    /** Các trường của hash tra cứu. */
    static Map<String, String> toFields(ApiKey apiKey) {
        Map<String, String> fields = new HashMap<>();
        fields.put(ApiKeyCacheKeys.FIELD_COMPANY_ID, String.valueOf(apiKey.getCompanyId()));
        fields.put(ApiKeyCacheKeys.FIELD_KEY_ID, String.valueOf(apiKey.getId()));
        fields.put(ApiKeyCacheKeys.FIELD_PREFIX, apiKey.getKeyPrefix());
        return fields;
    }

    /**
     * Đọc thời điểm dùng gần nhất do gateway ghi vào hash {@code wms:apikey:last-used}. Bỏ qua trường hỏng.
     * Redis lỗi thì trả rỗng.
     */
    public Map<Long, Instant> readLastUsed() {
        Map<Long, Instant> result = new LinkedHashMap<>();
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(ApiKeyCacheKeys.LAST_USED_KEY);
            if (entries == null) {
                return result;
            }
            entries.forEach((field, value) -> {
                try {
                    long keyId = Long.parseLong(String.valueOf(field).trim());
                    long epochMillis = Long.parseLong(String.valueOf(value).trim());
                    result.put(keyId, Instant.ofEpochMilli(epochMillis));
                } catch (NumberFormatException ex) {
                    log.debug("Bỏ qua trường hỏng {} trong {}", field, ApiKeyCacheKeys.LAST_USED_KEY);
                }
            });
        } catch (Exception ex) {
            log.warn("Không đọc được {} trên Redis: {}", ApiKeyCacheKeys.LAST_USED_KEY, ex.getMessage());
        }
        return result;
    }

    private void put(String key, Map<String, String> fields) {
        try {
            redisTemplate.opsForHash().putAll(key, fields);
        } catch (Exception ex) {
            log.warn("Không ghi được API key ID {} lên Redis: {}", fields.get(ApiKeyCacheKeys.FIELD_KEY_ID),
                    ex.getMessage());
        }
    }

    private void remove(String key, String keyId) {
        try {
            redisTemplate.delete(key);
            redisTemplate.opsForHash().delete(ApiKeyCacheKeys.LAST_USED_KEY, keyId);
        } catch (Exception ex) {
            log.warn("Không xóa được API key ID {} khỏi Redis: {}", keyId, ex.getMessage());
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
