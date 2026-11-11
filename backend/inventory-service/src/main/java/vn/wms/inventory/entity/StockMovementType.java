package vn.wms.inventory.entity;

/**
 * Loại biến động tồn kho ghi trong sổ kho {@code stock_movements} (WMS-17.1).
 * Mỗi loại thuộc đúng một chiều: nhập (cộng tồn) hoặc xuất (trừ tồn).
 */
public enum StockMovementType {

    /** Số dư mở sổ: tồn có sẵn tại thời điểm bắt đầu ghi sổ kho (migration V13), không rõ chứng từ gốc. */
    OPENING_BALANCE(true, "Số dư mở sổ kho"),
    /** Nhập kho từ phiếu nhập GRN (sự kiện order.received). */
    RECEIPT(true, "Nhập kho theo phiếu nhập"),
    /** Nhận hàng điều chuyển tại kho đích. */
    TRANSFER_IN(true, "Nhận hàng điều chuyển"),
    /** Điều chỉnh tăng sau kiểm kê (dành cho chức năng kiểm kê, điều chỉnh kho). */
    ADJUSTMENT_IN(true, "Điều chỉnh tăng"),
    /** Xuất bán theo đơn bán (sự kiện order.dispatched hoặc API xuất kho). */
    SALES_DISPATCH(false, "Xuất bán theo đơn bán"),
    /** Xuất điều chuyển khỏi kho nguồn. */
    TRANSFER_OUT(false, "Xuất điều chuyển"),
    /** Điều chỉnh giảm sau kiểm kê (dành cho chức năng kiểm kê, điều chỉnh kho). */
    ADJUSTMENT_OUT(false, "Điều chỉnh giảm"),
    /** Xuất kho khác qua API trừ tồn với lý do không thuộc các loại trên. */
    ISSUE_OTHER(false, "Xuất kho khác");

    private final boolean inbound;
    private final String label;

    StockMovementType(boolean inbound, String label) {
        this.inbound = inbound;
        this.label = label;
    }

    /** {@code true} nếu biến động cộng tồn (nhập), {@code false} nếu trừ tồn (xuất). */
    public boolean isInbound() {
        return inbound;
    }

    /** Tên tiếng Việt để hiển thị trên báo cáo và file Excel. */
    public String getLabel() {
        return label;
    }
}
