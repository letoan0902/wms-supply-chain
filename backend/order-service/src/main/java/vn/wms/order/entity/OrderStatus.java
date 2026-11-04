package vn.wms.order.entity;

/**
 * Trạng thái của đơn mua hàng (PO) và đơn bán hàng (SO) (WMS-09.1 - FR10).
 */
public enum OrderStatus {
    /** Nháp, còn chỉnh sửa được. */
    DRAFT,
    /** Đang chờ duyệt (WMS-09.1). */
    PENDING,
    /** Đã phê duyệt (WMS-09.1). */
    APPROVED,
    /** Bị từ chối phê duyệt (WMS-09.1). */
    REJECTED,
    /** Đã xác nhận. */
    CONFIRMED,
    /** Đã hoàn tất nhập/xuất hàng. */
    COMPLETED,
    /** Đã hủy. */
    CANCELLED
}
