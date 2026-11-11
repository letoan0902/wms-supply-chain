package vn.wms.notification.realtime;

import java.util.Set;

/**
 * Loại tin đẩy qua WebSocket và kênh STOMP tương ứng. Giá trị enum là trường {@code type} trong phong bì JSON,
 * frontend dựa vào đây để chọn cách hiển thị.
 *
 * <p>Kênh tách theo doanh nghiệp (WMS-22.4c): đích thật là {@code /topic/company/{companyId}/<kênh>}, ví dụ
 * {@code /topic/company/1/alerts}. Tin của doanh nghiệp nào chỉ đẩy lên kênh của doanh nghiệp đó.
 */
public enum RealtimeEventType {

    /** Tồn kho chạm ngưỡng an toàn (từ sự kiện alert.low-stock). */
    LOW_STOCK(RealtimeEventType.CHANNEL_ALERTS),
    /**
     * Bản tổng hợp cảnh báo tồn kho hằng ngày của một kho (từ sự kiện alert.stock-summary, WMS-15.2); payload là
     * {@code StockAlertSummaryEvent} với {@code kind} NEAR_EXPIRY, EXPIRED, DEAD_STOCK hoặc LOW_STOCK.
     */
    STOCK_ALERT(RealtimeEventType.CHANNEL_ALERTS),
    /** Đơn mua hoặc đơn bán mới (order.created). */
    ORDER_CREATED(RealtimeEventType.CHANNEL_ORDERS),
    /** Đơn đã được kho xử lý xong (order.dispatched). */
    ORDER_DISPATCHED(RealtimeEventType.CHANNEL_ORDERS),
    /** Tồn kho thay đổi (inventory.updated). */
    INVENTORY_UPDATED(RealtimeEventType.CHANNEL_INVENTORY),
    /** Bộ đếm KPI trong ngày vừa thay đổi, payload là {@link KpiSnapshot}. */
    KPI_UPDATED(RealtimeEventType.CHANNEL_KPI);

    /** Tiền tố chung của mọi kênh theo doanh nghiệp. */
    public static final String COMPANY_TOPIC_PREFIX = "/topic/company/";
    public static final String CHANNEL_ALERTS = "alerts";
    public static final String CHANNEL_ORDERS = "orders";
    public static final String CHANNEL_INVENTORY = "inventory";
    public static final String CHANNEL_KPI = "kpi";
    /** Các kênh client được đăng ký (sau {@code /topic/company/{companyId}/}). */
    public static final Set<String> CHANNELS = Set.of(CHANNEL_ALERTS, CHANNEL_ORDERS, CHANNEL_INVENTORY, CHANNEL_KPI);

    private final String channel;

    RealtimeEventType(String channel) {
        this.channel = channel;
    }

    /** Tên kênh, ví dụ {@code alerts}. */
    public String channel() {
        return channel;
    }

    /** Đích STOMP của loại tin này trong một doanh nghiệp, ví dụ {@code /topic/company/1/alerts}. */
    public String topic(Long companyId) {
        return topicOf(companyId, channel);
    }

    /** Đích STOMP của một kênh trong một doanh nghiệp. */
    public static String topicOf(Long companyId, String channel) {
        if (companyId == null || companyId <= 0) {
            throw new IllegalArgumentException("Kênh WebSocket phải gắn với một doanh nghiệp hợp lệ");
        }
        return COMPANY_TOPIC_PREFIX + companyId + "/" + channel;
    }
}
