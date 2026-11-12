package vn.wms.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.common.constant.Roles;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.client.InventoryKpiClient;
import vn.wms.notification.client.OrderKpiClient;
import vn.wms.notification.client.dto.InventoryKpiMetricsResponse;
import vn.wms.notification.client.dto.OrderKpiMetricsResponse;
import vn.wms.notification.dto.dashboard.KpiSummaryResponse;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardKpiServiceTest {

    @Mock
    private OrderKpiClient orderKpiClient;

    @Mock
    private InventoryKpiClient inventoryKpiClient;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private DashboardKpiService dashboardKpiService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        org.mockito.Mockito.lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        dashboardKpiService = new DashboardKpiService(orderKpiClient, inventoryKpiClient, redis, objectMapper);
    }

    @Test
    @DisplayName("Admin xem đầy đủ 8 thẻ KPI với số liệu tài chính công khai")
    void getKpiSummary_asAdmin_shouldReturnAll8CardsWithFinancials() {
        // Mock request context as Admin
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_NAME, "admin");
        request.addHeader(UserContext.HEADER_USER_ROLES, Roles.ADMIN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContext.setCompanyId(1L);

        try {
            when(valueOperations.get(anyString())).thenReturn(null); // Cache miss

            when(orderKpiClient.fetchMetrics(null)).thenReturn(new OrderKpiMetricsResponse(
                    new BigDecimal("248500000"), 12,
                    new BigDecimal("132800000"), 4,
                    new BigDecimal("655000000"), new BigDecimal("589000000"),
                    18, 5, 7, 3
            ));

            when(inventoryKpiClient.fetchMetrics(null)).thenReturn(new InventoryKpiMetricsResponse(
                    new BigDecimal("24850"), new BigDecimal("24850"),
                    184, 234, 78.5
            ));

            KpiSummaryResponse response = dashboardKpiService.getKpiSummary(null, "7_DAYS");

            assertThat(response).isNotNull();
            // Thẻ 1: Công nợ thu
            assertThat(response.receivables().visible()).isTrue();
            assertThat(response.receivables().amount()).isEqualByComparingTo("248500000");
            assertThat(response.receivables().overdueCustomersCount()).isEqualTo(12);

            // Thẻ 2: Công nợ trả
            assertThat(response.payables().visible()).isTrue();
            assertThat(response.payables().amount()).isEqualByComparingTo("132800000");
            assertThat(response.payables().dueSoonSuppliersCount()).isEqualTo(4);

            // Thẻ 3: Giá trị tồn kho
            assertThat(response.inventoryValuation().visible()).isTrue();

            // Thẻ 4: Doanh số tháng & tăng trưởng
            assertThat(response.monthlyRevenue().visible()).isTrue();
            assertThat(response.monthlyRevenue().growthRatePercentage()).isGreaterThan(0.0);
            assertThat(response.monthlyRevenue().isPositiveGrowth()).isTrue();

            // Thẻ 5: Tồn kho khả dụng
            assertThat(response.availableStock().totalQuantity()).isEqualByComparingTo("24850");

            // Thẻ 6: SO chờ xuất
            assertThat(response.pendingSalesOrders().totalOrders()).isEqualTo(18);
            assertThat(response.pendingSalesOrders().urgentOrders()).isEqualTo(5);

            // Thẻ 7: PO chờ nhận
            assertThat(response.pendingPurchaseOrders().totalOrders()).isEqualTo(7);
            assertThat(response.pendingPurchaseOrders().arrivingTodayCount()).isEqualTo(3);

            // Thẻ 8: Tỷ lệ lấp đầy
            assertThat(response.warehouseUtilization().occupancyRatePercentage()).isEqualTo(78.5);
            assertThat(response.warehouseUtilization().usedLocations()).isEqualTo(184);
            assertThat(response.warehouseUtilization().totalLocations()).isEqualTo(234);
        } finally {
            RequestContextHolder.resetRequestAttributes();
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Quản lý kho xem được chỉ số vận hành nhưng bị che (mask) 4 thẻ tài chính nhạy cảm")
    void getKpiSummary_asWarehouseManager_shouldMaskFinancialCards() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_NAME, "manager_wh1");
        request.addHeader(UserContext.HEADER_USER_ROLES, Roles.WAREHOUSE_MANAGER);
        request.addHeader(UserContext.HEADER_USER_WAREHOUSE_IDS, "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContext.setCompanyId(1L);

        try {
            when(valueOperations.get(anyString())).thenReturn(null);

            when(orderKpiClient.fetchMetrics(1L)).thenReturn(new OrderKpiMetricsResponse(
                    new BigDecimal("248500000"), 12,
                    new BigDecimal("132800000"), 4,
                    new BigDecimal("655000000"), new BigDecimal("589000000"),
                    18, 5, 7, 3
            ));

            when(inventoryKpiClient.fetchMetrics(1L)).thenReturn(new InventoryKpiMetricsResponse(
                    new BigDecimal("24850"), new BigDecimal("24850"),
                    184, 234, 78.5
            ));

            KpiSummaryResponse response = dashboardKpiService.getKpiSummary(1L, "7_DAYS");

            assertThat(response).isNotNull();
            // Thẻ tài chính bị che
            assertThat(response.receivables().visible()).isFalse();
            assertThat(response.receivables().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.payables().visible()).isFalse();
            assertThat(response.inventoryValuation().visible()).isFalse();
            assertThat(response.monthlyRevenue().visible()).isFalse();

            // Thẻ vận hành kho vẫn nhìn thấy bình thường
            assertThat(response.availableStock().visible()).isTrue();
            assertThat(response.availableStock().totalQuantity()).isEqualByComparingTo("24850");
            assertThat(response.pendingSalesOrders().visible()).isTrue();
            assertThat(response.pendingPurchaseOrders().visible()).isTrue();
            assertThat(response.warehouseUtilization().visible()).isTrue();
        } finally {
            RequestContextHolder.resetRequestAttributes();
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Chưa đăng nhập / không có accessToken bị từ chối 401 Unauthorized")
    void getKpiSummary_unauthenticated_shouldThrowUnauthorized() {
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dashboardKpiService.getKpiSummary(null, "7_DAYS"))
                .isInstanceOf(vn.wms.common.exception.ApiException.class)
                .hasMessageContaining("Yêu cầu thông tin xác thực");
    }

    @Test
    @DisplayName("Nhân viên kho truy cập kho ngoài quyền bị từ chối 403 Forbidden")
    void getKpiSummary_asWarehouseStaff_accessingOtherWarehouse_shouldThrowForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_NAME, "staff_wh1");
        request.addHeader(UserContext.HEADER_USER_ROLES, Roles.WAREHOUSE_STAFF);
        request.addHeader(UserContext.HEADER_USER_WAREHOUSE_IDS, "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContext.setCompanyId(1L);

        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> dashboardKpiService.getKpiSummary(2L, "7_DAYS"))
                    .isInstanceOf(vn.wms.common.exception.ApiException.class)
                    .hasMessageContaining("không có quyền truy cập dữ liệu của kho #2");
        } finally {
            RequestContextHolder.resetRequestAttributes();
            TenantContext.clear();
        }
    }
}
