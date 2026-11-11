package vn.wms.inventory.alert;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Cấu hình chung của các test cảnh báo tồn kho: H2, không broker (WmsEventPublisher giả lập), không product-service
 * (AlertProductClient giả lập trả danh mục theo doanh nghiệp trong TenantContext), ngày cố định. Hai doanh nghiệp dùng
 * ID riêng để không lẫn dữ liệu của các lớp test khác. Không đặt @Transactional: mỗi lời gọi mở phiên riêng như thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.alerts.product-cache-seconds=0")
abstract class StockAlertIntegrationTestBase {

    static final Long COMPANY_A = 1501L;
    static final Long COMPANY_B = 1502L;
    static final LocalDate TODAY = LocalDate.now(StockAlertTestSupport.ZONE);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WarehouseRepository warehouseRepository;

    @Autowired
    BinLocationRepository binLocationRepository;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    StockAlertRunRepository runRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    WmsEventPublisher eventPublisher;

    @MockBean
    AlertProductClient productClient;

    @MockBean
    StockAlertClock clock;

    @MockBean
    AlertRecipientClient recipientClient;

    StockAlertTestSupport data;

    /** Danh mục sản phẩm product-service "trả về" cho từng doanh nghiệp; doanh nghiệp không có thì danh mục rỗng. */
    final Map<Long, ApiResponse<PageResponse<AlertProductInfo>>> catalogs = new HashMap<>();

    /** Người nhận auth-service "trả về" theo (doanh nghiệp trong TenantContext, kho); không có thì danh sách rỗng. */
    final Map<String, List<AlertRecipient>> recipients = new HashMap<>();

    static String recipientKey(Long companyId, Long warehouseId) {
        return companyId + ":" + warehouseId;
    }

    @BeforeEach
    void setUpBase() {
        data = new StockAlertTestSupport(warehouseRepository, binLocationRepository, stockItemRepository,
                runRepository, jdbcTemplate);
        data.cleanUp(COMPANY_A, COMPANY_B);
        catalogs.clear();
        when(clock.today()).thenReturn(TODAY);
        when(clock.now()).thenReturn(Instant.now());
        when(productClient.listProducts(anyInt(), anyInt(), anyString())).thenAnswer(invocation ->
                catalogs.getOrDefault(TenantContext.getCompanyId(), StockAlertTestSupport.page()));
        recipients.clear();
        when(recipientClient.alertRecipients(anyLong())).thenAnswer(invocation -> ApiResponse.ok(recipients.getOrDefault(
                recipientKey(TenantContext.getCompanyId(), invocation.getArgument(0)), List.of())));
    }

    @AfterEach
    void tearDownBase() {
        data.cleanUp(COMPANY_A, COMPANY_B);
        TenantContext.clear();
    }
}
