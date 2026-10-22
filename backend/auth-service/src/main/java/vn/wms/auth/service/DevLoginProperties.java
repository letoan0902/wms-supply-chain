package vn.wms.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình đăng nhập giả lập cho giai đoạn khung (tiền tố app.auth.dev-login).
 *
 * @param enabled  cho phép đăng nhập bằng tài khoản dev. TODO: đặt AUTH_DEV_LOGIN_ENABLED=false khi đã có
 *                 đăng nhập thật dựa trên bảng users
 * @param password mật khẩu dùng chung của các tài khoản dev (mặc định change_me, đọc từ AUTH_DEV_PASSWORD)
 */
@ConfigurationProperties(prefix = "app.auth.dev-login")
public record DevLoginProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("change_me") String password) {
}
