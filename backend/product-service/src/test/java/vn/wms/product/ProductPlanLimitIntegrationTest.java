package vn.wms.product;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.wms.common.tenant.TenantContext;
import vn.wms.product.dto.CreateProductRequest;
import vn.wms.product.dto.UpdateProductRequest;
import vn.wms.product.entity.Product;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.CategoryRepository;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UnitConversionRepository;
import vn.wms.product.repository.UomRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Giới hạn số SKU theo gói (WMS-23.2) sau khi product-service tách dữ liệu theo doanh nghiệp (WMS-22.4b):
 * PlanLimitChecker thật, StringRedisTemplate giả lập hash {@code wms:subscription:{companyId}}. Số SKU chỉ đếm hàng
 * hóa đang hoạt động của chính doanh nghiệp; nhập hàng loạt cũng bị chặn ở dòng vượt giới hạn.
 *
 * <p>Không đặt {@code @Transactional}: nhập hàng loạt mở transaction riêng cho từng dòng nên dữ liệu được commit;
 * mỗi test tự dọn dữ liệu của hai doanh nghiệp dùng trong lớp. Doanh nghiệp mặc định của profile test được tắt để
 * kiểm tra request thiếu doanh nghiệp.
 */
@SpringBootTest(properties = {"app.plan-limit.enabled=true", "app.tenant.default-company-id="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductPlanLimitIntegrationTest {

    private static final long COMPANY_ID = 601L;
    private static final long OTHER_COMPANY_ID = 602L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UomRepository uomRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UnitConversionRepository unitConversionRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private HashOperations<String, Object, Object> hashOperations;
    private Long uomId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cleanUp();
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        uomId = TenantContext.callAs(COMPANY_ID, () -> uomRepository.save(newUom("PLAN-CAI", "Cái")).getId());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        TenantContext.clear();
    }

    private void cleanUp() {
        for (long companyId : List.of(COMPANY_ID, OTHER_COMPANY_ID)) {
            TenantContext.runAs(companyId, () -> {
                unitConversionRepository.deleteAll();
                productRepository.deleteAll();
                categoryRepository.deleteAll();
                uomRepository.deleteAll();
            });
        }
    }

    private static Uom newUom(String code, String name) {
        Uom uom = new Uom();
        uom.setCode(code);
        uom.setName(name);
        return uom;
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

    private long activeSkus(long companyId) {
        return TenantContext.callAs(companyId, () -> productRepository.countByActiveTrue());
    }

    private static String limitMessage(long max) {
        return "Gói Khởi Đầu chỉ cho phép tối đa " + max + " SKU. Nâng cấp gói để thêm SKU mới.";
    }

    private static void expectLimitExceeded(ResultActions result, long current, long max) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(limitMessage(max)))
                .andExpect(jsonPath("$.data.limit").value("SKUS"))
                .andExpect(jsonPath("$.data.current").value(current))
                .andExpect(jsonPath("$.data.max").value(max))
                .andExpect(jsonPath("$.data.plan").value("FREE"));
    }

    @Test
    void atLimit_createReturnsPlanLimitExceeded_underLimitWorks() throws Exception {
        long existing = activeSkus(COMPANY_ID);
        givenFreePlan(existing + 1);

        create("PLAN-SKU-1", true).andExpect(status().isCreated());
        verify(hashOperations, atLeastOnce()).put("wms:usage:" + COMPANY_ID, "skus", String.valueOf(existing + 1));

        expectLimitExceeded(create("PLAN-SKU-2", true), existing + 1, existing + 1);
    }

    @Test
    void reactivatingSoftDeletedSkuCountsAsCreating() throws Exception {
        long existing = activeSkus(COMPANY_ID);
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
    void onlyProductsOfTheCompanyAreCounted() throws Exception {
        // Doanh nghiệp khác có 3 SKU đang hoạt động: không được tính vào giới hạn của doanh nghiệp 601
        TenantContext.runAs(OTHER_COMPANY_ID, () -> {
            Uom otherUom = uomRepository.save(newUom("PLAN-CAI", "Cái"));
            for (int i = 1; i <= 3; i++) {
                Product product = new Product();
                product.setSku("OTHER-SKU-" + i);
                product.setName("Hàng của doanh nghiệp khác " + i);
                product.setUom(otherUom);
                productRepository.save(product);
            }
        });
        assertThat(activeSkus(COMPANY_ID)).isZero();
        givenFreePlan(1);

        create("PLAN-SKU-1", true).andExpect(status().isCreated());
        // Mức sử dụng ghi cho doanh nghiệp 601 là 1, không phải 4
        verify(hashOperations, atLeastOnce()).put("wms:usage:" + COMPANY_ID, "skus", "1");
        verify(hashOperations, never()).put("wms:usage:" + COMPANY_ID, "skus", "4");

        expectLimitExceeded(create("PLAN-SKU-2", true), 1, 1);
    }

    @Test
    void withoutCompany_createIsRejectedAndCheckIsNotConsulted() throws Exception {
        givenFreePlan(0);

        create("PLAN-SKU-NO-TENANT", false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_REQUIRED"));

        verify(hashOperations, never()).entries(anyString());
        assertThat(activeSkus(COMPANY_ID)).isZero();
    }

    @Test
    void bulkImport_rowsBeyondSkuLimitFailWithPlanLimitReason() throws Exception {
        givenFreePlan(3);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            items.add(Map.of("sku", "BULK-PLAN-" + i, "name", "Hàng nhập " + i, "uomName", "Cái"));
        }

        JsonNode data = bulk(items);
        assertThat(data.path("total").asInt()).isEqualTo(5);
        assertThat(data.path("created").asInt()).isEqualTo(3);
        assertThat(data.path("skipped").asInt()).isZero();
        assertThat(data.path("failed").asInt()).isEqualTo(2);
        for (int row = 4; row <= 5; row++) {
            JsonNode result = data.path("results").get(row - 1);
            assertThat(result.path("row").asInt()).isEqualTo(row);
            assertThat(result.path("status").asText()).isEqualTo("FAILED");
            assertThat(result.path("code").asText()).isEqualTo("BULK-PLAN-" + row);
            assertThat(result.path("message").asText()).isEqualTo(limitMessage(3));
        }
        assertThat(activeSkus(COMPANY_ID)).isEqualTo(3);
        verify(hashOperations, atLeastOnce()).put("wms:usage:" + COMPANY_ID, "skus", "3");

        // Nhập lại: 3 dòng đã có bị bỏ qua (không tính là tạo thêm), 2 dòng vẫn vượt giới hạn
        JsonNode again = bulk(items);
        assertThat(again.path("skipped").asInt()).isEqualTo(3);
        assertThat(again.path("failed").asInt()).isEqualTo(2);
        assertThat(again.path("created").asInt()).isZero();
        assertThat(activeSkus(COMPANY_ID)).isEqualTo(3);
    }

    private JsonNode bulk(List<Map<String, Object>> items) throws Exception {
        String body = mockMvc.perform(withCompany(post("/api/products/bulk"))
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
