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
import vn.wms.product.dto.CreateProductRequest;
import vn.wms.product.dto.UpdateProductRequest;
import vn.wms.product.entity.Category;
import vn.wms.product.entity.Product;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.CategoryRepository;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UomRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khởi động context thật với H2; mỗi test chạy trong transaction và tự rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductServiceApplicationTest {

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

    private Uom defaultUom;
    private Category defaultCategory;

    @BeforeEach
    void setUp() {
        defaultUom = new Uom();
        defaultUom.setCode("PCS");
        defaultUom.setName("Cái");
        defaultUom = uomRepository.save(defaultUom);

        defaultCategory = new Category();
        defaultCategory.setCode("ELEC");
        defaultCategory.setName("Điện tử");
        defaultCategory = categoryRepository.save(defaultCategory);
    }

    @Test
    void listProductsReturnsEmptyPageWhenNoData() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void listProductsMapsEntityWithCategoryAndUom() throws Exception {
        Product product = new Product();
        product.setSku("SKU-001");
        product.setBarcode("8930000000001");
        product.setName("Cáp USB-C 1m");
        product.setUom(defaultUom);
        product.setCategory(defaultCategory);
        product.setPurchasePrice(BigDecimal.valueOf(50000));
        product.setSellingPrice(BigDecimal.valueOf(80000));
        product.setVatRate(BigDecimal.valueOf(10));
        product.setManageByLot(true);
        product.setManageByExpiration(false);
        product.setSafetyStock(BigDecimal.valueOf(20));
        product.setReorderPoint(BigDecimal.valueOf(50));
        product.setMaxStock(BigDecimal.valueOf(500));
        productRepository.save(product);

        // Cột audit do AuditableEntity tự ghi
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
        assertThat(product.getCreatedBy()).isEqualTo("system");
        assertThat(product.getUpdatedBy()).isEqualTo("system");

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.data.content[0].uomCode").value("PCS"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("Điện tử"))
                .andExpect(jsonPath("$.data.content[0].purchasePrice").value(50000.0))
                .andExpect(jsonPath("$.data.content[0].sellingPrice").value(80000.0))
                .andExpect(jsonPath("$.data.content[0].vatRate").value(10.0))
                .andExpect(jsonPath("$.data.content[0].manageByLot").value(true))
                .andExpect(jsonPath("$.data.content[0].manageByExpiration").value(false))
                .andExpect(jsonPath("$.data.content[0].safetyStock").value(20.0))
                .andExpect(jsonPath("$.data.content[0].reorderPoint").value(50.0))
                .andExpect(jsonPath("$.data.content[0].maxStock").value(500.0));
    }

    @Test
    void createProductSuccessfully() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                "SKU-GAO-ST25",
                "8938501234567",
                "Gạo thơm ST25 5kg",
                "Gạo túi chính hãng",
                defaultCategory.getId(),
                defaultUom.getId(),
                BigDecimal.valueOf(140000),
                BigDecimal.valueOf(175000),
                BigDecimal.valueOf(5.0),
                "https://image.example.com/st25.png",
                true,
                true,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(250),
                BigDecimal.valueOf(1000)
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sku").value("SKU-GAO-ST25"))
                .andExpect(jsonPath("$.data.name").value("Gạo thơm ST25 5kg"))
                .andExpect(jsonPath("$.data.purchasePrice").value(140000.0))
                .andExpect(jsonPath("$.data.sellingPrice").value(175000.0))
                .andExpect(jsonPath("$.data.vatRate").value(5.0))
                .andExpect(jsonPath("$.data.imageUrl").value("https://image.example.com/st25.png"))
                .andExpect(jsonPath("$.data.manageByLot").value(true))
                .andExpect(jsonPath("$.data.manageByExpiration").value(true))
                .andExpect(jsonPath("$.data.safetyStock").value(100.0))
                .andExpect(jsonPath("$.data.reorderPoint").value(250.0))
                .andExpect(jsonPath("$.data.maxStock").value(1000.0));
    }

    @Test
    void createProductConflictSkuReturns409() throws Exception {
        Product existing = new Product();
        existing.setSku("SKU-EXISTING");
        existing.setName("Sản phẩm đã có");
        existing.setUom(defaultUom);
        productRepository.save(existing);

        CreateProductRequest request = new CreateProductRequest(
                "SKU-EXISTING",
                null,
                "Sản phẩm mới trùng mã",
                null,
                null,
                defaultUom.getId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                false,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getProductByIdAndSku() throws Exception {
        Product product = new Product();
        product.setSku("SKU-FIND-01");
        product.setName("Sản phẩm tìm kiếm");
        product.setUom(defaultUom);
        Product saved = productRepository.save(product);

        // Tìm theo ID
        mockMvc.perform(get("/api/products/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("SKU-FIND-01"))
                .andExpect(jsonPath("$.data.name").value("Sản phẩm tìm kiếm"));

        // Tìm theo SKU
        mockMvc.perform(get("/api/products/sku/SKU-FIND-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                .andExpect(jsonPath("$.data.sku").value("SKU-FIND-01"));
    }

    @Test
    void updateProductSuccessfully() throws Exception {
        Product product = new Product();
        product.setSku("SKU-OLD");
        product.setName("Tên cũ");
        product.setUom(defaultUom);
        Product saved = productRepository.save(product);

        UpdateProductRequest updateRequest = new UpdateProductRequest(
                "SKU-NEW",
                "8939999999999",
                "Tên mới cập nhật",
                "Mô tả mới",
                defaultCategory.getId(),
                defaultUom.getId(),
                BigDecimal.valueOf(60000),
                BigDecimal.valueOf(95000),
                BigDecimal.valueOf(8.0),
                "https://image.example.com/new.png",
                true,
                false,
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(200),
                true
        );

        mockMvc.perform(put("/api/products/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("SKU-NEW"))
                .andExpect(jsonPath("$.data.name").value("Tên mới cập nhật"))
                .andExpect(jsonPath("$.data.sellingPrice").value(95000.0));
    }

    @Test
    void deleteProductSuccessfully() throws Exception {
        Product product = new Product();
        product.setSku("SKU-DEL");
        product.setName("Sản phẩm xóa");
        product.setUom(defaultUom);
        Product saved = productRepository.save(product);

        mockMvc.perform(delete("/api/products/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/products/" + saved.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void livenessProbeIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
