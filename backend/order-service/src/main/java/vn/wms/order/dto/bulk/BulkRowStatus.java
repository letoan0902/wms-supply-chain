package vn.wms.order.dto.bulk;

/**
 * Kết quả xử lý một dòng của API nhập hàng loạt (WMS-22.4b).
 */
public enum BulkRowStatus {
    /** Đã tạo bản ghi mới. */
    CREATED,
    /** Mã đã tồn tại trong doanh nghiệp, bỏ qua và không ghi đè. */
    SKIPPED,
    /** Dòng không hợp lệ hoặc lưu thất bại, xem {@code message}. */
    FAILED
}
