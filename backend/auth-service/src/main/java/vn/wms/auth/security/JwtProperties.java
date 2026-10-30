package vn.wms.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình JWT (tiền tố app.jwt), đọc từ biến môi trường qua application.yml.
 *
 * @param secret            khóa ký HS256, tối thiểu 32 byte. Giá trị mặc định CHỈ dùng cho dev, phải đổi ở môi
 *                          trường thật (biến JWT_SECRET) và phải trùng với api-gateway
 * @param expirationMinutes thời gian sống của access token (phút)
 * @param issuer            giá trị claim iss của token
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @DefaultValue("change_me_dev_only_jwt_secret_key_min_32_bytes") String secret,
        @DefaultValue("15") long expirationMinutes,
        @DefaultValue("15") long accessTokenMinutes,
        @DefaultValue("10080") long refreshTokenMinutes,
        @DefaultValue("wms-auth-service") String issuer) {

    @ConstructorBinding
    public JwtProperties {
    }

    public JwtProperties(String secret, long expirationMinutes, String issuer) {
        this(secret, expirationMinutes, expirationMinutes, 10080L, issuer);
    }
}
