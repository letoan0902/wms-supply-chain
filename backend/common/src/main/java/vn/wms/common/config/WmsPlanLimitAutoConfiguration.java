package vn.wms.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.wms.common.plan.PlanLimitChecker;

/**
 * Tự đăng ký {@link PlanLimitChecker} (WMS-23.2) cho service có Spring Data Redis và bean
 * {@link StringRedisTemplate} (mọi service nghiệp vụ đều đã có starter data-redis).
 *
 * <p>Thuộc tính {@code app.plan-limit.enabled} (mặc định {@code true}) cho phép tắt hẳn việc kiểm tra, ví dụ trong
 * test không có Redis. Service phải tiêm {@link PlanLimitChecker} dạng tùy chọn ({@code ObjectProvider}) để vẫn chạy
 * khi bean không tồn tại.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "app.plan-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WmsPlanLimitAutoConfiguration {

    @Bean
    @ConditionalOnBean(type = "org.springframework.data.redis.core.StringRedisTemplate")
    @ConditionalOnMissingBean
    PlanLimitChecker wmsPlanLimitChecker(StringRedisTemplate redisTemplate) {
        return new PlanLimitChecker(redisTemplate);
    }
}
