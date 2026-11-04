package vn.wms.order.entity;

/**
 * Loại giao dịch công nợ với Khách hàng / Đại lý (FR14 - WMS-08.2).
 */
public enum CustomerTransactionType {
    /** Hóa đơn bán hàng / Đơn xuất bán (+ nợ phải thu). */
    SALES_INVOICE,

    /** Khách hàng thanh toán tiền hàng (- nợ phải thu). */
    PAYMENT,

    /** Khách hàng trả lại hàng (- nợ phải thu). */
    RETURN,

    /** Điều chỉnh số dư công nợ thủ công / nợ ban đầu. */
    ADJUSTMENT
}
