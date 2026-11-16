package vn.wms.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import vn.wms.notification.service.BrevoEmailClient;

import java.time.Duration;

/**
 * Client gửi email qua API Brevo. Bean luôn được tạo; {@code BREVO_API_KEY} rỗng thì client báo chưa cấu hình và
 * {@link vn.wms.notification.service.EmailService} dùng SMTP hoặc ghi log như trước.
 */
@Configuration
public class BrevoMailConfig {

    private static final Logger log = LoggerFactory.getLogger(BrevoMailConfig.class);

    @Bean
    BrevoEmailClient brevoEmailClient(NotificationProperties properties,
                                      @Value("${app.mail.brevo.api-key:}") String apiKey,
                                      @Value("${app.mail.brevo.base-url:" + BrevoEmailClient.DEFAULT_BASE_URL + "}") String baseUrl,
                                      @Value("${app.mail.brevo.timeout:10s}") Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        BrevoEmailClient client = new BrevoEmailClient(restClient, apiKey);
        if (client.isConfigured()) {
            if (StringUtils.hasText(properties.mail().from())) {
                log.info("Gửi email qua API Brevo, người gửi lấy từ MAIL_FROM");
            } else {
                log.warn("Có BREVO_API_KEY nhưng MAIL_FROM rỗng: Brevo chỉ nhận người gửi đã xác minh, hãy đặt MAIL_FROM");
            }
        }
        return client;
    }
}
