package vn.wms.order.client;

/**
 * inventory-service không giữ đủ chỗ cho đơn bán (409 từ {@code POST /internal/inventory/reservations}, WMS-20.4).
 * Không phải {@code ApiException}: nơi gọi phải tự quyết định theo quy tắc nghiệp vụ (chuyển BACKORDER hoặc từ chối
 * xác nhận 400), nên lớp này luôn được bắt trong {@code SalesOrderService}.
 */
public class StockShortageException extends RuntimeException {

    /** Danh sách thiếu hàng do inventory-service trả trong {@code data} (productId, skuCode, các số lượng). */
    private final transient Object shortages;

    public StockShortageException(String message, Object shortages) {
        super(message);
        this.shortages = shortages;
    }

    public Object getShortages() {
        return shortages;
    }
}
