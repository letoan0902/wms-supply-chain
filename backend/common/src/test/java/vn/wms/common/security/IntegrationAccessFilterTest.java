package vn.wms.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Vai trò INTEGRATION (API key, WMS-25.1) chỉ đọc hàng hóa, tồn kho, đơn hàng; vai trò khác không bị ảnh hưởng. */
class IntegrationAccessFilterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final IntegrationAccessFilter filter = new IntegrationAccessFilter(objectMapper);

    private static MockHttpServletRequest request(String method, String path, String roles) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (roles != null) {
            request.addHeader(UserContext.HEADER_USER_ROLES, roles);
        }
        return request;
    }

    private boolean passes(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicBoolean reached = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> reached.set(true));
        return reached.get();
    }

    @Test
    void integrationMayReadProductsStockAndOrders() throws Exception {
        for (String path : List.of("/api/products", "/api/products/5", "/api/inventory/stock/atp",
                "/api/inventory/warehouses", "/api/orders/sales", "/api/orders/purchase/7")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(passes(request("GET", path, "INTEGRATION"), response)).as(path).isTrue();
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void integrationCannotWriteAnything() throws Exception {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(passes(request(method, "/api/inventory/transfers", "INTEGRATION"), response))
                    .as(method).isFalse();

            assertThat(response.getStatus()).isEqualTo(403);
            JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
            assertThat(body.path("success").asBoolean()).isFalse();
            assertThat(body.path("code").asText()).isEqualTo("FORBIDDEN");
            assertThat(body.path("message").asText()).contains("chỉ được đọc");
        }
    }

    @Test
    void integrationCannotReadOtherModules() throws Exception {
        for (String path : List.of("/api/auth/users", "/api/notifications", "/api/ai/usage", "/internal/orders/kpi-metrics")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(passes(request("GET", path, "INTEGRATION"), response)).as(path).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @Test
    void roleIsDetectedInsideAListOfRoles() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(passes(request("POST", "/api/products", "SALES, INTEGRATION"), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void otherRolesAndAnonymousRequestsPassUntouched() throws Exception {
        assertThat(passes(request("POST", "/api/inventory/transfers", "ADMIN"), new MockHttpServletResponse())).isTrue();
        assertThat(passes(request("DELETE", "/api/products/1", "WAREHOUSE_MANAGER"), new MockHttpServletResponse()))
                .isTrue();
        assertThat(passes(request("POST", "/api/auth/login", null), new MockHttpServletResponse())).isTrue();
        // Tên vai trò chỉ chứa chuỗi INTEGRATION (không đúng tên) không bị coi là INTEGRATION
        assertThat(passes(request("POST", "/api/products", "INTEGRATION_X"), new MockHttpServletResponse())).isTrue();
    }
}
