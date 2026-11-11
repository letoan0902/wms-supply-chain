package vn.wms.notification.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import org.springframework.dao.DataIntegrityViolationException;
import vn.wms.common.messaging.event.LowStockAlertEvent;
import vn.wms.common.messaging.event.StockAlertDigestEvent;
import vn.wms.common.messaging.event.StockAlertItem;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.stockalert.StockAlertEmailService;
import vn.wms.notification.stockalert.StockAlertNotificationService;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.NotificationSeverity;
import vn.wms.notification.realtime.RealtimeEventType;
import vn.wms.notification.realtime.RealtimeNotification;
import vn.wms.notification.realtime.RealtimeNotificationService;
import vn.wms.notification.service.NotificationService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.wms.notification.messaging.ListenerTestSupport.COMPANY_A;
import static vn.wms.notification.messaging.ListenerTestSupport.COMPANY_B;
import static vn.wms.notification.messaging.ListenerTestSupport.json;
import static vn.wms.notification.messaging.ListenerTestSupport.message;
import static vn.wms.notification.messaging.ListenerTestSupport.ofCompany;
import static vn.wms.notification.messaging.ListenerTestSupport.withoutCompany;

/** Listener duy nhất của notification.alerts: cảnh báo tồn thấp lên WebSocket, định dạng email cũ vẫn gửi email. */
class AlertEventsListenerTest {

