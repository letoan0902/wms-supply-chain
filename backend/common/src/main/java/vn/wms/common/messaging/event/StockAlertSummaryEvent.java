package vn.wms.common.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sự kiện {@code alert.stock-summary} (WMS-15.1): tổng hợp cảnh báo tồn kho của MỘT kho cho MỘT loại cảnh báo trong
 * một ngày, thay vì một tin cho mỗi lô. Tác vụ quét hằng ngày của inventory-service phát tối đa một tin cho mỗi bộ
 * (doanh nghiệp, ngày, loại, kho).
 *
 * @param companyId     doanh nghiệp sở hữu tồn kho (trùng header x-wms-company-id)
 * @param alertDate     ngày quét theo giờ Việt Nam
 * @param kind          loại cảnh báo
 * @param severity      mức cao nhất trong các dòng
 * @param warehouseId   kho
 * @param warehouseCode mã kho
 * @param warehouseName tên kho
 * @param totalItems    tổng số dòng cảnh báo của kho (có thể lớn hơn số dòng trong {@code items})
 * @param totalQuantity tổng tồn thực tế của các dòng cảnh báo
 * @param thresholdDays ngưỡng ngày đã dùng (cận hạn hoặc tồn lâu), null với EXPIRED và LOW_STOCK
 * @param items         các dòng nghiêm trọng nhất, tối đa theo cấu hình {@code app.alerts.top-items}
 * @param detectedAt    thời điểm quét
 */
public record StockAlertSummaryEvent(Long companyId, LocalDate alertDate, StockAlertKind kind,
                                     StockAlertSeverity severity, Long warehouseId, String warehouseCode,
                                     String warehouseName, int totalItems, BigDecimal totalQuantity,
                                     Integer thresholdDays, List<StockAlertItem> items, Instant detectedAt) {

    public StockAlertSummaryEvent {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
