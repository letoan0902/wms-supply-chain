package vn.wms.auth.account.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.wms.auth.payment.payos.PayOsProperties;

import java.net.URI;

/**
 * Dựng liên kết tới các trang của giao diện gửi trong email (WMS-23.6).
 *
 * <p>Địa chỉ gốc lấy từ {@code app.frontend-url} (biến {@code APP_FRONTEND_URL}). Để trống thì dùng lại gốc của
 * {@code PAYOS_RETURN_URL} (trang kết quả thanh toán cũng nằm trên giao diện), mặc định {@code http://localhost:5173}.
 */
@Component
public class FrontendLinks {

    static final String ACTIVATE_PATH = "/activate?token=";
    static final String RESET_PASSWORD_PATH = "/reset-password?token=";

    private final String baseUrl;

    public FrontendLinks(@Value("${app.frontend-url:}") String frontendUrl, PayOsProperties payOsProperties) {
        this.baseUrl = resolveBaseUrl(frontendUrl, payOsProperties.returnUrl());
    }

    /** Địa chỉ gốc đang dùng, không có dấu {@code /} ở cuối. */
    public String baseUrl() {
        return baseUrl;
    }

    /** Liên kết kích hoạt doanh nghiệp. Mã đã ở dạng Base64 URL-safe nên không cần mã hóa thêm. */
    public String activation(String rawToken) {
        return baseUrl + ACTIVATE_PATH + rawToken;
    }

    /** Liên kết đặt lại mật khẩu. */
    public String passwordReset(String rawToken) {
        return baseUrl + RESET_PASSWORD_PATH + rawToken;
    }

    static String resolveBaseUrl(String frontendUrl, String payOsReturnUrl) {
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            return stripTrailingSlashes(frontendUrl.trim());
        }
        String fallback = payOsReturnUrl == null || payOsReturnUrl.isBlank()
                ? PayOsProperties.DEFAULT_RETURN_URL : payOsReturnUrl.trim();
        try {
            URI uri = URI.create(fallback);
            if (uri.getScheme() != null && uri.getRawAuthority() != null) {
                return uri.getScheme() + "://" + uri.getRawAuthority();
            }
        } catch (IllegalArgumentException ignored) {
            // Giá trị không phải URL hợp lệ: dùng mặc định bên dưới
        }
        return "http://localhost:5173";
    }

    private static String stripTrailingSlashes(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
