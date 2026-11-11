package vn.wms.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.ai.assistant.ServiceDataClient;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.AiContentPart;
import vn.wms.ai.client.AiJsonReply;
import vn.wms.ai.client.AiJsonRequest;
import vn.wms.ai.config.AiMode;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.config.AiSummaryProperties;
import vn.wms.ai.dto.AskResponse;
import vn.wms.ai.dto.SummaryResponse;
import vn.wms.ai.summary.SummaryCache;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Bộ nhớ đệm theo nhóm vai trò, làm mới, đếm lượt và các lỗi của {@link AiSummaryService}. */
class AiSummaryServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    private final AiClient aiClient = mock(AiClient.class);
    private final AiQuotaService quotaService = mock(AiQuotaService.class);
    private final ServiceDataClient dataClient = mock(ServiceDataClient.class);
    private final SummaryCache cache = mock(SummaryCache.class);

    private AiSummaryService service(AiMode mode) {
        return new AiSummaryService(new AiRuntime(mode, "claude-sonnet-5-5"), aiClient, quotaService, dataClient,
                cache, new AiSummaryProperties(Duration.ofMinutes(30), 1500, Duration.ofSeconds(60)), MAPPER,
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    private void user(String roles, String warehouses) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_NAME, "nguoi_xem");
        request.addHeader(UserContext.HEADER_USER_ROLES, roles);
        if (warehouses != null) {
            request.addHeader(UserContext.HEADER_USER_WAREHOUSE_IDS, warehouses);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.setCompanyId(701L);
        when(quotaService.currentMax()).thenReturn(500L);
        when(quotaService.currentUsage()).thenReturn(9L);
        when(quotaService.recordSuccessfulRequest()).thenReturn(10L);
        when(cache.find(anyString())).thenReturn(Optional.empty());
    }

    private void givenData(boolean financial) throws Exception {
        JsonNode kpi = vn.wms.ai.summary.DashboardFactsTestData.kpi(financial);
        when(dataClient.get(eq("notification-service"), eq("/api/notifications/dashboard/kpi-summary"), anyMap(),
                anyMap())).thenReturn(new ServiceDataClient.Result(ServiceDataClient.Status.OK, kpi, null));
        when(dataClient.get(eq("inventory-service"), eq("/api/inventory/reports/in-out-balance"), anyMap(),
                anyMap())).thenReturn(new ServiceDataClient.Result(ServiceDataClient.Status.OK,
                vn.wms.ai.summary.DashboardFactsTestData.inOut(true), null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void cacheKeySeparatesCompanyRoleGroupAndWarehouseScope() {
        user("ADMIN", null);
        String admin = SummaryCache.keyForCurrentUser(701L, null);
        user("ACCOUNTANT", null);
        String accountant = SummaryCache.keyForCurrentUser(701L, null);
        user("WAREHOUSE_MANAGER", "4,3");
        String manager = SummaryCache.keyForCurrentUser(701L, null);
        String managerWarehouse = SummaryCache.keyForCurrentUser(701L, 3L);
        user("ADMIN", null);
        String adminWarehouse = SummaryCache.keyForCurrentUser(701L, 3L);

        assertThat(admin).isEqualTo("wms:ai:summary:701:FIN:all").isEqualTo(accountant);
        assertThat(manager).isEqualTo("wms:ai:summary:701:OPS:a3-4");
        assertThat(managerWarehouse).isEqualTo("wms:ai:summary:701:OPS:w3");
        // Cùng kho nhưng khác nhóm vai trò: không dùng chung bản tóm tắt
        assertThat(adminWarehouse).isEqualTo("wms:ai:summary:701:FIN:w3").isNotEqualTo(managerWarehouse);
        assertThat(SummaryCache.keyForCurrentUser(702L, 3L)).startsWith("wms:ai:summary:702:");
    }

    @Test
    void cacheHitIsServedWithoutDataCallsAiOrQuota() {
        user("WAREHOUSE_MANAGER", "3");
        SummaryResponse stored = new SummaryResponse("- Ý 1", List.of("Ý 1"), List.of(), NOW, false, "LIVE", null,
                false, null);
        when(cache.find("wms:ai:summary:701:OPS:a3")).thenReturn(Optional.of(stored));

        SummaryResponse response = service(AiMode.LIVE).summary(null, false);

        assertThat(response.fromCache()).isTrue();
        assertThat(response.generatedAt()).isEqualTo(NOW);
        assertThat(response.usage()).isEqualTo(new AskResponse.Usage(9L, 500L));
        verifyNoInteractions(dataClient, aiClient);
        verify(quotaService, never()).checkQuota();
        verify(quotaService, never()).recordSuccessfulRequest();
    }

    @Test
    void refreshRegeneratesAndCountsOneRequest() throws Exception {
        user("ADMIN", null);
        givenData(true);
        when(aiClient.generateJson(any())).thenReturn(AiJsonReply.completed("""
                {"bullets":["- 7 đơn bán chờ xuất, 2 đơn gấp.","Kho lấp đầy 91,5%.","Phải thu 12.500.000 đ."],
                 "actions":["Ưu tiên 2 đơn gấp.","Giải phóng vị trí.","Thừa"]}""", "end_turn", 900, 80));

        SummaryResponse response = service(AiMode.LIVE).summary(null, true);

        verify(cache, never()).find(anyString());
        assertThat(response.fromCache()).isFalse();
        assertThat(response.mode()).isEqualTo("LIVE");
        assertThat(response.financialsIncluded()).isTrue();
        assertThat(response.bullets()).containsExactly("7 đơn bán chờ xuất, 2 đơn gấp.", "Kho lấp đầy 91,5%.",
                "Phải thu 12.500.000 đ.");
        assertThat(response.actions()).containsExactly("Ưu tiên 2 đơn gấp.", "Giải phóng vị trí.");
        assertThat(response.summary()).isEqualTo("- 7 đơn bán chờ xuất, 2 đơn gấp.\n- Kho lấp đầy 91,5%.\n"
                + "- Phải thu 12.500.000 đ.\n\nGợi ý:\n- Ưu tiên 2 đơn gấp.\n- Giải phóng vị trí.");
        assertThat(response.usage()).isEqualTo(new AskResponse.Usage(10L, 500L));
        verify(quotaService).checkQuota();
        verify(quotaService).recordSuccessfulRequest();
        verify(cache).save(eq("wms:ai:summary:701:FIN:all"), any());

        ArgumentCaptor<AiJsonRequest> request = ArgumentCaptor.forClass(AiJsonRequest.class);
        verify(aiClient).generateJson(request.capture());
        assertThat(request.getValue().systemPrompt()).isEqualTo(SummaryPrompts.SYSTEM_PROMPT);
        assertThat(request.getValue().jsonSchema()).isEqualTo(AiSummaryService.SCHEMA);
        String data = ((AiContentPart.Text) request.getValue().parts().get(0)).text();
        assertThat(data).startsWith("<kpi_data>").contains("12500000").contains("\"quantityIn\" : 500");
        // Báo cáo nhập xuất lấy cùng kho mà notification-service đã dùng, từ đầu tháng tới hôm nay
        verify(dataClient).get(eq("inventory-service"), eq("/api/inventory/reports/in-out-balance"), anyMap(),
                argThat((Map<String, ?> query) -> "2026-11-01".equals(query.get("fromDate"))
                        && "2026-11-12".equals(query.get("toDate")) && Long.valueOf(3L).equals(query.get("warehouseId"))));
    }

    @Test
    void maskedFiguresOfWarehouseRoleNeverReachTheModel() throws Exception {
        user("WAREHOUSE_STAFF", "3");
        givenData(false);
        when(aiClient.generateJson(any())).thenReturn(AiJsonReply.completed(
                "{\"bullets\":[\"7 đơn bán chờ xuất.\"],\"actions\":[]}", "end_turn", 1, 1));

        SummaryResponse response = service(AiMode.LIVE).summary(null, false);

        ArgumentCaptor<AiJsonRequest> request = ArgumentCaptor.forClass(AiJsonRequest.class);
        verify(aiClient).generateJson(request.capture());
        String data = ((AiContentPart.Text) request.getValue().parts().get(0)).text();
        assertThat(data).contains("hiddenForRole").doesNotContain("\"name\" : \"Công nợ phải thu\"");
        assertThat(response.financialsIncluded()).isFalse();
        verify(cache).save(eq("wms:ai:summary:701:OPS:a3"), any());
    }

    @Test
    void refusalOrTruncationIsUnavailableAndNotCountedOrCached() throws Exception {
        user("ADMIN", null);
        givenData(true);
        when(aiClient.generateJson(any())).thenReturn(AiJsonReply.refusal("refusal", 10, 0));

        assertThatThrownBy(() -> service(AiMode.LIVE).summary(null, false))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        verify(quotaService, never()).recordSuccessfulRequest();
        verify(cache, never()).save(anyString(), any());
    }

    @Test
    void forbiddenKpiIsForbiddenAndDoesNotTouchQuota() {
        user("WAREHOUSE_STAFF", null);
        when(dataClient.get(eq("notification-service"), anyString(), anyMap(), anyMap())).thenReturn(
                new ServiceDataClient.Result(ServiceDataClient.Status.FORBIDDEN, null,
                        ServiceDataClient.FORBIDDEN_MESSAGE));

        assertThatThrownBy(() -> service(AiMode.LIVE).summary(null, false))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(quotaService, never()).checkQuota();
        verifyNoInteractions(aiClient);
    }

    @Test
    void mockModeBuildsBulletsFromNumbersAndCountsOnce() throws Exception {
        user("ADMIN", null);
        givenData(true);

        SummaryResponse response = service(AiMode.MOCK).summary(3L, false);

        assertThat(response.mode()).isEqualTo("MOCK");
        assertThat(response.warehouseId()).isEqualTo(3L);
        assertThat(response.bullets()).hasSize(5);
        assertThat(response.bullets().get(0)).contains("7 đơn");
        assertThat(response.actions()).isNotEmpty();
        verify(quotaService).recordSuccessfulRequest();
        verify(cache).save(eq("wms:ai:summary:701:FIN:w3"), any());
        verifyNoInteractions(aiClient);
    }
}
