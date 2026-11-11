package vn.wms.notification.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.AccountEmailKind;
import vn.wms.common.messaging.event.AccountEmailRequestedEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.service.AccountEmailService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static vn.wms.notification.messaging.ListenerTestSupport.COMPANY_A;
import static vn.wms.notification.messaging.ListenerTestSupport.COMPANY_B;
import static vn.wms.notification.messaging.ListenerTestSupport.json;
import static vn.wms.notification.messaging.ListenerTestSupport.message;
import static vn.wms.notification.messaging.ListenerTestSupport.ofCompany;
import static vn.wms.notification.messaging.ListenerTestSupport.withoutCompany;

/** Listener của notification.auth-emails (WMS-23.6): chuyển yêu cầu email tài khoản trong ngữ cảnh doanh nghiệp. */
class AccountEmailListenerTest {

    private final AccountEmailService accountEmailService = mock(AccountEmailService.class);
    private final AccountEmailListener listener = new AccountEmailListener(ListenerTestSupport.OBJECT_MAPPER,
            accountEmailService);

    private final AccountEmailRequestedEvent event = new AccountEmailRequestedEvent(AccountEmailKind.PASSWORD_RESET,
            COMPANY_A, "Công ty mẫu A", "an@example.com", "Nguyễn Văn An",
            "http://localhost:5173/reset-password?token=abc", Instant.parse("2026-11-11T03:30:00Z"),
            Instant.parse("2026-11-11T03:00:00Z"));

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void eventIsHandledInCompanyContextFromHeader() throws Exception {
        AtomicReference<Long> tenantDuringSend = new AtomicReference<>();
        doAnswer(invocation -> {
            tenantDuringSend.set(TenantContext.getCompanyId());
            return null;
        }).when(accountEmailService).send(any(), any());

        listener.onMessage(ofCompany(json(event, WmsEvents.RoutingKeys.AUTH_PASSWORD_RESET_REQUESTED, "msg-1"),
                COMPANY_B));

        verify(accountEmailService).send(eq(event), eq("msg-1"));
        assertThat(tenantDuringSend.get()).isEqualTo(COMPANY_B);
        assertThat(TenantContext.getCompanyId()).isNull();
    }

    @Test
    void companyIdFieldIsUsedWhenHeaderIsMissing() throws Exception {
        AtomicReference<Long> tenantDuringSend = new AtomicReference<>();
        doAnswer(invocation -> {
            tenantDuringSend.set(TenantContext.getCompanyId());
            return null;
        }).when(accountEmailService).send(any(), any());

        listener.onMessage(withoutCompany(json(event, WmsEvents.RoutingKeys.AUTH_PASSWORD_RESET_REQUESTED, "msg-2")));

        assertThat(tenantDuringSend.get()).isEqualTo(COMPANY_A);
    }

    @Test
    void eventWithoutCompanyOrLinkIsSkipped() throws Exception {
        AccountEmailRequestedEvent noCompany = new AccountEmailRequestedEvent(AccountEmailKind.ACTIVATION, null,
                "Công ty", "an@example.com", null, "http://localhost:5173/activate?token=abc", null, null);
        listener.onMessage(withoutCompany(json(noCompany, WmsEvents.RoutingKeys.AUTH_ACTIVATION_REQUESTED, "msg-3")));

        AccountEmailRequestedEvent noLink = new AccountEmailRequestedEvent(AccountEmailKind.ACTIVATION, COMPANY_A,
                "Công ty", "an@example.com", null, " ", null, null);
        listener.onMessage(json(noLink, WmsEvents.RoutingKeys.AUTH_ACTIVATION_REQUESTED, "msg-4"));

        verifyNoInteractions(accountEmailService);
    }

    @Test
    void malformedJsonGoesToDeadLetter() {
        assertThatThrownBy(() -> listener.onMessage(message("{khong-phai-json".getBytes(StandardCharsets.UTF_8),
                WmsEvents.RoutingKeys.AUTH_ACTIVATION_REQUESTED, "msg-5")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(accountEmailService);
    }
}
