package vn.wms.order.dto.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Biểu đồ cột đôi so sánh mua hàng và bán hàng theo thời gian trên dashboard (WMS-16.4).
 *
 * <p>Mua hàng là đơn mua đã duyệt trở đi, bán hàng là đơn bán đã xác nhận hoặc đã hoàn tất, cùng quy ước với thẻ
 * "Doanh số xuất bán tháng" và biểu đồ mặt hàng bán chạy; mỗi đơn rơi vào kỳ theo ngày đặt hàng. Các trường số tiền
 * là {@code null} khi vai trò không được xem số tiền ({@code amountVisible = false}).
 *
 * @param dateRange          khoảng thời gian đã chuẩn hóa (TODAY, 7_DAYS, THIS_MONTH, THIS_QUARTER, ALL_TIME)
 * @param fromDate           ngày đầu (tính cả ngày đó)
 * @param toDate             ngày cuối, luôn là hôm nay
 * @param bucket             cách gom kỳ: {@code DAY} hoặc {@code MONTH}
 * @param amountVisible      vai trò hiện tại có được xem số tiền không (ADMIN, ACCOUNTANT)
 * @param currency           đơn vị tiền tệ của các trường số tiền
 * @param purchaseDefinition mô tả bằng lời những đơn mua được tính
 * @param salesDefinition    mô tả bằng lời những đơn bán được tính
 * @param quantityNote       lưu ý về cột số lượng (cộng gộp các đơn vị tính)
 * @param amountNote         lý do số tiền để trống, {@code null} khi được xem số tiền
 * @param points             các kỳ liên tiếp từ {@code fromDate} đến {@code toDate}, kỳ không phát sinh có giá trị 0
 * @param totals             tổng của cả khoảng
 * @param meta               thông tin phạm vi và thời điểm tính
 */
public record PurchaseSalesTrendResponse(
        String dateRange,
        LocalDate fromDate,
        LocalDate toDate,
        String bucket,
        boolean amountVisible,
        String currency,
        String purchaseDefinition,
        String salesDefinition,
        String quantityNote,
        String amountNote,
        List<TrendPoint> points,
        TrendFigures totals,
        ReportMeta meta
) {

    /**
     * Một kỳ của biểu đồ.
     *
     * @param periodStart ngày đầu kỳ
     * @param periodEnd   ngày cuối kỳ (kỳ tháng hiện tại kết thúc ở hôm nay)
     * @param label       nhãn trục ngang: {@code dd/MM} với kỳ ngày, {@code MM/yyyy} với kỳ tháng
     * @param figures     số liệu mua, bán của kỳ
     */
    public record TrendPoint(
            LocalDate periodStart,
            LocalDate periodEnd,
            String label,
            TrendFigures figures
    ) {}

    /**
     * Số liệu mua, bán của một kỳ hoặc của cả khoảng.
     *
     * @param purchaseOrderCount số đơn mua
     * @param purchaseQuantity   tổng số lượng trên dòng đơn mua
     * @param purchaseAmount     tổng thành tiền đơn mua (sau chiết khấu, thuế), null khi không được xem số tiền
     * @param salesOrderCount    số đơn bán
     * @param salesQuantity      tổng số lượng trên dòng đơn bán
     * @param salesAmount        tổng thành tiền đơn bán (sau chiết khấu, thuế), null khi không được xem số tiền
     */
    public record TrendFigures(
            long purchaseOrderCount,
            BigDecimal purchaseQuantity,
            BigDecimal purchaseAmount,
            long salesOrderCount,
            BigDecimal salesQuantity,
            BigDecimal salesAmount
    ) {}

    /**
     * @param companyId    doanh nghiệp của người xem
     * @param warehouseId  kho đã lọc khi phạm vi là đúng một kho, null khi gộp nhiều kho
     * @param warehouseIds các kho thực sự được tổng hợp, null là mọi kho của doanh nghiệp
     * @param calculatedAt thời điểm tính
     */
    public record ReportMeta(
            Long companyId,
            Long warehouseId,
            List<Long> warehouseIds,
            Instant calculatedAt
    ) {}
}
