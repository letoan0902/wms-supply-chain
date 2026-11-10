package vn.wms.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.product.dto.CreateProductRequest;
import vn.wms.product.dto.UpdateProductRequest;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UomRepository;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giới hạn số SKU theo gói (WMS-23.2): PlanLimitChecker thật, StringRedisTemplate giả lập hash
 * {@code wms:subscription:{companyId}}. product-service chưa tách dữ liệu theo doanh nghiệp nên số SKU đếm trên toàn
 * bảng; test lấy số hiện có làm mốc để không phụ thuộc dữ liệu của test khác.
 */
@SpringBootTest(properties = "app.plan-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductPlanLimitIntegrationTest {

    private static final long COMPANY_ID = 601L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UomRepository uomRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private HashOperations<String, Object, Object> hashOperations;
    private Long uomId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        uomId = uomRepository.findByCode("PLAN-CAI").orElseGet(() -> {
            Uom uom = new Uom();
            uom.setCode("PLAN-CAI");
            uom.setName("Cái");
            return uomRepository.save(uom);
        }).getId();
    }

    private void givenFreePlan(long maxSkus) {
        when(hashOperations.entries("wms:subscription:" + COMPANY_ID)).thenReturn(Map.of(
                "plan", "FREE", "status", "ACTIVE", "expiresAt", "",
                "maxUsers", "3", "maxWarehouses", "1", "maxSkus", String.valueOf(maxSkus),
                "maxAiRequests", "20", "apiAccess", "false"));
    }

    private MockHttpServletRequestBuilder withCompany(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Company-Id", COMPANY_ID).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions create(String sku, boolean withCompany) throws Exception {
        CreateProductRequest request = new CreateProductRequest(sku, null, "Sản phẩm " + sku, null, null, uomId,
                null, null, null, null, null, null, null, null, null);
        MockHttpServletRequestBuilder builder = post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
        return mockMvc.perform(withCompany ? withCompany(builder) : builder);
    }

    private long createdId(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private static void expectLimitExceeded(ResultActions result, long current, long max) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Gói Khởi Đầu chỉ cho phép tối đa " + max
                        + " SKU. Nâng cấp gói để thêm SKU mới."))
                .andExpect(jsonPath("$.data.limit").value("SKUS"))
                .andExpect(jsonPath("$.data.current").value(current))
                .andExpect(jsonPath("$.data.max").value(max))
                .andExpect(jsonPath("$.data.plan").value("FREE"));
    }

    @Test
    void atLimit_createReturnsPlanLimitExceeded_underLimitWorks() throws Exception {
        long existing = productRepository.countByActiveTrue();
        givenFreePlan(existing + 1);

        create("PLAN-SKU-1", true).andExpect(status().isCreated());
        verify(hashOperations, atLeastOnce()).put("wms:usage:" + COMPANY_ID, "skus", String.valueOf(existing + 1));

        expectLimitExceeded(create("PLAN-SKU-2", true), existing + 1, existing + 1);
    }

    @Test
    void reactivatingSoftDeletedSkuCountsAsCreating() throws Exception {
        long existing = productRepository.countByActiveTrue();
        givenFreePlan(existing + 1);
        long first = createdId(create("PLAN-SKU-1", true).andExpect(status().isCreated()));

        // Xóa mềm SKU thứ nhất thì tạo được SKU khác
        mockMvc.perform(withCompany(delete("/api/products/{id}", first))).andExpect(status().isOk());
        create("PLAN-SKU-2", true).andExpect(status().isCreated());

        // Bật lại SKU đã xóa mềm khi đã đủ giới hạn: bị chặn
        UpdateProductRequest reactivate = new UpdateProductRequest("PLAN-SKU-1", null, "Sản phẩm PLAN-SKU-1", null,
                null, uomId, null, null, null, null, null, null, null, null, null, true);
        expectLimitExceeded(mockMvc.perform(withCompany(put("/api/products/{id}", first))
                .content(objectMapper.writeValueAsString(reactivate))), existing + 1, existing + 1);
    }

    @Test
    void withoutCompanyHeader_checkIsSkipped() throws Exception {
        givenFreePlan(0);

        create("PLAN-SKU-NO-TENANT", false).andExpect(status().isCreated());

        verify(hashOperations, never()).entries(anyString());
    }
}