    private final RealtimeNotificationService realtimeService = mock(RealtimeNotificationService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final StockAlertNotificationService stockAlertNotificationService = mock(StockAlertNotificationService.class);
    private final StockAlertEmailService stockAlertEmailService = mock(StockAlertEmailService.class);
    private final AlertEventsListener listener = new AlertEventsListener(ListenerTestSupport.OBJECT_MAPPER,
            ListenerTestSupport.factory(), realtimeService, notificationService, stockAlertNotificationService,
            stockAlertEmailService);

    private final StockAlertSummaryEvent summary = new StockAlertSummaryEvent(COMPANY_A, LocalDate.of(2026, 11, 5),
            StockAlertKind.NEAR_EXPIRY, StockAlertSeverity.WARNING, 5L, "WH-HN", "Kho Hà Nội", 1,
            new BigDecimal("2.5"), 30, List.of(new StockAlertItem(100L, "SKU-001", "Sữa tươi", "L01", "WH-HN-Z1-R1-B1",
            new BigDecimal("2.5"), new BigDecimal("2.5"), LocalDate.of(2026, 11, 10), 5L, null, null, null, null,
            StockAlertSeverity.WARNING)), Instant.parse("2026-11-05T00:00:00Z"));

    private final StockAlertDigestEvent digest = new StockAlertDigestEvent(COMPANY_A, LocalDate.of(2026, 11, 5),
            StockAlertKind.NEAR_EXPIRY, "ql.kho@congty-a.vn", "Quản lý kho", "WAREHOUSE_MANAGER", List.of(summary),
            Instant.parse("2026-11-05T00:00:00Z"));

    @Test
    void stockSummaryGoesToStockAlertServiceInsideTenantContext() throws Exception {
        List<Long> context = new ArrayList<>();
        doAnswer(invocation -> {
            context.add(TenantContext.getCompanyId());
            return null;
        }).when(stockAlertNotificationService).onSummary(any(), any(), any());

        listener.onMessage(json(summary, WmsEvents.RoutingKeys.ALERT_STOCK_SUMMARY, "msg-s1"));

        verify(stockAlertNotificationService).onSummary(eq(summary), eq("msg-s1"), any());
        assertThat(context).containsExactly(COMPANY_A);
        verifyNoInteractions(notificationService, stockAlertEmailService);
    }

    @Test
    void stockDigestIsEmailedOnceAndDuplicateRaceIsAcknowledged() throws Exception {
        when(stockAlertEmailService.send(any(), any())).thenReturn(StockAlertEmailService.Outcome.SENT);
        listener.onMessage(json(digest, WmsEvents.RoutingKeys.ALERT_STOCK_DIGEST, "msg-d1"));
        verify(stockAlertEmailService).send(digest, "msg-d1");

        // Lượt khác vừa giữ chỗ cùng (người nhận, loại, ngày): không ném lỗi để broker không giao lại
        when(stockAlertEmailService.send(any(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_notification_logs_company_dedup"));
        assertThatCode(() -> listener.onMessage(json(digest, WmsEvents.RoutingKeys.ALERT_STOCK_DIGEST, "msg-d2")))
                .doesNotThrowAnyException();
        verifyNoInteractions(notificationService, realtimeService);
    }

    @Test
    void malformedStockAlertPayloadGoesToDeadLetter() {
        byte[] broken = "{khong phai json".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> listener.onMessage(message(broken, WmsEvents.RoutingKeys.ALERT_STOCK_DIGEST, "msg-d3")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onMessage(message(broken, WmsEvents.RoutingKeys.ALERT_STOCK_SUMMARY, "msg-s3")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(stockAlertEmailService, stockAlertNotificationService);
    }

    private final LowStockAlertEvent lowStock = new LowStockAlertEvent(5L, "Kho Hà Nội", 100L, "SKU-001",
            "Thùng carton", new BigDecimal("5"), new BigDecimal("20"), Instant.parse("2026-11-05T07:59:00Z"));

    @Test
    void lowStockEventIsPushedToAlertsTopicAndNotEmailed() throws Exception {
        listener.onMessage(json(lowStock, WmsEvents.RoutingKeys.ALERT_LOW_STOCK, "msg-1"));

        ArgumentCaptor<RealtimeNotification> captor = ArgumentCaptor.forClass(RealtimeNotification.class);
        verify(realtimeService).dispatch(eq(COMPANY_A), captor.capture(), eq(KpiMetric.LOW_STOCK_ALERTS), eq("msg-1"));
        RealtimeNotification notification = captor.getValue();
        assertThat(notification.type()).isEqualTo(RealtimeEventType.LOW_STOCK);
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(notification.message()).isEqualTo("SKU-001 Thùng carton tại Kho Hà Nội còn 5, dưới ngưỡng 20");
        assertThat(notification.payload()).isEqualTo(lowStock);
        verifyNoInteractions(notificationService);
    }

    @Test
    void eventTypeHeaderIsUsedWhenRoutingKeyIsMissing() throws Exception {
        Message message = json(lowStock, null, "msg-2");
        message.getMessageProperties().setHeader(WmsEventPublisher.HEADER_EVENT_TYPE, WmsEvents.RoutingKeys.ALERT_LOW_STOCK);

        listener.onMessage(message);

        verify(realtimeService).dispatch(eq(COMPANY_A), any(RealtimeNotification.class), eq(KpiMetric.LOW_STOCK_ALERTS), eq("msg-2"));
    }

    @Test
    void legacyEmailMessageOnOtherAlertKeyStillSendsEmail() throws Exception {
        NotificationMessage email = new NotificationMessage(
                "LOW_STOCK", "quan.ly.kho@wms.local", "Cảnh báo tồn kho thấp", "SKU-001 chỉ còn 3 cái");

        listener.onMessage(json(email, "alert.email", "msg-3"));

        verify(notificationService).process(email);
        verifyNoInteractions(realtimeService);
    }

    @Test
    void unknownAlertWithoutRecipientIsIgnored() throws Exception {
        assertThatCode(() -> listener.onMessage(json(Map.of("skuCode", "SKU-001", "level", "HIGH"),
                "alert.over-stock", "msg-4")))
                .doesNotThrowAnyException();
        verify(notificationService, never()).process(any());
        verifyNoInteractions(realtimeService);
    }

    @Test
    void rejectsMalformedPayloadWithoutRequeueOnBothBranches() {
        byte[] broken = "{khong phai json".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> listener.onMessage(message(broken, WmsEvents.RoutingKeys.ALERT_LOW_STOCK, "msg-5")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onMessage(message(broken, "alert.email", "msg-6")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(realtimeService, notificationService);
    }

    @Test
    void emailIsProcessedInsideTenantContextOfTheEvent() throws Exception {
        NotificationMessage email = new NotificationMessage(
                "LOW_STOCK", "quan.ly.kho.b@wms.local", "Cảnh báo tồn kho thấp", "SKU-009 chỉ còn 1 cái");
        List<Long> contextDuringProcess = new ArrayList<>();
        doAnswer(invocation -> {
            contextDuringProcess.add(TenantContext.getCompanyId());
            return null;
        }).when(notificationService).process(any());

        listener.onMessage(ofCompany(json(email, "alert.email", "msg-7"), COMPANY_B));

        verify(notificationService).process(email);
        assertThat(contextDuringProcess).containsExactly(COMPANY_B);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void companyIdFieldOfEventIsUsedWhenHeaderIsMissing() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("eventType", "LOW_STOCK");
        body.put("recipient", "quan.ly.kho.b@wms.local");
        body.put("subject", "Cảnh báo tồn kho thấp");
        body.put("content", "SKU-009 chỉ còn 1 cái");
        body.put("companyId", COMPANY_B);
        List<Long> contextDuringProcess = new ArrayList<>();
        doAnswer(invocation -> {
            contextDuringProcess.add(TenantContext.getCompanyId());
            return null;
        }).when(notificationService).process(any());

        listener.onMessage(withoutCompany(json(body, "alert.email", "msg-8")));

        assertThat(contextDuringProcess).containsExactly(COMPANY_B);
    }

    @Test
    void alertWithoutCompanyIsSkippedAndNoEmailIsSent() throws Exception {
        NotificationMessage email = new NotificationMessage(
                "LOW_STOCK", "quan.ly.kho@wms.local", "Cảnh báo tồn kho thấp", "SKU-001 chỉ còn 3 cái");

        assertThatCode(() -> listener.onMessage(withoutCompany(json(email, "alert.email", "msg-9"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.onMessage(withoutCompany(json(lowStock, WmsEvents.RoutingKeys.ALERT_LOW_STOCK,
                "msg-10")))).doesNotThrowAnyException();
        verifyNoInteractions(realtimeService, notificationService);
    }
}
