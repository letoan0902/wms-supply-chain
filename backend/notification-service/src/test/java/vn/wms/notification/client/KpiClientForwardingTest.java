package vn.wms.notification.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.client.dto.InventoryKpiMetricsResponse;
import vn.wms.notification.client.dto.OrderKpiMetricsResponse;
import vn.wms.notification.config.DashboardAggregatorConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Kiểm tra lời gọi từ notification-service sang inventory-service và order-service (WMS-16.1): đúng đường dẫn nội bộ
 * (ngoài /api/** nên gateway không route tới), mang theo X-Company-Id và các header người dùng của request đang xử lý,
 * và trả số liệu rỗng thay vì ném lỗi khi service phía sau lỗi. Không gọi mạng thật (MockRestServiceServer).
 */
class KpiClientForwardingTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        // Builder có interceptor chuyển tiếp header như bean thật, chỉ không có cân bằng tải Eureka
        builder = new DashboardAggregatorConfig().loadBalancedRestClientBuilder();
        server = MockRestServiceServer.bindTo(builder).build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_NAME, "manager_wh5");
        request.addHeader(UserContext.HEADER_USER_ROLES, "WAREHOUSE_MANAGER");
        request.addHeader(UserContext.HEADER_USER_WAREHOUSE_IDS, "5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContext.setCompanyId(7L);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Gọi inventory-service qua đường dẫn nội bộ, mang X-Company-Id và header người dùng")
    void inventoryClientForwardsCompanyAndUserHeaders() {
        server.expect(requestTo("http://inventory-service/internal/inventory/kpi-metrics?warehouseId=5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Company-Id", "7"))
                .andExpect(header(UserContext.HEADER_USER_NAME, "manager_wh5"))
                .andExpect(header(UserContext.HEADER_USER_ROLES, "WAREHOUSE_MANAGER"))
                .andExpect(header(UserContext.HEADER_USER_WAREHOUSE_IDS, "5"))
                .andRespond(withSuccess("""
                        {"success":true,"code":"OK","message":"Thành công","timestamp":"2026-11-11T03:00:00Z",
                         "data":{"availableStockQuantity":130,"totalStockQuantity":150,
                                 "occupiedLocations":1,"totalLocations":2,"occupancyRatePercentage":50.0}}
                        """, MediaType.APPLICATION_JSON));

        InventoryKpiMetricsResponse metrics = new InventoryKpiClient(builder).fetchMetrics(5L);

        server.verify();
        assertThat(metrics.availableStockQuantity()).isEqualByComparingTo("130");
        assertThat(metrics.totalLocations()).isEqualTo(2);
    }

    @Test
    @DisplayName("Gọi order-service qua đường dẫn nội bộ, mang X-Company-Id")
    void orderClientForwardsCompanyHeader() {
        server.expect(requestTo("http://order-service/internal/orders/kpi-metrics"))
                .andExpect(header("X-Company-Id", "7"))
                .andExpect(header(UserContext.HEADER_USER_NAME, "manager_wh5"))
                .andRespond(withSuccess("""
                        {"success":true,"code":"OK","message":"Thành công","timestamp":"2026-11-11T03:00:00Z",
                         "data":{"totalReceivables":1000,"overdueCustomersCount":2,"totalPayables":500,
                                 "dueSoonSuppliersCount":1,"currentMonthRevenue":300,"prevMonthRevenue":200,
                                 "pendingSalesOrdersCount":4,"urgentSalesOrdersCount":1,
                                 "pendingPurchaseOrdersCount":3,"arrivingTodayPurchaseOrdersCount":0}}
                        """, MediaType.APPLICATION_JSON));

        OrderKpiMetricsResponse metrics = new OrderKpiClient(builder).fetchMetrics(null);

        server.verify();
        assertThat(metrics.totalReceivables()).isEqualByComparingTo("1000");
        assertThat(metrics.pendingSalesOrdersCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("Service phía sau lỗi 500 thì trả số liệu rỗng, thẻ KPI không làm hỏng cả endpoint")
    void downstreamErrorDegradesToEmptyMetrics() {
        server.expect(requestTo("http://inventory-service/internal/inventory/kpi-metrics"))
                .andRespond(withServerError());

        InventoryKpiMetricsResponse metrics = new InventoryKpiClient(builder).fetchMetrics(null);

        server.verify();
        assertThat(metrics).isEqualTo(InventoryKpiMetricsResponse.empty());
    }
}
