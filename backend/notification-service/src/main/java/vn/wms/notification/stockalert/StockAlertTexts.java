package vn.wms.notification.stockalert;

import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;
import vn.wms.notification.realtime.NotificationSeverity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Câu chữ tiếng Việt dùng chung cho thông báo chuông và email cảnh báo tồn kho (WMS-15.2). */
public final class StockAlertTexts {

    private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private StockAlertTexts() {
    }

    public static String title(StockAlertKind kind) {
        return switch (kind) {
            case NEAR_EXPIRY -> "Hàng cận hạn sử dụng";
            case EXPIRED -> "Hàng đã hết hạn sử dụng";
            case DEAD_STOCK -> "Hàng tồn lâu không luân chuyển";
            case LOW_STOCK -> "Cảnh báo tồn kho thấp";
        };
    }

    /** Một câu tóm tắt cho một kho, ví dụ "Kho Hà Nội có 3 lô hàng cận hạn sử dụng trong 30 ngày tới". */
    public static String summaryLine(StockAlertSummaryEvent event) {
        String warehouse = warehouse(event);
        int count = event.totalItems();
        return switch (event.kind()) {
            case NEAR_EXPIRY -> warehouse + " có " + count + " lô hàng cận hạn sử dụng"
                    + (event.thresholdDays() != null ? " trong " + event.thresholdDays() + " ngày tới" : "")
                    + ". Ưu tiên xuất trước (FEFO)";
            case EXPIRED -> warehouse + " có " + count + " lô hàng đã hết hạn sử dụng, không được phép xuất bán";
            case DEAD_STOCK -> warehouse + " có " + count + " mặt hàng không phát sinh nhập xuất"
                    + (event.thresholdDays() != null ? " từ " + event.thresholdDays() + " ngày trở lên" : "");
            case LOW_STOCK -> warehouse + " có " + count + " mặt hàng chạm ngưỡng tồn tối thiểu"
                    + (event.severity() == StockAlertSeverity.CRITICAL ? ", có mặt hàng dưới mức an toàn" : "")
                    + ". Đề xuất tạo đơn mua PO";
        };
    }

    public static String warehouse(StockAlertSummaryEvent event) {
        if (event.warehouseName() != null && !event.warehouseName().isBlank()) {
            return event.warehouseName();
        }
        return event.warehouseId() != null ? "Kho #" + event.warehouseId() : "Kho chưa xác định";
    }

    /** Mức hiển thị của phong bì WebSocket: CRITICAL thành error, WARNING thành warning, INFO thành info. */
    public static NotificationSeverity severity(StockAlertSeverity severity) {
        if (severity == null) {
            return NotificationSeverity.WARNING;
        }
        return switch (severity) {
            case CRITICAL -> NotificationSeverity.ERROR;
            case WARNING -> NotificationSeverity.WARNING;
            case INFO -> NotificationSeverity.INFO;
        };
    }

    public static String severityLabel(StockAlertSeverity severity) {
        if (severity == null) {
            return "";
        }
        return switch (severity) {
            case CRITICAL -> "Khẩn cấp";
            case WARNING -> "Cần chú ý";
            case INFO -> "Thông tin";
        };
    }

    public static String date(LocalDate date) {
        return date == null ? "" : date.format(DATE_VN);
    }

    /** Số lượng không có số 0 thừa: 5.000 thành 5, 2.500 thành 2.5. */
    public static String quantity(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }
}
