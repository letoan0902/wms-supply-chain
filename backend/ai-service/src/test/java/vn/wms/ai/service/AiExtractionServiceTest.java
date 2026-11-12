package vn.wms.ai.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.ai.AiTestSupport;
import vn.wms.ai.client.MockAiClient;
import vn.wms.ai.config.AiMode;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.dto.BulkImportResult;
import vn.wms.ai.dto.ExtractionResponse;
import vn.wms.ai.dto.ImportRequest;
import vn.wms.ai.extraction.BulkImportClient;
import vn.wms.ai.extraction.ClaudeExtractor;
import vn.wms.ai.extraction.ColumnMapping;
import vn.wms.ai.extraction.ExtractionDataType;
import vn.wms.ai.extraction.ExtractionStore;
import vn.wms.ai.extraction.TestSpreadsheets;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Luồng trích xuất ở chế độ gọi Claude thật (Claude được giả lập bằng mock của {@link ClaudeExtractor}): tính đúng
 * một lượt khi thành công, không tính khi lỗi, kiểm tra hạn mức trước khi gọi AI, vai trò theo loại dữ liệu.
 */
class AiExtractionServiceTest {

    private static final long COMPANY_ID = 701L;
    private static final String ID = "3f1c2b9e-1111-4a2b-9c3d-0123456789ab";
    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    private final ClaudeExtractor claude = mock(ClaudeExtractor.class);
    private final AiQuotaService quota = mock(AiQuotaService.class);
    private final ExtractionStore store = mock(ExtractionStore.class);
    private final BulkImportClient bulk = mock(BulkImportClient.class);

