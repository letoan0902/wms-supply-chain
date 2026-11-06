package vn.wms.common.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kiểm tra bộ lọc đọc header X-Company-Id mà không cần khởi động Spring. */
class TenantFilterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void headerIsVisibleInsideChainAndClearedAfterwards() throws Exception {
        AtomicReference<Long> seen = new AtomicReference<>();
        MockHttpServletRequest request = request("GET", "/api/inventory/warehouses");
        request.addHeader(TenantContext.HEADER_COMPANY_ID, "42");

        filter(new TenantProperties()).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen.set(TenantContext.getCompanyId()));

        assertThat(seen.get()).isEqualTo(42L);
        assertThat(TenantContext.getCompanyId()).isNull();
    }

    @Test
    void contextIsClearedEvenWhenChainThrows() {
        MockHttpServletRequest request = request("GET", "/api/inventory/warehouses");
        request.addHeader(TenantContext.HEADER_COMPANY_ID, "42");
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("lỗi giả lập");
        };

        assertThatThrownBy(() -> filter(new TenantProperties()).doFilter(request, new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(TenantContext.getCompanyId()).isNull();
    }

    @Test
    void missingHeaderPassesThroughWhenNotRequired() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        AtomicReference<Long> seen = new AtomicReference<>(-1L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(new TenantProperties()).doFilter(request("GET", "/api/products"), response, (req, res) -> {
            called.set(true);
            seen.set(TenantContext.getCompanyId());
        });

        assertThat(called).isTrue();
        assertThat(seen.get()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void missingHeaderIsRejectedWithCompanyRequiredWhenRequired() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(required()).doFilter(request("GET", "/api/products"), response, (req, res) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo("COMPANY_REQUIRED");
        assertThat(body.get("message").asText()).isNotBlank();
    }

    @Test
    void invalidHeaderIsTreatedAsMissing() throws Exception {
        for (String value : List.of("abc", "0", "-5", " ")) {
            MockHttpServletRequest request = request("POST", "/api/products");
            request.addHeader(TenantContext.HEADER_COMPANY_ID, value);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter(required()).doFilter(request, response, (req, res) -> {
            });

            assertThat(response.getStatus()).as("header '%s'", value).isEqualTo(403);
        }
    }

    @Test
    void infrastructurePathsAndPreflightAreNeverRejected() throws Exception {
        TenantProperties properties = required();
        properties.setPublicPaths(List.of("/api/auth/plans", "/api/auth/companies/**"));
        TenantFilter filter = filter(properties);

        for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/v3/api-docs/swagger-config",
                "/swagger-ui/index.html", "/swagger-ui.html", "/error", "/api/auth/plans",
                "/api/auth/companies/register")) {
            AtomicBoolean called = new AtomicBoolean();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", path), response, (req, res) -> called.set(true));
            assertThat(called).as("đường dẫn %s phải được đi tiếp", path).isTrue();
        }

        AtomicBoolean preflight = new AtomicBoolean();
        filter.doFilter(request("OPTIONS", "/api/products"), new MockHttpServletResponse(),
                (req, res) -> preflight.set(true));
        assertThat(preflight).isTrue();
    }

    @Test
    void defaultCompanyIsUsedOnlyWhenHeaderIsMissing() throws Exception {
        TenantProperties properties = required();
        properties.setDefaultCompanyId(1L);
        AtomicReference<Long> seen = new AtomicReference<>();

        filter(properties).doFilter(request("GET", "/api/products"), new MockHttpServletResponse(),
                (req, res) -> seen.set(TenantContext.getCompanyId()));
        assertThat(seen.get()).isEqualTo(1L);

        MockHttpServletRequest withHeader = request("GET", "/api/products");
        withHeader.addHeader(TenantContext.HEADER_COMPANY_ID, "8");
        filter(properties).doFilter(withHeader, new MockHttpServletResponse(),
                (req, res) -> seen.set(TenantContext.getCompanyId()));
        assertThat(seen.get()).isEqualTo(8L);
    }

    private TenantFilter filter(TenantProperties properties) {
        return new TenantFilter(properties, objectMapper);
    }

    private static TenantProperties required() {
        TenantProperties properties = new TenantProperties();
        properties.setRequired(true);
        return properties;
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
