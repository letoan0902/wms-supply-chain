package vn.wms.common.tenant;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.tenant.sample.SampleItem;
import vn.wms.common.tenant.sample.SampleItemRepository;
import vn.wms.common.tenant.sample.TenantTestApplication;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra cách ly dữ liệu giữa hai doanh nghiệp trên JPA thật (H2): tiêu chí nghiệm thu của WMS-22.1.
 * Lớp test KHÔNG đặt @Transactional để mỗi lời gọi repository mở một phiên làm việc riêng, đúng như khi mỗi
 * request của một doanh nghiệp đi vào service.
 */
@SpringBootTest(classes = TenantTestApplication.class, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.datasource.url=jdbc:h2:mem:tenant-isolation;DB_CLOSE_DELAY=-1",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
class TenantIsolationTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;

    @Autowired
    private SampleItemRepository repository;

    @Autowired
    private MockMvc mockMvc;

    private Long itemOfA;
    private Long itemOfB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        itemOfA = TenantContext.callAs(COMPANY_A, () -> repository.save(new SampleItem("WH01", "Kho của A")).getId());
        TenantContext.runAs(COMPANY_A, () -> repository.save(new SampleItem("WH02", "Kho phụ của A")));
        itemOfB = TenantContext.callAs(COMPANY_B, () -> repository.save(new SampleItem("WH01", "Kho của B")).getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.runAs(COMPANY_A, repository::deleteEverything);
        TenantContext.runAs(COMPANY_B, repository::deleteEverything);
        TenantContext.clear();
    }

    @Test
    void newRowsGetCompanyOfCurrentContext() {
        assertThat(repository.companyIdOfNative(itemOfA)).isEqualTo(COMPANY_A);
        assertThat(repository.companyIdOfNative(itemOfB)).isEqualTo(COMPANY_B);
        SampleItem loaded = TenantContext.callAs(COMPANY_B, () -> repository.findById(itemOfB).orElseThrow());
        assertThat(loaded.getCompanyId()).isEqualTo(COMPANY_B);
    }

    @Test
    void listAndCountOnlyReturnRowsOfCurrentCompany() {
        List<String> codesOfA = TenantContext.callAs(COMPANY_A,
                () -> repository.findAll().stream().map(SampleItem::getCode).sorted().toList());
        List<String> namesOfB = TenantContext.callAs(COMPANY_B,
                () -> repository.findAll().stream().map(SampleItem::getName).toList());

        assertThat(codesOfA).containsExactly("WH01", "WH02");
        assertThat(namesOfB).containsExactly("Kho của B");
        long countOfA = TenantContext.callAs(COMPANY_A, repository::count);
        long countOfB = TenantContext.callAs(COMPANY_B, repository::count);
        assertThat(countOfA).isEqualTo(2);
        assertThat(countOfB).isEqualTo(1);
        // SQL gốc không được lọc: vẫn thấy đủ ba dòng của cả hai doanh nghiệp
        assertThat(repository.countAllNative()).isEqualTo(3);
    }

    @Test
    void loadingByIdARowOfAnotherCompanyIsNotFound() {
        // Hibernate không lọc theo doanh nghiệp khi tải theo khóa chính; TenantEntity chặn lại bằng lỗi NOT_FOUND
        assertNotFound(() -> TenantContext.runAs(COMPANY_A, () -> repository.findById(itemOfB)));
        assertNotFound(() -> TenantContext.runAs(COMPANY_B, () -> repository.findById(itemOfA)));
        boolean visibleToA = TenantContext.callAs(COMPANY_A, () -> repository.existsById(itemOfB));
        assertThat(visibleToA).isFalse();
        assertThat(TenantContext.callAs(COMPANY_A, () -> repository.findAllById(List.of(itemOfA, itemOfB))))
                .extracting(SampleItem::getId).containsExactly(itemOfA);
        assertThat(TenantContext.callAs(COMPANY_A, () -> repository.findById(itemOfA))).isPresent();
    }

    @Test
    void savingADetachedCopyOfAnotherCompanysRowIsRejected() {
        // Client của A gửi lên ID của B: merge phải thất bại, dữ liệu của B giữ nguyên
        SampleItem forged = new SampleItem("HACK", "Ghi đè từ A");
        forged.setId(itemOfB);
        assertNotFound(() -> TenantContext.runAs(COMPANY_A, () -> repository.saveAndFlush(forged)));
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.findById(itemOfB).orElseThrow().getName()))
                .isEqualTo("Kho của B");
        assertThat(repository.countAllNative()).isEqualTo(3);
    }

    @Test
    void derivedAndJpqlQueriesAreFiltered() {
        assertThat(TenantContext.callAs(COMPANY_A, () -> repository.findByCode("WH01")))
                .get().extracting(SampleItem::getName).isEqualTo("Kho của A");
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.findByCode("WH01")))
                .get().extracting(SampleItem::getName).isEqualTo("Kho của B");
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.findByCode("WH02"))).isEmpty();
        assertThat(TenantContext.callAs(COMPANY_A, () -> repository.searchByName("kho")))
                .extracting(SampleItem::getCode).containsExactly("WH01", "WH02");
    }

    @Test
    void sameCodeIsAllowedAcrossCompaniesButNotInsideOne() {
        // WH01 đã tồn tại ở cả A và B (setUp). Thêm WH01 lần nữa trong A phải vi phạm ràng buộc (company_id, code).
        assertThatThrownBy(() -> TenantContext.runAs(COMPANY_A,
                () -> repository.saveAndFlush(new SampleItem("WH01", "Trùng mã trong A"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bulkUpdateAndDeleteOnlyTouchCurrentCompany() {
        int updated = TenantContext.callAs(COMPANY_A, () -> repository.renameAll("Đã đổi tên"));
        assertThat(updated).isEqualTo(2);
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.findById(itemOfB).orElseThrow().getName()))
                .isEqualTo("Kho của B");

        int deleted = TenantContext.callAs(COMPANY_A, repository::deleteEverything);
        assertThat(deleted).isEqualTo(2);
        assertThat(repository.countAllNative()).isEqualTo(1);
    }

    @Test
    void deleteByIdOfAnotherCompanyIsNotFoundAndKeepsTheRow() {
        assertNotFound(() -> TenantContext.runAs(COMPANY_A, () -> repository.deleteById(itemOfB)));
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.findById(itemOfB))).isPresent();
        assertThat(repository.countAllNative()).isEqualTo(3);
    }

    @Test
    void withoutCompanyNothingIsVisibleAndNothingCanBeSaved() {
        assertThat(repository.findAll()).isEmpty();
        assertNotFound(() -> repository.findById(itemOfA));
        assertThatThrownBy(() -> repository.save(new SampleItem("WH09", "Không có doanh nghiệp")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMPANY_REQUIRED));
        assertThat(repository.countAllNative()).isEqualTo(3);
    }

    @Test
    void listenerSavesIntoCompanyCarriedByMessageHeader() {
        // Mô phỏng luồng consumer: post-processor chạy trước phương thức @RabbitListener
        MessageProperties properties = new MessageProperties();
        properties.setHeader(WmsEventPublisher.HEADER_COMPANY_ID, COMPANY_B);
        new TenantMessagePostProcessor()
                .postProcessMessage(new Message("{}".getBytes(StandardCharsets.UTF_8), properties));

        Long savedId = repository.save(new SampleItem("EVT01", "Tạo từ sự kiện của B")).getId();

        assertThat(repository.companyIdOfNative(savedId)).isEqualTo(COMPANY_B);
        assertThat(TenantContext.callAs(COMPANY_A, () -> repository.findByCode("EVT01"))).isEmpty();
        assertThat(TenantContext.callAs(COMPANY_B, () -> repository.findByCode("EVT01"))).isPresent();
    }

    @Test
    void httpRequestIsScopedByCompanyHeader() throws Exception {
        mockMvc.perform(get("/api/sample-items").header(TenantContext.HEADER_COMPANY_ID, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/sample-items").header(TenantContext.HEADER_COMPANY_ID, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        // ID của doanh nghiệp khác coi như không tồn tại
        mockMvc.perform(get("/api/sample-items/" + itemOfB).header(TenantContext.HEADER_COMPANY_ID, "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/sample-items/" + itemOfB).header(TenantContext.HEADER_COMPANY_ID, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("WH01"));
        // Mặc định app.tenant.required=false: thiếu header không bị chặn nhưng cũng không thấy dữ liệu nào
        mockMvc.perform(get("/api/sample-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        assertThat(TenantContext.getCompanyId()).as("ngữ cảnh phải được xóa sau request").isNull();
    }

    private static void assertNotFound(ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
