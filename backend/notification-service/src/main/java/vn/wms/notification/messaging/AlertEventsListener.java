package vn.wms.notification.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.LowStockAlertEvent;
import vn.wms.common.messaging.event.StockAlertDigestEvent;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.RealtimeNotificationFactory;
import vn.wms.notification.realtime.RealtimeNotificationService;
import vn.wms.notification.service.NotificationService;
import vn.wms.notification.stockalert.StockAlertEmailService;
import vn.wms.notification.stockalert.StockAlertNotificationService;

/**
 * Consumer DUY NHẤT của queue {@code notification.alerts} (binding {@code alert.#}). Một queue chỉ có một listener,
 * nếu có hai thì broker chia tin luân phiên giữa chúng, nên mọi luồng đi qua lớp này và được phân biệt theo
 * loại sự kiện (routing key, không có thì header {@code x-wms-event}):
 *
 * <ul>
 *   <li>{@code alert.low-stock}: payload {@link LowStockAlertEvent} (docs/MESSAGING.md mục 3), đẩy phong bì lên
 *       {@code /topic/company/{companyId}/alerts} và tăng KPI cảnh báo tồn thấp của doanh nghiệp đó.</li>
 *   <li>{@code alert.stock-summary}: payload {@link StockAlertSummaryEvent} (WMS-15.2), ghi nhật ký và đẩy phong bì
 *       {@code STOCK_ALERT} lên kênh alerts của doanh nghiệp.</li>
 *   <li>{@code alert.stock-digest}: payload {@link StockAlertDigestEvent} (WMS-15.2), gửi một email cảnh báo cho một
 *       người nhận, không gửi lại cùng (người nhận, loại, ngày).</li>
 *   <li>Mọi routing key {@code alert.*} khác (ví dụ {@code alert.email}): payload {@link NotificationMessage} cũ,
 *       chuyển cho {@link NotificationService} gửi email và ghi nhật ký dưới doanh nghiệp của sự kiện. Tin không có
 *       người nhận (loại cảnh báo chưa hỗ trợ) thì ghi log và bỏ qua.</li>
 * </ul>
 * Mọi nhánh chạy trong ngữ cảnh doanh nghiệp của sự kiện (WMS-22.4c); sự kiện không xác định được doanh nghiệp thì
 * ghi log cảnh báo và bỏ qua (ack, không thử lại, không gửi email). JSON hỏng thì chuyển dead-letter. Lỗi SMTP không
 * làm tin bị thử lại (ghi nhật ký FAILED).
 */
@Component
public class AlertEventsListener {

    private static final Logger log = LoggerFactory.getLogger(AlertEventsListener.class);

    private final ObjectReader eventReader;
    private final ObjectReader emailReader;
    private final RealtimeNotificationFactory factory;
    private final RealtimeNotificationService realtimeService;
    private final NotificationService notificationService;
    private final StockAlertNotificationService stockAlertNotificationService;
    private final StockAlertEmailService stockAlertEmailService;

    public AlertEventsListener(ObjectMapper objectMapper, RealtimeNotificationFactory factory,
                               RealtimeNotificationService realtimeService, NotificationService notificationService,
                               StockAlertNotificationService stockAlertNotificationService,
                               StockAlertEmailService stockAlertEmailService) {
        this.eventReader = objectMapper.reader();
        // Bỏ qua trường lạ để payload của loại cảnh báo khác không làm hỏng việc đọc, chỉ cần kiểm tra recipient
        this.emailReader = objectMapper.reader().without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.factory = factory;
        this.realtimeService = realtimeService;
        this.notificationService = notificationService;
        this.stockAlertNotificationService = stockAlertNotificationService;
        this.stockAlertEmailService = stockAlertEmailService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue:notification.alerts}", id = "notificationAlerts")
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String eventType = EventMessages.eventType(properties);
        String messageId = properties.getMessageId();
        Long companyId = EventMessages.companyId(eventReader, message);
        if (companyId == null) {
            log.warn("Bỏ qua cảnh báo không có doanh nghiệp (header x-wms-company-id hoặc trường companyId): "
                    + "routingKey='{}' messageId={}", eventType, messageId);
            return;
        }
        // Đặt ngữ cảnh trước khi gọi các service để transaction ghi nhật ký mở đúng doanh nghiệp
        TenantContext.runAs(companyId, () -> handle(message, properties, eventType, messageId, companyId));
    }

    private void handle(Message message, MessageProperties properties, String eventType, String messageId,
                        Long companyId) {
        if (WmsEvents.RoutingKeys.ALERT_LOW_STOCK.equals(eventType)) {
            LowStockAlertEvent event = EventMessages.read(eventReader, message, LowStockAlertEvent.class);
            log.info("Nhận {} cho {} tại kho {} (messageId={}, companyId={}), đẩy lên WebSocket",
                    eventType, event.skuCode(), event.warehouseId(), messageId, companyId);
            realtimeService.dispatch(companyId, factory.lowStock(event, messageId, EventMessages.timestamp(properties)),
                    KpiMetric.LOW_STOCK_ALERTS, messageId);
            return;
        }
        if (WmsEvents.RoutingKeys.ALERT_STOCK_SUMMARY.equals(eventType)) {
            StockAlertSummaryEvent event = EventMessages.read(emailReader, message, StockAlertSummaryEvent.class);
            log.info("Nhận {} loại {} tại kho {} (messageId={}, companyId={})",
                    eventType, event.kind(), event.warehouseId(), messageId, companyId);
            stockAlertNotificationService.onSummary(event, messageId, EventMessages.timestamp(properties));
            return;
        }
        if (WmsEvents.RoutingKeys.ALERT_STOCK_DIGEST.equals(eventType)) {
            StockAlertDigestEvent digest = EventMessages.read(emailReader, message, StockAlertDigestEvent.class);
            try {
                StockAlertEmailService.Outcome outcome = stockAlertEmailService.send(digest, messageId);
                log.info("Email cảnh báo {} ngày {}: {} (messageId={}, companyId={})",
                        digest.kind(), digest.alertDate(), outcome, messageId, companyId);
            } catch (DataIntegrityViolationException ex) {
                // Lượt xử lý khác vừa giữ chỗ cùng (người nhận, loại, ngày): email đó đã hoặc đang được gửi
                log.info("Email cảnh báo {} ngày {} đã được xử lý ở lượt khác (messageId={})",
                        digest.kind(), digest.alertDate(), messageId);
            }
            return;
        }
        NotificationMessage email = EventMessages.read(emailReader, message, NotificationMessage.class);
        if (!StringUtils.hasText(email.recipient())) {
            log.warn("Bỏ qua cảnh báo chưa hỗ trợ (không có người nhận email): routingKey='{}' messageId={}",
                    eventType, messageId);
            return;
        }
        log.info("Nhận sự kiện {} cho {} (routingKey='{}', companyId={}), gửi email",
                email.eventType(), email.recipient(), eventType, companyId);
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
