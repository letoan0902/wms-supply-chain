package vn.wms.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.wms.common.tenant.TenantContext;
import vn.wms.product.entity.Category;
import vn.wms.product.entity.Product;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.CategoryRepository;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UnitConversionRepository;
import vn.wms.product.repository.UomRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API nhập hàng hóa hàng loạt {@code POST /api/products/bulk} (WMS-22.4b, tiêu chí nghiệm thu 2 đến 4; tiêu chí giới
 * hạn gói nằm ở {@link ProductPlanLimitIntegrationTest}).
 *
 * <p>Không đặt {@code @Transactional}: mỗi dòng được lưu trong transaction riêng và commit ngay, nên test tự dọn dữ
 * liệu của hai doanh nghiệp dùng trong lớp.
 */
@SpringBootTest(properties = "app.tenant.default-company-id=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductBulkImportIntegrationTest {

    private static final Long COMPANY = 21L;
    private static final Long OTHER_COMPANY = 22L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UomRepository uomRepository;

    @Autowired
    private UnitConversionRepository unitConversionRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        TenantContext.clear();
    }

    private void cleanUp() {
        for (Long companyId : List.of(COMPANY, OTHER_COMPANY)) {
            TenantContext.runAs(companyId, () -> {
                unitConversionRepository.deleteAll();
                productRepository.deleteAll();
                categoryRepository.deleteAll();
                uomRepository.deleteAll();
            });
        }
    }

    /** Dòng hợp lệ thứ i; tên danh mục, đơn vị tính viết khác hoa thường và có khoảng trắng để kiểm tra dùng lại. */
    private static Map<String, Object> validRow(int i) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sku", String.format("BULK-%03d", i));
        row.put("name", "Hàng nhập " + i);
        row.put("barcode", String.format("89300000%05d", i));
        row.put("description", "Mô tả " + i);
        row.put("categoryName", i % 2 == 0 ? "Đồ uống" : "  ĐỒ UỐNG ");
        row.put("uomName", i % 3 == 0 ? " cái " : "Cái");
        row.put("purchasePrice", 10000 + i);
        row.put("sellingPrice", 15000 + i);
        row.put("vatRate", 8);
        row.put("safetyStock", 5);
        row.put("reorderPoint", 10);
        row.put("maxStock", 100);
        return row;
    }

    private static List<Map<String, Object>> validRows(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(validRow(i));
        }
        return rows;
    }

    private ResultActions postBulk(Long companyId, Object body) throws Exception {
        return mockMvc.perform(post("/api/products/bulk")
                .header("X-Company-Id", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode bulk(Long companyId, List<?> items) throws Exception {
        String body = postBulk(companyId, Map.of("items", items))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private static JsonNode row(JsonNode data, int row) {
        JsonNode result = data.path("results").get(row - 1);
        assertThat(result.path("row").asInt()).isEqualTo(row);
        return result;
    }

    private long countProducts(Long companyId) {
        return TenantContext.callAs(companyId, () -> productRepository.count());
    }

    @Test
    @DisplayName("Tiêu chí 2: nhập 50 hàng hóa hợp lệ thì created = 50 và xem được trong danh mục")
    void fiftyValidRowsAreAllCreated() throws Exception {
        JsonNode data = bulk(COMPANY, validRows(50));

        assertThat(data.path("total").asInt()).isEqualTo(50);
        assertThat(data.path("created").asInt()).isEqualTo(50);
        assertThat(data.path("skipped").asInt()).isZero();
        assertThat(data.path("failed").asInt()).isZero();
        for (int i = 1; i <= 50; i++) {
            JsonNode result = row(data, i);
            assertThat(result.path("status").asText()).isEqualTo("CREATED");
            assertThat(result.path("id").isNumber()).isTrue();
            assertThat(result.path("code").asText()).isEqualTo(String.format("BULK-%03d", i));
            assertThat(result.path("message").isNull()).isTrue();
        }

        mockMvc.perform(get("/api/products").param("size", "100").header("X-Company-Id", COMPANY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(50)));

        // Danh mục và đơn vị tính chưa có được tạo một lần rồi dùng lại (không phân biệt hoa thường, khoảng trắng)
        List<Category> categories = TenantContext.callAs(COMPANY, () -> categoryRepository.findAll());
        List<Uom> uoms = TenantContext.callAs(COMPANY, () -> uomRepository.findAll());
        assertThat(categories).singleElement().satisfies(category -> {
            assertThat(category.getName()).isIn("Đồ uống", "ĐỒ UỐNG");
            assertThat(category.getCode()).isEqualTo("DO-UONG");
        });
        assertThat(uoms).singleElement().satisfies(uom -> assertThat(uom.getCode()).isEqualTo("CAI"));

        // Các trường của dòng được ghi đúng như API tạo một sản phẩm
        long id7 = row(data, 7).path("id").asLong();
        mockMvc.perform(get("/api/products/" + id7).header("X-Company-Id", COMPANY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku", is("BULK-007")))
                .andExpect(jsonPath("$.data.barcode", is("8930000000007")))
                .andExpect(jsonPath("$.data.name", is("Hàng nhập 7")))
                .andExpect(jsonPath("$.data.categoryId", is(categories.get(0).getId().intValue())))
                .andExpect(jsonPath("$.data.uomId", is(uoms.get(0).getId().intValue())))
                .andExpect(jsonPath("$.data.purchasePrice", is(10007.0)))
                .andExpect(jsonPath("$.data.sellingPrice", is(15007.0)))
                .andExpect(jsonPath("$.data.vatRate", is(8.0)))
                .andExpect(jsonPath("$.data.maxStock", is(100.0)))
                .andExpect(jsonPath("$.data.active", is(true)));
    }

    @Test
    @DisplayName("Tiêu chí 3: 3 dòng lỗi (thiếu tên, SKU đã có, SKU lặp trong file) thì 47 dòng còn lại vẫn vào")
    void threeBadRowsDoNotStopTheOtherFortySeven() throws Exception {
        Long existingId = TenantContext.callAs(COMPANY, () -> {
            Uom uom = new Uom();
            uom.setCode("CAI");
            uom.setName("Cái");
            uom = uomRepository.save(uom);
            Product product = new Product();
            product.setSku("BULK-020");
            product.setName("Hàng đã có từ trước");
            product.setUom(uom);
            return productRepository.save(product).getId();
        });

        List<Map<String, Object>> rows = validRows(50);
        rows.get(9).remove("name");                  // dòng 10: thiếu tên
        rows.get(29).put("sku", "bulk-005 ");        // dòng 30: trùng SKU của dòng 5 sau khi chuẩn hóa
        // dòng 20 giữ SKU BULK-020, đã có trong doanh nghiệp

        JsonNode data = bulk(COMPANY, rows);

        assertThat(data.path("total").asInt()).isEqualTo(50);
        assertThat(data.path("created").asInt()).isEqualTo(47);
        assertThat(data.path("skipped").asInt()).isEqualTo(1);
        assertThat(data.path("failed").asInt()).isEqualTo(2);

        JsonNode missingName = row(data, 10);
        assertThat(missingName.path("status").asText()).isEqualTo("FAILED");
        assertThat(missingName.path("code").asText()).isEqualTo("BULK-010");
        assertThat(missingName.path("id").isNull()).isTrue();
        assertThat(missingName.path("message").asText()).isEqualTo("Tên sản phẩm không được để trống");

        JsonNode existing = row(data, 20);
        assertThat(existing.path("status").asText()).isEqualTo("SKIPPED");
        assertThat(existing.path("id").asLong()).isEqualTo(existingId);
        assertThat(existing.path("code").asText()).isEqualTo("BULK-020");
        assertThat(existing.path("message").asText()).isEqualTo("Mã SKU 'BULK-020' đã tồn tại, bỏ qua không ghi đè");

        JsonNode repeated = row(data, 30);
        assertThat(repeated.path("status").asText()).isEqualTo("FAILED");
        assertThat(repeated.path("code").asText()).isEqualTo("BULK-005");
        assertThat(repeated.path("message").asText())
                .isEqualTo("Mã SKU 'BULK-005' bị lặp lại trong file (đã có ở dòng 5)");
        assertThat(row(data, 5).path("status").asText()).isEqualTo("CREATED");

        // 47 hàng mới + 1 hàng đã có; hàng đã có không bị ghi đè
        assertThat(countProducts(COMPANY)).isEqualTo(48);
        Product kept = TenantContext.callAs(COMPANY, () -> productRepository.findById(existingId).orElseThrow());
        assertThat(kept.getName()).isEqualTo("Hàng đã có từ trước");
        assertThat(kept.getBarcode()).isNull();
        // Đơn vị tính "Cái" đã có được dùng lại, không tạo thêm
        assertThat(TenantContext.callAs(COMPANY, () -> uomRepository.count())).isEqualTo(1);
    }

    @Test
    @DisplayName("Tiêu chí 4: nhập lại đúng file đó thì tất cả SKIPPED, không sinh bản ghi trùng")
    void importingTheSameFileAgainSkipsEveryRow() throws Exception {
        List<Map<String, Object>> rows = validRows(50);
        JsonNode first = bulk(COMPANY, rows);
        assertThat(first.path("created").asInt()).isEqualTo(50);

        JsonNode second = bulk(COMPANY, rows);

        assertThat(second.path("total").asInt()).isEqualTo(50);
        assertThat(second.path("created").asInt()).isZero();
        assertThat(second.path("skipped").asInt()).isEqualTo(50);
        assertThat(second.path("failed").asInt()).isZero();
        for (int i = 1; i <= 50; i++) {
            assertThat(row(second, i).path("status").asText()).isEqualTo("SKIPPED");
            assertThat(row(second, i).path("id").asLong()).isEqualTo(row(first, i).path("id").asLong());
        }
        assertThat(countProducts(COMPANY)).isEqualTo(50);
        assertThat(TenantContext.callAs(COMPANY, () -> categoryRepository.count())).isEqualTo(1);
        assertThat(TenantContext.callAs(COMPANY, () -> uomRepository.count())).isEqualTo(1);
    }

    @Test
    @DisplayName("Mỗi dòng kiểm tra như API tạo một sản phẩm; dòng lỗi rollback cả danh mục vừa tự tạo cho nó")
    void eachRowIsValidatedLikeSingleCreateAndRolledBackAlone() throws Exception {
        List<Object> rows = new ArrayList<>();
        rows.add(validRow(1));                                            // 1: hợp lệ
        Map<String, Object> badSku = validRow(2);
        badSku.put("sku", "SKU 02");
        rows.add(badSku);                                                 // 2: SKU sai định dạng
        Map<String, Object> negativePrice = validRow(3);
        negativePrice.put("purchasePrice", -1);
        rows.add(negativePrice);                                          // 3: giá âm
        Map<String, Object> noUom = validRow(4);
        noUom.put("uomName", "   ");
        rows.add(noUom);                                                  // 4: thiếu đơn vị tính
        Map<String, Object> duplicateBarcode = validRow(5);
        duplicateBarcode.put("barcode", "8930000000001");
        duplicateBarcode.put("categoryName", "Danh mục chỉ có ở dòng lỗi");
        rows.add(duplicateBarcode);                                       // 5: trùng mã vạch của dòng 1
        rows.add(null);                                                   // 6: dòng trống
        Map<String, Object> noSku = validRow(7);
        noSku.remove("sku");
        noSku.remove("name");
        rows.add(noSku);                                                  // 7: thiếu SKU và tên
        Map<String, Object> lowerCase = validRow(8);
        lowerCase.put("sku", "  bulk-lower ");
        lowerCase.put("uomName", "Thùng");
        rows.add(lowerCase);                                              // 8: hợp lệ sau khi chuẩn hóa

        JsonNode data = bulk(COMPANY, rows);

        assertThat(data.path("created").asInt()).isEqualTo(2);
        assertThat(data.path("failed").asInt()).isEqualTo(6);
        assertThat(row(data, 2).path("message").asText())
                .isEqualTo("Mã SKU chỉ gồm chữ cái, số, dấu gạch ngang và gạch dưới");
        assertThat(row(data, 3).path("message").asText()).isEqualTo("Giá nhập không được âm");
        assertThat(row(data, 4).path("message").asText()).isEqualTo("Đơn vị tính không được để trống");
        assertThat(row(data, 5).path("message").asText()).isEqualTo("Mã vạch '8930000000001' đã tồn tại");
        assertThat(row(data, 6).path("message").asText()).isEqualTo("Dòng trống, không có dữ liệu");
        assertThat(row(data, 7).path("code").isNull()).isTrue();
        assertThat(row(data, 7).path("message").asText())
                .isEqualTo("Mã SKU không được để trống; Tên sản phẩm không được để trống");
        assertThat(row(data, 8).path("status").asText()).isEqualTo("CREATED");
        assertThat(row(data, 8).path("code").asText()).isEqualTo("BULK-LOWER");

        // Danh mục tự tạo cho dòng 5 bị rollback cùng dòng đó; dòng 8 tạo đơn vị tính "Thùng" mã THUNG
        List<String> categoryNames = TenantContext.callAs(COMPANY,
                () -> categoryRepository.findAll().stream().map(Category::getName).toList());
        assertThat(categoryNames).doesNotContain("Danh mục chỉ có ở dòng lỗi");
        assertThat(TenantContext.callAs(COMPANY, () -> uomRepository.findFirstByNameIgnoreCaseOrderByIdAsc("thùng")))
                .get().extracting(Uom::getCode).isEqualTo("THUNG");
        assertThat(countProducts(COMPANY)).isEqualTo(2);
    }

    @Test
    @DisplayName("Body trống, danh sách rỗng hoặc quá 500 dòng trả 400 VALIDATION_ERROR")
    void invalidBodyReturnsValidationError() throws Exception {
        postBulk(COMPANY, Map.of("items", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
        postBulk(COMPANY, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
        mockMvc.perform(post("/api/products/bulk").header("X-Company-Id", COMPANY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
        postBulk(COMPANY, Map.of("items", validRows(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));

        assertThat(countProducts(COMPANY)).isZero();
    }

    @Test
    @DisplayName("Doanh nghiệp mới bắt đầu với danh mục rỗng; cùng file nhập cho hai doanh nghiệp đều được tạo")
    void eachCompanyImportsIntoItsOwnCatalogue() throws Exception {
        mockMvc.perform(get("/api/products/uoms").header("X-Company-Id", OTHER_COMPANY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/products/categories").header("X-Company-Id", OTHER_COMPANY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        List<Map<String, Object>> rows = validRows(5);
        assertThat(bulk(COMPANY, rows).path("created").asInt()).isEqualTo(5);
        JsonNode other = bulk(OTHER_COMPANY, rows);

        assertThat(other.path("created").asInt()).isEqualTo(5);
        assertThat(other.path("skipped").asInt()).isZero();
        assertThat(countProducts(COMPANY)).isEqualTo(5);
        assertThat(countProducts(OTHER_COMPANY)).isEqualTo(5);
        assertThat(TenantContext.callAs(OTHER_COMPANY, () -> uomRepository.findAll()))
                .singleElement().extracting(Uom::getCompanyId).isEqualTo(OTHER_COMPANY);
        assertThat(TenantContext.callAs(OTHER_COMPANY, () -> productRepository.findBySku("BULK-001")))
                .get().extracting(Product::getPurchasePrice)
                .satisfies(price -> assertThat(price).isEqualByComparingTo(new BigDecimal("10001")));
    }
}
