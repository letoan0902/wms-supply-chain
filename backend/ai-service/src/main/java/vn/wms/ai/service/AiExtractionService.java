package vn.wms.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.MockAiClient;
import vn.wms.ai.config.AiExtractionProperties;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.dto.AskResponse;
import vn.wms.ai.dto.BulkImportResult;
import vn.wms.ai.dto.ExtractionResponse;
import vn.wms.ai.dto.ImportRequest;
import vn.wms.ai.extraction.BulkImportClient;
import vn.wms.ai.extraction.ClaudeExtractor;
import vn.wms.ai.extraction.ColumnMapping;
import vn.wms.ai.extraction.ExtractionAccess;
import vn.wms.ai.extraction.ExtractionAssembler;
import vn.wms.ai.extraction.ExtractionDataType;
import vn.wms.ai.extraction.ExtractionStore;
import vn.wms.ai.extraction.HeaderSynonymMatcher;
import vn.wms.ai.extraction.ImportRowConverter;
import vn.wms.ai.extraction.SheetTable;
import vn.wms.ai.extraction.SpreadsheetReader;
import vn.wms.ai.extraction.UploadedFile;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Trích xuất tài liệu thành dữ liệu danh mục rồi mới nhập (docs/SAAS_SPEC.md, mục WMS-24.2).
 *
 * <p>Luồng trích xuất ({@code POST /api/ai/extractions}), tính đúng một lượt AI giống {@code POST /api/ai/ask}:
 * <ol>
 *   <li>kiểm tra loại dữ liệu, vai trò, file (định dạng, dung lượng, nội dung) và đọc bảng tính tại chỗ: lỗi ở bước này
 *       trả 400 trước khi đụng tới hạn mức;</li>
 *   <li>kiểm tra hạn mức lượt AI;</li>
 *   <li>bảng tính: Claude chỉ ghép cột (chế độ giả lập: ghép theo tên tiêu đề), server áp cho mọi dòng; PDF, hình ảnh:
 *       Claude đọc cả tài liệu (chế độ giả lập: báo cần AI thật, không bịa dòng);</li>
 *   <li>chuẩn hóa, kiểm tra từng ô; không có cột nào phù hợp thì 400;</li>
 *   <li>lưu kết quả lên Redis 1 giờ, rồi mới tăng bộ đếm lượt AI. Lỗi ở bất kỳ bước nào trước đó (kể cả AI từ chối,
 *       kết quả bị cắt, AI không phản hồi) đều không bị tính.</li>
 * </ol>
 * Nhập ({@code POST /api/ai/extractions/{id}/import}) chỉ chạy khi người dùng bấm, không tính lượt AI.
 */
@Service
public class AiExtractionService {

    /** Kết quả do AI ghép cột của bảng tính. */
    public static final String METHOD_COLUMN_MAPPING = "COLUMN_MAPPING";
    /** Chế độ giả lập: ghép cột theo tên tiêu đề, không gọi AI. */
    public static final String METHOD_HEADER_MATCHING = "HEADER_MATCHING";
    /** AI đọc toàn bộ PDF, hình ảnh. */
    public static final String METHOD_AI_DOCUMENT = "AI_DOCUMENT";

    private static final Pattern EXTRACTION_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final AiRuntime runtime;
    private final AiQuotaService quotaService;
    private final ExtractionStore store;
    private final BulkImportClient bulkImportClient;
    private final ClaudeExtractor claudeExtractor;
    private final AiExtractionProperties properties;
    private final Clock clock;

    @Autowired
    public AiExtractionService(AiRuntime runtime, AiClient aiClient, AiQuotaService quotaService,
                               ExtractionStore store, BulkImportClient bulkImportClient,
                               AiExtractionProperties properties, ObjectMapper objectMapper) {
        this(runtime, new ClaudeExtractor(aiClient, properties, objectMapper), quotaService, store, bulkImportClient,
                properties, Clock.systemUTC());
    }

