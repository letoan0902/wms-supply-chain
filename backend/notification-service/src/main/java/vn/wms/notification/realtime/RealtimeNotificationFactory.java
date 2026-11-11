package vn.wms.notification.realtime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.wms.common.messaging.event.InventoryUpdatedEvent;
import vn.wms.common.messaging.event.LowStockAlertEvent;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.common.messaging.event.OrderType;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dựng phong bì {@link RealtimeNotification} từ record sự kiện: chọn mức độ, tiêu đề và nội dung tiếng Việt sẵn sàng
 * hiển thị toast. Thời điểm lấy theo thứ tự: thời gian trong payload, timestamp của message RabbitMQ, đồng hồ hệ thống.
 */
@Component
public class RealtimeNotificationFactory {

    private static final Map<String, String> INVENTORY_REASONS = Map.of(
            "GOODS_RECEIPT", "Nhập kho",
            "GOODS_ISSUE", "Xuất kho",
            "ADJUSTMENT", "Điều chỉnh",
            "TRANSFER", "Điều chuyển",
            "STOCKTAKE", "Kiểm kê");

    private final Clock clock;

    public RealtimeNotificationFactory(Clock clock) {
        this.clock = clock;
    }

    /** Cảnh báo tồn kho thấp, gửi lên kênh {@code alerts} của doanh nghiệp. */
    public RealtimeNotification lowStock(LowStockAlertEvent event, String messageId, Instant messageTimestamp) {
        String product = joinNonBlank(event.skuCode(), event.productName());
        String quantity = quantity(event.quantityOnHand());
        String threshold = quantity(event.minimumQuantity());
        boolean atThreshold = event.quantityOnHand() != null && event.minimumQuantity() != null
                && event.quantityOnHand().compareTo(event.minimumQuantity()) == 0;
        String text = (product.isEmpty() ? "Sản phẩm" : product) + " tại " + warehouse(event.warehouseName(), event.warehouseId())
                + " còn " + quantity + (atThreshold ? ", chạm ngưỡng " : ", dưới ngưỡng ") + threshold;
        return new RealtimeNotification(idOf(messageId), RealtimeEventType.LOW_STOCK, NotificationSeverity.WARNING,
                "Cảnh báo tồn kho thấp", text, occurredAt(event.detectedAt(), messageTimestamp), event);
    }

    /** Đơn mua hoặc đơn bán mới, gửi lên kênh {@code orders} của doanh nghiệp. */
    public RealtimeNotification orderCreated(OrderCreatedEvent event, String messageId, Instant messageTimestamp) {
        String title = byOrderType(event.orderType(), "Đơn mua hàng mới", "Đơn bán hàng mới", "Đơn hàng mới");
        List<String> parts = new ArrayList<>();
        parts.add("Đơn " + orderNumber(event.orderNumber()) + " tại " + warehouse(event.warehouseName(), event.warehouseId()));
        if (!event.items().isEmpty()) {
            parts.add(event.items().size() + " dòng hàng");
        }
        if (event.totalAmount() != null) {
            parts.add("tổng tiền " + money(event.totalAmount()));
        }
        return new RealtimeNotification(idOf(messageId), RealtimeEventType.ORDER_CREATED, NotificationSeverity.INFO,
                title, String.join(", ", parts), occurredAt(event.createdAt(), messageTimestamp), event);
    }

    /** Đơn đã được kho xử lý xong (xuất kho với đơn bán, nhập kho với đơn mua), gửi lên kênh {@code orders} của doanh nghiệp. */
    public RealtimeNotification orderDispatched(OrderDispatchedEvent event, String messageId, Instant messageTimestamp) {
        String title = byOrderType(event.orderType(), "Đơn mua hàng đã nhập kho", "Đơn bán hàng đã xuất kho",
                "Đơn hàng đã được kho xử lý xong");
        List<String> parts = new ArrayList<>();
        parts.add("Đơn " + orderNumber(event.orderNumber()) + " tại " + warehouse(null, event.warehouseId())
                + " đã hoàn tất");
        if (!event.items().isEmpty()) {
            parts.add(event.items().size() + " dòng hàng");
        }
        if (StringUtils.hasText(event.dispatchedBy())) {
            parts.add("người xác nhận " + event.dispatchedBy());
        }
        return new RealtimeNotification(idOf(messageId), RealtimeEventType.ORDER_DISPATCHED, NotificationSeverity.SUCCESS,
                title, String.join(", ", parts), occurredAt(event.dispatchedAt(), messageTimestamp), event);
    }

