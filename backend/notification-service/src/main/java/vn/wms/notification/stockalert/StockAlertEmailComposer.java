package vn.wms.notification.stockalert;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import vn.wms.common.messaging.event.StockAlertDigestEvent;
import vn.wms.common.messaging.event.StockAlertItem;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Dựng tiêu đề và thân email HTML tiếng Việt cho một {@link StockAlertDigestEvent}: mỗi kho một đoạn tóm tắt và một
 * bảng ngắn các dòng nghiêm trọng nhất (WMS-15.2). Mọi giá trị lấy từ sự kiện đều được escape HTML.
 */
@Component
public class StockAlertEmailComposer {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String CELL = "padding:6px 8px;border:1px solid #d9d9d9;";
    private static final String HEAD = CELL + "background:#fafafa;text-align:left;";

    /** Tiêu đề, ví dụ "[WMS] Hàng cận hạn sử dụng ngày 11/11/2026: 3 dòng tại 2 kho". */
    public String subject(StockAlertDigestEvent digest) {
        int items = digest.sections().stream().mapToInt(StockAlertSummaryEvent::totalItems).sum();
        String subject = "[WMS] " + StockAlertTexts.title(digest.kind()) + " ngày " + StockAlertTexts.date(digest.alertDate())
                + ": " + items + " dòng tại " + digest.sections().size() + " kho";
        return subject.length() <= 255 ? subject : subject.substring(0, 255);
    }

    public String html(StockAlertDigestEvent digest) {
        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\"></head>")
                .append("<body style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#262626;\">");
        String name = digest.recipientName() != null && !digest.recipientName().isBlank()
                ? digest.recipientName() : digest.recipientEmail();
        html.append("<p>Kính gửi ").append(esc(name)).append(",</p>");
        html.append("<p>Kết quả quét tồn kho tự động ngày <strong>").append(StockAlertTexts.date(digest.alertDate()))
                .append("</strong> có <strong>").append(esc(StockAlertTexts.title(digest.kind()).toLowerCase(java.util.Locale.ROOT)))
                .append("</strong> tại các kho anh/chị phụ trách:</p>");
        for (StockAlertSummaryEvent section : digest.sections()) {
            appendSection(html, section);
        }
        html.append("<p style=\"color:#8c8c8c;font-size:12px;\">Mỗi bảng chỉ liệt kê các dòng nghiêm trọng nhất; ")
                .append("danh sách đầy đủ xem trên màn hình Tồn kho của hệ thống. ")
                .append("Email được gửi tự động từ hệ thống quản lý kho, vui lòng không trả lời.</p>")
                .append("</body></html>");
        return html.toString();
    }

    private void appendSection(StringBuilder html, StockAlertSummaryEvent section) {
        html.append("<h3 style=\"margin:20px 0 6px;\">").append(esc(StockAlertTexts.warehouse(section)));
        if (section.warehouseCode() != null) {
            html.append(" (").append(esc(section.warehouseCode())).append(')');
        }
        html.append("</h3><p style=\"margin:0 0 8px;\">").append(esc(StockAlertTexts.summaryLine(section)))
                .append(".</p>");
        List<String> headers = headers(section.kind());
        html.append("<table style=\"border-collapse:collapse;width:100%;\"><thead><tr>");
        for (String header : headers) {
            html.append("<th style=\"").append(HEAD).append("\">").append(esc(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (StockAlertItem item : section.items()) {
            html.append("<tr>");
            for (String value : row(section.kind(), item)) {
                html.append("<td style=\"").append(CELL).append("\">").append(esc(value)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        int hidden = section.totalItems() - section.items().size();
        if (hidden > 0) {
            html.append("<p style=\"margin:4px 0 0;font-size:12px;\">Còn ").append(hidden)
                    .append(" dòng khác không hiển thị trong email.</p>");
        }
    }

    private static List<String> headers(StockAlertKind kind) {
        return switch (kind) {
            case NEAR_EXPIRY, EXPIRED -> List.of("SKU", "Sản phẩm", "Số lô", "Ô kệ", "Tồn", "Hạn dùng", "Còn lại (ngày)");
            case DEAD_STOCK -> List.of("SKU", "Sản phẩm", "Tồn", "Biến động gần nhất", "Số ngày không biến động");
            case LOW_STOCK -> List.of("SKU", "Sản phẩm", "Tồn dùng được", "Mức an toàn", "Điểm đặt hàng lại", "Mức độ");
        };
    }

    private static List<String> row(StockAlertKind kind, StockAlertItem item) {
        String sku = item.skuCode() != null ? item.skuCode() : "#" + item.productId();
        String name = item.productName() != null ? item.productName() : "";
        return switch (kind) {
            case NEAR_EXPIRY, EXPIRED -> List.of(sku, name, nz(item.lotNo()), nz(item.binCode()),
                    StockAlertTexts.quantity(item.quantity()), StockAlertTexts.date(item.expDate()),
                    item.daysLeft() == null ? "" : String.valueOf(item.daysLeft()));
            case DEAD_STOCK -> List.of(sku, name, StockAlertTexts.quantity(item.quantity()),
                    date(item.lastMovementAt()),
                    item.daysWithoutMovement() == null ? "" : String.valueOf(item.daysWithoutMovement()));
            case LOW_STOCK -> List.of(sku, name, StockAlertTexts.quantity(item.quantity()),
                    StockAlertTexts.quantity(item.safetyStock()), StockAlertTexts.quantity(item.reorderPoint()),
                    StockAlertTexts.severityLabel(item.severity()));
        };
    }

    private static String date(Instant instant) {
        return instant == null ? "" : StockAlertTexts.date(instant.atZone(VN).toLocalDate());
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
