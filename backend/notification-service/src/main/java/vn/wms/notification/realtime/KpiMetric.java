package vn.wms.notification.realtime;

import vn.wms.common.messaging.event.OrderType;

/** Chỉ số KPI được tăng theo từng sự kiện; {@link #field()} là tên trường trong hash Redis và trong {@link KpiSnapshot}. */
public enum KpiMetric {
    PURCHASE_ORDERS("purchaseOrders"),
    SALES_ORDERS("salesOrders"),
    DISPATCHED_ORDERS("dispatchedOrders"),
    LOW_STOCK_ALERTS("lowStockAlerts"),
    INVENTORY_MOVEMENTS("inventoryMovements");

    private final String field;

    KpiMetric(String field) {
        this.field = field;
    }

    public String field() {
        return field;
    }

    /** Chỉ số tương ứng với đơn mới theo loại đơn; null khi sự kiện không ghi loại đơn (không đếm). */
    public static KpiMetric forNewOrder(OrderType orderType) {
        if (orderType == null) {
            return null;
        }
        return orderType == OrderType.PURCHASE ? PURCHASE_ORDERS : SALES_ORDERS;
    }
}
