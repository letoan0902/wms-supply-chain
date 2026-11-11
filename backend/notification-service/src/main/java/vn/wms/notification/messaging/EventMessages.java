package vn.wms.notification.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.util.StringUtils;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.tenant.TenantContext;
import vn.wms.common.tenant.TenantMessagePostProcessor;

import java.io.IOException;
import java.time.Instant;

/** Tiện ích dùng chung cho các listener nhận {@link Message} thô của notification-service. */
final class EventMessages {

    private EventMessages() {
    }

    /**
     * Loại sự kiện của message: routing key nhận được, không có thì lấy header {@code x-wms-event}
     * do {@link WmsEventPublisher} gắn; không có cả hai thì trả chuỗi rỗng.
     */
    static String eventType(MessageProperties properties) {
        String routingKey = properties.getReceivedRoutingKey();
        if (StringUtils.hasText(routingKey)) {
            return routingKey;
        }
        Object header = properties.getHeader(WmsEventPublisher.HEADER_EVENT_TYPE);
        return header == null ? "" : header.toString();
    }

    /** Tên trường doanh nghiệp trong nội dung sự kiện (dự phòng khi message không có header). */
    static final String FIELD_COMPANY_ID = "companyId";

    /**
     * Doanh nghiệp phát sinh sự kiện: header {@value WmsEventPublisher#HEADER_COMPANY_ID} do {@link WmsEventPublisher}
     * gắn, không có thì trường {@code companyId} ở cấp ngoài cùng của JSON. Trả {@code null} khi không có cả hai,
     * giá trị không phải số dương hoặc nội dung không đọc được (listener ghi cảnh báo và bỏ qua).
     */
    static Long companyId(ObjectReader reader, Message message) {
        Long fromHeader = TenantMessagePostProcessor.companyIdOf(message);
        if (fromHeader != null) {
            return fromHeader;
        }
        try {
            JsonNode field = reader.readTree(message.getBody()).path(FIELD_COMPANY_ID);
            if (field.isIntegralNumber()) {
                long value = field.asLong();
                return value > 0 ? value : null;
            }
            return field.isTextual() ? TenantContext.parse(field.asText()) : null;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /** Timestamp của message (WmsEventPublisher luôn gắn), null nếu bên gửi không đặt. */
    static Instant timestamp(MessageProperties properties) {
        return properties.getTimestamp() == null ? null : properties.getTimestamp().toInstant();
    }

    /**
     * Đọc JSON thành kiểu đích. Nội dung hỏng thì thử lại cũng vô ích: ném {@link AmqpRejectAndDontRequeueException}
     * để broker chuyển tin sang wms.dlx.
     */
    static <T> T read(ObjectReader reader, Message message, Class<T> type) {
        T value;
        try {
            value = reader.forType(type).readValue(message.getBody());
        } catch (IOException ex) {
            throw new AmqpRejectAndDontRequeueException(
                    "Nội dung sự kiện không đọc được thành " + type.getSimpleName() + ", chuyển dead-letter", ex);
        }
        if (value == null) {
            throw new AmqpRejectAndDontRequeueException(
                    "Nội dung sự kiện rỗng, không đọc được thành " + type.getSimpleName() + ", chuyển dead-letter");
        }
        return value;
    }
}
