package vn.wms.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.wms.common.constant.Roles;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.client.InventoryKpiClient;
import vn.wms.notification.client.OrderKpiClient;
import vn.wms.notification.client.dto.InventoryKpiMetricsResponse;
import vn.wms.notification.client.dto.OrderKpiMetricsResponse;
import vn.wms.notification.dto.dashboard.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardKpiService {

    private static final Logger log = LoggerFactory.getLogger(DashboardKpiService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final BigDecimal AVERAGE_UNIT_COST = new BigDecimal("57143");

    private final OrderKpiClient orderKpiClient;
    private final InventoryKpiClient inventoryKpiClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public KpiSummaryResponse getKpiSummary(Long warehouseId, String dateRange) {
        CurrentUser currentUser = UserContext.requireCurrentUser();
        Long companyId = TenantContext.requireCompanyId();
        String range = (dateRange != null && !dateRange.isBlank()) ? dateRange.trim().toUpperCase() : "7_DAYS";

        boolean canViewFinancials = canViewFinancials(currentUser);

        // Kiểm tra phạm vi kho của nhân viên
        Long effectiveWarehouseId = warehouseId;
        if (!UserContext.isGlobalScope(currentUser)) {
            Set<Long> allowedWhs = currentUser.warehouseIds();
            // Chưa được gán kho nào thì không được rơi về số liệu toàn doanh nghiệp (BR-13)
            if (allowedWhs == null || allowedWhs.isEmpty()) {
                throw vn.wms.common.exception.ApiException.forbidden("Tài khoản chưa được phân quyền phụ trách bất kỳ kho nào");
            }
            if (warehouseId != null && (allowedWhs == null || !allowedWhs.contains(warehouseId))) {
                throw vn.wms.common.exception.ApiException.forbidden("Tài khoản không có quyền truy cập dữ liệu của kho #" + warehouseId);
            }
            if (warehouseId == null && allowedWhs != null && !allowedWhs.isEmpty()) {
                effectiveWarehouseId = allowedWhs.iterator().next();
            }
        }

        String cacheKey = "wms:kpi:company:" + companyId + ":summary:" + (effectiveWarehouseId != null ? effectiveWarehouseId : "all") + ":" + range;

        // 1. Thử lấy từ Redis Cache
        try {
            String cachedJson = redis.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                KpiSummaryResponse cached = objectMapper.readValue(cachedJson, KpiSummaryResponse.class);
                return applyRoleMasking(cached, canViewFinancials, true);
            }
        } catch (Exception ex) {
            log.warn("Không đọc được cache KPI từ Redis: {}", ex.getMessage());
        }

        // 2. Cache MISS: Gọi đồng bộ sang order-service và inventory-service
        OrderKpiMetricsResponse orderData = orderKpiClient.fetchMetrics(effectiveWarehouseId);
        InventoryKpiMetricsResponse invData = inventoryKpiClient.fetchMetrics(effectiveWarehouseId);

        // 3. Tính toán các chỉ số phái sinh
        // Growth Rate Doanh số tháng (MoM)
        double growthRate = 0.0;
        BigDecimal currentRev = orderData.currentMonthRevenue();
        BigDecimal prevRev = orderData.prevMonthRevenue();
        if (prevRev != null && prevRev.compareTo(BigDecimal.ZERO) > 0 && currentRev != null) {
            BigDecimal diff = currentRev.subtract(prevRev);
            growthRate = diff.divide(prevRev, 3, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        } else if (currentRev != null && currentRev.compareTo(BigDecimal.ZERO) > 0) {
            growthRate = 100.0;
        }

        boolean isPositiveGrowth = growthRate >= 0;
        String revSubText = (isPositiveGrowth ? "Tăng " : "Giảm ") + Math.abs(growthRate) + "% so với tháng trước";

        // Giá trị tồn kho ước tính theo giá vốn bình quân
        BigDecimal inventoryValuation = invData.totalStockQuantity().multiply(AVERAGE_UNIT_COST);

        // Lắp ráp 8 thẻ KPI
        ReceivablesCardDto receivables = new ReceivablesCardDto(
                "Công nợ phải thu",
                orderData.totalReceivables(),
                "VNĐ",
                orderData.overdueCustomersCount(),
                orderData.overdueCustomersCount() + " khách hàng còn dư nợ đến hạn",
                canViewFinancials
        );

        PayablesCardDto payables = new PayablesCardDto(
                "Công nợ phải trả",
                orderData.totalPayables(),
                "VNĐ",
                orderData.dueSoonSuppliersCount(),
                orderData.dueSoonSuppliersCount() + " nhà cung cấp đến hạn trong tuần",
                canViewFinancials
        );

        InventoryValuationCardDto valuationCard = new InventoryValuationCardDto(
                "Giá trị tồn kho",
                inventoryValuation,
                "VNĐ",
                "WEIGHTED_AVERAGE",
                "Tính theo giá vốn bình quân",
                canViewFinancials
        );

        MonthlyRevenueCardDto revenueCard = new MonthlyRevenueCardDto(
                "Doanh số xuất bán tháng",
                orderData.currentMonthRevenue(),
                "VNĐ",
                growthRate,
                isPositiveGrowth,
                revSubText,
                canViewFinancials
        );

        AvailableStockCardDto availableStock = new AvailableStockCardDto(
                "Tồn kho khả dụng",
                invData.availableStockQuantity(),
                "sản phẩm",
                "Sản phẩm lưu tại các vị trí ô/kệ",
                true
        );

        PendingSalesOrdersCardDto pendingSo = new PendingSalesOrdersCardDto(
                "Đơn bán chờ xuất kho (SO)",
                orderData.pendingSalesOrdersCount(),
                orderData.urgentSalesOrdersCount(),
                orderData.urgentSalesOrdersCount() + " đơn cần điều phối lấy hàng gấp",
                true
        );

        PendingPurchaseOrdersCardDto pendingPo = new PendingPurchaseOrdersCardDto(
                "Đơn mua chờ tiếp nhận (PO)",
                orderData.pendingPurchaseOrdersCount(),
                orderData.arrivingTodayPurchaseOrdersCount(),
                orderData.arrivingTodayPurchaseOrdersCount() + " lô hàng dự kiến đến trong hôm nay",
                true
        );

        WarehouseUtilizationCardDto utilization = new WarehouseUtilizationCardDto(
                "Tỷ lệ lấp đầy kho",
                invData.occupancyRatePercentage(),
                invData.occupiedLocations(),
                invData.totalLocations(),
                invData.occupiedLocations() + " / " + invData.totalLocations() + " vị trí ô kệ đang sử dụng",
                true
        );

        DashboardMetaDto meta = new DashboardMetaDto(
                companyId,
                effectiveWarehouseId,
                effectiveWarehouseId != null ? "Kho #" + effectiveWarehouseId : "Tất cả các kho (Toàn hệ thống)",
                range,
                Instant.now(),
                false
        );

        KpiSummaryResponse unmaskedResponse = new KpiSummaryResponse(
                receivables,
                payables,
                valuationCard,
                revenueCard,
                availableStock,
                pendingSo,
                pendingPo,
                utilization,
                meta
        );

        // 4. Lưu Cache vào Redis (Lưu bản đầy đủ)
        try {
            String json = objectMapper.writeValueAsString(unmaskedResponse);
            redis.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Không thể lưu cache KPI vào Redis: {}", ex.getMessage());
        }

        return applyRoleMasking(unmaskedResponse, canViewFinancials, false);
    }

    private boolean canViewFinancials(CurrentUser user) {
        if (user == null) {
            return false;
        }
        return user.hasRole(Roles.ADMIN) || user.hasRole(Roles.ACCOUNTANT);
    }

    private KpiSummaryResponse applyRoleMasking(KpiSummaryResponse source, boolean canViewFinancials, boolean fromCache) {
        DashboardMetaDto meta = new DashboardMetaDto(
                source.meta().companyId(),
                source.meta().warehouseId(),
                source.meta().warehouseName(),
                source.meta().dateRange(),
                source.meta().calculatedAt(),
                fromCache
        );

        if (canViewFinancials) {
            return new KpiSummaryResponse(
                    source.receivables(),
                    source.payables(),
                    source.inventoryValuation(),
                    source.monthlyRevenue(),
                    source.availableStock(),
                    source.pendingSalesOrders(),
                    source.pendingPurchaseOrders(),
                    source.warehouseUtilization(),
                    meta
            );
        }

        // Masking các thẻ tài chính với vai trò nhân viên kho
        return new KpiSummaryResponse(
                ReceivablesCardDto.masked(),
                PayablesCardDto.masked(),
                InventoryValuationCardDto.masked(),
                MonthlyRevenueCardDto.masked(),
                source.availableStock(),
                source.pendingSalesOrders(),
                source.pendingPurchaseOrders(),
                source.warehouseUtilization(),
                meta
        );
    }
}
