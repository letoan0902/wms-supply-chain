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

/**
 * Service xử lý báo cáo thống kê tỷ trọng đơn hàng và mặt hàng xuất bán chạy (Trending Items - WMS-16.2).
 */
@Service
@RequiredArgsConstructor
public class OrderReportService {

    private static final Logger log = LoggerFactory.getLogger(OrderReportService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

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
     * @param warehouseId ID kho (tùy chọn)
     * @param dateRange   Khoảng thời gian: TODAY, 7_DAYS, THIS_MONTH, THIS_QUARTER, ALL_TIME
     * @param limit       Số lượng mặt hàng hiển thị độc lập (mặc định 5)
     */
    @Transactional(readOnly = true)
    public TrendingItemsReportResponse getTrendingItems(Long warehouseId, String dateRange, int limit) {
        // 1. Kiểm tra xác thực người dùng (Chặn 401 nếu chưa đăng nhập)
        CurrentUser currentUser = UserContext.requireCurrentUser();
        Long companyId = TenantContext.isSet() ? TenantContext.getCompanyId() : 1L;

        int safeLimit = (limit > 0 && limit <= 20) ? limit : 5;
        String range = (dateRange != null && !dateRange.isBlank()) ? dateRange.trim().toUpperCase() : "7_DAYS";

        // 2. Kiểm tra phân quyền phạm vi kho (Chặn 403 nếu nhân viên truy cập kho ngoài quyền)
        Long effectiveWarehouseId = warehouseId;
        if (!UserContext.isGlobalScope(currentUser)) {
            Set<Long> allowedWhs = currentUser.warehouseIds();
            if (warehouseId != null && (allowedWhs == null || !allowedWhs.contains(warehouseId))) {
                throw ApiException.forbidden("Tài khoản không có quyền truy cập dữ liệu của kho #" + warehouseId);
            }
            if (warehouseId == null && allowedWhs != null && !allowedWhs.isEmpty()) {
                effectiveWarehouseId = allowedWhs.iterator().next();
            }
        }

        // Tên kho hiển thị
        String warehouseName = "Tất cả các kho";
        if (effectiveWarehouseId != null) {
            warehouseName = inventoryGateway.findWarehouse(effectiveWarehouseId)
                    .map(WarehouseSummary::name)
                    .orElse("Kho #" + effectiveWarehouseId);
        }

        String cacheKey = "wms:orders:company:" + companyId + ":trending:"
                + (effectiveWarehouseId != null ? effectiveWarehouseId : "all") + ":" + range + ":" + safeLimit;

        // 3. Đọc từ Redis Cache (Cache-Aside Pattern)
        try {
            String cachedJson = redis.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                TrendingItemsReportResponse cached = objectMapper.readValue(cachedJson, TrendingItemsReportResponse.class);
                return new TrendingItemsReportResponse(
                        cached.title(), cached.totalVolume(), cached.unit(), cached.dateRange(),
                        cached.warehouseName(), cached.slices(),
                        new TrendingItemsReportResponse.ReportMeta(companyId, effectiveWarehouseId, cached.meta().calculatedAt(), true)
                );
            }
        } catch (Exception ex) {
            log.warn("Không đọc được cache báo cáo Trending Items từ Redis: {}", ex.getMessage());
        }

        // 4. Xác định mốc thời gian lọc
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate = today;

        switch (range) {
            case "TODAY" -> startDate = today;
            case "THIS_MONTH" -> startDate = today.withDayOfMonth(1);
            case "THIS_QUARTER" -> {
                int firstMonthOfQuarter = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                startDate = LocalDate.of(today.getYear(), firstMonthOfQuarter, 1);
            }
            case "ALL_TIME" -> startDate = LocalDate.of(2000, 1, 1);
            default -> startDate = today.minusDays(7); // Mặc định 7 ngày qua
        }

        // 5. Truy vấn dữ liệu thực tế từ so_items
        List<OrderStatus> validStatuses = List.of(OrderStatus.CONFIRMED, OrderStatus.COMPLETED);
        List<Object[]> rawRows = salesOrderItemRepository.findTopSoldProducts(
                startDate, endDate, validStatuses, effectiveWarehouseId
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
                BigDecimal qty = (BigDecimal) row[4];

                topSlicesVolume = topSlicesVolume.add(qty);

                double percentage = qty.divide(totalVolume, 3, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();

                String color = (i < CHART_COLORS.length) ? CHART_COLORS[i] : CHART_COLORS[CHART_COLORS.length - 1];

                slices.add(new TrendingItemSliceDto(
                        productId, sku, name, uom != null ? uom : "SP", qty, percentage, color
                ));
            }

            // Nếu còn các mặt hàng khác nằm ngoài top limit: Gom thành nhóm "Khác"
            if (rawRows.size() > safeLimit) {
                BigDecimal otherQty = totalVolume.subtract(topSlicesVolume);
                double otherPercentage = otherQty.divide(totalVolume, 3, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();

                slices.add(new TrendingItemSliceDto(
                        null, "OTHERS", "Mặt hàng khác", "SP", otherQty, otherPercentage, OTHER_COLOR
                ));
            }
        }

        TrendingItemsReportResponse response = new TrendingItemsReportResponse(
                "Trending Items - Mặt hàng xuất bán chạy",
                totalVolume,
                "sản phẩm",
                range,
                warehouseName,
                slices,
                new TrendingItemsReportResponse.ReportMeta(companyId, effectiveWarehouseId, Instant.now(), false)
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
}
