package vn.wms.product;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;
import vn.wms.product.dto.CreateCategoryRequest;
import vn.wms.product.dto.CreateProductRequest;
import vn.wms.product.dto.CreateUnitConversionRequest;
import vn.wms.product.dto.CreateUomRequest;
import vn.wms.product.dto.UpdateProductRequest;
import vn.wms.product.dto.UpdateUnitConversionRequest;
import vn.wms.product.entity.Category;
import vn.wms.product.entity.Product;
import vn.wms.product.entity.UnitConversion;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.CategoryRepository;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UnitConversionRepository;
import vn.wms.product.repository.UomRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cách ly danh mục hàng hóa giữa hai doanh nghiệp trong product-service (WMS-22.4b, TASK-137): hàng hóa, danh mục,
 * đơn vị tính, quy đổi đơn vị. Lớp test KHÔNG đặt {@code @Transactional} để mỗi lời gọi MockMvc hoặc repository mở
 * một phiên làm việc riêng như request thật. Doanh nghiệp mặc định của profile test được tắt để kiểm tra request
 * không có {@code X-Company-Id}.
 */
@SpringBootTest(properties = "app.tenant.default-company-id=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductTenantIsolationTest {

    private static final Long COMPANY_A = 11L;
    private static final Long COMPANY_B = 12L;

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

    private Fixture a;
    private Fixture b;

    /** Dữ liệu giống hệt nhau (cùng mã, cùng SKU, cùng mã vạch) tạo cho mỗi doanh nghiệp. */
    private record Fixture(Long categoryId, Long pcsId, Long boxId, Long productId, Long conversionId) {
    }

    @BeforeEach
    void setUp() {
        cleanUp();
        a = TenantContext.callAs(COMPANY_A, () -> createFixture("Doanh nghiệp A"));
        b = TenantContext.callAs(COMPANY_B, () -> createFixture("Doanh nghiệp B"));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        TenantContext.clear();
    }

    private void cleanUp() {
        for (Long companyId : List.of(COMPANY_A, COMPANY_B)) {
            TenantContext.runAs(companyId, () -> {
                unitConversionRepository.deleteAll();
                productRepository.deleteAll();
                categoryRepository.deleteAll();
                uomRepository.deleteAll();
            });
        }
    }

    private Fixture createFixture(String owner) {
        Category category = new Category();
        category.setCode("ELEC");
        category.setName("Điện tử");
        category = categoryRepository.save(category);

        Uom pcs = uomRepository.save(uom("PCS", "Cái"));
        Uom box = uomRepository.save(uom("BOX", "Hộp"));

        Product product = new Product();
        product.setSku("SKU-001");
        product.setBarcode("8930000000001");
        product.setName("Cáp USB-C của " + owner);
        product.setCategory(category);
        product.setUom(pcs);
        product = productRepository.save(product);

        UnitConversion conversion = new UnitConversion();
        conversion.setProduct(product);
        conversion.setFromUom(box);
        conversion.setToUom(pcs);
        conversion.setConversionFactor(new BigDecimal("10"));
        conversion.setDescription("1 Hộp = 10 Cái");
        conversion = unitConversionRepository.save(conversion);

        return new Fixture(category.getId(), pcs.getId(), box.getId(), product.getId(), conversion.getId());
    }

    private static Uom uom(String code, String name) {
        Uom uom = new Uom();
        uom.setCode(code);
        uom.setName(name);
        return uom;
    }

    private static MockHttpServletRequestBuilder as(Long companyId, MockHttpServletRequestBuilder builder) {
        return builder.header("X-Company-Id", companyId).contentType(MediaType.APPLICATION_JSON);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static CreateProductRequest productRequest(String sku, String barcode, Long uomId) {
        return new CreateProductRequest(sku, barcode, "Sản phẩm " + sku, null, null, uomId,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Hai doanh nghiệp dùng trùng SKU, mã vạch, mã danh mục và mã đơn vị tính; trong một doanh nghiệp thì không")
    void sameCodesAllowedAcrossCompaniesButNotWithinOne() throws Exception {
        Product productA = TenantContext.callAs(COMPANY_A, () -> productRepository.findById(a.productId()).orElseThrow());
        Product productB = TenantContext.callAs(COMPANY_B, () -> productRepository.findById(b.productId()).orElseThrow());
        assertThat(productA.getSku()).isEqualTo(productB.getSku()).isEqualTo("SKU-001");
        assertThat(productA.getBarcode()).isEqualTo(productB.getBarcode());
        assertThat(productA.getCompanyId()).isEqualTo(COMPANY_A);
        assertThat(productB.getCompanyId()).isEqualTo(COMPANY_B);

        // Qua API: cả hai doanh nghiệp tạo cùng SKU, mã vạch, danh mục, đơn vị tính mới
        for (Fixture fixture : List.of(a, b)) {
            Long company = fixture == a ? COMPANY_A : COMPANY_B;
            mockMvc.perform(as(company, post("/api/products"))
                            .content(json(productRequest("SKU-NEW", "8930000000099", fixture.pcsId()))))
                    .andExpect(status().isCreated());
            mockMvc.perform(as(company, post("/api/products/categories"))
                            .content(json(new CreateCategoryRequest("FOOD", "Thực phẩm", null))))
                    .andExpect(status().isCreated());
            mockMvc.perform(as(company, post("/api/products/uoms"))
                            .content(json(new CreateUomRequest("KG", "Kilogram"))))
                    .andExpect(status().isCreated());
        }

        // Trong cùng doanh nghiệp A: trùng SKU, trùng mã vạch, trùng mã danh mục, trùng mã đơn vị tính đều 409
        mockMvc.perform(as(COMPANY_A, post("/api/products"))
                        .content(json(productRequest("SKU-001", null, a.pcsId()))))
                .andExpect(status().isConflict());
        mockMvc.perform(as(COMPANY_A, post("/api/products"))
                        .content(json(productRequest("SKU-OTHER", "8930000000001", a.pcsId()))))
                .andExpect(status().isConflict());
        mockMvc.perform(as(COMPANY_A, post("/api/products/categories"))
                        .content(json(new CreateCategoryRequest("ELEC", "Trùng mã", null))))
                .andExpect(status().isConflict());
        mockMvc.perform(as(COMPANY_A, post("/api/products/uoms"))
                        .content(json(new CreateUomRequest("PCS", "Trùng mã"))))
                .andExpect(status().isConflict());

        // Nhiều hàng hóa không có mã vạch trong cùng doanh nghiệp vẫn được (barcode NULL không tính trùng)
        mockMvc.perform(as(COMPANY_A, post("/api/products"))
                        .content(json(productRequest("SKU-NO-BC-1", null, a.pcsId()))))
                .andExpect(status().isCreated());
        mockMvc.perform(as(COMPANY_A, post("/api/products"))
                        .content(json(productRequest("SKU-NO-BC-2", " ", a.pcsId()))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("B liệt kê hàng hóa chỉ thấy của B; xem theo ID của A nhận 404; tìm theo SKU trả hàng của B")
    void companyBCannotReadProductsOfA() throws Exception {
        mockMvc.perform(as(COMPANY_B, get("/api/products")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(b.productId().intValue())));

        mockMvc.perform(as(COMPANY_B, get("/api/products/" + a.productId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
        mockMvc.perform(as(COMPANY_B, get("/api/products/" + a.productId() + "/barcode")))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(COMPANY_B, get("/api/products/sku/SKU-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(b.productId().intValue())))
                .andExpect(jsonPath("$.data.name", is("Cáp USB-C của Doanh nghiệp B")));
    }

    @Test
    @DisplayName("B sửa hoặc xóa hàng hóa của A nhận 404, dữ liệu của A không đổi")
    void companyBCannotUpdateOrDeleteProductsOfA() throws Exception {
        UpdateProductRequest update = new UpdateProductRequest("SKU-001", null, "Bị B đổi tên", null, null,
                b.pcsId(), null, null, null, null, null, null, null, null, null, false);

        mockMvc.perform(as(COMPANY_B, put("/api/products/" + a.productId())).content(json(update)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(COMPANY_B, delete("/api/products/" + a.productId())))
                .andExpect(status().isNotFound());

        Product productA = TenantContext.callAs(COMPANY_A, () -> productRepository.findById(a.productId()).orElseThrow());
        assertThat(productA.getName()).isEqualTo("Cáp USB-C của Doanh nghiệp A");
        assertThat(productA.isActive()).isTrue();
    }

    @Test
    @DisplayName("B không dùng được danh mục, đơn vị tính của A để tạo hoặc sửa hàng hóa của mình")
    void companyBCannotReferenceCatalogueOfA() throws Exception {
        mockMvc.perform(as(COMPANY_B, post("/api/products"))
                        .content(json(productRequest("SKU-B-2", null, a.pcsId()))))
                .andExpect(status().isNotFound());

        UpdateProductRequest useCategoryOfA = new UpdateProductRequest("SKU-001", null, "Cáp", null, a.categoryId(),
                b.pcsId(), null, null, null, null, null, null, null, null, null, true);
        mockMvc.perform(as(COMPANY_B, put("/api/products/" + b.productId())).content(json(useCategoryOfA)))
                .andExpect(status().isNotFound());

        Product productB = TenantContext.callAs(COMPANY_B, () -> productRepository.findById(b.productId()).orElseThrow());
        assertThat(productB.getName()).isEqualTo("Cáp USB-C của Doanh nghiệp B");
        assertThat(TenantContext.callAs(COMPANY_B, () -> productRepository.count())).isEqualTo(1);
    }

    @Test
    @DisplayName("Danh mục và đơn vị tính: B chỉ thấy của B, xem theo ID của A nhận 404")
    void companyBCannotSeeCategoriesOrUnitsOfA() throws Exception {
        mockMvc.perform(as(COMPANY_B, get("/api/products/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(b.categoryId().intValue())));
        mockMvc.perform(as(COMPANY_B, get("/api/products/categories/" + a.categoryId())))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(COMPANY_B, get("/api/products/uoms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[?(@.id == " + a.pcsId() + ")]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.id == " + a.boxId() + ")]").doesNotExist());
        mockMvc.perform(as(COMPANY_B, get("/api/products/uoms/" + a.pcsId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Quy đổi đơn vị: B không xem, sửa, xóa được quy đổi của A và không dùng được đơn vị tính của A")
    void companyBCannotTouchUnitConversionsOfA() throws Exception {
        String conversionsOfA = "/api/products/" + a.productId() + "/conversions";
        mockMvc.perform(as(COMPANY_B, get(conversionsOfA)))
                .andExpect(status().isNotFound());

        UpdateUnitConversionRequest update = new UpdateUnitConversionRequest(b.boxId(), b.pcsId(),
                new BigDecimal("99"), null, "Bị B sửa");
        mockMvc.perform(as(COMPANY_B, put(conversionsOfA + "/" + a.conversionId())).content(json(update)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(COMPANY_B, delete(conversionsOfA + "/" + a.conversionId())))
                .andExpect(status().isNotFound());

        // B thêm quy đổi cho hàng của mình nhưng dùng đơn vị tính của A: 404
        CreateUnitConversionRequest withUnitsOfA = new CreateUnitConversionRequest(a.boxId(), b.pcsId(),
                new BigDecimal("12"), null, null);
        mockMvc.perform(as(COMPANY_B, post("/api/products/" + b.productId() + "/conversions"))
                        .content(json(withUnitsOfA)))
                .andExpect(status().isNotFound());

        // Quy đổi của A còn nguyên, B chỉ thấy quy đổi của mình
        mockMvc.perform(as(COMPANY_A, get(conversionsOfA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].conversionFactor", is(10.0)));
        mockMvc.perform(as(COMPANY_B, get("/api/products/" + b.productId() + "/conversions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(b.conversionId().intValue())));
    }

    @Test
    @DisplayName("Request không có doanh nghiệp nhận 403 COMPANY_REQUIRED")
    void requestWithoutCompanyIsRejected() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("COMPANY_REQUIRED")));
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("SKU-NONE", null, a.pcsId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("COMPANY_REQUIRED")));
        mockMvc.perform(post("/api/products/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"X\",\"name\":\"X\",\"uomName\":\"Cái\"}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("COMPANY_REQUIRED")));
    }

    @Test
    @DisplayName("Chưa xác định doanh nghiệp: truy vấn trả rỗng, lưu bị từ chối với COMPANY_REQUIRED")
    void withoutCompanyContextQueriesAreEmptyAndSavesFail() {
        TenantContext.clear();

        assertThat(productRepository.findAll()).isEmpty();
        assertThat(categoryRepository.findAll()).isEmpty();
        assertThat(uomRepository.findAll()).isEmpty();
        assertThat(unitConversionRepository.findAll()).isEmpty();
        assertThat(productRepository.findBySku("SKU-001")).isEmpty();

        assertThatThrownBy(() -> uomRepository.save(uom("ORPHAN", "Không doanh nghiệp")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMPANY_REQUIRED);
    }
}
