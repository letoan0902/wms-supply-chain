package vn.wms.notification.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import vn.wms.common.messaging.event.AccountEmailKind;
import vn.wms.common.messaging.event.AccountEmailRequestedEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.config.NotificationProperties;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.repository.NotificationLogRepository;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gửi email tài khoản (WMS-23.6) qua JavaMailSender giả lập: có SMTP, chưa có SMTP, SMTP lỗi, tin giao lại. */
@ExtendWith(OutputCaptureExtension.class)
class AccountEmailServiceTest {

    private static final Long COMPANY_ID = 7L;
    private static final String LINK = "http://localhost:5173/activate?token=AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final NotificationLogRepository repository = mock(NotificationLogRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final EmailService emailService = new EmailService(providerOf(mailSender),
            new NotificationProperties(new NotificationProperties.Mail("no-reply@wms.local"),
                    new NotificationProperties.Rabbitmq("wms.events", "notification.alerts", "alert.#")),
            "smtp.example.test", "mailer");

    private final AccountEmailRequestedEvent activation = new AccountEmailRequestedEvent(AccountEmailKind.ACTIVATION,
            COMPANY_ID, "Công ty <Kho> Mẫu", "an@example.com", "Nguyễn Văn An", LINK,
            Instant.parse("2026-11-12T03:00:00Z"), Instant.parse("2026-11-11T03:00:00Z"));

    AccountEmailServiceTest() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        when(repository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /** EmailService nhận JavaMailSender qua ObjectProvider (bean có thể vắng khi chưa cấu hình SMTP). */
    private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
        return new ObjectProvider<>() {
            @Override
            public JavaMailSender getObject() {
                return sender;
            }

            @Override
            public JavaMailSender getObject(Object... args) {
                return sender;
            }

            @Override
            public JavaMailSender getIfAvailable() {
                return sender;
            }

            @Override
            public JavaMailSender getIfUnique() {
                return sender;
            }
        };
    }

    private AccountEmailService service(String host, String username) {
        return new AccountEmailService(emailService, repository, redis, host, username);
    }

    @Test
    void smtpIsConfiguredWithAccountOrLocalHostOnly() {
        assertThat(AccountEmailService.isSmtpConfigured("smtp.gmail.com", "kho@example.com")).isTrue();
        assertThat(AccountEmailService.isSmtpConfigured("localhost", "")).isTrue();
        assertThat(AccountEmailService.isSmtpConfigured("smtp.gmail.com", "")).isFalse();
        assertThat(AccountEmailService.isSmtpConfigured("", "kho@example.com")).isFalse();
        assertThat(AccountEmailService.isSmtpConfigured(null, null)).isFalse();
    }

    @Test
    void sendsVietnameseHtmlEmailAndLogsSentUnderCompany(CapturedOutput output) throws Exception {
        NotificationLog entry = TenantContext.callAs(COMPANY_ID,
                () -> service("smtp.example.com", "wms@example.com").send(activation, "msg-1"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("an@example.com");
        assertThat(sent.getSubject()).isEqualTo("Kích hoạt doanh nghiệp Công ty <Kho> Mẫu trên WMS Supply Chain");
        String html = htmlOf(sent);
        assertThat(html).contains("Xin chào Nguyễn Văn An", "href=\"" + LINK + "\"", "Kích hoạt doanh nghiệp",
                "Công ty &lt;Kho&gt; Mẫu", "10:00 ngày 12/11/2026");

        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(entry.getEventType()).isEqualTo("ACCOUNT_ACTIVATION");
        assertThat(entry.getRecipient()).isEqualTo("an@example.com");
        verify(valueOps).set(eq(AccountEmailService.PROCESSED_KEY_PREFIX + "msg-1"), anyString(), any(Duration.class));
        // Có SMTP thì liên kết không bao giờ xuất hiện trong log
        assertThat(output.getAll()).doesNotContain(LINK);
    }

    @Test
    void withoutSmtpLogsRecipientAndLinkOnceAndDoesNotSend(CapturedOutput output) {
        NotificationLog entry = TenantContext.callAs(COMPANY_ID,
                () -> service("smtp.gmail.com", "").send(activation, "msg-2"));

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.LOGGED);
        assertThat(entry.getErrorMessage()).isEqualTo(AccountEmailService.SMTP_NOT_CONFIGURED);
        String logs = output.getAll();
        assertThat(logs).contains("an@example.com", LINK);
        assertThat(logs.split(java.util.regex.Pattern.quote(LINK), -1)).hasSize(2);
    }

    @Test
    void smtpFailureIsLoggedAsFailedAndNeverThrown(CapturedOutput output) {
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessage.class));
        AccountEmailRequestedEvent reset = new AccountEmailRequestedEvent(AccountEmailKind.PASSWORD_RESET, COMPANY_ID,
                "Công ty Mẫu", "an@example.com", null, LINK.replace("activate", "reset-password"),
                Instant.parse("2026-11-11T03:30:00Z"), Instant.parse("2026-11-11T03:00:00Z"));

        NotificationLog[] holder = new NotificationLog[1];
        assertThatCode(() -> holder[0] = TenantContext.callAs(COMPANY_ID,
                () -> service("smtp.example.com", "wms@example.com").send(reset, "msg-3"))).doesNotThrowAnyException();

        assertThat(holder[0].getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(holder[0].getEventType()).isEqualTo("PASSWORD_RESET");
        assertThat(holder[0].getSubject()).isEqualTo("Đặt lại mật khẩu tài khoản WMS Supply Chain");
        assertThat(holder[0].getErrorMessage()).contains("Connection refused");
        assertThat(output.getAll()).doesNotContain("reset-password?token=");
    }

    @Test
    void redeliveredMessageIsNotSentTwice() {
        when(redis.hasKey(AccountEmailService.PROCESSED_KEY_PREFIX + "msg-4")).thenReturn(true);

        NotificationLog entry = TenantContext.callAs(COMPANY_ID,
                () -> service("smtp.example.com", "wms@example.com").send(activation, "msg-4"));

        assertThat(entry).isNull();
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(repository, never()).save(any());
    }

    private static String htmlOf(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof MimeMultipart multipart) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            multipart.writeTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
        return content.toString();
    }
}
