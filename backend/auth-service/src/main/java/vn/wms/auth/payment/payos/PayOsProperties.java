package vn.wms.auth.payment.payos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình cổng thanh toán PayOS (tiền tố {@code app.payos}), đọc từ biến môi trường qua application.yml.
 *
 * <p>Ba khóa {@code clientId}, {@code apiKey}, {@code checksumKey} chỉ nằm trong file {@code .env} trên máy chủ:
 * không ghi log, không trả qua API, không commit. {@link #toString()} đã che các khóa này.
 *
 * @param clientId    PAYOS_CLIENT_ID, gửi trong header {@code x-client-id}
 * @param apiKey      PAYOS_API_KEY, gửi trong header {@code x-api-key}
 * @param checksumKey PAYOS_CHECKSUM_KEY, khóa HMAC-SHA256 để ký yêu cầu và kiểm tra chữ ký webhook
 * @param returnUrl   PAYOS_RETURN_URL, trang kết quả thanh toán của frontend (PayOS chuyển về sau khi trả tiền)
 * @param cancelUrl   PAYOS_CANCEL_URL, trang PayOS chuyển về khi người dùng hủy
 * @param baseUrl     PAYOS_BASE_URL, mặc định {@code https://api-merchant.payos.vn}
 * @param mock        PAYOS_MOCK, mặc định {@code true}: không gọi PayOS, liên kết trỏ tới trang giả lập của hệ thống
 */
@ConfigurationProperties(prefix = "app.payos")
public record PayOsProperties(
        String clientId,
        String apiKey,
        String checksumKey,
        String returnUrl,
        String cancelUrl,
        @DefaultValue(PayOsProperties.DEFAULT_BASE_URL) String baseUrl,
        @DefaultValue("true") boolean mock) {

    public static final String DEFAULT_BASE_URL = "https://api-merchant.payos.vn";
    public static final String DEFAULT_RETURN_URL = "http://localhost:5173/payment/result";

    /** Biến môi trường để trống (docker-compose truyền chuỗi rỗng) thì dùng giá trị mặc định. */
    public PayOsProperties {
        clientId = trimToNull(clientId);
        apiKey = trimToNull(apiKey);
        checksumKey = trimToNull(checksumKey);
        returnUrl = trimToNull(returnUrl) == null ? DEFAULT_RETURN_URL : returnUrl.trim();
        cancelUrl = trimToNull(cancelUrl) == null ? returnUrl : cancelUrl.trim();
        baseUrl = trimToNull(baseUrl) == null ? DEFAULT_BASE_URL : stripTrailingSlash(baseUrl.trim());
    }

    /** Đã có đủ ba khóa để gọi PayOS thật. */
    public boolean hasCredentials() {
        return clientId != null && apiKey != null && checksumKey != null;
    }

    /** Đã cấu hình checksum key để kiểm tra chữ ký webhook. */
    public boolean hasChecksumKey() {
        return checksumKey != null;
    }

    /** Không bao giờ in khóa ra log, kể cả khi cả đối tượng cấu hình bị ghi log. */
    @Override
    public String toString() {
        return "PayOsProperties[clientId=" + mask(clientId) + ", apiKey=" + mask(apiKey)
                + ", checksumKey=" + mask(checksumKey) + ", returnUrl=" + returnUrl + ", cancelUrl=" + cancelUrl
                + ", baseUrl=" + baseUrl + ", mock=" + mock + "]";
    }

    private static String mask(String value) {
        return value == null ? "<trống>" : "***";
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
