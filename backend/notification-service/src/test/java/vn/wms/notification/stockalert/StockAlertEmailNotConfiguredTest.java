package vn.wms.notification.stockalert;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.messaging.NotificationMessage;
import vn.wms.notification.repository.NotificationLogRepository;
import vn.wms.notification.service.EmailService;
import vn.wms.notification.service.NotificationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static vn.wms.notification.stockalert.StockAlertTestData.digest;
import static vn.wms.notification.stockalert.StockAlertTestData.lowStock;

/**
 * SMTP chưa cấu hình (MAIL_HOST rỗng): service vẫn chạy, không gọi JavaMailSender, ghi tiêu đề, số người nhận và nội
 * dung vào log, nhật ký có trạng thái LOGGED.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=")
@ExtendWith(OutputCaptureExtension.class)
class StockAlertEmailNotConfiguredTest {

    private static final Long COMPANY_A = 1L;

    @Autowired
    StockAlertEmailService stockAlertEmailService;

    @Autowired
    NotificationService notificationService;

    @Autowired
    EmailService emailService;

    @Autowired
    NotificationLogRepository repository;

    @MockBean
    JavaMailSender mailSender;

    @AfterEach
    void tearDown() {
        TenantContext.runAs(COMPANY_A, repository::deleteAll);
        TenantContext.clear();
    }

    @Test
    void contentIsLoggedInsteadOfSent(CapturedOutput output) {
        assertThat(emailService.isConfigured()).isFalse();

        StockAlertEmailService.Outcome outcome = TenantContext.callAs(COMPANY_A, () -> stockAlertEmailService.send(
                digest(COMPANY_A, "ql.kho@congty-a.vn", lowStock(COMPANY_A, 1L)), "m-1"));

        assertThat(outcome).isEqualTo(StockAlertEmailService.Outcome.LOGGED);
        verify(mailSender, never()).send(any(jakarta.mail.internet.MimeMessage.class));
        assertThat(output).contains("SMTP chưa cấu hình").contains("[WMS] Cảnh báo tồn kho thấp ngày 11/11/2026")
                .contains("số người nhận=1").contains("SKU-200").doesNotContain("ql.kho@congty-a.vn");
        NotificationLog entry = TenantContext.callAs(COMPANY_A, () -> repository.findAll()).get(0);
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.LOGGED);
    }

    @Test
    void legacyEmailMessageIsAlsoLogged() {
        NotificationLog entry = TenantContext.callAs(COMPANY_A, () -> notificationService.process(new NotificationMessage(
                "LOW_STOCK", "ql.kho@congty-a.vn", "Cảnh báo tồn kho thấp", "SKU-001 còn 3")));

        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.LOGGED);
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }
}
