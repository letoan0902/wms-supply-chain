package vn.wms.inventory.service.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.report.InOutBalanceQuery;
import vn.wms.inventory.dto.report.InOutBalanceReportResponse;
import vn.wms.inventory.dto.report.InOutBalanceRow;
import vn.wms.inventory.dto.report.InOutBalanceTotals;
import vn.wms.inventory.dto.report.StockCardLine;
import vn.wms.inventory.dto.report.StockCardResponse;
import vn.wms.inventory.entity.StockMovement;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.StockMovementRepository;
import vn.wms.inventory.repository.StockReportQueryRepository;
import vn.wms.inventory.repository.StockReportQueryRepository.AggregateRow;
import vn.wms.inventory.repository.StockReportQueryRepository.Criteria;
import vn.wms.inventory.repository.StockReportQueryRepository.SortField;
import vn.wms.inventory.repository.StockReportQueryRepository.Totals;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.service.report.ReportLookups.ProductDisplay;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Báo cáo Xuất - Nhập - Tồn và thẻ kho chi tiết (WMS-17.1), tính hoàn toàn từ sổ kho {@code stock_movements}.
 *
 * <p>Công thức: tồn đầu kỳ = tổng (nhập - xuất) của các dòng sổ trước ngày đầu kỳ; nhập, xuất trong kỳ = tổng các
 * dòng sổ từ đầu ngày đầu kỳ đến hết ngày cuối kỳ; tồn cuối kỳ = tổng (nhập - xuất) mọi dòng sổ đến hết ngày cuối kỳ.
 * Vì cùng một tập dòng sổ nên tồn đầu + nhập - xuất = tồn cuối luôn đúng tuyệt đối. Ngày được cắt theo giờ Việt Nam.
 *
 * <p>Các phương thức {@code ...ForExport} trả toàn bộ dòng (tối đa {@link #EXPORT_MAX_ROWS}) để bộ xuất Excel
 * (WMS-17.3) dùng lại đúng dữ liệu và quyền của màn hình, không phải tự truy vấn. Quá giới hạn thì trả {@link #EXPORT_MAX_ROWS}
 * dòng đầu, {@code totalElements} của trang vẫn là tổng số dòng thật để bộ xuất ghi chú file bị cắt.
 */
@Service
public class StockReportService {

    /** Múi giờ cắt ngày của báo cáo. */
    public static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Khoảng thời gian tối đa của một báo cáo, tính cả hai đầu. */
    public static final int MAX_RANGE_DAYS = 366;
    /** Số dòng tối đa mỗi trang qua API. */
    public static final int MAX_PAGE_SIZE = 200;
    /** Số dòng tối đa của một lần xuất toàn bộ (Excel). */
    public static final int EXPORT_MAX_ROWS = 20000;

    public static final String GROUP_BY_PRODUCT_WAREHOUSE = "PRODUCT_WAREHOUSE";
    public static final String GROUP_BY_PRODUCT = "PRODUCT";

    static final String VALUATION_NOTE = "Tồn kho chưa có giá vốn (đơn giá nhập thực tế để tính theo FIFO hoặc bình quân "
            + "gia quyền) nên chưa tính được giá trị; các trường giá trị tiền để trống thay vì nhân với một đơn giá giả định.";

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss 'ngày' dd/MM/yyyy", Locale.forLanguageTag("vi"));
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Map<String, SortField> SORT_FIELDS = Map.of(
            "productid", SortField.PRODUCT_ID,
            "warehouseid", SortField.WAREHOUSE_ID,
            "openingquantity", SortField.OPENING_QUANTITY,
            "quantityin", SortField.QUANTITY_IN,
            "quantityout", SortField.QUANTITY_OUT,
            "closingquantity", SortField.CLOSING_QUANTITY);

    private final ReportAccess reportAccess;
    private final StockReportQueryRepository reportQueryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductCatalogLookup productCatalogLookup;
    private final ReportLookups reportLookups;

    public StockReportService(ReportAccess reportAccess,
                              StockReportQueryRepository reportQueryRepository,
                              StockMovementRepository stockMovementRepository,
                              WarehouseRepository warehouseRepository,
                              ProductCatalogLookup productCatalogLookup,
                              ReportLookups reportLookups) {
        this.reportAccess = reportAccess;
        this.reportQueryRepository = reportQueryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.warehouseRepository = warehouseRepository;
        this.productCatalogLookup = productCatalogLookup;
        this.reportLookups = reportLookups;
    }

    // ===================== Báo cáo Xuất - Nhập - Tồn =====================

    /** Một trang báo cáo Xuất - Nhập - Tồn kèm dòng tổng cộng. */
    @Transactional(readOnly = true)
    public InOutBalanceReportResponse getInOutBalance(InOutBalanceQuery query) {
        return buildInOutBalance(query, false);
    }

    /** Toàn bộ dòng báo cáo Xuất - Nhập - Tồn (trang duy nhất), dùng cho xuất Excel. */
    @Transactional(readOnly = true)
    public InOutBalanceReportResponse getInOutBalanceForExport(InOutBalanceQuery query) {
        return buildInOutBalance(query, true);
    }

    private InOutBalanceReportResponse buildInOutBalance(InOutBalanceQuery query, boolean exportAll) {
        validateRange(query.fromDate(), query.toDate());
        if (!exportAll) {
            validatePaging(query.page(), query.size());
        }
        boolean groupByWarehouse = parseGroupBy(query.groupBy());
        SortField sortField = parseSort(query.sort(), groupByWarehouse);
        boolean ascending = parseDirection(query.direction());

        ReportAccess.Scope scope = reportAccess.resolve(query.warehouseId());
        Instant fromTs = startOfDay(query.fromDate());
        Instant toTs = startOfDay(query.toDate().plusDays(1));
        Coverage coverage = coverage(fromTs, toTs);
        Set<Long> productIds = resolveProductFilter(query.productId(), query.keyword(), query.categoryId());

        Criteria criteria = new Criteria(scope.companyId(), fromTs, toTs, scope.warehouseIds(), productIds,
                groupByWarehouse, sortField, ascending);

        List<AggregateRow> aggregates;
        long totalElements;
        Totals totals;
        if (productIds != null && productIds.isEmpty()) {
            // Bộ lọc danh mục, từ khóa không khớp sản phẩm nào: báo cáo rỗng, không chạy truy vấn với IN ()
            aggregates = List.of();
            totalElements = 0;
            totals = new Totals(BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3),
                    BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3));
        } else {
            totalElements = reportQueryRepository.countRows(criteria);
            // Xuất Excel: quá giới hạn thì lấy EXPORT_MAX_ROWS dòng đầu; rows.totalElements giữ tổng thật để bộ xuất ghi chú
            aggregates = exportAll
                    ? reportQueryRepository.findRows(criteria, 0, EXPORT_MAX_ROWS)
                    : reportQueryRepository.findRows(criteria, query.page() * query.size(), query.size());
            totals = reportQueryRepository.totals(criteria);
        }

        Map<Long, ProductDisplay> products = reportLookups.productDisplay(aggregates.stream().map(AggregateRow::productId).toList());
        Map<Long, Warehouse> warehouses = reportLookups.warehouses(aggregates.stream().map(AggregateRow::warehouseId).toList());

        List<InOutBalanceRow> rows = new ArrayList<>(aggregates.size());
        for (AggregateRow a : aggregates) {
            ProductDisplay p = products.getOrDefault(a.productId(), ProductDisplay.EMPTY);
            Warehouse w = a.warehouseId() != null ? warehouses.get(a.warehouseId()) : null;
            rows.add(new InOutBalanceRow(
                    a.productId(), p.skuCode(), p.productName(), p.uom(),
                    a.warehouseId(), w != null ? w.getCode() : null, w != null ? w.getName() : null,
                    coverage.openingKnown() ? a.openingQuantity() : null,
                    coverage.openingKnown() ? a.quantityIn() : null,
                    coverage.openingKnown() ? a.quantityOut() : null,
                    coverage.closingKnown() ? a.closingQuantity() : null,
                    null, null, null, null));
        }

        int page = exportAll ? 0 : query.page();
        int size = exportAll ? Math.max(rows.size(), 1) : query.size();
        InOutBalanceTotals totalsDto = new InOutBalanceTotals(
                coverage.openingKnown() ? totals.openingQuantity() : null,
                coverage.openingKnown() ? totals.quantityIn() : null,
                coverage.openingKnown() ? totals.quantityOut() : null,
                coverage.closingKnown() ? totals.closingQuantity() : null,
                null, null, null, null);

        return new InOutBalanceReportResponse(
                query.fromDate(),
                query.toDate(),
                REPORT_ZONE.getId(),
                query.warehouseId(),
                scope.warehouseIds() != null ? scope.warehouseIds().stream().sorted().toList() : null,
                groupByWarehouse ? GROUP_BY_PRODUCT_WAREHOUSE : GROUP_BY_PRODUCT,
                PageResponse.of(rows, page, size, totalElements),
                totalsDto,
                coverage.ledgerStartedAt(),
                coverage.openingKnown(),
                coverage.note(),
                null,
                VALUATION_NOTE,
                Instant.now());
    }

    // ===================== Thẻ kho chi tiết =====================

    /** Thẻ kho của một sản phẩm tại một kho: tồn đầu kỳ, từng biến động có số dư lũy kế, tồn cuối kỳ. */
    @Transactional(readOnly = true)
    public StockCardResponse getStockCard(Long productId, Long warehouseId, LocalDate fromDate, LocalDate toDate,
                                          int page, int size) {
        validatePaging(page, size);
        return buildStockCard(productId, warehouseId, fromDate, toDate, PageRequest.of(page, size, LINE_ORDER), false);
    }

    /** Toàn bộ dòng thẻ kho của kỳ (trang duy nhất), dùng cho xuất Excel. */
    @Transactional(readOnly = true)
    public StockCardResponse getStockCardForExport(Long productId, Long warehouseId, LocalDate fromDate,
                                                   LocalDate toDate) {
        return buildStockCard(productId, warehouseId, fromDate, toDate,
                PageRequest.of(0, EXPORT_MAX_ROWS + 1, LINE_ORDER), true);
    }

    private static final Sort LINE_ORDER = Sort.by(Sort.Order.asc("occurredAt"), Sort.Order.asc("id"));

    private StockCardResponse buildStockCard(Long productId, Long warehouseId, LocalDate fromDate, LocalDate toDate,
                                             PageRequest pageable, boolean exportAll) {
        if (productId == null) {
            throw ApiException.badRequest("Thiếu tham số bắt buộc: productId");
        }
        if (warehouseId == null) {
            throw ApiException.badRequest("Thiếu tham số bắt buộc: warehouseId");
        }
        validateRange(fromDate, toDate);
        reportAccess.resolve(warehouseId);

        Instant fromTs = startOfDay(fromDate);
        Instant toTs = startOfDay(toDate.plusDays(1));
        Coverage coverage = coverage(fromTs, toTs);

        BigDecimal opening = scale(stockMovementRepository.sumBalanceBefore(warehouseId, productId, fromTs));
        Object[] inOut = stockMovementRepository.sumInOutBetween(warehouseId, productId, fromTs, toTs).get(0);
        BigDecimal totalIn = scale((BigDecimal) inOut[0]);
        BigDecimal totalOut = scale((BigDecimal) inOut[1]);
        BigDecimal closing = opening.add(totalIn).subtract(totalOut);

        Page<StockMovement> linePage = stockMovementRepository.findLines(warehouseId, productId, fromTs, toTs, pageable);
        // Xuất Excel: quá giới hạn thì chỉ giữ EXPORT_MAX_ROWS dòng đầu; lines.totalElements giữ tổng thật
        List<StockMovement> pageContent = exportAll && linePage.getContent().size() > EXPORT_MAX_ROWS
                ? linePage.getContent().subList(0, EXPORT_MAX_ROWS) : linePage.getContent();

        BigDecimal balance = opening;
        if (!linePage.isFirst() && linePage.hasContent()) {
            StockMovement first = linePage.getContent().get(0);
            balance = scale(stockMovementRepository.sumBalanceBeforeLine(
                    warehouseId, productId, first.getOccurredAt(), first.getId()));
        }
        List<StockCardLine> lines = new ArrayList<>(pageContent.size());
        for (StockMovement m : pageContent) {
            balance = balance.add(m.getQuantityIn()).subtract(m.getQuantityOut());
            lines.add(new StockCardLine(
                    m.getId(), m.getOccurredAt(), m.getMovementType().name(), m.getMovementType().getLabel(),
                    m.getDocumentType(), m.getDocumentId(), m.getDocumentNumber(),
                    m.getReferenceType(), m.getReferenceId(), m.getReferenceNumber(),
                    m.getPartnerId(), m.getCounterpartWarehouseId(),
                    m.getLotNo(), m.getExpDate(), m.getBinLocationId(), m.getBinLocationCode(),
                    scale(m.getQuantityIn()), scale(m.getQuantityOut()), balance,
                    m.getPerformedBy(), m.getReason()));
        }

        ProductDisplay product = reportLookups.productDisplay(List.of(productId)).getOrDefault(productId, ProductDisplay.EMPTY);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho ID: " + warehouseId));

        PageResponse<StockCardLine> linesPage = exportAll
                ? PageResponse.of(lines, 0, Math.max(lines.size(), 1), linePage.getTotalElements())
                : PageResponse.of(lines, linePage.getNumber(), linePage.getSize(), linePage.getTotalElements());

        return new StockCardResponse(
                productId, product.skuCode(), product.productName(), product.uom(),
                warehouseId, warehouse.getCode(), warehouse.getName(),
                fromDate, toDate, REPORT_ZONE.getId(),
                coverage.openingKnown() ? opening : null,
                coverage.openingKnown() ? totalIn : null,
                coverage.openingKnown() ? totalOut : null,
                coverage.closingKnown() ? closing : null,
                linesPage,
                coverage.ledgerStartedAt(),
                coverage.note(),
                Instant.now());
    }

    // ===================== Dùng chung =====================

    /**
     * Phạm vi số liệu sổ kho xác định được cho kỳ [from, to).
     *
     * @param openingKnown tồn đầu kỳ, nhập, xuất trong kỳ xác định được
     * @param closingKnown tồn cuối kỳ xác định được
     */
    record Coverage(Instant ledgerStartedAt, boolean openingKnown, boolean closingKnown, String note) {
    }

    Coverage coverage(Instant fromTs, Instant toTs) {
        Instant start = reportLookups.ledgerStartedAt();
        if (start == null) {
            return new Coverage(null, true, true, null);
        }
        boolean openingKnown = fromTs.isAfter(start);
        boolean closingKnown = toTs.isAfter(start);
        String startText = DISPLAY_TIME.format(start.atZone(REPORT_ZONE));
        String note = null;
        if (!closingKnown) {
            note = "Sổ kho của doanh nghiệp bắt đầu ghi từ " + startText + ". Kỳ báo cáo kết thúc trước thời điểm này "
                    + "nên hệ thống không có số liệu tồn, nhập, xuất của kỳ; các trường số lượng để trống.";
        } else if (!openingKnown) {
            LocalDate firstFullDay = start.atZone(REPORT_ZONE).toLocalDate().plusDays(1);
            note = "Sổ kho của doanh nghiệp bắt đầu ghi từ " + startText + ". Biến động trước thời điểm này không được "
                    + "lưu nên tồn đầu kỳ, nhập và xuất trong kỳ không xác định và để trống; tồn cuối kỳ vẫn chính xác "
                    + "vì đã gồm số dư mở sổ. Chọn ngày đầu kỳ từ " + DISPLAY_DATE.format(firstFullDay)
                    + " trở đi để có đủ số liệu.";
        }
        return new Coverage(start, openingKnown, closingKnown, note);
    }

    static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(REPORT_ZONE).toInstant();
    }

    static void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) {
            throw ApiException.badRequest("Thiếu tham số bắt buộc: fromDate");
        }
        if (toDate == null) {
            throw ApiException.badRequest("Thiếu tham số bắt buộc: toDate");
        }
        if (toDate.isBefore(fromDate)) {
            throw ApiException.badRequest("Ngày cuối kỳ (toDate) phải bằng hoặc sau ngày đầu kỳ (fromDate)");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) + 1 > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("Khoảng thời gian báo cáo tối đa " + MAX_RANGE_DAYS + " ngày");
        }
    }

    private static void validatePaging(int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("Số trang (page) không được âm");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("Số dòng mỗi trang (size) phải từ 1 đến " + MAX_PAGE_SIZE);
        }
    }

    private static boolean parseGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank() || GROUP_BY_PRODUCT_WAREHOUSE.equalsIgnoreCase(groupBy.trim())) {
            return true;
        }
        if (GROUP_BY_PRODUCT.equalsIgnoreCase(groupBy.trim())) {
            return false;
        }
        throw ApiException.badRequest("groupBy chỉ nhận PRODUCT_WAREHOUSE hoặc PRODUCT");
    }

    private static SortField parseSort(String sort, boolean groupByWarehouse) {
        if (sort == null || sort.isBlank()) {
            return SortField.PRODUCT_ID;
        }
        SortField field = SORT_FIELDS.get(sort.trim().toLowerCase(Locale.ROOT));
        if (field == null) {
            throw ApiException.badRequest("sort chỉ nhận productId, warehouseId, openingQuantity, quantityIn, "
                    + "quantityOut, closingQuantity");
        }
        if (field == SortField.WAREHOUSE_ID && !groupByWarehouse) {
            throw ApiException.badRequest("Không sắp xếp theo kho khi đã gộp mọi kho (groupBy = PRODUCT)");
        }
        return field;
    }

    private static boolean parseDirection(String direction) {
        if (direction == null || direction.isBlank() || "asc".equalsIgnoreCase(direction.trim())) {
            return true;
        }
        if ("desc".equalsIgnoreCase(direction.trim())) {
            return false;
        }
        throw ApiException.badRequest("direction chỉ nhận asc hoặc desc");
    }

    /** Tập sản phẩm cần lọc; {@code null} là không lọc, tập rỗng là không sản phẩm nào khớp. */
    private Set<Long> resolveProductFilter(Long productId, String keyword, Long categoryId) {
        String kw = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        Set<Long> ids = null;
        if (kw != null || categoryId != null) {
            ids = new LinkedHashSet<>(productCatalogLookup.findProductIds(kw, categoryId));
        }
        if (productId != null) {
            if (ids == null) {
                ids = new LinkedHashSet<>(Set.of(productId));
            } else {
                ids.retainAll(Set.of(productId));
            }
        }
        return ids;
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(3, java.math.RoundingMode.UNNECESSARY);
    }
}
