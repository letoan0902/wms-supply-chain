package vn.wms.notification.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import vn.wms.notification.service.NotificationService;

/** Consumer nhận sự kiện cảnh báo từ queue và chuyển cho NotificationService xử lý. */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue:notification.alerts}")
    public void onMessage(NotificationMessage message) {
        log.info("Nhận sự kiện {} cho {}", message.eventType(), message.recipient());
        notificationService.process(message);
    }
}
