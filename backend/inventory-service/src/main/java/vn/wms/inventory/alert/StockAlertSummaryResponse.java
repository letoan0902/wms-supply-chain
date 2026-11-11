package vn.wms.inventory.alert;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Số cảnh báo hiện tại trong phạm vi kho của người gọi, dùng cho chuông thông báo và thanh tóm tắt (WMS-15.1).
 *
 * @param alertDate         ngày tính (giờ Việt Nam)
 * @param nearExpiryDays    ngưỡng cận hạn đang dùng
 * @param deadStockDays     ngưỡng tồn lâu đang dùng
 * @param total             tổng số cảnh báo
 * @param countsByKind      số cảnh báo theo loại (luôn đủ bốn khóa)
 * @param countsBySeverity  số cảnh báo theo mức (luôn đủ ba khóa)
 * @param lowStockAvailable false khi không đọc được mức tồn an toàn từ product-service (LOW_STOCK không tính được)
 * @param note              lý do khi {@code lowStockAvailable = false}, null khi bình thường
 * @param topItems          tối đa 5 cảnh báo nghiêm trọng nhất
 */
@Schema(description = "Tóm tắt cảnh báo tồn kho")
public record StockAlertSummaryResponse(LocalDate alertDate, int nearExpiryDays, int deadStockDays, long total,
                                        Map<StockAlertKind, Long> countsByKind,
                                        Map<StockAlertSeverity, Long> countsBySeverity,
                                        boolean lowStockAvailable, String note, List<StockAlertResponse> topItems) {
}
