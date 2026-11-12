package vn.wms.auth.account.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình đăng ký doanh nghiệp (tiền tố {@code app.registration}, WMS-23.6).
 *
 * @param requireActivation biến {@code REGISTRATION_REQUIRE_ACTIVATION}, mặc định {@code true} (kể cả khi biến để
 *                          trống): doanh nghiệp mới ở trạng thái {@code PENDING} cho tới khi bấm liên kết kích hoạt
 *                          trong email. Chỉ đặt {@code false} trên máy phát triển không có mail; production luôn để
 *                          mặc định.
 */
@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(Boolean requireActivation) {

    public RegistrationProperties {
        requireActivation = requireActivation == null || requireActivation;
    }
}
