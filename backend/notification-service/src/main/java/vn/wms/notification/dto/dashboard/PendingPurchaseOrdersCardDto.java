package vn.wms.notification.dto.dashboard;

/**
 * Thẻ 7: Đơn mua chờ tiếp nhận (Pending Purchase Orders - PO).
 */
public record PendingPurchaseOrdersCardDto(
        String title,
        long totalOrders,
        long arrivingTodayCount,
        String subText,
        boolean visible
) {}
