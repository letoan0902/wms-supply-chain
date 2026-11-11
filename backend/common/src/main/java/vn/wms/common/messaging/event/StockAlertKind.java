package vn.wms.common.messaging.event;

/**
 * Loại cảnh báo tồn kho do inventory-service phát hiện (WMS-15.1). Dùng chung cho API cảnh báo, sự kiện
 * {@code alert.stock-summary} và email cảnh báo.
 */
public enum StockAlertKind {

    /** Lô còn hàng có hạn sử dụng trong ngưỡng cận hạn (mặc định 30 ngày), chưa tới ngày hết hạn. */
    NEAR_EXPIRY,

    /** Lô còn hàng đã tới hoặc quá hạn sử dụng (hạn dùng nhỏ hơn hoặc bằng hôm nay). */
    EXPIRED,

    /** Mặt hàng còn tồn trong kho nhưng không có biến động trong số ngày cấu hình (mặc định 90 ngày). */
    DEAD_STOCK,

    /** Tồn dùng được của mặt hàng tại kho nhỏ hơn hoặc bằng điểm đặt hàng lại hoặc mức tồn an toàn. */
    LOW_STOCK
}
