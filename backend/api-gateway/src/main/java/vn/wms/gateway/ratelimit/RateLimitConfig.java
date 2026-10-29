package vn.wms.gateway.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký KeyResolver cho bộ lọc RequestRateLimiter (xem spring.cloud.gateway.default-filters trong application.yml).
 * Bộ đếm token bucket được Spring Cloud Gateway lưu trên Redis (RedisRateLimiter), dùng chung REDIS_HOST với các service.
 * Khi Redis không truy cập được, RedisRateLimiter ghi log lỗi và cho request đi qua, nên môi trường dev thiếu Redis
 * vẫn chạy bình thường.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public KeyResolver userOrIpKeyResolver(RateLimitProperties properties) {
        return new UserOrIpKeyResolver(properties);
    }
}
