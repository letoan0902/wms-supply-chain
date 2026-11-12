package vn.wms.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.constant.Roles;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.dto.report.PurchaseSalesTrendResponse;
import vn.wms.order.dto.report.PurchaseSalesTrendResponse.ReportMeta;
import vn.wms.order.dto.report.PurchaseSalesTrendResponse.TrendFigures;
import vn.wms.order.dto.report.PurchaseSalesTrendResponse.TrendPoint;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.repository.PurchaseOrderRepository;
import vn.wms.order.repository.SalesOrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Biểu đồ mua hàng và bán hàng theo thời gian trên dashboard (WMS-16.4).
 *
 * <p>Quy ước số liệu, chọn để khớp các thẻ KPI bên cạnh:
 * <ul>
 *   <li>Bán hàng: đơn bán trạng thái CONFIRMED, COMPLETED theo ngày đặt hàng, giống thẻ "Doanh số xuất bán tháng"
 *       ({@link OrderKpiInternalService}) và biểu đồ mặt hàng bán chạy ({@link OrderReportService}). Với khoảng "Tháng
 *       này", tổng tiền bán bằng đúng số trên thẻ doanh số khi cùng phạm vi kho.</li>
 *   <li>Mua hàng: đơn mua đã duyệt trở đi (APPROVED, CONFIRMED, PARTIALLY_RECEIVED, COMPLETED) theo ngày đặt hàng;
 *       đơn nháp, chờ duyệt, bị từ chối, đã hủy không tính. Thẻ "Đơn mua chờ tiếp nhận" chỉ đếm phần APPROVED.</li>
 *   <li>Số tiền là thành tiền của đơn (sau chiết khấu, thuế), chỉ ADMIN và ACCOUNTANT được xem như thẻ KPI tài chính;
 *       vai trò khác nhận {@code null}.</li>
 * </ul>
 * Phạm vi kho giống biểu đồ mặt hàng bán chạy: ADMIN, ACCOUNTANT mọi kho; vai trò khác chỉ các kho được gán.
 */
@Service
@RequiredArgsConstructor
public class PurchaseSalesTrendService {

    public static final String BUCKET_DAY = "DAY";
    public static final String BUCKET_MONTH = "MONTH";

    /** Đơn bán được tính là đã bán: cùng quy ước với thẻ doanh số tháng và biểu đồ mặt hàng bán chạy. */
    static final List<OrderStatus> SALES_STATUSES = List.of(OrderStatus.CONFIRMED, OrderStatus.COMPLETED);

    /** Đơn mua đã được duyệt (đã cam kết mua), kể cả đã nhập một phần hoặc nhập đủ. */
    static final List<OrderStatus> PURCHASE_STATUSES = List.of(
            OrderStatus.APPROVED, OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_RECEIVED, OrderStatus.COMPLETED);

    static final String PURCHASE_DEFINITION =
            "Đơn mua đã duyệt, đang nhập hoặc đã nhập đủ, tính theo ngày đặt hàng; không gồm đơn nháp, chờ duyệt, bị từ chối, đã hủy";
    static final String SALES_DEFINITION =
            "Đơn bán đã xác nhận hoặc đã hoàn tất, tính theo ngày đặt hàng (cùng cách tính với thẻ doanh số xuất bán tháng)";
    static final String QUANTITY_NOTE =
            "Số lượng là tổng số lượng trên các dòng đơn, cộng gộp các đơn vị tính khác nhau";
    static final String AMOUNT_HIDDEN_NOTE = "Vai trò của bạn không được xem số tiền mua bán";

    private static final List<Long> NO_WAREHOUSE_FILTER = List.of(-1L);
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MM/yyyy");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;

