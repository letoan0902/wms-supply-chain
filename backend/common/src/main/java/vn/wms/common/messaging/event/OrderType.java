package vn.wms.common.messaging.event;

/** Loại đơn hàng trong các sự kiện đơn hàng. */
public enum OrderType {
    /** Đơn mua hàng (Purchase Order), hàng nhập kho. */
    PURCHASE,
    /** Đơn bán hàng (Sales Order), hàng xuất kho. */
    SALES
}
