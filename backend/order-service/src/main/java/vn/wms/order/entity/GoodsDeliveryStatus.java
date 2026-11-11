package vn.wms.order.entity;

/**
 * Trạng thái của Phiếu xuất kho (Goods Delivery Note - GDN) (WMS-12.1).
 */
public enum GoodsDeliveryStatus {
    /** Phiếu nháp đang chờ nhân viên kho xử lý. */
    DRAFT,
    /** Đang thực hiện lấy hàng theo Picking List. */
    PICKING,
    /** Đã hoàn tất xuất kho, giao hàng và giảm tồn kho. */
    COMPLETED,
    /** Phiếu xuất bị hủy. */
    CANCELLED
}
