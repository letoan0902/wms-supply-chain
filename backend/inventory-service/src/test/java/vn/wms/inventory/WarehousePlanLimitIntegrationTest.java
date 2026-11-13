package vn.wms.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.CreateWarehouseRequest;
import vn.wms.inventory.dto.UpdateWarehouseRequest;
import vn.wms.inventory.repository.WarehouseRepository;

import java.util.Map;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giới hạn số kho theo gói (WMS-23.2): PlanLimitChecker thật, StringRedisTemplate giả lập hash
 * {@code wms:subscription:{companyId}} do auth-service ghi. Dùng doanh nghiệp riêng để không lẫn dữ liệu test khác.
 */
@SpringBootTest(properties = "app.plan-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WarehousePlanLimitIntegrationTest {

    private static final long COMPANY_ID = 501L;
    private static final String USAGE_KEY = "wms:usage:501";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private HashOperations<String, Object, Object> hashOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cleanUp();
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        TenantContext.runAs(COMPANY_ID, () -> warehouseRepository.deleteAll());
    }

    private void givenPlan(String plan, String maxWarehouses) {
        when(hashOperations.entries("wms:subscription:" + COMPANY_ID)).thenReturn(Map.of(
                "plan", plan, "status", "ACTIVE", "expiresAt", "",
                "maxUsers", "3", "maxWarehouses", maxWarehouses, "maxSkus", "200",
                "maxAiRequests", "20", "apiAccess", "false"));
    }

    private ResultActions create(String code, boolean active) throws Exception {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                code, "Kho " + code, "Hà Nội", "Quản lý", "0901234567", 100, active);
        return mockMvc.perform(post("/api/inventory/warehouses")
                .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private long createdId(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    private ResultActions toggle(long id, boolean active) throws Exception {
        return mockMvc.perform(patch("/api/inventory/warehouses/{id}/status", id)
                .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": " + active + "}"));
    }

    private static void expectLimitExceeded(ResultActions result, int current, int max) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message")
                        .value("Gói Khởi Đầu chỉ cho phép tối đa 1 kho. Nâng cấp gói để thêm kho mới."))
                .andExpect(jsonPath("$.data.limit").value("WAREHOUSES"))
                .andExpect(jsonPath("$.data.current").value(current))
                .andExpect(jsonPath("$.data.max").value(max))
                .andExpect(jsonPath("$.data.plan").value("FREE"));
    }

    @Test
    void freePlan_secondActiveWarehouseIsRejectedWithPlanLimitExceeded() throws Exception {
        givenPlan("FREE", "1");

        create("WH-A", true).andExpect(status().isCreated());
        verify(hashOperations, atLeastOnce()).put(USAGE_KEY, "warehouses", "1");

        expectLimitExceeded(create("WH-B", true), 1, 1);
    }

    @Test
    void underLimit_creationWorks() throws Exception {
        givenPlan("TRIAL", "20");

        create("WH-A", true).andExpect(status().isCreated());
        create("WH-B", true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("WH-B"));
        verify(hashOperations, atLeastOnce()).put(USAGE_KEY, "warehouses", "2");
    }

    @Test
    void reactivatingWarehouseCountsAsCreating() throws Exception {
        givenPlan("FREE", "1");
        long first = createdId(create("WH-A", true).andExpect(status().isCreated()));
        // Kho tạo ở trạng thái ngưng không tính vào giới hạn
        long second = createdId(create("WH-B", false).andExpect(status().isCreated()));

        // Bật lại kho thứ hai khi đã có 1 kho hoạt động: bị chặn, qua cả PATCH trạng thái lẫn PUT cập nhật
        expectLimitExceeded(toggle(second, true), 1, 1);
        UpdateWarehouseRequest update = new UpdateWarehouseRequest(
                "Kho WH-B", "Hà Nội", "Quản lý", "0901234567", 100, true);
        expectLimitExceeded(mockMvc.perform(put("/api/inventory/warehouses/{id}", second)
                .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update))), 1, 1);

        // Ngưng kho thứ nhất thì bật được kho thứ hai
        toggle(first, false).andExpect(status().isOk());
        verify(hashOperations, atLeastOnce()).put(USAGE_KEY, "warehouses", "0");
        toggle(second, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }
}
