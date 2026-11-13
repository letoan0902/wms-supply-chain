package vn.wms.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.notification.realtime.RealtimeNotificationFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * Dữ liệu dùng chung cho test các listener: ObjectMapper, factory với đồng hồ cố định và cách dựng message thô.
 * Message mặc định mang header doanh nghiệp {@link #COMPANY_A} như khi {@link WmsEventPublisher} phát.
 */
final class ListenerTestSupport {

    static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");
    static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    static final Long COMPANY_A = 1L;
    static final Long COMPANY_B = 2L;

    private ListenerTestSupport() {
    }

    static RealtimeNotificationFactory factory() {
        return new RealtimeNotificationFactory(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static Message message(byte[] body, String routingKey, String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setReceivedRoutingKey(routingKey);
        properties.setMessageId(messageId);
        properties.setTimestamp(Date.from(NOW));
        properties.setHeader(WmsEventPublisher.HEADER_COMPANY_ID, COMPANY_A);
        return new Message(body, properties);
    }

    /** Bỏ header doanh nghiệp, giống message cũ phát trước khi có mô hình nhiều doanh nghiệp. */
    static Message withoutCompany(Message message) {
        message.getMessageProperties().getHeaders().remove(WmsEventPublisher.HEADER_COMPANY_ID);
        return message;
    }

    /** Đổi doanh nghiệp trong header của message. */
    static Message ofCompany(Message message, Long companyId) {
        message.getMessageProperties().setHeader(WmsEventPublisher.HEADER_COMPANY_ID, companyId);
        return message;
    }

    static Message json(Object payload, String routingKey, String messageId) throws Exception {
        return message(OBJECT_MAPPER.writeValueAsBytes(payload), routingKey, messageId);
    }
}
