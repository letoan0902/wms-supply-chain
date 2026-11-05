package vn.wms.notification.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

/**
 * Đẩy phong bì thông báo lên topic STOMP và cập nhật bộ đếm KPI.
 *
 * <p>Thứ tự: đẩy thông báo chính trước, sau đó tăng KPI; nếu đếm được thì đẩy thêm snapshot lên {@code /topic/kpi}.
 * Sự kiện giao lại (trùng messageId) vẫn được đẩy lại thông báo chính với cùng {@code id} để frontend tự bỏ trùng,
 * nhưng không đếm KPI lần hai.
 */
@Service
public class RealtimeNotificationService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNotificationService.class);

    private final SimpMessageSendingOperations messagingTemplate;
    private final RealtimeKpiService kpiService;
    private final RealtimeNotificationFactory factory;

    public RealtimeNotificationService(SimpMessageSendingOperations messagingTemplate, RealtimeKpiService kpiService,
                                       RealtimeNotificationFactory factory) {
        this.messagingTemplate = messagingTemplate;
        this.kpiService = kpiService;
        this.factory = factory;
    }

    /**
     * @param notification phong bì đã dựng
     * @param metric       chỉ số KPI cần tăng, null nếu sự kiện không ảnh hưởng KPI
     * @param messageId    messageId của sự kiện gốc (chống đếm trùng), có thể null
     */
    public void dispatch(RealtimeNotification notification, KpiMetric metric, String messageId) {
        publish(notification);
        if (metric == null) {
            return;
        }
        kpiService.increment(metric, messageId).ifPresent(snapshot -> publish(factory.kpiUpdated(snapshot)));
    }

    /** Gửi một phong bì lên topic của loại tin. Lỗi kênh broker trong bộ nhớ chỉ ghi log, không làm hỏng consumer. */
    public void publish(RealtimeNotification notification) {
        try {
            messagingTemplate.convertAndSend(notification.type().topic(), notification);
            log.debug("Đã đẩy {} lên {} (id={})", notification.type(), notification.type().topic(), notification.id());
        } catch (MessagingException ex) {
            log.warn("Không đẩy được {} lên {} (id={}): {}", notification.type(), notification.type().topic(),
                    notification.id(), ex.getMessage());
        }
    }
}
