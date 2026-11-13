package vn.wms.product;

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
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.wms.common.test.AuthorizationMatrix;
import vn.wms.common.test.AuthorizationMatrix.Endpoint;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.AuthorizationMatrix.endpoint;
import static vn.wms.common.test.GatewayHeaders.asIntegration;
import static vn.wms.common.test.GatewayHeaders.companyOnly;

/**
 * Ma trận phân quyền của product-service ở chế độ máy chủ ({@code app.security.allow-anonymous-without-gateway=false},
 * WMS-20.3): mỗi controller có API đọc và API ghi đại diện, gọi với từng vai trò; request thiếu danh tính nhận 401;
 * INTEGRATION chỉ đọc. Bảng đầy đủ ở docs/SECURITY.md.
 */
@SpringBootTest(properties = "app.security.allow-anonymous-without-gateway=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestExecutionListeners(listeners = DefaultCompanyTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class ProductAuthorizationTest {

    private static final String A = "ADMIN";
    private static final String M = "WAREHOUSE_MANAGER";
    private static final String S = "WAREHOUSE_STAFF";
    private static final String P = "PURCHASING";
    private static final String L = "SALES";
    private static final String C = "ACCOUNTANT";
    private static final String I = "INTEGRATION";

    private static final Long MISSING_ID = 999_999L;

    private static final List<Endpoint> ENDPOINTS = List.of(
            endpoint("GET /api/products", () -> get("/api/products"), A, M, S, P, L, C, I),
            endpoint("POST /api/products/bulk", () -> post("/api/products/bulk")
                    .contentType(MediaType.APPLICATION_JSON), A, M, S, P),
            endpoint("DELETE /api/products/{id}", () -> delete("/api/products/" + MISSING_ID), A, M),
            endpoint("GET /api/products/categories", () -> get("/api/products/categories"), A, M, S, P, L, C, I),
            endpoint("POST /api/products/categories", () -> post("/api/products/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"AUTHZ\",\"name\":\"Nhóm kiểm thử quyền\"}"), A, M, S, P),
            endpoint("GET /api/products/uoms", () -> get("/api/products/uoms"), A, M, S, P, L, C, I),
            endpoint("POST /api/products/uoms", () -> post("/api/products/uoms")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"AUTHZ\",\"name\":\"Đơn vị kiểm thử\"}"), A, M, S, P),
            endpoint("GET /api/products/{id}/conversions", () -> get("/api/products/" + MISSING_ID + "/conversions"),
                    A, M, S, P, L, C, I),
            endpoint("DELETE /api/products/{id}/conversions/{id}",
                    () -> delete("/api/products/" + MISSING_ID + "/conversions/" + MISSING_ID), A, M, S, P),
            endpoint("GET /api/products/barcodes/generate", () -> get("/api/products/barcodes/generate")
                    .param("text", "SKU-001"), A, M, S, P, L, C, I),
            endpoint("POST /api/products/{id}/barcode/batch", () -> post("/api/products/" + MISSING_ID + "/barcode/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"batchNumber\":\"LOT-01\",\"expirationDate\":\"2027-01-01\"}"), A, M, S));

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
    void integrationCannotUseCategoryAliasOutsideProductPaths() throws Exception {
        // Đường dẫn bí danh /api/categories nằm ngoài /api/products/**: API key không được gọi (IntegrationAccessFilter)
        mockMvc.perform(get("/api/categories").with(asIntegration(1L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalProductListServesBackgroundJobsWithoutUser() throws Exception {
        // Tác vụ quét cảnh báo tồn kho của inventory-service gọi không có người dùng, chỉ có doanh nghiệp (WMS-15.1)
        mockMvc.perform(get("/internal/products").with(companyOnly(1L)))
                .andExpect(status().isOk());
        // Cùng lời gọi không người dùng tới API công khai thì bị từ chối ở chế độ máy chủ
        mockMvc.perform(get("/api/products").with(companyOnly(1L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyApiDeclaresItsRoles() {
        assertThat(AuthorizationMatrix.handlersWithoutPreAuthorize(handlerMapping, "vn.wms.product")).isEmpty();
    }
}