    /**
     * @param warehouseId kho (tùy chọn); bỏ trống thì mọi kho trong phạm vi của người xem
     * @param dateRange   TODAY, 7_DAYS, THIS_MONTH, THIS_QUARTER, ALL_TIME (bỏ trống là 7_DAYS); giá trị khác trả 400
     */
    @Transactional(readOnly = true)
    public PurchaseSalesTrendResponse getTrend(Long warehouseId, String dateRange) {
        CurrentUser user = UserContext.requireCurrentUser();
        Long companyId = TenantContext.requireCompanyId();
        String range = OrderReportService.resolveRange(dateRange);

        Set<Long> scope = UserContext.resolveAllowedWarehouses(user, warehouseId);
        boolean allWarehouses = scope.isEmpty();
        List<Long> warehouseIds = allWarehouses ? NO_WAREHOUSE_FILTER : scope.stream().sorted().toList();
        boolean amountVisible = user.hasRole(Roles.ADMIN) || user.hasRole(Roles.ACCOUNTANT);

        LocalDate today = LocalDate.now();
        LocalDate fromDate = "ALL_TIME".equals(range)
                ? earliestOrderDate(allWarehouses, warehouseIds, today)
                : OrderReportService.rangeStart(range, today);
        // Ngày và tuần hiện rõ theo ngày; quý (3 tháng) và toàn bộ thời gian đọc dễ hơn khi gom theo tháng
        String bucket = "THIS_QUARTER".equals(range) || "ALL_TIME".equals(range) ? BUCKET_MONTH : BUCKET_DAY;

        Map<LocalDate, Accumulator> byPeriod = new TreeMap<>();
        for (Object[] row : purchaseOrderRepository.sumByOrderDate(
                fromDate, today, PURCHASE_STATUSES, allWarehouses, warehouseIds)) {
            Accumulator acc = byPeriod.computeIfAbsent(periodStart((LocalDate) row[0], bucket), k -> new Accumulator());
            acc.purchaseCount += ((Number) row[1]).longValue();
            acc.purchaseAmount = acc.purchaseAmount.add(decimal(row[2]));
        }
        for (Object[] row : purchaseOrderRepository.sumItemQuantityByOrderDate(
                fromDate, today, PURCHASE_STATUSES, allWarehouses, warehouseIds)) {
            Accumulator acc = byPeriod.computeIfAbsent(periodStart((LocalDate) row[0], bucket), k -> new Accumulator());
            acc.purchaseQuantity = acc.purchaseQuantity.add(decimal(row[1]));
        }
        for (Object[] row : salesOrderRepository.sumByOrderDate(
                fromDate, today, SALES_STATUSES, allWarehouses, warehouseIds)) {
            Accumulator acc = byPeriod.computeIfAbsent(periodStart((LocalDate) row[0], bucket), k -> new Accumulator());
            acc.salesCount += ((Number) row[1]).longValue();
            acc.salesAmount = acc.salesAmount.add(decimal(row[2]));
        }
        for (Object[] row : salesOrderRepository.sumItemQuantityByOrderDate(
                fromDate, today, SALES_STATUSES, allWarehouses, warehouseIds)) {
            Accumulator acc = byPeriod.computeIfAbsent(periodStart((LocalDate) row[0], bucket), k -> new Accumulator());
            acc.salesQuantity = acc.salesQuantity.add(decimal(row[1]));
        }

        // Mọi kỳ liên tiếp từ ngày đầu tới hôm nay, kỳ không phát sinh bằng 0 để trục thời gian không bị hở
        List<TrendPoint> points = new ArrayList<>();
        Accumulator total = new Accumulator();
        for (LocalDate cursor = periodStart(fromDate, bucket); !cursor.isAfter(today); cursor = nextPeriod(cursor, bucket)) {
            Accumulator acc = byPeriod.getOrDefault(cursor, new Accumulator());
            total.add(acc);
            LocalDate start = cursor.isBefore(fromDate) ? fromDate : cursor;
            LocalDate end = BUCKET_DAY.equals(bucket) ? cursor : min(nextPeriod(cursor, bucket).minusDays(1), today);
            String label = BUCKET_DAY.equals(bucket) ? cursor.format(DAY_LABEL) : cursor.format(MONTH_LABEL);
            points.add(new TrendPoint(start, end, label, acc.toFigures(amountVisible)));
        }

        return new PurchaseSalesTrendResponse(
                range,
                fromDate,
                today,
                bucket,
                amountVisible,
                "VNĐ",
                PURCHASE_DEFINITION,
                SALES_DEFINITION,
                QUANTITY_NOTE,
                amountVisible ? null : AMOUNT_HIDDEN_NOTE,
                points,
                total.toFigures(amountVisible),
                new ReportMeta(companyId,
                        !allWarehouses && warehouseIds.size() == 1 ? warehouseIds.get(0) : null,
                        allWarehouses ? null : warehouseIds,
                        Instant.now())
        );
    }

    /** Ngày đặt hàng sớm nhất trong phạm vi (đơn mua hoặc đơn bán được tính); không có đơn nào thì là hôm nay. */
    private LocalDate earliestOrderDate(boolean allWarehouses, List<Long> warehouseIds, LocalDate today) {
        LocalDate earliest = today;
        LocalDate purchase = purchaseOrderRepository.findEarliestOrderDate(PURCHASE_STATUSES, allWarehouses, warehouseIds);
        LocalDate sales = salesOrderRepository.findEarliestOrderDate(SALES_STATUSES, allWarehouses, warehouseIds);
        if (purchase != null && purchase.isBefore(earliest)) {
            earliest = purchase;
        }
        if (sales != null && sales.isBefore(earliest)) {
            earliest = sales;
        }
        return earliest;
    }

    private static LocalDate periodStart(LocalDate date, String bucket) {
        return BUCKET_DAY.equals(bucket) ? date : date.withDayOfMonth(1);
    }

    private static LocalDate nextPeriod(LocalDate periodStart, String bucket) {
        return BUCKET_DAY.equals(bucket) ? periodStart.plusDays(1) : periodStart.plusMonths(1);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }

    /** Cộng dồn số liệu của một kỳ. */
    private static final class Accumulator {
        long purchaseCount;
        BigDecimal purchaseQuantity = BigDecimal.ZERO;
        BigDecimal purchaseAmount = BigDecimal.ZERO;
        long salesCount;
        BigDecimal salesQuantity = BigDecimal.ZERO;
        BigDecimal salesAmount = BigDecimal.ZERO;

        void add(Accumulator other) {
            purchaseCount += other.purchaseCount;
            purchaseQuantity = purchaseQuantity.add(other.purchaseQuantity);
            purchaseAmount = purchaseAmount.add(other.purchaseAmount);
            salesCount += other.salesCount;
            salesQuantity = salesQuantity.add(other.salesQuantity);
            salesAmount = salesAmount.add(other.salesAmount);
        }

        TrendFigures toFigures(boolean amountVisible) {
            return new TrendFigures(
                    purchaseCount, purchaseQuantity, amountVisible ? purchaseAmount : null,
                    salesCount, salesQuantity, amountVisible ? salesAmount : null);
        }
    }
}
