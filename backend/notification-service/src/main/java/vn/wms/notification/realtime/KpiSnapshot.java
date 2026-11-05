package vn.wms.notification.realtime;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Bộ đếm KPI trong một ngày (theo giờ Việt Nam, Asia/Ho_Chi_Minh), lưu ở Redis.
 *
 * @param date               ngày đếm
 * @param purchaseOrders     số đơn mua mới
 * @param salesOrders        số đơn bán mới
 * @param dispatchedOrders   số đơn đã được kho xử lý xong
 * @param lowStockAlerts     số cảnh báo tồn kho thấp
 * @param inventoryMovements số lần tồn kho thay đổi
 * @param updatedAt          lần tăng gần nhất, null khi trong ngày chưa có sự kiện
 */
public record KpiSnapshot(LocalDate date, long purchaseOrders, long salesOrders, long dispatchedOrders,
                          long lowStockAlerts, long inventoryMovements, Instant updatedAt) {

    /** Snapshot rỗng của một ngày chưa có sự kiện nào. */
    public static KpiSnapshot empty(LocalDate date) {
        return new KpiSnapshot(date, 0, 0, 0, 0, 0, null);
    }
}
