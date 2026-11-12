package vn.wms.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import vn.wms.common.plan.SubscriptionCacheKeys;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Hợp đồng với api-gateway và các service khác: mọi thứ auth-service ghi lên Redis là chuỗi UTF-8 thường, kể cả tên
 * trường và giá trị trường của hash. Các test khác giả lập {@code HashOperations} nên không thấy được byte thật;
 * test này kiểm tra byte do chính bean {@link RedisConfig#redisTemplate} sinh ra.
 */
class RedisConfigTest {

    private final RedisTemplate<String, String> template =
            new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class));

    @Test
    void hashFieldsAndValuesAreWrittenAsPlainUtf8Strings() {
        assertThat(bytes(template.getHashKeySerializer(), SubscriptionCacheKeys.FIELD_PLAN))
                .isEqualTo(SubscriptionCacheKeys.FIELD_PLAN.getBytes(StandardCharsets.UTF_8));
        assertThat(bytes(template.getHashValueSerializer(), "Gói Khởi Đầu"))
                .isEqualTo("Gói Khởi Đầu".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void keysAndValuesAreWrittenAsPlainUtf8Strings() {
        String key = SubscriptionCacheKeys.subscriptionKey(7L);
        assertThat(bytes(template.getKeySerializer(), key)).isEqualTo(key.getBytes(StandardCharsets.UTF_8));
        assertThat(bytes(template.getValueSerializer(), "EXPIRED")).isEqualTo("EXPIRED".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void hashFieldsWrittenByOtherServicesAreReadBackAsStrings() {
        byte[] field = SubscriptionCacheKeys.USAGE_FIELD_SKUS.getBytes(StandardCharsets.UTF_8);
        assertThat(template.getHashKeySerializer().deserialize(field)).isEqualTo(SubscriptionCacheKeys.USAGE_FIELD_SKUS);
        assertThat(template.getHashValueSerializer().deserialize("200".getBytes(StandardCharsets.UTF_8))).isEqualTo("200");
    }

    @SuppressWarnings("unchecked")
    private static byte[] bytes(RedisSerializer<?> serializer, String value) {
        return ((RedisSerializer<Object>) serializer).serialize(value);
    }
}
