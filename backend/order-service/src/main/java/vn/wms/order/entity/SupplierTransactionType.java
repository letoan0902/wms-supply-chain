package vn.wms.order.entity;

/**
 * Loại giao dịch công nợ với Nhà cung cấp (FR09 - WMS-08.1).
 */
public enum SupplierTransactionType {
    /** Hóa đơn nhập hàng / phát sinh nợ mới (+ nợ). */
    PURCHASE_INVOICE,

    /** Thanh toán tiền hàng cho nhà cung cấp (- nợ). */
    PAYMENT,

    /** Trả hàng nhà cung cấp (- nợ). */
    RETURN,

    /** Điều chỉnh số dư công nợ thủ công / công nợ ban đầu. */
    ADJUSTMENT
}
