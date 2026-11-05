package vn.wms.notification.realtime;

/**
 * Loại tin đẩy qua WebSocket và topic STOMP tương ứng. Giá trị enum là trường {@code type} trong phong bì JSON,
 * frontend dựa vào đây để chọn cách hiển thị.
 */
public enum RealtimeEventType {

    /** Tồn kho chạm ngưỡng an toàn (từ sự kiện alert.low-stock). */
    LOW_STOCK(RealtimeEventType.TOPIC_ALERTS),
    /** Đơn mua hoặc đơn bán mới (order.created). */
    ORDER_CREATED(RealtimeEventType.TOPIC_ORDERS),
    /** Đơn đã được kho xử lý xong (order.dispatched). */
    ORDER_DISPATCHED(RealtimeEventType.TOPIC_ORDERS),
    /** Tồn kho thay đổi (inventory.updated). */
    INVENTORY_UPDATED(RealtimeEventType.TOPIC_INVENTORY),
    /** Bộ đếm KPI trong ngày vừa thay đổi, payload là {@link KpiSnapshot}. */
    KPI_UPDATED(RealtimeEventType.TOPIC_KPI);

    public static final String TOPIC_ALERTS = "/topic/alerts";
    public static final String TOPIC_ORDERS = "/topic/orders";
    public static final String TOPIC_INVENTORY = "/topic/inventory";
    public static final String TOPIC_KPI = "/topic/kpi";

    private final String topic;

    RealtimeEventType(String topic) {
        this.topic = topic;
    }

    /** Đích STOMP mà client SUBSCRIBE để nhận loại tin này. */
    public String topic() {
        return topic;
    }
}
