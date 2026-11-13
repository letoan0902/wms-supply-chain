package vn.wms.notification.service;

import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gửi email qua API giao dịch của Brevo ({@code POST /v3/smtp/email}) bằng khóa API v3 ({@code BREVO_API_KEY}).
 *
 * <p>Dùng khi nhà cung cấp chỉ cấp khóa API (dạng {@code xkeysib-...}): khóa này không đăng nhập SMTP được. Địa chỉ người
 * gửi phải là sender đã xác minh trong tài khoản Brevo (biến {@code MAIL_FROM}), Brevo từ chối địa chỉ khác.
 *
 * <p>Lỗi của Brevo (khóa sai, sender chưa xác minh, hết hạn mức) và lỗi mạng được đổi thành {@link MailSendException}
 * để người gọi xử lý như lỗi SMTP: ghi nhật ký FAILED, không ném tiếp. Thông báo lỗi không chứa khóa.
 */
public class BrevoEmailClient {

    /** Địa chỉ gốc của API Brevo. */
    public static final String DEFAULT_BASE_URL = "https://api.brevo.com";

    private static final int MAX_ERROR_BODY = 300;

    private final RestClient restClient;
    private final String apiKey;

    /**
     * @param restClient client đã đặt địa chỉ gốc và thời gian chờ
     * @param apiKey     khóa API v3 của Brevo; rỗng là chưa cấu hình
     */
    public BrevoEmailClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    /** Đã có khóa API. */
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * Gửi một email tới một người nhận.
     *
     * @param fromEmail sender đã xác minh trong Brevo
     * @param fromName  tên hiển thị của người gửi
     * @param html      {@code true}: {@code content} là HTML; {@code false}: văn bản thuần
     * @throws MailSendException Brevo trả lỗi hoặc không gọi được
     */
    public void send(String fromEmail, String fromName, String to, String subject, String content, boolean html) {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("email", fromEmail);
        if (StringUtils.hasText(fromName)) {
            sender.put("name", fromName.trim());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(Map.of("email", to)));
        body.put("subject", subject);
        body.put(html ? "htmlContent" : "textContent", content);
        try {
            restClient.post()
                    .uri("/v3/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("api-key", apiKey)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new MailSendException("Brevo từ chối email: HTTP " + ex.getStatusCode().value() + " "
                    + abbreviate(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            throw new MailSendException("Không gọi được Brevo: " + ex.getMessage(), ex);
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= MAX_ERROR_BODY ? oneLine : oneLine.substring(0, MAX_ERROR_BODY) + "...";
    }
}
