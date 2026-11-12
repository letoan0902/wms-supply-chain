package vn.wms.order;

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
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.dto.CreateCustomerRequest;
import vn.wms.order.dto.CreateSupplierRequest;
import vn.wms.order.dto.bulk.BulkCreateCustomerRequest;
import vn.wms.order.dto.bulk.BulkCreateSupplierRequest;
import vn.wms.order.dto.bulk.BulkImportResponse;
import vn.wms.order.dto.bulk.BulkRowResult;
import vn.wms.order.dto.bulk.BulkRowStatus;
import vn.wms.order.entity.CustomerType;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.CustomerTransactionRepository;
import vn.wms.order.repository.SupplierRepository;
import vn.wms.order.repository.SupplierTransactionRepository;
import vn.wms.order.service.PartnerBulkImportService;
import vn.wms.order.service.SupplierService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API nhập hàng loạt nhà cung cấp và khách hàng (WMS-22.4b, phần 2). Không đặt {@code @Transactional} vì mỗi dòng
 * được lưu trong transaction riêng và đã commit; dữ liệu được dọn ở {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerBulkImportIntegrationTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PartnerBulkImportService bulkImportService;
    @Autowired private SupplierService supplierService;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private SupplierTransactionRepository supplierTransactionRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerTransactionRepository customerTransactionRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        for (Long companyId : List.of(COMPANY_A, COMPANY_B)) {
            TenantContext.runAs(companyId, () -> {
                supplierTransactionRepository.deleteAll();
                customerTransactionRepository.deleteAll();
                supplierRepository.deleteAll();
                customerRepository.deleteAll();
            });
        }
    }

    /** 10 dòng hợp lệ và 3 dòng lỗi: thiếu tên (dòng 4), mã đã có trong doanh nghiệp (dòng 8), mã lặp trong file (dòng 12). */
    private List<CreateSupplierRequest> supplierFileWithErrors() {
        List<CreateSupplierRequest> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rows.add(supplier("ncc-" + i, "Nhà cung cấp " + i, "03000000" + String.format("%02d", i)));
        }
        rows.add(3, supplier("NCC-NONAME", "  ", null));
        rows.add(7, supplier("NCC-EXIST", "Tên mới không được ghi đè", null));
        rows.add(11, supplier(" NCC-2 ", "Lặp mã dòng 2", null));
        return rows;
    }

    @Test
    @DisplayName("Nhập nhà cung cấp: dòng hợp lệ vào hết, 3 dòng lỗi có đúng số dòng và lý do, mã đã có không bị ghi đè")
    void importSuppliersWithInvalidExistingAndRepeatedRows() {
        Long existingId = TenantContext.callAs(COMPANY_A,
                () -> supplierService.create(supplier("NCC-EXIST", "Nhà cung cấp đang có", null)).id());

        BulkImportResponse result = TenantContext.callAs(COMPANY_A,
                () -> bulkImportService.importSuppliers(supplierFileWithErrors()));

        assertThat(result.total()).isEqualTo(13);
        assertThat(result.created()).isEqualTo(10);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.results()).extracting(BulkRowResult::row)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 13).boxed().toList());

        BulkRowResult missingName = result.results().get(3);
        assertThat(missingName.row()).isEqualTo(4);
        assertThat(missingName.status()).isEqualTo(BulkRowStatus.FAILED);
        assertThat(missingName.code()).isEqualTo("NCC-NONAME");
        assertThat(missingName.id()).isNull();
        assertThat(missingName.message()).isEqualTo("Tên nhà cung cấp không được để trống");

        BulkRowResult existing = result.results().get(7);
        assertThat(existing.row()).isEqualTo(8);
        assertThat(existing.status()).isEqualTo(BulkRowStatus.SKIPPED);
        assertThat(existing.id()).isEqualTo(existingId);
        assertThat(existing.message()).contains("NCC-EXIST").contains("đã tồn tại");

        BulkRowResult repeated = result.results().get(11);
        assertThat(repeated.row()).isEqualTo(12);
        assertThat(repeated.status()).isEqualTo(BulkRowStatus.FAILED);
        assertThat(repeated.code()).isEqualTo("NCC-2");
        assertThat(repeated.message()).contains("trùng với dòng 2");

        BulkRowResult first = result.results().get(0);
        assertThat(first.status()).isEqualTo(BulkRowStatus.CREATED);
        assertThat(first.code()).isEqualTo("NCC-1");
        assertThat(first.id()).isNotNull();

        TenantContext.runAs(COMPANY_A, () -> {
            assertThat(supplierRepository.count()).isEqualTo(11);
            assertThat(supplierRepository.findById(existingId).orElseThrow().getName()).isEqualTo("Nhà cung cấp đang có");
            assertThat(supplierRepository.findByCode("NCC-2").orElseThrow().getName()).isEqualTo("Nhà cung cấp 2");
        });
        // Doanh nghiệp khác không bị ảnh hưởng
        TenantContext.runAs(COMPANY_B, () -> assertThat(supplierRepository.count()).isZero());
    }

    @Test
    @DisplayName("Nhập lại đúng file: mọi dòng hợp lệ thành SKIPPED, không sinh bản ghi trùng; file sạch nhập lại thì tất cả SKIPPED")
    void reimportingSameFileSkipsEverything() {
        TenantContext.runAs(COMPANY_A, () -> supplierService.create(supplier("NCC-EXIST", "Nhà cung cấp đang có", null)));
        TenantContext.runAs(COMPANY_A, () -> bulkImportService.importSuppliers(supplierFileWithErrors()));

        BulkImportResponse again = TenantContext.callAs(COMPANY_A,
                () -> bulkImportService.importSuppliers(supplierFileWithErrors()));
        assertThat(again.created()).isZero();
        assertThat(again.skipped()).isEqualTo(11);
        // Hai dòng sai dữ liệu vẫn báo lỗi như lần đầu
        assertThat(again.failed()).isEqualTo(2);
        assertThat(again.results().get(3).status()).isEqualTo(BulkRowStatus.FAILED);
        assertThat(again.results().get(11).status()).isEqualTo(BulkRowStatus.FAILED);
        TenantContext.runAs(COMPANY_A, () -> assertThat(supplierRepository.count()).isEqualTo(11));

        List<CreateCustomerRequest> cleanFile = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> customer("KH-" + i, "Khách hàng " + i, CustomerType.RETAIL, null, null))
                .toList();
        BulkImportResponse first = TenantContext.callAs(COMPANY_A, () -> bulkImportService.importCustomers(cleanFile));
        assertThat(first.created()).isEqualTo(5);
        BulkImportResponse second = TenantContext.callAs(COMPANY_A, () -> bulkImportService.importCustomers(cleanFile));
        assertThat(second.total()).isEqualTo(5);
        assertThat(second.skipped()).isEqualTo(5);
        assertThat(second.created()).isZero();
        assertThat(second.failed()).isZero();
        assertThat(second.results()).extracting(BulkRowResult::id)
                .containsExactlyElementsOf(first.results().stream().map(BulkRowResult::id).toList());
        TenantContext.runAs(COMPANY_A, () -> assertThat(customerRepository.count()).isEqualTo(5));
    }

    @Test
    @DisplayName("Mỗi dòng kiểm tra như tạo đơn lẻ: trùng mã số thuế với dòng trước thì FAILED, công nợ ban đầu được ghi giao dịch")
    void eachRowFollowsSingleCreateRules() {
        BulkImportResponse result = TenantContext.callAs(COMPANY_A, () -> bulkImportService.importCustomers(List.of(
                customer("KH-TAX-1", "Khách 1", CustomerType.WHOLESALE, "0311111111", new BigDecimal("2000000")),
                customer("KH-TAX-2", "Khách 2 trùng MST", CustomerType.RETAIL, "0311111111", null),
                customer("KH-TAX-3", "Khách 3", null, null, null),
                new CreateCustomerRequest("KH-TAX-4", "Khách 4", CustomerType.RETAIL, null, null, "sai-email",
                        null, null, null, new BigDecimal("-1"), null, null))));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(3);
        assertThat(result.results().get(1).message()).contains("Mã số thuế '0311111111'");
        assertThat(result.results().get(2).message()).isEqualTo("Loại khách hàng không được để trống");
        assertThat(result.results().get(3).message())
                .contains("Hạn mức nợ không được âm").contains("Email không hợp lệ");

        TenantContext.runAs(COMPANY_A, () -> {
            var created = customerRepository.findByCode("KH-TAX-1").orElseThrow();
            assertThat(created.getCurrentDebt()).isEqualByComparingTo("2000000");
            assertThat(customerTransactionRepository.findAll()).hasSize(1);
        });
        // Cùng mã, cùng mã số thuế ở doanh nghiệp B vẫn tạo được
        BulkImportResponse ofB = TenantContext.callAs(COMPANY_B, () -> bulkImportService.importCustomers(List.of(
                customer("KH-TAX-1", "Khách của B", CustomerType.RETAIL, "0311111111", null))));
        assertThat(ofB.created()).isEqualTo(1);
    }

    @Test
    @DisplayName("API khách hàng: luôn 200 với total, created, skipped, failed và results theo đúng cấu trúc")
    void customerBulkEndpointReturnsResultShape() throws Exception {
        BulkCreateCustomerRequest body = new BulkCreateCustomerRequest(List.of(
                customer("kh-api-1", "Khách API 1", CustomerType.WHOLESALE, null, null),
                customer("KH-API-2", "", CustomerType.RETAIL, null, null)));

        mockMvc.perform(post("/api/orders/customers/bulk")
                        .header("X-Company-Id", COMPANY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.total", is(2)))
                .andExpect(jsonPath("$.data.created", is(1)))
                .andExpect(jsonPath("$.data.skipped", is(0)))
                .andExpect(jsonPath("$.data.failed", is(1)))
                .andExpect(jsonPath("$.data.results", hasSize(2)))
                .andExpect(jsonPath("$.data.results[0].row", is(1)))
                .andExpect(jsonPath("$.data.results[0].status", is("CREATED")))
                .andExpect(jsonPath("$.data.results[0].id", notNullValue()))
                .andExpect(jsonPath("$.data.results[0].code", is("KH-API-1")))
                .andExpect(jsonPath("$.data.results[0].message", is("Đã tạo mới")))
                .andExpect(jsonPath("$.data.results[1].row", is(2)))
                .andExpect(jsonPath("$.data.results[1].status", is("FAILED")))
                .andExpect(jsonPath("$.data.results[1].id", nullValue()))
                .andExpect(jsonPath("$.data.results[1].message", is("Tên khách hàng không được để trống")));

        TenantContext.runAs(COMPANY_B, () -> assertThat(customerRepository.findByCode("KH-API-1")).isPresent());
        TenantContext.runAs(COMPANY_A, () -> assertThat(customerRepository.findByCode("KH-API-1")).isEmpty());
    }

    @Test
    @DisplayName("API nhà cung cấp: body rỗng, danh sách rỗng hoặc quá 500 dòng trả 400 VALIDATION_ERROR")
    void supplierBulkEndpointRejectsEmptyAndOversizedBodies() throws Exception {
        mockMvc.perform(post("/api/orders/suppliers/bulk").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
        mockMvc.perform(post("/api/orders/suppliers/bulk").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
        mockMvc.perform(post("/api/orders/suppliers/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));

        List<CreateSupplierRequest> tooMany = IntStream.rangeClosed(1, 501)
                .mapToObj(i -> supplier("NCC-MAX-" + i, "Nhà cung cấp " + i, null)).toList();
        mockMvc.perform(post("/api/orders/suppliers/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkCreateSupplierRequest(tooMany))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.data.items", containsString("500")));
        TenantContext.runAs(COMPANY_A, () -> assertThat(supplierRepository.count()).isZero());

        // Đúng 500 dòng vẫn được nhận
        List<CreateSupplierRequest> exactly500 = new ArrayList<>(tooMany.subList(0, 500));
        Collections.reverse(exactly500);
        mockMvc.perform(post("/api/orders/suppliers/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkCreateSupplierRequest(exactly500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(500)))
                .andExpect(jsonPath("$.data.created", is(500)));
    }

    private static CreateSupplierRequest supplier(String code, String name, String taxCode) {
        return new CreateSupplierRequest(code, name, taxCode, null, null, null, null, null, null, null, null, null);
    }

    private static CreateCustomerRequest customer(String code, String name, CustomerType type, String taxCode,
                                                  BigDecimal initialDebt) {
        return new CreateCustomerRequest(code, name, type, taxCode, null, null, null, null, null, null,
                initialDebt, null);
    }
}
