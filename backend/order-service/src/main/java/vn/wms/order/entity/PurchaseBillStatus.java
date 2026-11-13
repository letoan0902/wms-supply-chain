package vn.wms.order.entity;

/**
 * Trạng thái thanh toán của Hóa đơn mua hàng (Purchase Bill - FR13 - WMS-10.3).
 */
public enum PurchaseBillStatus {
    /** Hóa đơn mới tạo, chưa thanh toán khoản nào. */
    UNPAID,

    /** Đã thanh toán một phần số tiền. */
    PARTIALLY_PAID,

    /** Đã thanh toán đầy đủ 100% công nợ (PAID). */
    PAID,

    /** Hóa đơn đã bị hủy. */
    CANCELLED
}
