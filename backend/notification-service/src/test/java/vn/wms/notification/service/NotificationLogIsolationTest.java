package vn.wms.notification.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.messaging.NotificationMessage;
import vn.wms.notification.repository.NotificationLogRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Cách ly nhật ký thông báo giữa hai doanh nghiệp (WMS-22.4c). Không đặt {@code @Transactional} để mỗi lời gọi mở
 * một phiên làm việc riêng như khi listener xử lý tin thật (mẫu ở docs/MULTI_TENANT.md mục 5).
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationLogIsolationTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationLogRepository repository;

    @MockBean
    private EmailService emailService;

    private Long logOfA;
    private Long logOfB;

    @BeforeEach
    void setUp() {
        logOfA = TenantContext.callAs(COMPANY_A, () -> notificationService.process(new NotificationMessage(
                "LOW_STOCK", "kho.a@wms.local", "Cảnh báo của A", "SKU-A còn 1")).getId());
        logOfB = TenantContext.callAs(COMPANY_B, () -> notificationService.process(new NotificationMessage(
                "LOW_STOCK", "kho.b@wms.local", "Cảnh báo của B", "SKU-B còn 2")).getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.runAs(COMPANY_A, repository::deleteAll);
        TenantContext.runAs(COMPANY_B, repository::deleteAll);
        TenantContext.clear();
    }

    @Test
    void logsOfCompanyAAreInvisibleToCompanyB() {
        List<NotificationLog> seenByB = TenantContext.callAs(COMPANY_B, repository::findAll);
        assertThat(seenByB).extracting(NotificationLog::getId).containsExactly(logOfB);
        assertThat(seenByB).extracting(NotificationLog::getRecipient).containsExactly("kho.b@wms.local");
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.findByStatus(NotificationStatus.SENT)))
                .extracting(NotificationLog::getId).containsExactly(logOfB);

        // Đọc theo ID của A từ phía B: 404 như thể không tồn tại
        assertThatThrownBy(() -> TenantContext.callAs(COMPANY_B, () -> repository.findById(logOfA)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.existsById(logOfA))).isFalse();

        // Phía A vẫn thấy đúng bản ghi của mình, gắn company_id của A
        NotificationLog ofA = TenantContext.callAs(COMPANY_A, () -> repository.findById(logOfA).orElseThrow());
        assertThat(ofA.getCompanyId()).isEqualTo(COMPANY_A);
        long countOfA = TenantContext.callAs(COMPANY_A, () -> repository.count());
        assertThat(countOfA).isEqualTo(1);
    }

    @Test
    void companyBCannotDeleteLogOfCompanyA() {
        assertThatThrownBy(() -> TenantContext.runAs(COMPANY_B, () -> repository.deleteById(logOfA)))
                .isInstanceOf(ApiException.class);

        assertThat(TenantContext.callAs(COMPANY_A, () -> repository.existsById(logOfA))).isTrue();
    }

    @Test
    void processWithoutCompanyIsRefusedBeforeSendingEmail() {
        TenantContext.clear();

        assertThatThrownBy(() -> notificationService.process(new NotificationMessage(
                "LOW_STOCK", "ai.do@wms.local", "Không rõ doanh nghiệp", "Không được gửi")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMPANY_REQUIRED);
        verify(emailService, never()).send(eq("ai.do@wms.local"), anyString(), anyString());
    }
}
