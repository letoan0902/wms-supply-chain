package vn.wms.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import vn.wms.notification.config.NotificationProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Người nhận thuộc tên miền dành riêng cho thử nghiệm (tài khoản demo) không bao giờ được chuyển cho Brevo hay SMTP;
 * địa chỉ thật vẫn gửi bình thường.
 */
class EmailServiceReservedDomainTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final BrevoEmailClient brevo = mock(BrevoEmailClient.class);

    private EmailService viaBrevo() {
        when(brevo.isConfigured()).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<BrevoEmailClient> brevoProvider = mock(ObjectProvider.class);
        when(brevoProvider.getIfAvailable()).thenReturn(brevo);
        return new EmailService(providerOf(mailSender), properties(), "", "", brevoProvider);
    }

    private EmailService viaSmtp() {
        return new EmailService(providerOf(mailSender), properties(), "smtp.vidu.vn", "mailer");
    }

    private static NotificationProperties properties() {
        return new NotificationProperties(new NotificationProperties.Mail("kho@vidu.vn", "WMS Supply Chain"),
                new NotificationProperties.Rabbitmq("wms.events", "notification.alerts", "alert.#"));
    }

    @Test
    void reservedRecipientIsNeverHandedToBrevo() {
        EmailService service = viaBrevo();

        assertThat(service.sendHtml("quantri@trai-cay.example", "Kích hoạt", "<p>nội dung</p>"))
                .isEqualTo(EmailService.DeliveryResult.LOGGED);
        assertThat(service.send("Thu.Kho@Kho-Van.EXAMPLE ", "Cảnh báo", "nội dung"))
                .isEqualTo(EmailService.DeliveryResult.LOGGED);

        verify(brevo, never()).send(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void reservedRecipientIsNeverHandedToSmtp() {
        EmailService service = viaSmtp();

        assertThat(service.send("nguoi.dung@demo.test", "Cảnh báo", "nội dung"))
                .isEqualTo(EmailService.DeliveryResult.LOGGED);
        assertThat(service.send("ai.do@khong-co.invalid", "Cảnh báo", "nội dung"))
                .isEqualTo(EmailService.DeliveryResult.LOGGED);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void realRecipientIsStillSent() {
        EmailService service = viaBrevo();

        assertThat(service.sendHtml("khach@congty.vn", "Kích hoạt", "<p>nội dung</p>"))
                .isEqualTo(EmailService.DeliveryResult.SENT);

        verify(brevo).send(eq("kho@vidu.vn"), eq("WMS Supply Chain"), eq("khach@congty.vn"), eq("Kích hoạt"),
                eq("<p>nội dung</p>"), eq(true));
    }

    @Test
    void onlyTheDomainSuffixCounts() {
        assertThat(EmailService.isReservedAddress("example@congty.vn")).isFalse();
        assertThat(EmailService.isReservedAddress("a@example.com")).isFalse();
        assertThat(EmailService.isReservedAddress("a@cua-hang.example")).isTrue();
        assertThat(EmailService.isReservedAddress("a@may.localhost")).isTrue();
        assertThat(EmailService.isReservedAddress(null)).isFalse();
        assertThat(EmailService.isReservedAddress("  ")).isFalse();
    }

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
}
