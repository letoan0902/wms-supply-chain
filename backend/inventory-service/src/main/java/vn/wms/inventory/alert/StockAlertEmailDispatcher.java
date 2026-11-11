package vn.wms.inventory.alert;

import org.springframework.stereotype.Component;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.StockAlertDigestEvent;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;
import vn.wms.common.tenant.TenantContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Yêu cầu gửi email cảnh báo (WMS-15.2): với một loại cảnh báo, tra người nhận của từng kho có cảnh báo qua endpoint
 * nội bộ của auth-service (quản lý của kho, admin của doanh nghiệp, trong doanh nghiệp hiện tại), gộp theo địa chỉ
 * email rồi phát MỘT {@code alert.stock-digest} cho mỗi người nhận, chỉ chứa các kho người đó phụ trách. Admin phụ trách
 * mọi kho nên nhận một email gồm tất cả các kho.
 */
@Component
public class StockAlertEmailDispatcher {

    private final AlertRecipientClient recipientClient;
    private final WmsEventPublisher eventPublisher;

    public StockAlertEmailDispatcher(AlertRecipientClient recipientClient, WmsEventPublisher eventPublisher) {
        this.recipientClient = recipientClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Phải gọi trong ngữ cảnh doanh nghiệp sở hữu tồn kho. Lỗi tra người nhận hoặc lỗi broker được ném ra cho người gọi.
     *
     * @param recipientsByWarehouse bộ nhớ đệm người nhận theo kho trong một lần quét (tra mỗi kho một lần cho mọi loại)
     * @return số email đã yêu cầu gửi
     */
    public int dispatch(LocalDate alertDate, Instant detectedAt, StockAlertKind kind,
                        List<StockAlertSummaryEvent> summaries, Map<Long, List<AlertRecipient>> recipientsByWarehouse) {
        Long companyId = TenantContext.requireCompanyId();
        Map<String, Recipient> byEmail = new LinkedHashMap<>();
        for (StockAlertSummaryEvent summary : summaries) {
            List<AlertRecipient> recipients = recipientsByWarehouse.computeIfAbsent(summary.warehouseId(), this::fetch);
            for (AlertRecipient recipient : recipients) {
                if (recipient == null || recipient.email() == null || recipient.email().isBlank()) {
                    continue;
                }
                String key = recipient.email().trim().toLowerCase(Locale.ROOT);
                byEmail.computeIfAbsent(key, k -> new Recipient(recipient)).sections.add(summary);
            }
        }
        for (Recipient recipient : byEmail.values()) {
            eventPublisher.publish(WmsEvents.RoutingKeys.ALERT_STOCK_DIGEST, new StockAlertDigestEvent(companyId,
                    alertDate, kind, recipient.info.email().trim(), recipient.info.fullName(), recipient.info.role(),
                    recipient.sections, detectedAt));
        }
        return byEmail.size();
    }

    private List<AlertRecipient> fetch(Long warehouseId) {
        ApiResponse<List<AlertRecipient>> response = recipientClient.alertRecipients(warehouseId);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("auth-service trả danh sách người nhận rỗng cho kho " + warehouseId);
        }
        return response.data();
    }

    private static final class Recipient {
        private final AlertRecipient info;
        private final List<StockAlertSummaryEvent> sections = new ArrayList<>();

        private Recipient(AlertRecipient info) {
            this.info = info;
        }
    }
}