    private AiExtractionService service(AiMode mode) {
        return new AiExtractionService(new AiRuntime(mode, "claude-sonnet-5-5"), claude, quota, store, bulk,
                AiTestSupport.extractionProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private final AiExtractionService live = service(AiMode.LIVE);

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(COMPANY_ID);
        asUser("PURCHASING");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    private static void asUser(String roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_NAME, "nguoi_dung");
        request.addHeader(UserContext.HEADER_USER_ROLES, roles);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static MockMultipartFile xlsx() {
        return new MockMultipartFile("file", "hang.xlsx", "application/octet-stream",
                TestSpreadsheets.xlsx(TestSpreadsheets.productRows(3)));
    }

    private static ColumnMapping productMapping() {
        return new ColumnMapping(Map.of(
                "sku", new ColumnMapping.MappedColumn(0, "Mã hàng", 0.97),
                "name", new ColumnMapping.MappedColumn(1, "Tên hàng hóa", 0.97),
                "uomName", new ColumnMapping.MappedColumn(2, "Đơn vị tính", 0.9),
                "purchasePrice", new ColumnMapping.MappedColumn(3, "Giá nhập", 0.9)), List.of());
    }

    @Test
    void spreadsheet_aiMapsColumnsServerAppliesToRows_storesThenCountsOnce() {
        when(claude.mapColumns(eq(ExtractionDataType.PRODUCT), any())).thenReturn(productMapping());
        when(quota.recordSuccessfulRequest()).thenReturn(6L);
        when(quota.currentMax()).thenReturn(20L);

        ExtractionResponse response = live.extract(xlsx(), "product");

        assertThat(response.method()).isEqualTo(AiExtractionService.METHOD_COLUMN_MAPPING);
        assertThat(response.dataType()).isEqualTo(ExtractionDataType.PRODUCT);
        assertThat(response.rows()).hasSize(3);
        assertThat(response.rows().get(0).values()).containsEntry("sku", "SP001")
                .containsEntry("purchasePrice", new BigDecimal("150001"));
        assertThat(response.rows().get(0).confidence()).containsEntry("sku", 0.97);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(response.usage().used()).isEqualTo(6L);
        assertThat(response.usage().max()).isEqualTo(20L);
        var order = inOrder(quota, claude, store);
        order.verify(quota).checkQuota();
        order.verify(claude).mapColumns(eq(ExtractionDataType.PRODUCT), any());
        order.verify(store).save(eq(COMPANY_ID), any());
        order.verify(quota).recordSuccessfulRequest();
    }

    @Test
    void overQuota_doesNotCallAi() {
        doThrow(new ApiException(ErrorCode.PLAN_LIMIT_EXCEEDED)).when(quota).checkQuota();

        assertThatThrownBy(() -> live.extract(xlsx(), "PRODUCT")).isInstanceOf(ApiException.class);
        verifyNoInteractions(claude, store);
        verify(quota, never()).recordSuccessfulRequest();
    }

    @Test
    void aiFailureRefusalNoColumnAndStoreFailure_areNotCounted() {
        doThrow(new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI lỗi")).when(claude).mapColumns(any(), any());
        assertThatThrownBy(() -> live.extract(xlsx(), "PRODUCT")).isInstanceOf(ApiException.class);

        doThrow(ApiException.badRequest("AI từ chối")).when(claude).mapColumns(any(), any());
        assertThatThrownBy(() -> live.extract(xlsx(), "PRODUCT")).isInstanceOf(ApiException.class);

        doReturn(new ColumnMapping(Map.of(), List.of())).when(claude).mapColumns(any(), any());
        assertThatThrownBy(() -> live.extract(xlsx(), "PRODUCT"))
                .hasMessageContaining("Không tìm thấy cột nào phù hợp");

        doReturn(productMapping()).when(claude).mapColumns(any(), any());
        doThrow(new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Redis")).when(store).save(anyLong(), any());
        assertThatThrownBy(() -> live.extract(xlsx(), "PRODUCT")).isInstanceOf(ApiException.class);

        verify(quota, never()).recordSuccessfulRequest();
    }

    @Test
    void unreadableFileIsRejectedBeforeQuotaAndAi() {
        MockMultipartFile broken = new MockMultipartFile("file", "hang.csv", "text/csv",
                "Mã,Tên\nSP1,\"Bánh\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> live.extract(broken, "PRODUCT"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getHttpStatus()).isEqualTo(400));
        verifyNoInteractions(quota, claude, store);
    }

    @Test
    void invalidDataTypeIsBadRequest() {
        assertThatThrownBy(() -> live.extract(xlsx(), "WAREHOUSE"))
                .hasMessageContaining("PRODUCT (hàng hóa), SUPPLIER (nhà cung cấp) hoặc CUSTOMER (khách hàng)");
        assertThatThrownBy(() -> live.extract(xlsx(), null)).isInstanceOf(ApiException.class);
    }

    @Test
    void mockMode_pdfNeedsRealAiAndIsNotCounted() {
        MockMultipartFile pdf = new MockMultipartFile("file", "bang-gia.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service(AiMode.MOCK).extract(pdf, "SUPPLIER"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(MockAiClient.NEEDS_REAL_AI_MESSAGE);
                });
        verifyNoInteractions(quota, claude, store);
    }

    @Test
    void mockMode_spreadsheetUsesHeaderMatchingWithoutAi() {
        ExtractionResponse response = service(AiMode.MOCK).extract(xlsx(), "PRODUCT");

        assertThat(response.method()).isEqualTo(AiExtractionService.METHOD_HEADER_MATCHING);
        assertThat(response.rows().get(0).values()).containsEntry("uomName", "Thùng")
                .containsEntry("vatRate", new BigDecimal("10")).containsEntry("categoryName", "Đồ uống");
        verifyNoInteractions(claude);
        verify(quota).checkQuota();
        verify(quota).recordSuccessfulRequest();
    }

    @Test
    void roles_productAndSupplierForWarehouseRolesCustomerForAdminAndSales() {
        asUser("SALES");
        assertThatThrownBy(() -> live.extract(xlsx(), "PRODUCT"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> live.extract(xlsx(), "SUPPLIER")).isInstanceOf(ApiException.class);

        asUser("PURCHASING");
        assertThatThrownBy(() -> live.extract(xlsx(), "CUSTOMER"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        asUser("WAREHOUSE_STAFF,ACCOUNTANT");
        assertThatThrownBy(() -> live.extract(xlsx(), "PRODUCT")).isInstanceOf(ApiException.class);
        verifyNoInteractions(quota, claude);

        asUser("SALES");
        when(claude.mapColumns(any(), any())).thenReturn(new ColumnMapping(Map.of(
                "code", new ColumnMapping.MappedColumn(0, "Mã hàng", 0.9),
                "name", new ColumnMapping.MappedColumn(1, "Tên", 0.9)), List.of()));
        assertThat(live.extract(xlsx(), "CUSTOMER").dataType()).isEqualTo(ExtractionDataType.CUSTOMER);
        asUser("WAREHOUSE_MANAGER");
        when(claude.mapColumns(any(), any())).thenReturn(productMapping());
        assertThat(live.extract(xlsx(), "PRODUCT").rows()).hasSize(3);
        asUser("ADMIN");
        assertThat(live.extract(xlsx(), "PRODUCT").rows()).hasSize(3);
    }

    private static ExtractionResponse stored(ExtractionDataType type) {
        return new ExtractionResponse(ID, type, "a.xlsx", "XLSX", "COLUMN_MAPPING", 1, false, List.of(), List.of(),
                List.of(), NOW, NOW.plusSeconds(3600), null);
    }

    @Test
    void import_convertsRowsCallsBulkApiOnceAndReturnsItsResult_withoutCountingAi() {
        when(store.find(COMPANY_ID, ID)).thenReturn(Optional.of(stored(ExtractionDataType.PRODUCT)));
        when(store.tryLockImport(COMPANY_ID, ID)).thenReturn(true);
        BulkImportResult result = new BulkImportResult(1, 1, 0, 0,
                List.of(new BulkImportResult.RowResult(1, "CREATED", 10L, "SP001", null)));
        when(bulk.importItems(eq(ExtractionDataType.PRODUCT), any())).thenReturn(result);

        BulkImportResult returned = live.importRows(ID, new ImportRequest(List.of(new ImportRequest.Row(3,
                Map.of("sku", "SP001", "name", "Nước", "uomName", "Chai", "purchasePrice", "5.000")))));

        assertThat(returned).isSameAs(result);
        verify(bulk).importItems(ExtractionDataType.PRODUCT, List.of(Map.of("sku", "SP001", "name", "Nước",
                "uomName", "Chai", "purchasePrice", new BigDecimal("5000"))));
        verify(store).unlockImport(COMPANY_ID, ID);
        verifyNoInteractions(quota);
    }

    @Test
    void import_concurrentSecondClickIsConflictAndUnknownOrForeignIdIsNotFound() {
        when(store.find(COMPANY_ID, ID)).thenReturn(Optional.of(stored(ExtractionDataType.PRODUCT)));
        when(store.tryLockImport(COMPANY_ID, ID)).thenReturn(false);
        ImportRequest request = new ImportRequest(List.of(new ImportRequest.Row(1, Map.of("sku", "A"))));

        assertThatThrownBy(() -> live.importRows(ID, request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verifyNoInteractions(bulk);

        when(store.find(COMPANY_ID, ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> live.importRows(ID, request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> live.get("../../khong-hop-le"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void import_customerExtractionRequiresAdminOrSales() {
        when(store.find(COMPANY_ID, ID)).thenReturn(Optional.of(stored(ExtractionDataType.CUSTOMER)));
        asUser("PURCHASING");

        assertThatThrownBy(() -> live.importRows(ID, new ImportRequest(List.of(
                new ImportRequest.Row(1, Map.of("code", "KH1"))))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(bulk);
    }
}
