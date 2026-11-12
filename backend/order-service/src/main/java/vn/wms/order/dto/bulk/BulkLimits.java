package vn.wms.order.dto.bulk;

/**
 * Giới hạn của API nhập hàng loạt (WMS-22.4b).
 */
public final class BulkLimits {

    /** Số dòng tối đa của một lần nhập. */
    public static final int MAX_ROWS = 500;

    private BulkLimits() {
        // Lớp hằng số không khởi tạo
    }
}