    /** Tồn kho thay đổi, gửi lên kênh {@code inventory} của doanh nghiệp. */
    public RealtimeNotification inventoryUpdated(InventoryUpdatedEvent event, String messageId, Instant messageTimestamp) {
        String sku = StringUtils.hasText(event.skuCode()) ? event.skuCode() : "Sản phẩm #" + event.productId();
        StringBuilder text = new StringBuilder(sku)
                .append(" tại ").append(warehouse(null, event.warehouseId()))
                .append(" thay đổi từ ").append(quantity(event.quantityBefore()))
                .append(" thành ").append(quantity(event.quantityAfter()));
        List<String> details = new ArrayList<>(2);
        if (StringUtils.hasText(event.reason())) {
            details.add(INVENTORY_REASONS.getOrDefault(event.reason(), event.reason()));
        }
        if (StringUtils.hasText(event.referenceNumber())) {
            details.add("chứng từ " + event.referenceNumber());
        }
        if (!details.isEmpty()) {
            text.append(" (").append(String.join(", ", details)).append(')');
        }
        return new RealtimeNotification(idOf(messageId), RealtimeEventType.INVENTORY_UPDATED, NotificationSeverity.INFO,
                "Tồn kho thay đổi", text.toString(), occurredAt(event.updatedAt(), messageTimestamp), event);
    }

    /** Snapshot KPI sau mỗi lần tăng, gửi lên kênh {@code kpi} của doanh nghiệp. Id luôn mới vì không gắn với một sự kiện cụ thể. */
    public RealtimeNotification kpiUpdated(KpiSnapshot snapshot) {
        String text = "Hôm nay: " + snapshot.purchaseOrders() + " đơn mua, " + snapshot.salesOrders() + " đơn bán, "
                + snapshot.dispatchedOrders() + " đơn đã xử lý xong, " + snapshot.lowStockAlerts()
                + " cảnh báo tồn thấp, " + snapshot.inventoryMovements() + " lần thay đổi tồn kho";
        return new RealtimeNotification(UUID.randomUUID().toString(), RealtimeEventType.KPI_UPDATED,
                NotificationSeverity.INFO, "Cập nhật chỉ số KPI", text,
                snapshot.updatedAt() != null ? snapshot.updatedAt() : clock.instant(), snapshot);
    }

    private static String idOf(String messageId) {
        return StringUtils.hasText(messageId) ? messageId : UUID.randomUUID().toString();
    }

    private Instant occurredAt(Instant eventTime, Instant messageTimestamp) {
        if (eventTime != null) {
            return eventTime;
        }
        return messageTimestamp != null ? messageTimestamp : clock.instant();
    }

    private static String byOrderType(OrderType type, String purchase, String sales, String unknown) {
        if (type == OrderType.PURCHASE) {
            return purchase;
        }
        return type == OrderType.SALES ? sales : unknown;
    }

    private static String orderNumber(String orderNumber) {
        return StringUtils.hasText(orderNumber) ? orderNumber : "(chưa có mã)";
    }

    private static String warehouse(String name, Long id) {
        if (StringUtils.hasText(name)) {
            return name;
        }
        return id != null ? "kho #" + id : "kho chưa xác định";
    }

    /** Số lượng không có số 0 thừa: 5.000 thành 5, 2.500 thành 2.5. */
    static String quantity(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }

    /** Tiền VNĐ, phân cách hàng nghìn bằng dấu chấm: 1200000.00 thành 1.200.000 VNĐ. */
    static String money(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symbols).format(value) + " VNĐ";
    }

    private static String joinNonBlank(String first, String second) {
        List<String> parts = new ArrayList<>(2);
        if (StringUtils.hasText(first)) {
            parts.add(first);
        }
        if (StringUtils.hasText(second)) {
            parts.add(second);
        }
        return String.join(" ", parts);
    }
}
