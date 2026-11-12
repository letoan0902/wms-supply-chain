package vn.wms.inventory.dto.report;

import java.time.LocalDate;

/**
 * Điều kiện lập báo cáo Xuất - Nhập - Tồn (WMS-17.1), dùng chung cho API xem và bộ xuất Excel.
 *
 * @param warehouseId kho cần xem; null là mọi kho trong phạm vi của người dùng
 * @param fromDate    ngày đầu kỳ (bắt buộc)
 * @param toDate      ngày cuối kỳ (bắt buộc, không quá {@code StockReportService.MAX_RANGE_DAYS} ngày)
 * @param categoryId  lọc theo danh mục sản phẩm (tra product-service)
 * @param productId   lọc một sản phẩm
 * @param keyword     lọc theo mã SKU hoặc tên sản phẩm (tra product-service)
 * @param groupBy     {@code PRODUCT_WAREHOUSE} (mặc định) hoặc {@code PRODUCT}
 * @param sort        productId, warehouseId, openingQuantity, quantityIn, quantityOut, closingQuantity; mặc định productId
 * @param direction   asc (mặc định) hoặc desc
 * @param page        trang, bắt đầu từ 0
 * @param size        số dòng mỗi trang
 */
public record InOutBalanceQuery(
        Long warehouseId,
        LocalDate fromDate,
        LocalDate toDate,
        Long categoryId,
        Long productId,
        String keyword,
        String groupBy,
        String sort,
        String direction,
        int page,
        int size
) {
}
