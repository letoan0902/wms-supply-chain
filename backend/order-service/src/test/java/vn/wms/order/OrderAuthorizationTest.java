package vn.wms.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.wms.common.test.AuthorizationMatrix;
import vn.wms.common.test.AuthorizationMatrix.Endpoint;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.AuthorizationMatrix.endpoint;
import static vn.wms.common.test.GatewayHeaders.asIntegration;
import static vn.wms.common.test.GatewayHeaders.companyOnly;

/**
 * Ma trận phân quyền của order-service ở chế độ máy chủ ({@code app.security.allow-anonymous-without-gateway=false},
 * WMS-20.3): mỗi controller có API đọc và API ghi đại diện, gọi với từng vai trò trên cơ sở dữ liệu trống (vai trò
 * được phép nhận kết quả nghiệp vụ như 200, 400 hoặc 404); request thiếu danh tính nhận 401; INTEGRATION chỉ đọc.
 * Bảng đầy đủ ở docs/SECURITY.md.
 */
@SpringBootTest(properties = {
        "app.security.allow-anonymous-without-gateway=false",
        "spring.datasource.url=jdbc:h2:mem:order_authz;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderAuthorizationTest {

    private static final String A = "ADMIN";
    private static final String M = "WAREHOUSE_MANAGER";
    private static final String S = "WAREHOUSE_STAFF";
    private static final String P = "PURCHASING";
    private static final String L = "SALES";
    private static final String C = "ACCOUNTANT";
    private static final String I = "INTEGRATION";

    private static final Long MISSING_ID = 999_999L;

    private static final List<Endpoint> ENDPOINTS = List.of(
            endpoint("GET /api/orders/customers", () -> get("/api/orders/customers"), A, M, L, C, I),
            endpoint("DELETE /api/orders/customers/{id}", () -> delete("/api/orders/customers/" + MISSING_ID), A, L, C),
            endpoint("GET /api/orders/suppliers", () -> get("/api/orders/suppliers"), A, M, S, P, C, I),
            endpoint("GET /api/orders/suppliers/summary", () -> get("/api/orders/suppliers/summary"), A, M, P, C, I),
            endpoint("POST /api/orders/suppliers/bulk", () -> post("/api/orders/suppliers/bulk")
                    .contentType(MediaType.APPLICATION_JSON), A, M, P, C),
            endpoint("DELETE /api/orders/suppliers/{id}", () -> delete("/api/orders/suppliers/" + MISSING_ID), A, P, C),
            endpoint("GET /api/orders/purchase", () -> get("/api/orders/purchase"), A, M, S, P, C, I),
            endpoint("POST /api/orders/purchase/{id}/approve",
                    () -> post("/api/orders/purchase/" + MISSING_ID + "/approve"), A, M, P),
            endpoint("POST /api/orders/purchase/{id}/cancel",
                    () -> post("/api/orders/purchase/" + MISSING_ID + "/cancel"), A, P),
            endpoint("GET /api/orders/sales", () -> get("/api/orders/sales"), A, M, S, L, C, I),
            endpoint("POST /api/orders/sales/{id}/confirm",
                    () -> post("/api/orders/sales/" + MISSING_ID + "/confirm"), A, M, L),
            endpoint("POST /api/orders/sales/{id}/cancel",
                    () -> post("/api/orders/sales/" + MISSING_ID + "/cancel"), A, L),
            endpoint("GET /api/orders/sales/{id}/picking-list",
                    () -> get("/api/orders/sales/" + MISSING_ID + "/picking-list"), A, M, S, L),
            endpoint("GET /api/orders/goods-receipt-notes", () -> get("/api/orders/goods-receipt-notes"), A, M, S, P, C, I),
            endpoint("POST /api/orders/goods-receipt-notes/{id}/confirm",
                    () -> post("/api/orders/goods-receipt-notes/" + MISSING_ID + "/confirm"), A, M, S),
            endpoint("GET /api/orders/goods-delivery-notes", () -> get("/api/orders/goods-delivery-notes"),
                    A, M, S, L, C, I),
            endpoint("POST /api/orders/goods-delivery-notes/{id}/dispatch",
                    () -> post("/api/orders/goods-delivery-notes/" + MISSING_ID + "/dispatch"), A, M, S),
            endpoint("GET /api/orders/purchase-bills", () -> get("/api/orders/purchase-bills"), A, M, P, C, I),
            endpoint("POST /api/orders/purchase-bills/{id}/payments",
                    () -> post("/api/orders/purchase-bills/" + MISSING_ID + "/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":1000,\"paymentDate\":\"2026-11-11\",\"paymentMethod\":\"CASH\"}"),
                    A, C),
            endpoint("GET /api/orders/reports/trending-items", () -> get("/api/orders/reports/trending-items"),
                    A, M, S, P, L, C),
            endpoint("GET /api/orders/reports/purchase-sales-trend",
                    () -> get("/api/orders/reports/purchase-sales-trend"), A, M, S, P, L, C));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    static Stream<Arguments> matrix() {
        return AuthorizationMatrix.cases(ENDPOINTS);
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource("matrix")
    void roleMatrix(Endpoint endpoint, String role) throws Exception {
        AuthorizationMatrix.assertAccess(mockMvc, endpoint, role);
    }

    @Test
    void requestWithoutIdentityIsRejectedWith401() throws Exception {
        for (Endpoint endpoint : ENDPOINTS) {
            AuthorizationMatrix.assertRejectedWithoutIdentity(mockMvc, endpoint);
        }
    }

    @Test
    void integrationCannotWrite() throws Exception {
        mockMvc.perform(post("/api/orders/sales/" + MISSING_ID + "/cancel").with(asIntegration(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void internalKpiNeedsCompanyButNoUser() throws Exception {
        mockMvc.perform(get("/internal/orders/kpi-metrics").with(companyOnly(1L)))
                .andExpect(status().isOk());
    }

    @Test
    void everyApiDeclaresItsRoles() {
        assertThat(AuthorizationMatrix.handlersWithoutPreAuthorize(handlerMapping, "vn.wms.order")).isEmpty();
    }
}
