package vn.wms.notification.stockalert;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.NotificationSeverity;
import vn.wms.notification.realtime.RealtimeEventType;
import vn.wms.notification.realtime.RealtimeNotification;
import vn.wms.notification.realtime.RealtimeNotificationService;
import vn.wms.notification.repository.NotificationLogRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static vn.wms.notification.stockalert.StockAlertTestData.lowStock;
import static vn.wms.notification.stockalert.StockAlertTestData.nearExpiry;

/** Bản tổng hợp cảnh báo: một dòng nhật ký, đẩy kênh alerts của đúng doanh nghiệp, tin giao lại không ghi thêm. */
@SpringBootTest
@ActiveProfiles("test")
class StockAlertNotificationServiceTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;

    @Autowired
    StockAlertNotificationService service;

    @Autowired
    NotificationLogRepository repository;

    @MockBean
    RealtimeNotificationService realtimeService;

    @BeforeEach
    void setUp() {
        reset(realtimeService);
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

    @Test
    void summaryIsLoggedOnceAndPushedToCompanyAlertsTopic() {
        StockAlertSummaryEvent event = nearExpiry(COMPANY_A, 5L, "Kho Hà Nội");

        TenantContext.runAs(COMPANY_A, () -> service.onSummary(event, "msg-1", null));
        TenantContext.runAs(COMPANY_A, () -> service.onSummary(event, "msg-1", null));

        ArgumentCaptor<RealtimeNotification> captor = ArgumentCaptor.forClass(RealtimeNotification.class);
        verify(realtimeService, times(2)).dispatch(eq(COMPANY_A), captor.capture(), isNull(), eq("msg-1"));
        RealtimeNotification notification = captor.getValue();
        assertThat(notification.id()).isEqualTo("msg-1");
        assertThat(notification.type()).isEqualTo(RealtimeEventType.STOCK_ALERT);
        assertThat(notification.type().topic(COMPANY_A)).isEqualTo("/topic/company/1/alerts");
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(notification.title()).isEqualTo("Hàng cận hạn sử dụng");
        assertThat(notification.message())
                .isEqualTo("Kho Hà Nội có 3 lô hàng cận hạn sử dụng trong 30 ngày tới. Ưu tiên xuất trước (FEFO)");
        assertThat(notification.payload()).isEqualTo(event);

        List<NotificationLog> logs = TenantContext.callAs(COMPANY_A, () -> repository.findAll());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getChannel()).isEqualTo(NotificationLog.CHANNEL_WEBSOCKET);
        assertThat(logs.get(0).getRecipient()).isEqualTo("/topic/company/1/alerts");
        assertThat(logs.get(0).getWarehouseId()).isEqualTo(5L);
        assertThat(logs.get(0).getDedupKey()).isEqualTo("SUMMARY:NEAR_EXPIRY:2026-11-11:5");
    }

    @Test
    void lowStockSummaryCountsKpiAndCriticalIsError() {
        TenantContext.runAs(COMPANY_A, () -> service.onSummary(lowStock(COMPANY_A, 5L), "msg-2", null));

        ArgumentCaptor<RealtimeNotification> captor = ArgumentCaptor.forClass(RealtimeNotification.class);
        verify(realtimeService).dispatch(eq(COMPANY_A), captor.capture(), eq(KpiMetric.LOW_STOCK_ALERTS), eq("msg-2"));
        assertThat(captor.getValue().severity()).isEqualTo(NotificationSeverity.ERROR);
        assertThat(captor.getValue().title()).isEqualTo("Cảnh báo tồn kho thấp");
    }

    @Test
    void companiesAreSeparated() {
        TenantContext.runAs(COMPANY_A, () -> service.onSummary(nearExpiry(COMPANY_A, 5L, "Kho của A"), "a", null));
        TenantContext.runAs(COMPANY_B, () -> service.onSummary(nearExpiry(COMPANY_B, 5L, "Kho của B"), "b", null));
        // Tin mang companyId của B nhưng header là A: bỏ qua, không ghi, không đẩy
        TenantContext.runAs(COMPANY_A, () -> service.onSummary(lowStock(COMPANY_B, 9L), "x", null));

        verify(realtimeService).dispatch(eq(COMPANY_A), any(), any(), eq("a"));
        verify(realtimeService).dispatch(eq(COMPANY_B), any(), any(), eq("b"));
        verify(realtimeService, never()).dispatch(any(), any(), any(), eq("x"));
        assertThat(TenantContext.callAs(COMPANY_A, () -> repository.findAll())).extracting(NotificationLog::getSubject)
                .allMatch(subject -> subject.contains("Kho của A")).hasSize(1);
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.findAll())).extracting(NotificationLog::getRecipient)
                .containsExactly("/topic/company/2/alerts");
    }
}
