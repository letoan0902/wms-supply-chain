package vn.wms.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.client.InventoryGateway;
import vn.wms.order.client.WarehouseSummary;
import vn.wms.order.dto.report.TrendingItemSliceDto;
import vn.wms.order.dto.report.TrendingItemsReportResponse;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.repository.SalesOrderItemRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service xử lý báo cáo thống kê tỷ trọng đơn hàng và mặt hàng xuất bán chạy (Trending Items - WMS-16.2).
 */
@Service
@RequiredArgsConstructor
public class OrderReportService {

    private static final Logger log = LoggerFactory.getLogger(OrderReportService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /** Số mặt hàng hiển thị riêng mặc định và tối đa; phần còn lại gom vào nhóm "Mặt hàng khác". */
    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 20;

    /** Các khoảng thời gian được chấp nhận ở tham số {@code dateRange}. */
    public static final List<String> DATE_RANGES = List.of("TODAY", "7_DAYS", "THIS_MONTH", "THIS_QUARTER", "ALL_TIME");

    /** Đơn bán được tính là đã bán: cùng quy ước với doanh số tháng của thẻ KPI (OrderKpiInternalService). */
    private static final List<OrderStatus> SOLD_STATUSES = List.of(OrderStatus.CONFIRMED, OrderStatus.COMPLETED);

    /** Giá trị giữ chỗ cho danh sách kho khi không lọc theo kho (mệnh đề IN không nhận danh sách rỗng). */
    private static final List<Long> NO_WAREHOUSE_FILTER = List.of(-1L);

    /**
     * Bảng màu chuẩn dùng cho biểu đồ tròn Donut (Recharts) ở giao diện người dùng.
     */
    private static final String[] CHART_COLORS = {
            "#1890ff", // Xanh dương chủ đạo
            "#52c41a", // Xanh lá
            "#fa8c16", // Cam
            "#eb2f96", // Hồng tím
            "#13c2c2", // Xanh ngọc
            "#722ed1"  // Tím đậm
    };
    private static final String OTHER_COLOR = "#8c8c8c"; // Xám cho nhóm 'Khác'

    private final SalesOrderItemRepository salesOrderItemRepository;
    private final InventoryGateway inventoryGateway;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * Thống kê Top mặt hàng xuất bán chạy nhất theo khoảng thời gian và kho.
     *
     * @param warehouseId ID kho (tùy chọn); bỏ trống thì lấy mọi kho trong phạm vi của người xem
     * @param dateRange   Khoảng thời gian: TODAY, 7_DAYS, THIS_MONTH, THIS_QUARTER, ALL_TIME (bỏ trống là 7_DAYS)
     * @param limit       Số lượng mặt hàng hiển thị độc lập, từ 1 đến 20 (bỏ trống là 5)
     */
    @Transactional(readOnly = true)
    public TrendingItemsReportResponse getTrendingItems(Long warehouseId, String dateRange, Integer limit) {
        // 1. Bắt buộc có người dùng (401) và doanh nghiệp (COMPANY_REQUIRED); không được rơi về doanh nghiệp mặc định
        // vì khóa cache có ID doanh nghiệp: rơi về doanh nghiệp 1 sẽ trả số liệu của doanh nghiệp 1 cho người khác.
        CurrentUser currentUser = UserContext.requireCurrentUser();
        Long companyId = TenantContext.requireCompanyId();

        int safeLimit = resolveLimit(limit);
        String range = resolveRange(dateRange);

        // 2. Phạm vi kho: ADMIN, ACCOUNTANT xem mọi kho; vai trò khác chỉ các kho được gán, chưa được gán kho nào
        // thì 403 (BR-13), xin kho ngoài phạm vi thì 403. Tập rỗng nghĩa là không lọc theo kho.
        Set<Long> warehouseScope = UserContext.resolveAllowedWarehouses(currentUser, warehouseId);
        boolean allWarehouses = warehouseScope.isEmpty();
        List<Long> warehouseIds = allWarehouses ? NO_WAREHOUSE_FILTER : warehouseScope.stream().sorted().toList();
        Long singleWarehouseId = warehouseIds.size() == 1 && !allWarehouses ? warehouseIds.get(0) : null;

        String cacheKey = "wms:orders:company:" + companyId + ":trending:"
                + (allWarehouses ? "all" : warehouseIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
                + ":" + range + ":" + safeLimit;

        // 3. Đọc từ Redis Cache (Cache-Aside Pattern)
        try {
            String cachedJson = redis.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                TrendingItemsReportResponse cached = objectMapper.readValue(cachedJson, TrendingItemsReportResponse.class);
                return new TrendingItemsReportResponse(
                        cached.title(), cached.totalVolume(), cached.unit(), cached.dateRange(),
                        cached.warehouseName(), cached.slices(),
                        new TrendingItemsReportResponse.ReportMeta(companyId, singleWarehouseId, cached.meta().calculatedAt(), true)
                );
            }
        } catch (Exception ex) {
            log.warn("Không đọc được cache báo cáo Trending Items từ Redis: {}", ex.getMessage());
        }

        // 4. Xác định mốc thời gian lọc (ngày đặt hàng, tính cả hai đầu)
        LocalDate today = LocalDate.now();
        LocalDate startDate = rangeStart(range, today);

        // 5. Tổng hợp trong cơ sở dữ liệu (JPQL nên được lọc theo doanh nghiệp tự động qua @TenantId)
        List<Object[]> rawRows = salesOrderItemRepository.findTopSoldProducts(
                startDate, today, SOLD_STATUSES, allWarehouses, warehouseIds
        );

        // 6. Tính tổng sản lượng toàn bộ mặt hàng xuất bán trong kỳ
        BigDecimal totalVolume = BigDecimal.ZERO;
        for (Object[] row : rawRows) {
            BigDecimal qty = (BigDecimal) row[4];
            if (qty != null) {
                totalVolume = totalVolume.add(qty);
            }
        }

        List<TrendingItemSliceDto> slices = new ArrayList<>();

        if (totalVolume.compareTo(BigDecimal.ZERO) > 0) {
            int topCount = Math.min(rawRows.size(), safeLimit);
            BigDecimal topSlicesVolume = BigDecimal.ZERO;

            for (int i = 0; i < topCount; i++) {
                Object[] row = rawRows.get(i);
                Long productId = (Long) row[0];
                String sku = (String) row[1];
                String name = (String) row[2];
                String uom = (String) row[3];
                BigDecimal qty = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;

                topSlicesVolume = topSlicesVolume.add(qty);

                String color = (i < CHART_COLORS.length) ? CHART_COLORS[i] : CHART_COLORS[CHART_COLORS.length - 1];

                slices.add(new TrendingItemSliceDto(
                        productId, sku, name, uom != null ? uom : "SP", qty, percentageOf(qty, totalVolume), color
                ));
            }

            // Nếu còn các mặt hàng khác nằm ngoài top limit: Gom thành nhóm "Khác"
            if (rawRows.size() > safeLimit) {
                BigDecimal otherQty = totalVolume.subtract(topSlicesVolume);
                slices.add(new TrendingItemSliceDto(
                        null, "OTHERS", "Mặt hàng khác", "SP", otherQty, percentageOf(otherQty, totalVolume), OTHER_COLOR
                ));
            }
        }

        TrendingItemsReportResponse response = new TrendingItemsReportResponse(
                "Mặt hàng xuất bán chạy",
                totalVolume,
                "sản phẩm",
                range,
                resolveWarehouseName(allWarehouses, warehouseIds),
                slices,
                new TrendingItemsReportResponse.ReportMeta(companyId, singleWarehouseId, Instant.now(), false)
        );

        // 7. Lưu Cache vào Redis
        try {
            String json = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Không thể lưu cache báo cáo Trending Items vào Redis: {}", ex.getMessage());
        }

        return response;
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

    /**
     * Ngày đầu của khoảng thời gian (tính cả ngày đó); ngày cuối luôn là {@code today}. Dùng chung cho mọi báo cáo
     * của dashboard để các biểu đồ cùng một mốc.
     */
    public static LocalDate rangeStart(String range, LocalDate today) {
        return switch (range) {
            case "TODAY" -> today;
            case "THIS_MONTH" -> today.withDayOfMonth(1);
            case "THIS_QUARTER" -> LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case "ALL_TIME" -> LocalDate.of(2000, 1, 1);
            default -> today.minusDays(6); // 7 ngày gần nhất, tính cả hôm nay
        };
    }

    /** Chuẩn hóa tham số {@code dateRange}: bỏ trống là 7_DAYS, giá trị ngoài {@link #DATE_RANGES} trả 400. */
    public static String resolveRange(String dateRange) {
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

    private static double percentageOf(BigDecimal qty, BigDecimal total) {
        return qty.divide(total, 3, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Tên kho chỉ để hiển thị: không gọi được inventory-service thì dùng "Kho #id", không làm hỏng cả báo cáo. */
    private String resolveWarehouseName(boolean allWarehouses, List<Long> warehouseIds) {
        if (allWarehouses) {
            return "Tất cả các kho";
        }
        if (warehouseIds.size() > 1) {
            return warehouseIds.size() + " kho được phân công";
        }
        Long id = warehouseIds.get(0);
        try {
            return inventoryGateway.findWarehouse(id).map(WarehouseSummary::name).orElse("Kho #" + id);
        } catch (ApiException ex) {
            log.warn("Không lấy được tên kho {} cho báo cáo Trending Items: {}", id, ex.getMessage());
            return "Kho #" + id;
        }
    }
}
