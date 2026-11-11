package vn.wms.common.messaging.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sự kiện {@code alert.stock-digest} (WMS-15.2): yêu cầu notification-service gửi MỘT email cảnh báo tồn kho tới MỘT
 * người nhận cho MỘT loại cảnh báo trong ngày. inventory-service chọn người nhận trong chính doanh nghiệp sở hữu tồn
 * kho (quản lý của kho có cảnh báo và admin của doanh nghiệp, tra qua endpoint nội bộ của auth-service) và chỉ đưa vào
 * {@code sections} các kho người đó phụ trách. notification-service không gửi lại email cùng (người nhận, loại, ngày).
 *
 * @param companyId      doanh nghiệp sở hữu tồn kho (trùng header x-wms-company-id)
 * @param alertDate      ngày quét theo giờ Việt Nam
 * @param kind           loại cảnh báo
 * @param recipientEmail địa chỉ email người nhận
 * @param recipientName  họ tên người nhận (có thể null)
 * @param recipientRole  vai trò của người nhận: ADMIN hoặc WAREHOUSE_MANAGER
 * @param sections       bản tổng hợp của từng kho người nhận phụ trách
 * @param detectedAt     thời điểm quét
 */
public record StockAlertDigestEvent(Long companyId, LocalDate alertDate, StockAlertKind kind, String recipientEmail,
                                    String recipientName, String recipientRole, List<StockAlertSummaryEvent> sections,
                                    Instant detectedAt) {

    public StockAlertDigestEvent {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