    AiExtractionService(AiRuntime runtime, ClaudeExtractor claudeExtractor, AiQuotaService quotaService,
                        ExtractionStore store, BulkImportClient bulkImportClient, AiExtractionProperties properties,
                        Clock clock) {
        this.runtime = runtime;
        this.claudeExtractor = claudeExtractor;
        this.quotaService = quotaService;
        this.store = store;
        this.bulkImportClient = bulkImportClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** Trích xuất một file, trả bảng xem trước. Không ghi gì vào danh mục. */
    public ExtractionResponse extract(MultipartFile multipartFile, String dataTypeValue) {
        ExtractionDataType type = ExtractionDataType.parse(dataTypeValue).orElseThrow(() -> ApiException.badRequest(
                "dataType phải là PRODUCT (hàng hóa), SUPPLIER (nhà cung cấp) hoặc CUSTOMER (khách hàng)."));
        ExtractionAccess.requireRole(type);
        long companyId = TenantContext.requireCompanyId();
        UploadedFile file = UploadedFile.of(multipartFile);

        ExtractionAssembler.Preview preview;
        String method;
        if (file.type().spreadsheet()) {
            SheetTable table = SpreadsheetReader.read(file);
            quotaService.checkQuota();
            ColumnMapping mapping;
            if (runtime.isMock()) {
                mapping = HeaderSynonymMatcher.match(type, table.headers());
                method = METHOD_HEADER_MATCHING;
            } else {
                mapping = claudeExtractor.mapColumns(type, table);
                method = METHOD_COLUMN_MAPPING;
            }
            preview = ExtractionAssembler.fromSpreadsheet(type, table, mapping);
        } else {
            if (runtime.isMock()) {
                throw ApiException.badRequest(MockAiClient.NEEDS_REAL_AI_MESSAGE);
            }
            quotaService.checkQuota();
            preview = ExtractionAssembler.fromDocument(type, claudeExtractor.extractDocument(type, file));
            method = METHOD_AI_DOCUMENT;
        }

        Instant now = clock.instant();
        ExtractionResponse extraction = new ExtractionResponse(UUID.randomUUID().toString(), type, file.fileName(),
                file.type().name(), method, preview.totalRows(), preview.truncated(), preview.columns(),
                preview.rows(), preview.warnings(), now, now.plus(properties.resultTtl()), null);
        store.save(companyId, extraction);
        long used = quotaService.recordSuccessfulRequest();
        return extraction.withUsage(new AskResponse.Usage(used, quotaService.currentMax()));
    }

    /** Xem lại kết quả trích xuất của doanh nghiệp hiện tại trong thời hạn lưu. */
    public ExtractionResponse get(String extractionId) {
        long companyId = TenantContext.requireCompanyId();
        ExtractionResponse extraction = find(companyId, extractionId);
        ExtractionAccess.requireRole(extraction.dataType());
        return extraction;
    }

    /**
     * Gửi các dòng người dùng đã sửa và chọn sang API nhập hàng loạt tương ứng, trả nguyên kết quả từng dòng.
     * Hai lần bấm cùng lúc: lần sau nhận {@code 409 CONFLICT}; bấm lại sau khi xong thì API nhập hàng loạt bỏ qua mã đã
     * có ({@code SKIPPED}), không sinh bản ghi trùng.
     */
    public BulkImportResult importRows(String extractionId, ImportRequest request) {
        long companyId = TenantContext.requireCompanyId();
        ExtractionResponse extraction = find(companyId, extractionId);
        ExtractionDataType type = extraction.dataType();
        ExtractionAccess.requireRole(type);
        List<Map<String, Object>> items = ImportRowConverter.toItems(type, request.rows());
        if (!store.tryLockImport(companyId, extractionId)) {
            throw ApiException.conflict("Dữ liệu của lần trích xuất này đang được nhập, vui lòng chờ kết quả.");
        }
        try {
            return bulkImportClient.importItems(type, items);
        } finally {
            store.unlockImport(companyId, extractionId);
        }
    }

    private ExtractionResponse find(long companyId, String extractionId) {
        if (extractionId == null || !EXTRACTION_ID.matcher(extractionId).matches()) {
            throw notFound();
        }
        return store.find(companyId, extractionId).orElseThrow(AiExtractionService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound("Không tìm thấy kết quả trích xuất hoặc kết quả đã quá 1 giờ, "
                + "hãy tải file lên lại.");
    }
}
