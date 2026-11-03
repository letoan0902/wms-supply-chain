package vn.wms.order.entity;

/**
 * Phân loại khách hàng (FR14 - WMS-08.2).
 */
public enum CustomerType {
    /** Khách hàng sỉ / Đại lý / Doanh nghiệp B2B (thường có hạn mức công nợ). */
    WHOLESALE,

    /** Khách hàng lẻ / Cá nhân B2C (thanh toán ngay COD hoặc chuyển khoản). */
    RETAIL
}
