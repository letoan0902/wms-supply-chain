package vn.wms.order.entity;

/**
 * Trạng thái chung của đơn mua (PO) và đơn bán (SO).
 * TODO (Sprint 2): bổ sung trạng thái nhận/xuất một phần khi làm phiếu nhập (GRN) và phiếu xuất (GDN).
 */
public enum OrderStatus {
    /** Nháp, còn chỉnh sửa được. */
    DRAFT,
    /** Đã xác nhận. */
    CONFIRMED,
    /** Đã hoàn tất nhập/xuất hàng. */
    COMPLETED,
    /** Đã hủy. */
    CANCELLED
}
