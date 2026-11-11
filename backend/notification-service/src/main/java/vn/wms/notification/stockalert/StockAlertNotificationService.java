package vn.wms.notification.stockalert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.RealtimeEventType;
import vn.wms.notification.realtime.RealtimeNotification;
import vn.wms.notification.realtime.RealtimeNotificationFactory;
import vn.wms.notification.realtime.RealtimeNotificationService;

import java.time.Instant;

/**
 * Xử lý {@code alert.stock-summary} (WMS-15.2): ghi một dòng {@code notification_logs} (kênh WEBSOCKET, khóa chống trùng
 * {@code SUMMARY:<loại>:<ngày>:<kho>}) rồi đẩy phong bì {@code STOCK_ALERT} lên {@code /topic/company/{companyId}/alerts}.
 * Tin giao lại không ghi thêm dòng; phong bì vẫn được đẩy lại với cùng {@code id} để giao diện tự bỏ trùng, giống các
 * loại thông báo khác. Người gọi (listener) đặt TenantContext theo doanh nghiệp của sự kiện trước khi gọi.
 */
@Service
public class StockAlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(StockAlertNotificationService.class);

    private final StockAlertLogWriter logWriter;
    private final RealtimeNotificationFactory factory;
    private final RealtimeNotificationService realtimeService;

    public StockAlertNotificationService(StockAlertLogWriter logWriter, RealtimeNotificationFactory factory,
                                         RealtimeNotificationService realtimeService) {
        this.logWriter = logWriter;
        this.factory = factory;
        this.realtimeService = realtimeService;
    }

    public void onSummary(StockAlertSummaryEvent event, String messageId, Instant messageTimestamp) {
        Long companyId = TenantContext.requireCompanyId();
        if (event.companyId() != null && !event.companyId().equals(companyId)) {
            log.warn("Bỏ qua bản tổng hợp cảnh báo có companyId {} khác doanh nghiệp của tin {} (messageId={})",
                    event.companyId(), companyId, messageId);
            return;
        }
        if (event.kind() == null) {
            log.warn("Bỏ qua bản tổng hợp cảnh báo không có loại (messageId={})", messageId);
            return;
        }
        RealtimeNotification notification = factory.stockAlert(event, messageId, messageTimestamp);

        NotificationLog entry = new NotificationLog();
        entry.setChannel(NotificationLog.CHANNEL_WEBSOCKET);
        entry.setEventType("STOCK_ALERT_" + event.kind().name());
        entry.setRecipient(RealtimeEventType.STOCK_ALERT.topic(companyId));
        entry.setSubject(truncate(notification.title() + ": " + notification.message(), 255));
        entry.setStatus(NotificationStatus.SENT);
        entry.setAlertKind(event.kind().name());
        entry.setAlertDate(event.alertDate());
        entry.setWarehouseId(event.warehouseId());
        entry.setDedupKey("SUMMARY:" + event.kind().name() + ":" + event.alertDate() + ":" + event.warehouseId());
        try {
            if (!logWriter.saveIfAbsent(entry)) {
                log.info("Bản tổng hợp {} đã ghi nhật ký trước đó (messageId={}), chỉ đẩy lại thông báo",
                        entry.getDedupKey(), messageId);
            }
        } catch (DataIntegrityViolationException ex) {
            log.info("Bản tổng hợp {} vừa được ghi bởi lượt xử lý khác (messageId={})", entry.getDedupKey(), messageId);
        }

        KpiMetric metric = event.kind() == StockAlertKind.LOW_STOCK ? KpiMetric.LOW_STOCK_ALERTS : null;
        realtimeService.dispatch(companyId, notification, metric, messageId);
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
