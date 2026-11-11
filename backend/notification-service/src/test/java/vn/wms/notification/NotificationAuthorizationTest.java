package vn.wms.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.wms.common.test.AuthorizationMatrix;
import vn.wms.common.test.AuthorizationMatrix.Endpoint;
import vn.wms.notification.service.DashboardKpiService;
import vn.wms.notification.realtime.RealtimeKpiService;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.AuthorizationMatrix.endpoint;

/**
 * Ma trận phân quyền của notification-service ở chế độ máy chủ ({@code app.security.allow-anonymous-without-gateway=false},
 * WMS-20.3). Số liệu KPI được giả lập (không có Redis khi test); chỉ kiểm tra ai được gọi API. Hub WebSocket vẫn mở
 * vì JWT được kiểm tra ở frame STOMP CONNECT.
 */
@SpringBootTest(properties = {
        "app.security.allow-anonymous-without-gateway=false",
        "spring.datasource.url=jdbc:h2:mem:notification_authz;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationAuthorizationTest {

    private static final String A = "ADMIN";
    private static final String M = "WAREHOUSE_MANAGER";
    private static final String S = "WAREHOUSE_STAFF";
    private static final String P = "PURCHASING";
    private static final String L = "SALES";
    private static final String C = "ACCOUNTANT";

    private static final List<Endpoint> ENDPOINTS = List.of(
            endpoint("GET /api/notifications/dashboard/kpi-summary",
                    () -> get("/api/notifications/dashboard/kpi-summary"), A, M, S, P, L, C),
            endpoint("GET /api/notifications/realtime/kpi", () -> get("/api/notifications/realtime/kpi"),
                    A, M, S, P, L, C),
            endpoint("GET /api/notifications/health-info", () -> get("/api/notifications/health-info"), A));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockBean
    private DashboardKpiService dashboardKpiService;

    @MockBean
    private RealtimeKpiService realtimeKpiService;

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
    void webSocketHandshakeStaysOpen() throws Exception {
        mockMvc.perform(get("/ws-wms/info"))
                .andExpect(status().is(not(401)));
    }

    @Test
    void everyApiDeclaresItsRoles() {
        assertThat(AuthorizationMatrix.handlersWithoutPreAuthorize(handlerMapping, "vn.wms.notification")).isEmpty();
    }
}
