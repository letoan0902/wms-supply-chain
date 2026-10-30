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
import vn.wms.product.dto.BarcodeType;
import vn.wms.product.dto.BatchBarcodeRequest;
import vn.wms.product.entity.Product;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UomRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BarcodeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UomRepository uomRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        Uom uom = uomRepository.findByCode("PCS").orElseGet(() -> {
            Uom u = new Uom();
            u.setCode("PCS");
            u.setName("Cái");
            return uomRepository.save(u);
        });

        testProduct = new Product();
        testProduct.setSku("SKU-MILK-TH-1L");
        testProduct.setBarcode("8936036010012");
        testProduct.setName("Sữa tươi TH True Milk 1L");
        testProduct.setUom(uom);
        testProduct = productRepository.save(testProduct);
    }

    @Test
    void generateGenericCode128Barcode() throws Exception {
        mockMvc.perform(get("/api/products/barcodes/generate")
                        .param("text", "SKU-GAO-ST25")
                        .param("type", "CODE_128"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("SKU-GAO-ST25"))
                .andExpect(jsonPath("$.data.type").value("CODE_128"))
                .andExpect(jsonPath("$.data.base64Image").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
    }

    @Test
    void generateGenericQrCode() throws Exception {
        mockMvc.perform(get("/api/products/barcodes/generate")
                        .param("text", "https://wms.example.com/sku/12345")
                        .param("type", "QR_CODE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("QR_CODE"))
                .andExpect(jsonPath("$.data.base64Image").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
    }

    @Test
    void generateGenericEan13BarcodeWithAutoCheckDigit() throws Exception {
        // Truyền 12 chữ số -> hệ thống tự tính số kiểm tra số 13
        mockMvc.perform(get("/api/products/barcodes/generate")
                        .param("text", "893850123456")
                        .param("type", "EAN_13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("EAN_13"))
                .andExpect(jsonPath("$.data.content").value("8938501234568"));
    }

    @Test
    void generateBarcodeImageDirectlyReturnsPng() throws Exception {
        byte[] responseBytes = mockMvc.perform(get("/api/products/barcodes/generate/image")
                        .param("text", "PALLET-A01-02")
                        .param("type", "CODE_128"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(responseBytes).isNotEmpty();
        // Kiểm tra magic number của định dạng ảnh PNG: 0x89 'P' 'N' 'G'
        assertThat(responseBytes[0]).isEqualTo((byte) 0x89);
        assertThat(responseBytes[1]).isEqualTo((byte) 'P');
        assertThat(responseBytes[2]).isEqualTo((byte) 'N');
        assertThat(responseBytes[3]).isEqualTo((byte) 'G');
    }

    @Test
    void getProductBarcodeById() throws Exception {
        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/barcode")
                        .param("type", "CODE_128"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("SKU-MILK-TH-1L"))
                .andExpect(jsonPath("$.data.base64Image").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
    }

    @Test
    void generateProductBatchBarcodeLabel() throws Exception {
        BatchBarcodeRequest request = new BatchBarcodeRequest(
                "LOT-20261028-001",
                LocalDate.of(2027, 10, 28),
                BarcodeType.QR_CODE,
                250,
                250
        );

        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/barcode/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("SKU-MILK-TH-1L"))
                .andExpect(jsonPath("$.data.batchNumber").value("LOT-20261028-001"))
                .andExpect(jsonPath("$.data.encodedContent").value("SKU:SKU-MILK-TH-1L|LOT:LOT-20261028-001|EXP:2027-10-28"))
                .andExpect(jsonPath("$.data.base64Image").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
    }
}
