package vn.wms.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.product.entity.Category;
import vn.wms.product.entity.Product;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.CategoryRepository;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UomRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UomRepository uomRepository;

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
        Uom uom = new Uom();
        uom.setCode("PCS");
        uom.setName("Cái");
        uomRepository.save(uom);

        Category category = new Category();
        category.setCode("ELEC");
        category.setName("Điện tử");
        categoryRepository.save(category);

        Product product = new Product();
        product.setSku("SKU-001");
        product.setBarcode("8930000000001");
        product.setName("Cáp USB-C 1m");
        product.setUom(uom);
        product.setCategory(category);
        productRepository.save(product);

        // Cột audit do AuditableEntity tự ghi: ngoài ngữ cảnh HTTP có người dùng thì ghi system
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
        assertThat(product.getCreatedBy()).isEqualTo("system");
        assertThat(product.getUpdatedBy()).isEqualTo("system");

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.data.content[0].uomCode").value("PCS"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("Điện tử"));
    }

    @Test
    void livenessProbeIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
