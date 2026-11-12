package vn.wms.notification.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

/**
 * Đẩy phong bì thông báo lên kênh STOMP của doanh nghiệp và cập nhật bộ đếm KPI của doanh nghiệp đó.
 *
 * <p>Thứ tự: đẩy thông báo chính trước, sau đó tăng KPI; nếu đếm được thì đẩy thêm snapshot lên
 * {@code /topic/company/{companyId}/kpi}. Sự kiện giao lại (trùng messageId) vẫn được đẩy lại thông báo chính với cùng
 * {@code id} để frontend tự bỏ trùng, nhưng không đếm KPI lần hai. Không có gì dùng chung giữa các doanh nghiệp:
 * kênh và khóa Redis đều mang {@code companyId} (WMS-22.4c).
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
     * @param companyId    doanh nghiệp phát sinh sự kiện, quyết định kênh và bộ đếm KPI
     * @param notification phong bì đã dựng
     * @param metric       chỉ số KPI cần tăng, null nếu sự kiện không ảnh hưởng KPI
     * @param messageId    messageId của sự kiện gốc (chống đếm trùng), có thể null
     */
    public void dispatch(Long companyId, RealtimeNotification notification, KpiMetric metric, String messageId) {
        publish(companyId, notification);
        if (metric == null) {
            return;
        }
        kpiService.increment(companyId, metric, messageId)
                .ifPresent(snapshot -> publish(companyId, factory.kpiUpdated(snapshot)));
    }

    /** Gửi một phong bì lên kênh của doanh nghiệp. Lỗi kênh broker trong bộ nhớ chỉ ghi log, không làm hỏng consumer. */
    public void publish(Long companyId, RealtimeNotification notification) {
        String destination = notification.type().topic(companyId);
        try {
            messagingTemplate.convertAndSend(destination, notification);
            log.debug("Đã đẩy {} lên {} (id={})", notification.type(), destination, notification.id());
        } catch (MessagingException ex) {
            log.warn("Không đẩy được {} lên {} (id={}): {}", notification.type(), destination,
                    notification.id(), ex.getMessage());
        }
    }
}
