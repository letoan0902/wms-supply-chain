package vn.wms.inventory;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.AuthorizationMatrix.endpoint;
import static vn.wms.common.test.GatewayHeaders.asIntegration;
import static vn.wms.common.test.GatewayHeaders.companyOnly;

/**
 * Ma trận phân quyền của inventory-service ở chế độ máy chủ ({@code app.security.allow-anonymous-without-gateway=false},
 * WMS-20.3): mỗi controller có API đọc và API ghi đại diện, gọi với từng vai trò (người dùng được gán kho 1, cơ sở dữ
 * liệu trống nên vai trò được phép nhận kết quả nghiệp vụ như 200 hoặc 404); request thiếu danh tính nhận 401;
 * INTEGRATION chỉ đọc; API nội bộ cần doanh nghiệp nhưng không cần người dùng. Bảng đầy đủ ở docs/SECURITY.md.
 */
@SpringBootTest(properties = {
        "app.security.allow-anonymous-without-gateway=false",
        "spring.datasource.url=jdbc:h2:mem:inventory_authz;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryAuthorizationTest {

    private static final String A = "ADMIN";
    private static final String M = "WAREHOUSE_MANAGER";
    private static final String S = "WAREHOUSE_STAFF";
    private static final String P = "PURCHASING";
    private static final String L = "SALES";
    private static final String C = "ACCOUNTANT";
    private static final String I = "INTEGRATION";

    /** Kho 1 là kho được gán cho người dùng trong test nhưng không tồn tại trong cơ sở dữ liệu trống. */
    private static final Long WAREHOUSE = AuthorizationMatrix.ASSIGNED_WAREHOUSE_ID;
    private static final Long MISSING_ID = 999_999L;

    private static final List<Endpoint> ENDPOINTS = List.of(
            endpoint("GET /api/inventory/warehouses", () -> get("/api/inventory/warehouses"), A, M, S, P, L, C, I),
            endpoint("PATCH /api/inventory/warehouses/{id}/status",
                    () -> patch("/api/inventory/warehouses/" + WAREHOUSE + "/status")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"), A, M),
            endpoint("DELETE /api/inventory/warehouses/{id}", () -> delete("/api/inventory/warehouses/" + WAREHOUSE), A),
            endpoint("GET /api/inventory/locations", () -> get("/api/inventory/locations"), A, M, S, I),
            endpoint("DELETE /api/inventory/locations/{id}", () -> delete("/api/inventory/locations/" + MISSING_ID), A, M),
            endpoint("GET /api/inventory/incoming-stocks", () -> get("/api/inventory/incoming-stocks")
                    .param("warehouseId", String.valueOf(WAREHOUSE)), A, M, S, P, L, C, I),
            endpoint("GET /api/inventory/lots", () -> get("/api/inventory/lots"), A, M, S, P, L, C, I),
            endpoint("GET /api/inventory/alerts", () -> get("/api/inventory/alerts").param("kind", "NEAR_EXPIRY"),
                    A, M, S, P, L, C, I),
            endpoint("POST /api/inventory/alerts/scan (vai trò không phải ADMIN bị từ chối)",
                    () -> post("/api/inventory/alerts/scan"), A),
            endpoint("PUT /api/inventory/lots/{id}/unlock", () -> put("/api/inventory/lots/" + MISSING_ID + "/unlock"), A, M),
            endpoint("GET /api/inventory/stock-adjustments", () -> get("/api/inventory/stock-adjustments"), A, M, C),
            endpoint("GET /api/inventory/stock/atp", () -> get("/api/inventory/stock/atp").param("productId", "1"),
                    A, M, S, P, L, C, I),
            endpoint("POST /api/inventory/stock/reservations/release",
                    () -> post("/api/inventory/stock/reservations/release").param("orderId", String.valueOf(MISSING_ID)),
                    A, M, L),
            endpoint("GET /api/inventory/reports/lot-trace", () -> get("/api/inventory/reports/lot-trace")
                    .param("lotNo", "LOT-KHONG-CO"), A, M, S, C),
            endpoint("GET /api/inventory/dashboard/stock-by-category",
                    () -> get("/api/inventory/dashboard/stock-by-category"), A, M, S, P, L, C),
            endpoint("GET /api/inventory/dashboard/recent-transactions",
                    () -> get("/api/inventory/dashboard/recent-transactions"), A, M, S, C),
            endpoint("GET /api/inventory/stocktakes", () -> get("/api/inventory/stocktakes"), A, M, S, C),
            endpoint("POST /api/inventory/stocktakes/{id}/start",
                    () -> post("/api/inventory/stocktakes/" + MISSING_ID + "/start"), A, M),
            endpoint("POST /api/inventory/stocktakes/{id}/adjustment/approve",
                    () -> post("/api/inventory/stocktakes/" + MISSING_ID + "/adjustment/approve"), A, C),
            endpoint("GET /api/inventory/transfers", () -> get("/api/inventory/transfers"), A, M, S, C, I),
            endpoint("POST /api/inventory/transfers/{id}/dispatch",
                    () -> post("/api/inventory/transfers/" + MISSING_ID + "/dispatch"), A, M));

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
    void integrationCannotWriteEvenWhereReadIsAllowed() throws Exception {
        mockMvc.perform(post("/api/inventory/stock/atp/batch").with(asIntegration(1L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productIds\":[1]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void internalKpiNeedsCompanyButNoUser() throws Exception {
        // Lời gọi của tác vụ nền hoặc service khác: chỉ có doanh nghiệp, không có người dùng
        mockMvc.perform(get("/internal/inventory/kpi-metrics").with(companyOnly(1L)))
                .andExpect(status().isOk());
        // Lời gọi chuyển tiếp người dùng (notification-service gắn lại header của người xem dashboard)
        mockMvc.perform(get("/internal/inventory/kpi-metrics").with(AuthorizationMatrix.identity(S)))
                .andExpect(status().isOk());
    }

    @Test
    void everyApiDeclaresItsRoles() {
        assertThat(AuthorizationMatrix.handlersWithoutPreAuthorize(handlerMapping, "vn.wms.inventory")).isEmpty();
    }
}
