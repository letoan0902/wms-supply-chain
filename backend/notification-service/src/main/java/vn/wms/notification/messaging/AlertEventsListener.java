package vn.wms.notification.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.LowStockAlertEvent;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.RealtimeNotificationFactory;
import vn.wms.notification.realtime.RealtimeNotificationService;
import vn.wms.notification.service.NotificationService;

/**
 * Consumer DUY NHẤT của queue {@code notification.alerts} (binding {@code alert.#}). Một queue chỉ có một listener,
 * nếu có hai thì broker chia tin luân phiên giữa chúng, nên cả hai luồng đi qua lớp này và được phân biệt theo
 * loại sự kiện (routing key, không có thì header {@code x-wms-event}):
 *
 * <ul>
 *   <li>{@code alert.low-stock}: payload {@link LowStockAlertEvent} (docs/MESSAGING.md mục 3), đẩy phong bì lên
 *       {@code /topic/alerts} và tăng KPI cảnh báo tồn thấp.</li>
 *   <li>Mọi routing key {@code alert.*} khác (ví dụ {@code alert.email}): payload {@link NotificationMessage} cũ,
 *       chuyển cho {@link NotificationService} gửi email và ghi nhật ký như trước. Tin không có người nhận
 *       (loại cảnh báo chưa hỗ trợ) thì ghi log và bỏ qua.</li>
 * </ul>
 * JSON hỏng ở cả hai nhánh thì chuyển dead-letter.
 */
@Component
public class AlertEventsListener {

    private static final Logger log = LoggerFactory.getLogger(AlertEventsListener.class);

    private final ObjectReader eventReader;
    private final ObjectReader emailReader;
    private final RealtimeNotificationFactory factory;
    private final RealtimeNotificationService realtimeService;
    private final NotificationService notificationService;

    public AlertEventsListener(ObjectMapper objectMapper, RealtimeNotificationFactory factory,
                               RealtimeNotificationService realtimeService, NotificationService notificationService) {
        this.eventReader = objectMapper.reader();
        // Bỏ qua trường lạ để payload của loại cảnh báo khác không làm hỏng việc đọc, chỉ cần kiểm tra recipient
        this.emailReader = objectMapper.reader().without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.factory = factory;
        this.realtimeService = realtimeService;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue:notification.alerts}", id = "notificationAlerts")
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String eventType = EventMessages.eventType(properties);
        String messageId = properties.getMessageId();
        if (WmsEvents.RoutingKeys.ALERT_LOW_STOCK.equals(eventType)) {
            LowStockAlertEvent event = EventMessages.read(eventReader, message, LowStockAlertEvent.class);
            log.info("Nhận {} cho {} tại kho {} (messageId={}), đẩy lên WebSocket",
                    eventType, event.skuCode(), event.warehouseId(), messageId);
            realtimeService.dispatch(factory.lowStock(event, messageId, EventMessages.timestamp(properties)),
                    KpiMetric.LOW_STOCK_ALERTS, messageId);
            return;
        }
        NotificationMessage email = EventMessages.read(emailReader, message, NotificationMessage.class);
        if (!StringUtils.hasText(email.recipient())) {
            log.warn("Bỏ qua cảnh báo chưa hỗ trợ (không có người nhận email): routingKey='{}' messageId={}",
                    eventType, messageId);
            return;
        }
        log.info("Nhận sự kiện {} cho {} (routingKey='{}'), gửi email", email.eventType(), email.recipient(), eventType);
        notificationService.process(email);

        // Đẩy thông báo thời gian thực lên WebSocket /topic/alerts cho Admin toàn hệ thống
        if ("alert.registration".equals(eventType) || "COMPANY_REGISTRATION".equals(email.eventType())) {
            realtimeService.publish(new vn.wms.notification.realtime.RealtimeNotification(
                    messageId != null ? messageId : java.util.UUID.randomUUID().toString(),
                    vn.wms.notification.realtime.RealtimeEventType.LOW_STOCK,
                    vn.wms.notification.realtime.NotificationSeverity.INFO,
                    email.subject(),
                    email.content(),
                    EventMessages.timestamp(properties),
                    email
            ));
        }
    }
}
