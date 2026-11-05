package vn.wms.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import vn.wms.notification.realtime.RealtimeNotificationFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

/** Dữ liệu dùng chung cho test các listener: ObjectMapper, factory với đồng hồ cố định và cách dựng message thô. */
final class ListenerTestSupport {

    static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");
    static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

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
        return new Message(body, properties);
    }

    static Message json(Object payload, String routingKey, String messageId) throws Exception {
        return message(OBJECT_MAPPER.writeValueAsBytes(payload), routingKey, messageId);
    }
}
