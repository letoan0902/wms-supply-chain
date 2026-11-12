package vn.wms.common.messaging.event;

/**
 * Mức độ của một cảnh báo tồn kho, theo quy ước màu của tài liệu yêu cầu: đỏ cho hàng đã hết hạn và tồn nhỏ hơn
 * hoặc bằng mức an toàn, vàng cho hàng cận hạn và tồn chạm điểm đặt hàng lại, xanh dương cho hàng tồn lâu.
 */
public enum StockAlertSeverity {

    /** Khẩn cấp (thẻ đỏ). */
    CRITICAL,

    /** Cần chú ý (thẻ vàng). */
    WARNING,

    /** Thông tin (thẻ xanh dương). */
    INFO;

    /** Thứ tự ưu tiên khi sắp xếp: số nhỏ đứng trước. */
    public int rank() {
        return ordinal();
    }

    /** Mức cao hơn trong hai mức (dùng để lấy mức của một bản tổng hợp). */
    public static StockAlertSeverity max(StockAlertSeverity a, StockAlertSeverity b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
