package vn.wms.notification.dto.dashboard;

/**
 * Thẻ 6: Đơn bán chờ xuất kho (Pending Sales Orders - SO).
 */
public record PendingSalesOrdersCardDto(
        String title,
        long totalOrders,
        long urgentOrders,
        String subText,
        boolean visible
) {}
