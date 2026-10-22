package vn.wms.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.messaging.NotificationMessage;
import vn.wms.notification.service.EmailService;
import vn.wms.notification.service.NotificationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khởi động context thật với H2; listener RabbitMQ không tự chạy (xem application-test.yml), SMTP được mock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationServiceApplicationTest {

    private static final NotificationMessage LOW_STOCK = new NotificationMessage(
            "LOW_STOCK", "quan.ly.kho@wms.local", "Cảnh báo tồn kho thấp", "SKU-001 chỉ còn 3 cái");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @MockBean
    private EmailService emailService;

    @Test
    void healthInfoReportsRabbitTopologyAndMailSettings() throws Exception {
        mockMvc.perform(get("/api/notifications/health-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.service").value("notification-service"))
                .andExpect(jsonPath("$.data.exchange").value("wms.events"))
                .andExpect(jsonPath("$.data.queue").value("notification.alerts"))
                .andExpect(jsonPath("$.data.routingKey").value("alert.#"))
                .andExpect(jsonPath("$.data.mailHost").value("localhost"));
    }

    @Test
    void processSendsEmailAndLogsSentStatus() {
        NotificationLog entry = notificationService.process(LOW_STOCK);

        verify(emailService).send("quan.ly.kho@wms.local", "Cảnh báo tồn kho thấp", "SKU-001 chỉ còn 3 cái");
        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(entry.getCreatedBy()).isEqualTo("system");
        assertThat(entry.getErrorMessage()).isNull();
    }

    @Test
    void processLogsFailedStatusWhenSmtpIsDown() {
        doThrow(new MailSendException("SMTP không phản hồi"))
                .when(emailService).send(anyString(), anyString(), anyString());

        NotificationLog entry = notificationService.process(LOW_STOCK);

        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(entry.getErrorMessage()).contains("SMTP");
    }

    @Test
    void livenessProbeIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
