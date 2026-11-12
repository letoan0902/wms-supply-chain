package vn.wms.notification.messaging;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.AccountEmailKind;
import vn.wms.common.messaging.event.AccountEmailRequestedEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.repository.NotificationLogRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.wms.notification.messaging.ListenerTestSupport.COMPANY_A;
import static vn.wms.notification.messaging.ListenerTestSupport.COMPANY_B;
import static vn.wms.notification.messaging.ListenerTestSupport.json;

/**
 * Trọn luồng trong notification-service (WMS-23.6): tin {@code auth.activation-requested} tới listener, email đi qua
 * JavaMailSender giả lập (cấu hình test: host localhost, coi như đã có SMTP), nhật ký ghi dưới doanh nghiệp của sự
 * kiện và doanh nghiệp khác không đọc được.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountEmailFlowIntegrationTest {

    @Autowired
    private AccountEmailListener listener;

    @Autowired
    private NotificationLogRepository repository;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private StringRedisTemplate redis;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        when(redis.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        TenantContext.runAs(COMPANY_A, repository::deleteAll);
        TenantContext.runAs(COMPANY_B, repository::deleteAll);
        TenantContext.clear();
    }

    @Test
    void activationEmailIsSentAndLoggedForTheEventCompanyOnly() throws Exception {
        AccountEmailRequestedEvent event = new AccountEmailRequestedEvent(AccountEmailKind.ACTIVATION, COMPANY_A,
                "Công ty mẫu A", "quan.tri@cong-ty-a.vn", "Quản Trị A",
                "http://localhost:5173/activate?token=AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde",
                Instant.now().plusSeconds(86400), Instant.now());

        listener.onMessage(json(event, WmsEvents.RoutingKeys.AUTH_ACTIVATION_REQUESTED, "msg-flow-1"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("quan.tri@cong-ty-a.vn");
        assertThat(captor.getValue().getSubject()).isEqualTo("Kích hoạt doanh nghiệp Công ty mẫu A trên WMS Supply Chain");

        List<NotificationLog> ofA = TenantContext.callAs(COMPANY_A, repository::findAll);
        assertThat(ofA).hasSize(1);
        assertThat(ofA.get(0).getCompanyId()).isEqualTo(COMPANY_A);
        assertThat(ofA.get(0).getEventType()).isEqualTo("ACCOUNT_ACTIVATION");
        assertThat(ofA.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        List<NotificationLog> ofB = TenantContext.callAs(COMPANY_B, repository::findAll);
        assertThat(ofB).isEmpty();
    }
}
