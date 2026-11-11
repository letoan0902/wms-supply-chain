package vn.wms.ai.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.ai.AiTestSupport;
import vn.wms.ai.assistant.ServiceDataClient;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.summary.DashboardFactsTestData;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/ai/summary} ở chế độ giả lập (profile test): Redis giả lập, PlanLimitChecker thật, service KPI giả
 * lập. Kiểm tra hợp đồng JSON cho khối "AI Summary" của dashboard, khóa bộ nhớ đệm và lỗi.
 */
@SpringBootTest(properties = "app.plan-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiSummaryControllerIntegrationTest {

    private static final long COMPANY_ID = 701L;
    private static final String AI_KEY_PREFIX = "wms:usage:ai:701:";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiClient aiClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ServiceDataClient serviceDataClient;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries("wms:subscription:" + COMPANY_ID))
                .thenReturn(Map.copyOf(AiTestSupport.subscription("TRIAL", "500")));
        when(valueOperations.get(startsWith(AI_KEY_PREFIX))).thenReturn("3");
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(4L);
        when(serviceDataClient.get(eq("notification-service"), anyString(), anyMap(), anyMap()))
                .thenReturn(new ServiceDataClient.Result(ServiceDataClient.Status.OK,
                        DashboardFactsTestData.kpi(false), null));
        when(serviceDataClient.get(eq("inventory-service"), anyString(), anyMap(), anyMap()))
                .thenReturn(new ServiceDataClient.Result(ServiceDataClient.Status.OK,
                        DashboardFactsTestData.inOut(true), null));
    }

    @Test
    void mockSummaryForWarehouseRoleIsGeneratedCountedAndCachedUnderItsRoleGroup() throws Exception {
        mockMvc.perform(get("/api/ai/summary")
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID)
                        .header(UserContext.HEADER_USER_NAME, "kho_01")
                        .header(UserContext.HEADER_USER_ROLES, "WAREHOUSE_MANAGER")
                        .header(UserContext.HEADER_USER_WAREHOUSE_IDS, "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mode").value("MOCK"))
                .andExpect(jsonPath("$.data.fromCache").value(false))
                .andExpect(jsonPath("$.data.financialsIncluded").value(false))
                .andExpect(jsonPath("$.data.bullets.length()").value(4))
                .andExpect(jsonPath("$.data.bullets[0]").value("Đơn bán chờ xuất kho: 7 đơn, trong đó 2 đơn cần "
                        + "lấy hàng gấp. Đơn mua chờ tiếp nhận: 4 đơn, 1 lô dự kiến đến hôm nay."))
                .andExpect(jsonPath("$.data.actions[0]").value("Ưu tiên điều phối lấy hàng cho 2 đơn bán gấp."))
                .andExpect(jsonPath("$.data.summary").isString())
                .andExpect(jsonPath("$.data.generatedAt").isString())
                .andExpect(jsonPath("$.data.warehouseId").isEmpty())
                .andExpect(jsonPath("$.data.usage.used").value(4))
                .andExpect(jsonPath("$.data.usage.max").value(500));

        verify(valueOperations).set(eq("wms:ai:summary:701:OPS:a3"), anyString(), eq(Duration.ofMinutes(30)));
        verifyNoInteractions(aiClient);
    }

    @Test
    void cachedSummaryIsReturnedWithoutCountingAndRefreshRegenerates() throws Exception {
        when(valueOperations.get("wms:ai:summary:701:FIN:w3")).thenReturn("""
                {"summary":"- Ý đã lưu","bullets":["Ý đã lưu"],"actions":[],"generatedAt":"2026-11-12T02:40:00Z",
                 "fromCache":false,"mode":"MOCK","warehouseId":3,"financialsIncluded":true,"usage":null}""");

        mockMvc.perform(get("/api/ai/summary").param("warehouseId", "3")
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID)
                        .header(UserContext.HEADER_USER_NAME, "admin")
                        .header(UserContext.HEADER_USER_ROLES, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fromCache").value(true))
                .andExpect(jsonPath("$.data.bullets[0]").value("Ý đã lưu"))
                .andExpect(jsonPath("$.data.generatedAt").value("2026-11-12T02:40:00Z"))
                .andExpect(jsonPath("$.data.usage.used").value(3));
        verify(valueOperations, never()).increment(anyString());
        verify(serviceDataClient, never()).get(anyString(), anyString(), anyMap(), anyMap());

        mockMvc.perform(get("/api/ai/summary").param("warehouseId", "3").param("refresh", "true")
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID)
                        .header(UserContext.HEADER_USER_NAME, "admin")
                        .header(UserContext.HEADER_USER_ROLES, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fromCache").value(false))
                .andExpect(jsonPath("$.data.warehouseId").value(3))
                .andExpect(jsonPath("$.data.usage.used").value(4));
        verify(valueOperations).increment(startsWith(AI_KEY_PREFIX));
    }

    @Test
    void forbiddenKpiIsForbidden() throws Exception {
        when(serviceDataClient.get(eq("notification-service"), anyString(), anyMap(), anyMap()))
                .thenReturn(new ServiceDataClient.Result(ServiceDataClient.Status.FORBIDDEN, null,
                        ServiceDataClient.FORBIDDEN_MESSAGE));

        mockMvc.perform(get("/api/ai/summary")
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID)
                        .header(UserContext.HEADER_USER_NAME, "kho_02")
                        .header(UserContext.HEADER_USER_ROLES, "WAREHOUSE_STAFF"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void invalidWarehouseIdAndMissingCompanyAreRejected() throws Exception {
        mockMvc.perform(get("/api/ai/summary").param("warehouseId", "abc")
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ai/summary"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_REQUIRED"));
    }
}
