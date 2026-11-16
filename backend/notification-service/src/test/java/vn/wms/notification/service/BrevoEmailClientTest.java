package vn.wms.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.wms.notification.config.NotificationProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/** Gửi email qua API Brevo: nội dung request, xử lý lỗi và thứ tự chọn Brevo, SMTP, ghi log của EmailService. */
class BrevoEmailClientTest {

    private static final String KEY = "xkeysib-test-khong-phai-khoa-that";
    private static final NotificationProperties PROPERTIES = new NotificationProperties(
            new NotificationProperties.Mail("kho@example.com", "WMS Supply Chain"),
            new NotificationProperties.Rabbitmq("wms.events", "notification.alerts", "alert.#"));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BrevoEmailClient client = new BrevoEmailClient(builder.build(), KEY);

    @Test
    void sendsHtmlEmailWithVerifiedSenderAndApiKeyHeader() {
        server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("kho@example.com"))
                .andExpect(jsonPath("$.sender.name").value("WMS Supply Chain"))
                .andExpect(jsonPath("$.to[0].email").value("an@example.com"))
                .andExpect(jsonPath("$.subject").value("Kích hoạt doanh nghiệp"))
                .andExpect(jsonPath("$.htmlContent").value("<p>Xin chào</p>"))
                .andExpect(jsonPath("$.textContent").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<abc@smtp-relay.mailin.fr>\"}"));

        client.send("kho@example.com", "WMS Supply Chain", "an@example.com", "Kích hoạt doanh nghiệp",
                "<p>Xin chào</p>", true);

        server.verify();
    }

    @Test
    void plainTextUsesTextContent() {
        server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                .andExpect(jsonPath("$.textContent").value("Nội dung"))
                .andExpect(jsonPath("$.htmlContent").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED));

        client.send("kho@example.com", null, "an@example.com", "Tiêu đề", "Nội dung", false);

        server.verify();
    }

    @Test
    void rejectedRequestBecomesMailSendExceptionWithoutTheKey() {
        server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                .andRespond(withUnauthorizedRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}"));

        assertThatThrownBy(() -> client.send("kho@example.com", "WMS", "an@example.com", "T", "<p>x</p>", true))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageContaining("Key not found")
                .hasMessageNotContaining(KEY);
    }

    @Test
    void blankKeyMeansNotConfigured() {
        assertThat(new BrevoEmailClient(builder.build(), "  ").isConfigured()).isFalse();
        assertThat(new BrevoEmailClient(builder.build(), null).isConfigured()).isFalse();
        assertThat(client.isConfigured()).isTrue();
    }

    @Test
    void emailServicePrefersBrevoOverSmtp() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        BrevoEmailClient brevo = mock(BrevoEmailClient.class);
        when(brevo.isConfigured()).thenReturn(true);
        EmailService service = new EmailService(providerOf(smtp), PROPERTIES, "smtp.example.test", "mailer",
                providerOf(brevo));

        assertThat(service.isConfigured()).isTrue();
        assertThat(service.sendHtml("an@example.com", "Tiêu đề", "<p>x</p>")).isEqualTo(EmailService.DeliveryResult.SENT);
        verify(brevo).send("kho@example.com", "WMS Supply Chain", "an@example.com", "Tiêu đề", "<p>x</p>", true);
        verify(smtp, never()).createMimeMessage();
    }

    @Test
    void emailServiceLogsWhenNeitherBrevoNorSmtpIsConfigured() {
        BrevoEmailClient brevo = new BrevoEmailClient(builder.build(), "");
        EmailService service = new EmailService(providerOf(null), PROPERTIES, "", "", providerOf(brevo));

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.sendHtml("an@example.com", "Tiêu đề", "<p>x</p>")).isEqualTo(EmailService.DeliveryResult.LOGGED);
        server.verify();
    }

    @Test
    void emailServiceFallsBackToSmtpWhenBrevoHasNoKey() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        when(smtp.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null));
        BrevoEmailClient brevo = mock(BrevoEmailClient.class);
        EmailService service = new EmailService(providerOf(smtp), PROPERTIES, "smtp.example.test", "mailer",
                providerOf(brevo));

        assertThat(service.sendHtml("an@example.com", "Tiêu đề", "<p>x</p>")).isEqualTo(EmailService.DeliveryResult.SENT);
        verify(smtp).send(any(jakarta.mail.internet.MimeMessage.class));
        verify(brevo, never()).send(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        when(provider.getObject()).thenReturn(value);
        return provider;
    }
}
