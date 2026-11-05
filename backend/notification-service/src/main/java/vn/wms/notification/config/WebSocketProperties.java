package vn.wms.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Cấu hình hub WebSocket STOMP {@code /ws-wms} (tiền tố app.websocket).
 *
 * @param authRequired   bắt buộc JWT ở frame CONNECT (biến GATEWAY_JWT_ENABLED, cùng công tắc với api-gateway).
 *                       Mặc định TẮT cho giai đoạn phát triển; production bật
 * @param jwtSecret      khóa ký HS256, PHẢI trùng JWT_SECRET của auth-service và api-gateway
 * @param allowedOrigins các origin (cho phép mẫu, ví dụ {@code https://*.example.com}) được mở kết nối,
 *                       lấy từ biến CORS_ALLOWED_ORIGINS phân tách bằng dấu phẩy
 */
@ConfigurationProperties(prefix = "app.websocket")
public record WebSocketProperties(
        @DefaultValue("false") boolean authRequired,
        @DefaultValue("change_me_dev_only_jwt_secret_key_min_32_bytes") String jwtSecret,
        @DefaultValue("http://localhost:5173") List<String> allowedOrigins) {

    /** Danh sách origin đã bỏ khoảng trắng và phần tử rỗng, dùng cho {@code setAllowedOriginPatterns}. */
    public String[] allowedOriginPatterns() {
        return allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
