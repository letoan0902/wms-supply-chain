package vn.wms.order.entity;

/**
 * Trạng thái của Phiếu nhập kho (Goods Receipt Note - GRN) (FR11 - WMS-10.1).
 */
public enum GoodsReceiptStatus {
    /** Phiếu nháp, đang kiểm đếm. */
    DRAFT,

    /** Đã hoàn tất nhập kho, ghi nhận số lượng đạt/hỏng. */
    COMPLETED,

    /** Đã hủy phiếu nhập. */
    CANCELLED
}
