package vn.wms.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.product.dto.CalculateConversionRequest;
import vn.wms.product.dto.ConvertToBaseRequest;
import vn.wms.product.dto.CreateUomRequest;
import vn.wms.product.dto.CreateUnitConversionRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UnitConversionIntegrationTest {

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

    private Uom uomChai;
    private Uom uomLoc;
    private Uom uomThung;
    private Product productTiger;

    @BeforeEach
    void setUp() {
        uomChai = createUomIfAbsent("CHAI", "Chai");
        uomLoc = createUomIfAbsent("LOC", "Lốc");
        uomThung = createUomIfAbsent("THUNG", "Thùng");

        Category cat = new Category();
        cat.setCode("BEV");
        cat.setName("Đồ uống");
        cat = categoryRepository.save(cat);

        // Sản phẩm Tiger có đơn vị cơ sở là CHAI
        productTiger = new Product();
        productTiger.setSku("BEER-TIGER-330");
        productTiger.setName("Bia Tiger 330ml");
        productTiger.setCategory(cat);
        productTiger.setUom(uomChai);
        productTiger = productRepository.save(productTiger);
    }

    private Uom createUomIfAbsent(String code, String name) {
        return uomRepository.findByCode(code).orElseGet(() -> {
            Uom u = new Uom();
            u.setCode(code);
            u.setName(name);
            return uomRepository.save(u);
        });
    }

    @Test
    void createAndGetUnitConversionsWithMultiLevelFactorToBase() throws Exception {
        // 1. Cấu hình: 1 Lốc = 6 Chai
        CreateUnitConversionRequest req1 = new CreateUnitConversionRequest(
                uomLoc.getId(),
                uomChai.getId(),
                BigDecimal.valueOf(6.0),
                "893000000001",
                "1 Lốc = 6 Chai"
        );

        mockMvc.perform(post("/api/products/" + productTiger.getId() + "/conversions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.conversionFactor").value(6.0))
                .andExpect(jsonPath("$.data.factorToBase").value(6.0));

        // 2. Cấu hình đa cấp: 1 Thùng = 4 Lốc
        CreateUnitConversionRequest req2 = new CreateUnitConversionRequest(
                uomThung.getId(),
                uomLoc.getId(),
                BigDecimal.valueOf(4.0),
                "893000000002",
                "1 Thùng = 4 Lốc"
        );

        mockMvc.perform(post("/api/products/" + productTiger.getId() + "/conversions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversionFactor").value(4.0))
                // Tự động tính tỷ lệ tích lũy về đơn vị cơ sở (Chai): 4 * 6 = 24 Chai
                .andExpect(jsonPath("$.data.factorToBase").value(24.0));

        // 3. Lấy danh sách quy đổi
        mockMvc.perform(get("/api/products/" + productTiger.getId() + "/conversions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void convertToBaseAutomaticallyForInventoryCalculation() throws Exception {
        // Thiết lập: 1 Thùng = 4 Lốc, 1 Lốc = 6 Chai (-> 1 Thùng = 24 Chai)
        setupMultiLevelConversions();

        // Kiểm tra 1: Nhập 5 Thùng -> quy đổi về 120 Chai cơ sở
        ConvertToBaseRequest reqThung = new ConvertToBaseRequest(uomThung.getId(), BigDecimal.valueOf(5));
        mockMvc.perform(post("/api/products/" + productTiger.getId() + "/conversions/convert-to-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqThung)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputQuantity").value(5))
                .andExpect(jsonPath("$.data.baseQuantity").value(120.0))
                .andExpect(jsonPath("$.data.conversionRate").value(24.0))
                .andExpect(jsonPath("$.data.baseUomCode").value("CHAI"));

        // Kiểm tra 2: Nhập 3 Lốc -> quy đổi về 18 Chai cơ sở
        ConvertToBaseRequest reqLoc = new ConvertToBaseRequest(uomLoc.getId(), BigDecimal.valueOf(3));
        mockMvc.perform(post("/api/products/" + productTiger.getId() + "/conversions/convert-to-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLoc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseQuantity").value(18.0))
                .andExpect(jsonPath("$.data.conversionRate").value(6.0));

        // Kiểm tra 3: Nhập chính đơn vị cơ sở (Chai) -> tỷ lệ 1.0
        ConvertToBaseRequest reqChai = new ConvertToBaseRequest(uomChai.getId(), BigDecimal.valueOf(10));
        mockMvc.perform(post("/api/products/" + productTiger.getId() + "/conversions/convert-to-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqChai)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseQuantity").value(10.0))
                .andExpect(jsonPath("$.data.conversionRate").value(1.0));
    }

    @Test
    void calculateArbitraryConversionPairs() throws Exception {
        setupMultiLevelConversions();

        // Quy đổi xuôi: 2 Thùng -> Lốc (2 * 4 = 8 Lốc)
        CalculateConversionRequest req1 = new CalculateConversionRequest(uomThung.getId(), uomLoc.getId(), BigDecimal.valueOf(2));
        mockMvc.perform(post("/api/products/" + productTiger.getId() + "/conversions/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.convertedQuantity").value(8.0))
                .andExpect(jsonPath("$.data.conversionRate").value(4.0));

        // Quy đổi ngược: 12 Chai -> Lốc (12 / 6 = 2 Lốc)
        CalculateConversionRequest req2 = new CalculateConversionRequest(uomChai.getId(), uomLoc.getId(), BigDecimal.valueOf(12));
        mockMvc.perform(post("/api/products/" + productTiger.getId() + "/conversions/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.convertedQuantity").value(2.0));
    }

    @Test
    void breakdownInventoryIntoPackagingLevels() throws Exception {
        setupMultiLevelConversions();

        // 50 Chai = 2 Thùng (48 chai) + 0 Lốc + 2 Chai
        mockMvc.perform(get("/api/products/" + productTiger.getId() + "/conversions/breakdown")
                        .param("baseQuantity", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBaseQuantity").value(50.0))
                .andExpect(jsonPath("$.data.breakdown[0].uomName").value("Thùng"))
                .andExpect(jsonPath("$.data.breakdown[0].quantity").value(2))
                .andExpect(jsonPath("$.data.breakdown[1].uomName").value("Chai"))
                .andExpect(jsonPath("$.data.breakdown[1].quantity").value(2))
                .andExpect(jsonPath("$.data.summary").value("2 Thùng, 2 Chai"));
    }

    @Test
    void createDuplicateConversionReturnsConflict() throws Exception {
        setupMultiLevelConversions();

        CreateUnitConversionRequest dup = new CreateUnitConversionRequest(
                uomThung.getId(),
                uomLoc.getId(),
                BigDecimal.valueOf(4.0),
                null,
                null
        );

        mockMvc.perform(post("/api/products/" + productTiger.getId() + "/conversions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void updateAndDeleteUnitConversion() throws Exception {
        setupMultiLevelConversions();
        UnitConversion existing = unitConversionRepository.findByProductId(productTiger.getId()).get(0);

        UpdateUnitConversionRequest updateReq = new UpdateUnitConversionRequest(
                existing.getFromUom().getId(),
                existing.getToUom().getId(),
                BigDecimal.valueOf(5.0),
                "BARCODE-UPDATED",
                "Đã sửa hệ số"
        );

        mockMvc.perform(put("/api/products/" + productTiger.getId() + "/conversions/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversionFactor").value(5.0));

        mockMvc.perform(delete("/api/products/" + productTiger.getId() + "/conversions/" + existing.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void uomCrudEndpoints() throws Exception {
        CreateUomRequest newUom = new CreateUomRequest("TUBE", "Tuýp");

        mockMvc.perform(post("/api/uoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUom)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("TUBE"))
                .andExpect(jsonPath("$.data.name").value("Tuýp"));

        mockMvc.perform(get("/api/uoms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    private void setupMultiLevelConversions() {
        // 1 Lốc = 6 Chai
        UnitConversion uc1 = new UnitConversion();
        uc1.setProduct(productTiger);
        uc1.setFromUom(uomLoc);
        uc1.setToUom(uomChai);
        uc1.setConversionFactor(BigDecimal.valueOf(6.0));
        unitConversionRepository.save(uc1);

        // 1 Thùng = 4 Lốc
        UnitConversion uc2 = new UnitConversion();
        uc2.setProduct(productTiger);
        uc2.setFromUom(uomThung);
        uc2.setToUom(uomLoc);
        uc2.setConversionFactor(BigDecimal.valueOf(4.0));
        unitConversionRepository.save(uc2);
    }
}
