package vn.wms.inventory.cache;

/**
 * Tên khóa Redis của bộ nhớ đệm tồn kho (WMS-19.1). Mọi khóa bắt đầu bằng {@code wms:stock:{companyId}:} nên hai
 * doanh nghiệp không bao giờ đọc nhầm số của nhau (docs/SAAS_SPEC.md mục 2.3, quy tắc 8). Chỉ inventory-service đọc
 * và ghi các khóa này nên hằng số nằm ở đây, không đưa vào module common.
 *
 * <pre>
 * wms:stock:{companyId}:p:{productId}:gen              STRING thế hệ của sản phẩm, tăng (INCR) sau mỗi lần tồn của
 *                                                             sản phẩm thay đổi; sống 1 ngày kể từ lần tăng cuối
 * wms:stock:{companyId}:p:{productId}:w:{warehouseId}  STRING "thế hệ|ngày|tồn thực tế|đã giữ chỗ" theo kho
 * wms:stock:{companyId}:p:{productId}:w:all            STRING như trên, cộng mọi kho của doanh nghiệp
 * wms:stock:{companyId}:gen                            STRING thế hệ của doanh nghiệp, tăng khi bất kỳ tồn nào đổi
 * wms:stock:{companyId}:kpi:{warehouseId|all}          STRING "thế hệ|ngày|tồn khả dụng|tổng tồn|số ô đang chứa hàng"
 * </pre>
 */
public final class StockCacheKeys {

    public static final String PREFIX = "wms:stock:";
    /** Hậu tố khóa của giá trị cộng mọi kho. */
    public static final String ALL_WAREHOUSES = "all";

    private StockCacheKeys() {
        // Lớp hằng số không khởi tạo
    }

    public static String productGeneration(long companyId, long productId) {
        return PREFIX + companyId + ":p:" + productId + ":gen";
    }

    public static String productTotals(long companyId, long productId, Long warehouseId) {
        return PREFIX + companyId + ":p:" + productId + ":w:" + warehouseSuffix(warehouseId);
    }

    public static String companyGeneration(long companyId) {
        return PREFIX + companyId + ":gen";
    }

    public static String warehouseFigures(long companyId, Long warehouseId) {
        return PREFIX + companyId + ":kpi:" + warehouseSuffix(warehouseId);
    }

    private static String warehouseSuffix(Long warehouseId) {
        return warehouseId == null ? ALL_WAREHOUSES : String.valueOf(warehouseId);
    }
}
