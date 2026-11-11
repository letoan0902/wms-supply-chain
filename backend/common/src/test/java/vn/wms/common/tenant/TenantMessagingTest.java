package vn.wms.common.tenant;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.security.UserContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Doanh nghiệp đi kèm sự kiện RabbitMQ và lời gọi OpenFeign giữa các service. */
class TenantMessagingTest {

    private static final String ROUTING_KEY = WmsEvents.RoutingKeys.ORDER_CREATED;

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final WmsEventPublisher publisher = new WmsEventPublisher(rabbitTemplate, "order-service");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishedEventCarriesCompanyOfCurrentContext() {
        TenantContext.setCompanyId(3L);

        publisher.publish(ROUTING_KEY, Map.of("orderId", 1));

        assertThat(sentMessage().getMessageProperties().<Long>getHeader(WmsEventPublisher.HEADER_COMPANY_ID))
                .isEqualTo(3L);
    }

    @Test
    void publishedEventHasNoCompanyHeaderWhenContextIsEmpty() {
        publisher.publish(ROUTING_KEY, Map.of("orderId", 1));

        assertThat(sentMessage().getMessageProperties().getHeaders())
                .doesNotContainKey(WmsEventPublisher.HEADER_COMPANY_ID);
    }

    @Test
    void publishAfterCommitKeepsCompanyCapturedAtRegistration() {
        TransactionSynchronizationManager.initSynchronization();
        TenantContext.setCompanyId(3L);
        publisher.publishAfterCommit(ROUTING_KEY, Map.of("orderId", 1));

        // Ngữ cảnh của luồng đã đổi trước khi transaction commit: sự kiện vẫn phải mang doanh nghiệp ban đầu
        TenantContext.setCompanyId(9L);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        assertThat(sentMessage().getMessageProperties().<Long>getHeader(WmsEventPublisher.HEADER_COMPANY_ID))
                .isEqualTo(3L);
    }

    @Test
    void listenerPostProcessorSetsCompanyFromHeader() {
        TenantMessagePostProcessor postProcessor = new TenantMessagePostProcessor();

        postProcessor.postProcessMessage(message(5L));
        assertThat(TenantContext.getCompanyId()).isEqualTo(5L);

        // Header có thể về dạng Integer hoặc chuỗi tùy bên phát
        postProcessor.postProcessMessage(message(6));
        assertThat(TenantContext.getCompanyId()).isEqualTo(6L);
        postProcessor.postProcessMessage(message("7"));
        assertThat(TenantContext.getCompanyId()).isEqualTo(7L);
    }

    @Test
    void listenerPostProcessorClearsCompanyLeftByPreviousMessage() {
        TenantMessagePostProcessor postProcessor = new TenantMessagePostProcessor();
        postProcessor.postProcessMessage(message(5L));

        postProcessor.postProcessMessage(message(null));
        assertThat(TenantContext.getCompanyId()).isNull();

        postProcessor.postProcessMessage(message(5L));
        postProcessor.postProcessMessage(message("không phải số"));
        assertThat(TenantContext.getCompanyId()).isNull();
    }

    @Test
    void feignInterceptorForwardsCompanyAndUserHeaders() {
        TenantContext.setCompanyId(4L);
        MockHttpServletRequest incoming = new MockHttpServletRequest("GET", "/api/orders/sales");
        incoming.addHeader(UserContext.HEADER_USER_NAME, "sales01");
        incoming.addHeader(UserContext.HEADER_USER_ROLES, "SALES");
        incoming.addHeader(UserContext.HEADER_USER_WAREHOUSE_IDS, "1,2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
        RequestTemplate template = new RequestTemplate();
        // Giá trị do code gọi tự đặt (có thể sai) phải bị thay bằng doanh nghiệp trong ngữ cảnh
        template.header(TenantContext.HEADER_COMPANY_ID, "999");

        new TenantFeignRequestInterceptor().apply(template);

        assertThat(template.headers().get(TenantContext.HEADER_COMPANY_ID)).containsExactly("4");
        assertThat(template.headers().get(UserContext.HEADER_USER_NAME)).containsExactly("sales01");
        assertThat(template.headers().get(UserContext.HEADER_USER_ROLES)).containsExactly("SALES");
        assertThat(template.headers().get(UserContext.HEADER_USER_WAREHOUSE_IDS)).containsExactly("1,2");
    }

    @Test
    void feignInterceptorAddsNothingOutsideRequestAndWithoutCompany() {
        RequestTemplate template = new RequestTemplate();

        new TenantFeignRequestInterceptor().apply(template);

        assertThat(template.headers()).isEmpty();
    }

    private Message sentMessage() {
        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(WmsEvents.EXCHANGE), eq(ROUTING_KEY), any(Object.class), captor.capture());
        return captor.getValue().postProcessMessage(message(null));
    }

    private static Message message(Object companyHeader) {
        MessageProperties properties = new MessageProperties();
        if (companyHeader != null) {
            properties.setHeader(WmsEventPublisher.HEADER_COMPANY_ID, companyHeader);
        }
        return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
    }
}
