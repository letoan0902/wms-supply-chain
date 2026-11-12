package vn.wms.inventory.service.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.api.PageResponse;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.client.ProductClient;
import vn.wms.inventory.client.ProductInfo;
import vn.wms.inventory.dto.dashboard.RecentTransactionsResponse;
import vn.wms.inventory.dto.dashboard.StockByCategoryResponse;
import vn.wms.inventory.dto.dashboard.StockByCategoryResponse.Slice;
import vn.wms.inventory.entity.StockAdjustment;
import vn.wms.inventory.entity.StockMovementType;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.InventoryDashboardQueryRepository;
import vn.wms.inventory.repository.StockAdjustmentRepository;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.service.report.ReportAccess;
import vn.wms.inventory.service.report.StockReportService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Số liệu kho cho dashboard (WMS-16.4): tỷ trọng tồn kho theo nhóm hàng và các giao dịch kho gần nhất.
 *
 * <p>Phạm vi kho theo {@link UserContext#resolveAllowedWarehouses}: ADMIN, ACCOUNTANT mọi kho của doanh nghiệp; vai
 * trò khác chỉ các kho được gán (chưa được gán kho nào thì 403, xin kho ngoài phạm vi thì 403). Kho của doanh nghiệp
 * khác hoặc không tồn tại: 404. Mọi truy vấn là JPQL nên chỉ đọc dữ liệu của doanh nghiệp hiện tại.
 */
@Service
public class InventoryDashboardService {

    private static final Logger log = LoggerFactory.getLogger(InventoryDashboardService.class);

    /** Các khoảng thời gian được chấp nhận, giống biểu đồ mặt hàng bán chạy của order-service. */
    public static final List<String> DATE_RANGES = List.of("TODAY", "7_DAYS", "THIS_MONTH", "THIS_QUARTER", "ALL_TIME");

    /** Số lát cắt tối đa của biểu đồ donut; nhiều nhóm hơn thì gom phần nhỏ vào "Các nhóm khác". */
    public static final int MAX_SLICES = 8;

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;

    public static final String BASIS_QUANTITY = "QUANTITY";
    public static final String VALUATION_NOT_AVAILABLE = "NOT_AVAILABLE";
    static final String UNCATEGORIZED_NAME = "Chưa phân nhóm hàng";
    static final String OTHERS_NAME = "Các nhóm khác";
    static final String BASIS_NOTE = "Tồn kho chưa có giá vốn nên chưa tính được giá trị (giống thẻ Giá trị tồn kho); "
            + "tỷ trọng tính theo số lượng tồn thực tế tại thời điểm xem, gồm cả hàng đã giữ chỗ, lô bị khóa hoặc hết hạn, "
            + "cộng gộp các đơn vị tính";

    /** Kích thước trang tối đa product-service cho phép và số trang tối đa đọc cho một lần tính. */
    static final int CATALOG_PAGE_SIZE = 100;
    static final int CATALOG_MAX_PAGES = 100;

    private static final List<Long> NO_WAREHOUSE_FILTER = List.of(-1L);

    private final InventoryDashboardQueryRepository queryRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final ProductClient productClient;
    private final ReportAccess reportAccess;

    public InventoryDashboardService(InventoryDashboardQueryRepository queryRepository,
                                     WarehouseRepository warehouseRepository,
                                     StockAdjustmentRepository stockAdjustmentRepository,
                                     ProductClient productClient,
                                     ReportAccess reportAccess) {
        this.queryRepository = queryRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.productClient = productClient;
        this.reportAccess = reportAccess;
    }

    /**
     * Tỷ trọng tồn kho theo nhóm hàng. Sáu vai trò tài khoản đều xem được (như thẻ tồn kho khả dụng); chỉ có số lượng.
     *
     * @param warehouseId kho (tùy chọn); bỏ trống thì mọi kho trong phạm vi
     * @param dateRange   khoảng thời gian của dashboard, chỉ kiểm tra hợp lệ và trả lại: tồn luôn là tồn hiện tại
     */
    @Transactional(readOnly = true)
    public StockByCategoryResponse getStockByCategory(Long warehouseId, String dateRange) {
        CurrentUser user = UserContext.requireCurrentUser();
        TenantContext.requireCompanyId();
        String range = resolveRange(dateRange);
        Set<Long> scope = UserContext.resolveAllowedWarehouses(user, warehouseId);
        requireWarehouseExists(warehouseId);
        boolean allWarehouses = scope.isEmpty();
        List<Long> warehouseIds = allWarehouses ? NO_WAREHOUSE_FILTER : scope.stream().sorted().toList();
        boolean valueVisible = user.hasRole(Roles.ADMIN) || user.hasRole(Roles.ACCOUNTANT);

        Map<Long, BigDecimal> stockByProduct = new HashMap<>();
        for (Object[] row : queryRepository.sumOnHandByProduct(allWarehouses, warehouseIds)) {
            stockByProduct.merge((Long) row[0], (BigDecimal) row[1], BigDecimal::add);
        }

        String dataNote = null;
        Map<Long, ProductInfo> catalog = Map.of();
        if (!stockByProduct.isEmpty()) {
            CatalogResult result = loadCatalog();
            catalog = result.products();
            if (!result.complete()) {
                dataNote = "Danh mục hàng hóa có hơn " + (CATALOG_PAGE_SIZE * CATALOG_MAX_PAGES)
                        + " sản phẩm, chỉ đọc được phần đầu: sản phẩm ngoài phần đã đọc được tính vào nhóm \""
                        + UNCATEGORIZED_NAME + "\"";
            }
        }

        // Gom theo nhóm hàng; sản phẩm chưa gán nhóm hoặc không còn trong danh mục vào một nhóm có tên riêng
        Map<String, Group> groups = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : stockByProduct.entrySet()) {
            ProductInfo product = catalog.get(entry.getKey());
            Long categoryId = product != null ? product.categoryId() : null;
            Group group = categoryId == null
                    ? groups.computeIfAbsent("uncategorized",
                            k -> new Group(k, StockByCategoryResponse.KIND_UNCATEGORIZED, null, UNCATEGORIZED_NAME))
                    : groups.computeIfAbsent("category-" + categoryId,
                            k -> new Group(k, StockByCategoryResponse.KIND_CATEGORY, categoryId,
                                    product.categoryName() != null && !product.categoryName().isBlank()
                                            ? product.categoryName() : "Nhóm hàng #" + categoryId));
            group.quantity = group.quantity.add(entry.getValue());
            group.productCount++;
            total = total.add(entry.getValue());
        }

        List<Group> sorted = groups.values().stream()
                .sorted(Comparator.comparing((Group g) -> g.quantity).reversed().thenComparing(g -> g.name))
                .toList();
        List<Group> shown = sorted;
        if (sorted.size() > MAX_SLICES) {
            shown = new ArrayList<>(sorted.subList(0, MAX_SLICES - 1));
            Group others = new Group("others", StockByCategoryResponse.KIND_OTHERS, null, OTHERS_NAME);
            for (Group g : sorted.subList(MAX_SLICES - 1, sorted.size())) {
                others.quantity = others.quantity.add(g.quantity);
                others.productCount += g.productCount;
            }
            shown.add(others);
        }
        BigDecimal grandTotal = total;
        List<Slice> slices = shown.stream()
                .map(g -> new Slice(g.key, g.kind, g.categoryId, g.name, g.quantity, null,
                        percentage(g.quantity, grandTotal), g.productCount))
                .toList();

        return new StockByCategoryResponse(
                range,
                Instant.now(),
                BASIS_QUANTITY,
                VALUATION_NOT_AVAILABLE,
                valueVisible,
                BASIS_NOTE,
                "đơn vị",
                total,
                null,
                stockByProduct.size(),
                groups.size(),
                slices,
                warehouseId,
                allWarehouses ? null : warehouseIds,
                dataNote);
    }

    /**
     * Các chứng từ kho gần nhất trong khoảng thời gian. Cùng vai trò và phạm vi với báo cáo kho ({@link ReportAccess}):
     * ADMIN, ACCOUNTANT, WAREHOUSE_MANAGER, WAREHOUSE_STAFF.
     *
     * @param limit số dòng, từ 1 đến {@value #MAX_LIMIT} (mặc định {@value #DEFAULT_LIMIT})
     */
    @Transactional(readOnly = true)
    public RecentTransactionsResponse getRecentTransactions(Long warehouseId, String dateRange, Integer limit) {
        String range = resolveRange(dateRange);
        int safeLimit = resolveLimit(limit);
        ReportAccess.Scope scope = reportAccess.resolve(warehouseId);
        boolean allWarehouses = scope.warehouseIds() == null;
        List<Long> warehouseIds = allWarehouses ? NO_WAREHOUSE_FILTER : scope.warehouseIds().stream().sorted().toList();

        Instant from = rangeStartInstant(range);
        List<Object[]> raw = queryRepository.findRecentDocuments(StockMovementType.OPENING_BALANCE,
                from != null ? from : Instant.EPOCH, allWarehouses, warehouseIds, PageRequest.of(0, safeLimit));

        Set<Long> warehouseRefs = new HashSet<>();
        Set<Long> adjustmentIds = new HashSet<>();
        for (Object[] row : raw) {
            warehouseRefs.add((Long) row[4]);
            if (row[11] != null) {
                warehouseRefs.add((Long) row[11]);
            }
            if (StockAdjustmentDocument.TYPE.equals(row[0]) && row[1] != null) {
                adjustmentIds.add((Long) row[1]);
            }
        }
        Map<Long, Warehouse> warehouses = warehouseRepository.findAllById(warehouseRefs).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<Long, Long> stocktakeByAdjustment = new HashMap<>();
        for (StockAdjustment adjustment : stockAdjustmentRepository.findAllById(adjustmentIds)) {
            stocktakeByAdjustment.put(adjustment.getId(), adjustment.getSession().getId());
        }

        List<RecentTransactionsResponse.Row> rows = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            String documentType = (String) row[0];
            Long documentId = (Long) row[1];
            StockMovementType type = (StockMovementType) row[3];
            Long whId = (Long) row[4];
            Long counterpartId = (Long) row[11];
            Warehouse wh = warehouses.get(whId);
            Warehouse counterpart = counterpartId != null ? warehouses.get(counterpartId) : null;
            BigDecimal quantity = type.isInbound() ? (BigDecimal) row[6] : (BigDecimal) row[7];
            rows.add(new RecentTransactionsResponse.Row(
                    documentType,
                    documentId,
                    (String) row[2],
                    type.name(),
                    type.getLabel(),
                    type.isInbound() ? "IN" : "OUT",
                    whId,
                    wh != null ? wh.getCode() : null,
                    wh != null ? wh.getName() : null,
                    counterpartId,
                    counterpart != null ? counterpart.getName() : null,
                    quantity,
                    ((Number) row[9]).longValue(),
                    ((Number) row[8]).longValue(),
                    (Instant) row[5],
                    (String) row[10],
                    StockAdjustmentDocument.TYPE.equals(documentType) ? stocktakeByAdjustment.get(documentId) : null));
        }

        return new RecentTransactionsResponse(range, from, safeLimit, rows, warehouseId,
                allWarehouses ? null : warehouseIds, Instant.now());
    }

    /** Loại chứng từ mà phiếu điều chỉnh kiểm kê ghi vào sổ kho ({@code StocktakeApprovalService}). */
    static final class StockAdjustmentDocument {
        static final String TYPE = "STOCKTAKE";

        private StockAdjustmentDocument() {
        }
    }

    /** Đọc toàn bộ danh mục hàng hóa của doanh nghiệp; product-service không trả lời thì 503, không đoán nhóm hàng. */
    private CatalogResult loadCatalog() {
        Map<Long, ProductInfo> products = new HashMap<>();
        for (int page = 0; page < CATALOG_MAX_PAGES; page++) {
            PageResponse<ProductInfo> data;
            try {
                ApiResponse<PageResponse<ProductInfo>> response = productClient.listCatalog(page, CATALOG_PAGE_SIZE, "id");
                data = response != null ? response.data() : null;
            } catch (Exception ex) {
                log.warn("Không đọc được danh mục hàng hóa từ product-service cho biểu đồ tồn kho theo nhóm: {}",
                        ex.getMessage());
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                        "Chưa lấy được nhóm hàng vì dịch vụ sản phẩm chưa sẵn sàng, vui lòng thử lại sau");
            }
            if (data == null || data.content() == null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                        "Dịch vụ sản phẩm trả về dữ liệu không hợp lệ, chưa lấy được nhóm hàng");
            }
            for (ProductInfo product : data.content()) {
                if (product != null && product.id() != null) {
                    products.put(product.id(), product);
                }
            }
            if (data.last() || data.content().isEmpty()) {
                return new CatalogResult(products, true);
            }
        }
        return new CatalogResult(products, false);
    }

    private record CatalogResult(Map<Long, ProductInfo> products, boolean complete) {
    }

    private void requireWarehouseExists(Long warehouseId) {
        if (warehouseId != null && !warehouseRepository.existsById(warehouseId)) {
            throw ApiException.notFound("Không tìm thấy kho ID: " + warehouseId);
        }
    }

    /** Chuẩn hóa {@code dateRange}: bỏ trống là 7_DAYS, giá trị ngoài {@link #DATE_RANGES} trả 400. */
    static String resolveRange(String dateRange) {
        if (dateRange == null || dateRange.isBlank()) {
            return "7_DAYS";
        }
        String range = dateRange.trim().toUpperCase();
        if (!DATE_RANGES.contains(range)) {
            throw ApiException.badRequest("Khoảng thời gian không hợp lệ: " + dateRange
                    + ". Giá trị được chấp nhận: " + String.join(", ", DATE_RANGES));
        }
        return range;
    }

    private static int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw ApiException.badRequest("Tham số limit phải từ 1 đến " + MAX_LIMIT);
        }
        return limit;
    }

    /** Đầu ngày đầu tiên của khoảng theo giờ Việt Nam (như báo cáo kho); {@code null} với ALL_TIME. */
    static Instant rangeStartInstant(String range) {
        LocalDate today = LocalDate.now(StockReportService.REPORT_ZONE);
        LocalDate start = switch (range) {
            case "TODAY" -> today;
            case "THIS_MONTH" -> today.withDayOfMonth(1);
            case "THIS_QUARTER" -> LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case "ALL_TIME" -> null;
            default -> today.minusDays(6);
        };
        return start == null ? null : start.atStartOfDay(StockReportService.REPORT_ZONE).toInstant();
    }

    private static double percentage(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return 0.0;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    /** Cộng dồn một nhóm hàng. */
    private static final class Group {
        final String key;
        final String kind;
        final Long categoryId;
        final String name;
        BigDecimal quantity = BigDecimal.ZERO;
        int productCount;

        Group(String key, String kind, Long categoryId, String name) {
            this.key = key;
            this.kind = kind;
            this.categoryId = categoryId;
            this.name = name;
        }
    }
}
