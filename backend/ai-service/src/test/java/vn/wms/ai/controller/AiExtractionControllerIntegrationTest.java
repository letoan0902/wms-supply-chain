package vn.wms.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.wms.ai.AiTestSupport;
import vn.wms.ai.dto.BulkImportResult;
import vn.wms.ai.extraction.BulkImportClient;
import vn.wms.ai.extraction.ExtractionDataType;
import vn.wms.ai.extraction.TestSpreadsheets;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API trích xuất chạy trọn luồng ở chế độ giả lập (không khóa, không gọi mạng): file Excel dựng bằng POI, ghép cột
 * theo tên tiêu đề, kết quả lưu trên Redis giả lập (bộ nhớ trong test), hạn mức qua PlanLimitChecker thật, API nhập
 * hàng loạt giả lập bằng {@code @MockBean BulkImportClient}.
 */
@SpringBootTest(properties = "app.plan-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiExtractionControllerIntegrationTest {

    private static final long COMPANY_A = 801L;
    private static final long COMPANY_B = 802L;
    private static final String AI_COUNTER_PREFIX = "wms:usage:ai:" + COMPANY_A + ":";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private BulkImportClient bulkImportClient;

    private final Map<String, String> redisData = new HashMap<>();
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries("wms:subscription:" + COMPANY_A))
                .thenReturn(Map.copyOf(AiTestSupport.subscription("FREE", "20")));
        doAnswer(invocation -> redisData.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redisData.get(invocation.<String>getArgument(0)));
        when(valueOperations.increment(startsWith(AI_COUNTER_PREFIX))).thenReturn(4L);
        // Khóa chống bấm nhập hai lần: Redis thật trả true khi đặt được khóa
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    private static MockMultipartFile xlsx(int rows) {
        return new MockMultipartFile("file", "hang-hoa.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                TestSpreadsheets.xlsx(TestSpreadsheets.productRows(rows)));
    }

    private ResultActions upload(MockMultipartFile file, String dataType, long companyId, String roles)
            throws Exception {
        return mockMvc.perform(multipart("/api/ai/extractions")
                .file(file)
                .param("dataType", dataType)
                .header(TenantContext.HEADER_COMPANY_ID, companyId)
                .header(UserContext.HEADER_USER_NAME, "nguoi_dung")
                .header(UserContext.HEADER_USER_ROLES, roles));
    }

    private String extractionId(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.path("data").path("extractionId").asText();
    }

    @Test
    void excel50Rows_extractsEveryCellCountsOneAiRequestAndStoresForOneHourPerCompany() throws Exception {
        ResultActions result = upload(xlsx(50), "PRODUCT", COMPANY_A, "PURCHASING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dataType").value("PRODUCT"))
                .andExpect(jsonPath("$.data.fileName").value("hang-hoa.xlsx"))
                .andExpect(jsonPath("$.data.fileType").value("XLSX"))
                .andExpect(jsonPath("$.data.method").value("HEADER_MATCHING"))
                .andExpect(jsonPath("$.data.totalRows").value(50))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.columns", hasSize(12)))
                .andExpect(jsonPath("$.data.columns[0].field").value("sku"))
                .andExpect(jsonPath("$.data.columns[0].sourceHeader").value("Mã hàng"))
                .andExpect(jsonPath("$.data.columns[0].sourceColumn").value(1))
                .andExpect(jsonPath("$.data.rows", hasSize(50)))
                .andExpect(jsonPath("$.data.rows[0].row").value(1))
                .andExpect(jsonPath("$.data.rows[0].sourceRow").value(2))
                .andExpect(jsonPath("$.data.rows[0].values.sku").value("SP001"))
                .andExpect(jsonPath("$.data.rows[0].values.name").value("Sản phẩm 1"))
                .andExpect(jsonPath("$.data.rows[0].values.uomName").value("Thùng"))
                .andExpect(jsonPath("$.data.rows[0].values.purchasePrice").value(150001))
                .andExpect(jsonPath("$.data.rows[0].values.sellingPrice").value(180000.5))
                .andExpect(jsonPath("$.data.rows[0].values.vatRate").value(10))
                .andExpect(jsonPath("$.data.rows[0].values.categoryName").value("Đồ uống"))
                .andExpect(jsonPath("$.data.rows[0].values.barcode").isEmpty())
                .andExpect(jsonPath("$.data.rows[0].confidence.sku").value(0.95))
                .andExpect(jsonPath("$.data.rows[0].warnings", hasSize(0)))
                .andExpect(jsonPath("$.data.rows[49].values.sku").value("SP050"))
                .andExpect(jsonPath("$.data.usage.used").value(4))
                .andExpect(jsonPath("$.data.usage.max").value(20));

        verify(valueOperations, times(1)).increment(startsWith(AI_COUNTER_PREFIX));
        String id = extractionId(result);
        verify(valueOperations).set(eq("wms:ai:extraction:" + COMPANY_A + ":" + id), anyString(),
                eq(Duration.ofHours(1)));

        // Xem lại được trong 1 giờ, không tính thêm lượt
        mockMvc.perform(get("/api/ai/extractions/" + id)
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extractionId").value(id))
                .andExpect(jsonPath("$.data.rows", hasSize(50)))
                .andExpect(jsonPath("$.data.rows[0].values.purchasePrice").value(150001))
                .andExpect(jsonPath("$.data.usage").isEmpty());
        verify(valueOperations, times(1)).increment(anyString());

        // Cách ly: doanh nghiệp B dùng đúng mã trích xuất của A nhận 404
        mockMvc.perform(get("/api/ai/extractions/" + id)
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(post("/api/ai/extractions/" + id + "/import")
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{\"row\":1,\"values\":{\"sku\":\"SP001\"}}]}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(bulkImportClient);

        // Hết hạn (Redis đã xóa khóa) thì 404
        redisData.clear();
        mockMvc.perform(get("/api/ai/extractions/" + id).header(TenantContext.HEADER_COMPANY_ID, COMPANY_A))
                .andExpect(status().isNotFound());
    }

    @Test
    void moreThan200Rows_returnsFirst200WithWarning() throws Exception {
        upload(xlsx(230), "PRODUCT", COMPANY_A, "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows", hasSize(200)))
                .andExpect(jsonPath("$.data.totalRows").value(230))
                .andExpect(jsonPath("$.data.truncated").value(true))
                .andExpect(jsonPath("$.data.warnings[0]").value(containsString("chỉ lấy 200 dòng đầu")));
    }

    @Test
    void import_sendsEditedRowsToBulkApiAndReturnsItsResultWithoutCountingAi() throws Exception {
        String id = extractionId(upload(xlsx(2), "PRODUCT", COMPANY_A, "WAREHOUSE_MANAGER").andExpect(status().isOk()));
        BulkImportResult result = new BulkImportResult(2, 1, 1, 0, List.of(
                new BulkImportResult.RowResult(1, "CREATED", 11L, "SP001", null),
                new BulkImportResult.RowResult(2, "SKIPPED", 5L, "SP002", "Mã SKU đã tồn tại")));
        when(bulkImportClient.importItems(eq(ExtractionDataType.PRODUCT), any())).thenReturn(result);

        mockMvc.perform(post("/api/ai/extractions/" + id + "/import")
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_A)
                        .header(UserContext.HEADER_USER_NAME, "quan_ly")
                        .header(UserContext.HEADER_USER_ROLES, "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[
                                  {"row":1,"values":{"sku":"SP001","name":"Nước suối sửa tên","uomName":"Thùng",
                                                     "purchasePrice":150001,"vatRate":"10%","khongCo":"x"}},
                                  {"row":2,"values":{"sku":"SP002","name":"Sản phẩm 2","uomName":"Thùng",
                                                     "sellingPrice":"180.000,5"}}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.results[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.results[1].message").value("Mã SKU đã tồn tại"));

        verify(bulkImportClient).importItems(ExtractionDataType.PRODUCT, List.of(
                Map.of("sku", "SP001", "name", "Nước suối sửa tên", "uomName", "Thùng",
                        "purchasePrice", new BigDecimal("150001"), "vatRate", new BigDecimal("10")),
                Map.of("sku", "SP002", "name", "Sản phẩm 2", "uomName", "Thùng",
                        "sellingPrice", new BigDecimal("180000.5"))));
        // Chỉ lượt trích xuất bị tính, bước nhập không tính
        verify(valueOperations, times(1)).increment(startsWith(AI_COUNTER_PREFIX));
    }

    @Test
    void import_emptyRowsIsValidationError() throws Exception {
        String id = extractionId(upload(xlsx(1), "PRODUCT", COMPANY_A, "ADMIN"));

        mockMvc.perform(post("/api/ai/extractions/" + id + "/import")
                        .header(TenantContext.HEADER_COMPANY_ID, COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.rows").value("Chưa chọn dòng nào để nhập"));
        verifyNoInteractions(bulkImportClient);
    }

    @Test
    void roles_areCheckedPerDataTypeBeforeAnyWork() throws Exception {
        upload(xlsx(1), "PRODUCT", COMPANY_A, "SALES")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        upload(xlsx(1), "CUSTOMER", COMPANY_A, "PURCHASING")
                .andExpect(status().isForbidden());
        upload(xlsx(1), "SUPPLIER", COMPANY_A, "ACCOUNTANT")
                .andExpect(status().isForbidden());

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void invalidFiles_areBadRequestAndNotCounted() throws Exception {
        upload(new MockMultipartFile("file", "hang.docx", "application/octet-stream", new byte[]{1, 2, 3}),
                "PRODUCT", COMPANY_A, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("Chỉ nhận xlsx, xls, csv, pdf, png, jpg")));
        upload(new MockMultipartFile("file", "hang.xlsx", "application/octet-stream",
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)), "PRODUCT", COMPANY_A, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("không đúng định dạng .xlsx")));
        byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
        tooLarge[0] = 'a';
        upload(new MockMultipartFile("file", "hang.csv", "text/csv", tooLarge), "PRODUCT", COMPANY_A, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("tối đa 10 MB")));
        upload(new MockMultipartFile("file", "khach.csv", "text/csv",
                "Foo,Bar\n1,2\n".getBytes(StandardCharsets.UTF_8)), "CUSTOMER", COMPANY_A, "SALES")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Không tìm thấy cột nào phù hợp")));
        upload(new MockMultipartFile("file", "bang-gia.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)), "SUPPLIER", COMPANY_A, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cần AI thật")));
        upload(xlsx(1), "KHO", COMPANY_A, "ADMIN")
                .andExpect(status().isBadRequest());

        verify(valueOperations, never()).increment(anyString());
        assertThat(redisData).isEmpty();
    }

    @Test
    void overQuota_isPlanLimitExceeded() throws Exception {
        when(valueOperations.get(startsWith(AI_COUNTER_PREFIX))).thenReturn("20");

        upload(xlsx(1), "PRODUCT", COMPANY_A, "ADMIN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.data.limit").value("AI_REQUESTS"));

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void missingCompany_isCompanyRequired() throws Exception {
        mockMvc.perform(multipart("/api/ai/extractions").file(xlsx(1)).param("dataType", "PRODUCT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_REQUIRED"));
    }
}
