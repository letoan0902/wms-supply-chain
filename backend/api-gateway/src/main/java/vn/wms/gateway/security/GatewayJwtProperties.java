package vn.wms.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Cấu hình kiểm tra JWT tại gateway (tiền tố gateway.jwt).
 *
 * @param enabled     bật/tắt kiểm tra JWT. Mặc định TẮT để nhóm phát triển Sprint 1 không bị chặn;
 *                    TODO: bật (GATEWAY_JWT_ENABLED=true) khi RBAC của auth-service hoàn thiện.
 * @param secret      khóa ký HS256, PHẢI trùng với app.jwt.secret của auth-service (biến môi trường JWT_SECRET)
 * @param publicPaths các đường dẫn (kiểu Ant) không cần token, ví dụ đăng nhập và actuator
 */
@ConfigurationProperties(prefix = "gateway.jwt")
public record GatewayJwtProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("change_me_dev_only_jwt_secret_key_min_32_bytes") String secret,
        @DefaultValue({"/api/auth/login", "/actuator/**"}) List<String> publicPaths) {
}
