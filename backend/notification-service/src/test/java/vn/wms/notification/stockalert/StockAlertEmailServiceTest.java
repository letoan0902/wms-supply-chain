package vn.wms.notification.stockalert;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vn.wms.common.messaging.event.StockAlertDigestEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.repository.NotificationLogRepository;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.wms.notification.stockalert.StockAlertTestData.digest;
import static vn.wms.notification.stockalert.StockAlertTestData.lowStock;
import static vn.wms.notification.stockalert.StockAlertTestData.nearExpiry;

/**
 * Email cảnh báo tồn kho khi SMTP đã cấu hình (JavaMailSender giả lập, không gọi mạng): HTML tiếng Việt có bảng, một
 * email cho mỗi (người nhận, loại, ngày), SMTP lỗi thì ghi FAILED mà không ném lỗi, tách theo doanh nghiệp.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.mail.host=smtp.congty-a.vn", "app.mail.from=canh-bao@congty-a.vn"})
class StockAlertEmailServiceTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;

    @Autowired
    StockAlertEmailService emailService;

    @Autowired
    NotificationLogRepository repository;

    @MockBean
    JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        TenantContext.clear();
    }

    private void cleanUp() {
        TenantContext.runAs(COMPANY_A, repository::deleteAll);
        TenantContext.runAs(COMPANY_B, repository::deleteAll);
    }

    private static String html(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String text) {
            return text;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ((MimeMultipart) content).getBodyPart(0).writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void sendsOneVietnameseHtmlEmailPerRecipientKindAndDay() throws Exception {
        StockAlertDigestEvent event = digest(COMPANY_A, "ql.kho@congty-a.vn",
                nearExpiry(COMPANY_A, 1L, "Kho Hà Nội"), nearExpiry(COMPANY_A, 2L, "Kho Đà Nẵng"));

        StockAlertEmailService.Outcome first = TenantContext.callAs(COMPANY_A, () -> emailService.send(event, "m-1"));
        // Tin giao lại, hoặc lần quét thứ hai trong ngày
        StockAlertEmailService.Outcome second = TenantContext.callAs(COMPANY_A, () -> emailService.send(event, "m-2"));

        assertThat(first).isEqualTo(StockAlertEmailService.Outcome.SENT);
        assertThat(second).isEqualTo(StockAlertEmailService.Outcome.DUPLICATE);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getRecipients(Message.RecipientType.TO)).extracting(Object::toString)
                .containsExactly("ql.kho@congty-a.vn");
        assertThat(sent.getFrom()).extracting(Object::toString).containsExactly("canh-bao@congty-a.vn");
        assertThat(sent.getSubject()).isEqualTo("[WMS] Hàng cận hạn sử dụng ngày 11/11/2026: 6 dòng tại 2 kho");
        String body = html(sent);
        assertThat(body).contains("Kính gửi Nguyễn Văn Quản Lý")
                .contains("<table").contains("Kho Hà Nội").contains("Kho Đà Nẵng")
                .contains("Hạn dùng").contains("14/11/2026").contains("10.25")
                .contains("SKU-&lt;SUA&gt;").contains("Sữa tươi &amp; bánh")
                .doesNotContain("SKU-<SUA>")
                .contains("Còn 1 dòng khác không hiển thị");

        List<NotificationLog> logs = TenantContext.callAs(COMPANY_A, () -> repository.findAll());
        assertThat(logs).hasSize(1);
        NotificationLog entry = logs.get(0);
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(entry.getChannel()).isEqualTo(NotificationLog.CHANNEL_EMAIL);
        assertThat(entry.getAlertKind()).isEqualTo("NEAR_EXPIRY");
        assertThat(entry.getRecipient()).isEqualTo("ql.kho@congty-a.vn");
        assertThat(entry.getWarehouseId()).isNull();
        assertThat(entry.getErrorMessage()).isNull();
        assertThat(entry.getDedupKey()).isEqualTo("EMAIL:NEAR_EXPIRY:2026-11-11:ql.kho@congty-a.vn");
    }

    @Test
    void otherKindOrOtherCompanyIsAnotherEmail() {
        TenantContext.runAs(COMPANY_A, () -> emailService.send(
                digest(COMPANY_A, "ql.kho@congty-a.vn", nearExpiry(COMPANY_A, 1L, "Kho Hà Nội")), "m-1"));
        StockAlertEmailService.Outcome lowStock = TenantContext.callAs(COMPANY_A, () -> emailService.send(
                digest(COMPANY_A, "QL.KHO@congty-a.vn", lowStock(COMPANY_A, 1L)), "m-2"));
        // Cùng địa chỉ và loại nhưng ở doanh nghiệp B: khóa chống trùng tách theo doanh nghiệp
        StockAlertEmailService.Outcome ofB = TenantContext.callAs(COMPANY_B, () -> emailService.send(
                digest(COMPANY_B, "ql.kho@congty-a.vn", nearExpiry(COMPANY_B, 9L, "Kho của B")), "m-3"));

        assertThat(lowStock).isEqualTo(StockAlertEmailService.Outcome.SENT);
        assertThat(ofB).isEqualTo(StockAlertEmailService.Outcome.SENT);
        verify(mailSender, times(3)).send(any(MimeMessage.class));
        assertThat(TenantContext.callAs(COMPANY_A, () -> repository.findAll())).hasSize(2);
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.findAll())).extracting(NotificationLog::getCompanyId)
                .containsExactly(COMPANY_B);
    }

    @Test
    void smtpFailureIsLoggedAsFailedWithoutThrowing() {
        doThrow(new MailSendException("Không kết nối được máy chủ SMTP")).when(mailSender).send(any(MimeMessage.class));
        StockAlertDigestEvent event = digest(COMPANY_A, "ql.kho@congty-a.vn", lowStock(COMPANY_A, 1L));

        StockAlertEmailService.Outcome outcome = TenantContext.callAs(COMPANY_A, () -> emailService.send(event, "m-1"));

        assertThat(outcome).isEqualTo(StockAlertEmailService.Outcome.FAILED);
        NotificationLog entry = TenantContext.callAs(COMPANY_A, () -> repository.findAll()).get(0);
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(entry.getErrorMessage()).contains("Không kết nối được máy chủ SMTP");
        assertThat(entry.getWarehouseId()).isEqualTo(1L);
    }

    @Test
    void eventOfAnotherCompanyIsNotSent() {
        StockAlertDigestEvent ofB = digest(COMPANY_B, "admin@congty-b.vn", lowStock(COMPANY_B, 9L));

        StockAlertEmailService.Outcome outcome = TenantContext.callAs(COMPANY_A, () -> emailService.send(ofB, "m-1"));

        assertThat(outcome).isEqualTo(StockAlertEmailService.Outcome.SKIPPED);
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(TenantContext.callAs(COMPANY_A, () -> repository.findAll())).isEmpty();
    }
}
