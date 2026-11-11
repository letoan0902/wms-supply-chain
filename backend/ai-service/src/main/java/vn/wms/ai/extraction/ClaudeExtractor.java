package vn.wms.ai.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.AiContentPart;
import vn.wms.ai.client.AiJsonReply;
import vn.wms.ai.client.AiJsonRequest;
import vn.wms.ai.config.AiExtractionProperties;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hai cách nhờ Claude trích xuất, đều qua {@link AiClient#generateJson(AiJsonRequest)} với lược đồ JSON cố định:
 * <ul>
 *   <li>bảng tính: chỉ gửi tiêu đề và {@link ExtractionLimits#SAMPLE_ROWS} dòng mẫu, nhận lại phép ghép cột;</li>
 *   <li>PDF, hình ảnh: gửi nguyên tài liệu, nhận lại từng dòng kèm độ tin cậy từng ô.</li>
 * </ul>
 * Mô hình từ chối hoặc kết quả bị cắt vì quá dài đều là lỗi rõ ràng (không dùng kết quả dở dang), lượt không bị tính.
 */
public class ClaudeExtractor {

    private static final Logger log = LoggerFactory.getLogger(ClaudeExtractor.class);

    static final String REFUSED_MESSAGE = "AI từ chối xử lý nội dung file này. Hãy kiểm tra file chỉ chứa danh sách "
            + "dữ liệu cần nhập rồi thử lại. Lượt này không bị tính.";
    static final String TRUNCATED_MESSAGE = "File có quá nhiều dữ liệu để AI đọc hết trong một lần. Hãy tách thành "
            + "các file nhỏ hơn (tối đa " + ExtractionLimits.MAX_ROWS + " dòng mỗi file) rồi thử lại. "
            + "Lượt này không bị tính.";
    static final String INVALID_REPLY_MESSAGE = "Dịch vụ AI trả về kết quả không đọc được, lượt này không bị tính. "
            + "Vui lòng thử lại.";

    /**
     * Kết quả Claude đọc tài liệu.
     *
     * @param rows  các dòng (giá trị gốc dạng chuỗi, độ tin cậy từng ô, cảnh báo)
     * @param notes ghi chú chung của Claude
     */
    public record DocumentResult(List<RawRow> rows, List<String> notes) {
    }

    /** Một dòng Claude đọc được từ tài liệu, trước khi chuẩn hóa. */
    public record RawRow(Map<String, String> values, Map<String, Double> confidence, List<String> warnings) {
    }

    private final AiClient aiClient;
    private final AiExtractionProperties properties;
    private final ObjectMapper objectMapper;

    public ClaudeExtractor(AiClient aiClient, AiExtractionProperties properties, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Hỏi Claude cách ghép cột của bảng tính; server tự áp cho mọi dòng sau đó. */
    public ColumnMapping mapColumns(ExtractionDataType type, SheetTable table) {
        AiJsonRequest request = new AiJsonRequest(ExtractionPrompts.mappingSystemPrompt(type),
                List.of(new AiContentPart.Text(mappingMessage(table))), ExtractionPrompts.mappingSchema(type),
                properties.mappingMaxTokens(), properties.timeout());
        JsonNode root = parse(checked(aiClient.generateJson(request)));
        List<ColumnMapping.Candidate> candidates = new ArrayList<>();
        Map<String, String> noteByField = new LinkedHashMap<>();
        for (JsonNode item : root.path("mappings")) {
            String fieldName = item.path("field").asText("");
            JsonNode source = item.path("sourceColumn");
            if (type.field(fieldName).isEmpty() || !source.canConvertToInt()) {
                continue;
            }
            double confidence = CellNormalizer.clamp(item.path("confidence").asDouble(0));
            candidates.add(new ColumnMapping.Candidate(fieldName, source.asInt() - 1, confidence));
            String note = item.path("note").asText("").strip();
            if (!note.isEmpty()) {
                noteByField.putIfAbsent(fieldName, note);
            }
        }
        Map<String, ColumnMapping.MappedColumn> columns = ColumnMapping.resolve(candidates, table.headers());
        // Chỉ chuyển cho người dùng ghi chú của cột đã ghép mà chưa chắc chắn.
        List<String> notes = new ArrayList<>();
        noteByField.forEach((fieldName, note) -> {
            ColumnMapping.MappedColumn mapped = columns.get(fieldName);
            if (mapped != null && mapped.confidence() < 0.8) {
                notes.add(type.field(fieldName).orElseThrow().label() + ": " + note);
            }
        });
        return new ColumnMapping(columns, notes);
    }

    /** Nhờ Claude đọc toàn bộ PDF hoặc hình ảnh. */
    public DocumentResult extractDocument(ExtractionDataType type, UploadedFile file) {
        AiContentPart document = file.type() == UploadedFileType.PDF
                ? new AiContentPart.Pdf(file.bytes())
                : new AiContentPart.Image(file.bytes(), file.type().mediaType());
        AiJsonRequest request = new AiJsonRequest(ExtractionPrompts.documentSystemPrompt(type),
                List.of(document, new AiContentPart.Text(ExtractionPrompts.documentInstruction(type))),
                ExtractionPrompts.documentSchema(type), properties.maxTokens(), properties.timeout());
        JsonNode root = parse(checked(aiClient.generateJson(request)));
        List<RawRow> rows = new ArrayList<>();
        for (JsonNode item : root.path("rows")) {
            Map<String, String> values = new LinkedHashMap<>();
            Map<String, Double> confidence = new LinkedHashMap<>();
            for (TargetField field : type.fields()) {
                JsonNode value = item.path("values").path(field.name());
                values.put(field.name(), value.isNull() || value.isMissingNode() ? null : value.asText());
                confidence.put(field.name(), CellNormalizer.clamp(
                        item.path("confidence").path(field.name()).asDouble(0)));
            }
            List<String> warnings = new ArrayList<>();
            item.path("warnings").forEach(warning -> {
                if (!warning.asText("").isBlank()) {
                    warnings.add(warning.asText().strip());
                }
            });
            rows.add(new RawRow(values, confidence, warnings));
        }
        List<String> notes = new ArrayList<>();
        root.path("notes").forEach(note -> {
            if (!note.asText("").isBlank()) {
                notes.add(note.asText().strip());
            }
        });
        return new DocumentResult(rows, notes);
    }

    /** Tiêu đề và vài dòng mẫu của từng cột, dạng JSON để không lẫn dấu phân cách với dữ liệu. */
    String mappingMessage(SheetTable table) {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (int column = 0; column < table.headers().size(); column++) {
            List<String> samples = new ArrayList<>();
            for (SheetTable.SheetRow row : table.rows()) {
                if (samples.size() >= ExtractionLimits.SAMPLE_ROWS) {
                    break;
                }
                samples.add(shorten(row.cells().get(column)));
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("column", column + 1);
            entry.put("header", shorten(table.headers().get(column)));
            entry.put("samples", samples);
            columns.add(entry);
        }
        try {
            return "Bảng tính có " + table.headers().size() + " cột và " + table.totalDataRows()
                    + " dòng dữ liệu. Tiêu đề và dữ liệu mẫu của từng cột:\n"
                    + objectMapper.writeValueAsString(columns);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không ghi được dữ liệu mẫu", ex);
        }
    }

    private static String shorten(String value) {
        if (value.codePointCount(0, value.length()) <= ExtractionLimits.SAMPLE_CELL_LENGTH) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, ExtractionLimits.SAMPLE_CELL_LENGTH)) + "...";
    }

    /** Xét cờ của câu trả lời trước khi đọc JSON. */
    private static String checked(AiJsonReply reply) {
        if (reply.refused()) {
            throw ApiException.badRequest(REFUSED_MESSAGE);
        }
        if (reply.truncated()) {
            throw ApiException.badRequest(TRUNCATED_MESSAGE);
        }
        return reply.json();
    }

    private JsonNode parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("không phải object");
            }
            return root;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            // Không ghi nội dung trả về vào log (có thể chứa dữ liệu của doanh nghiệp).
            log.warn("Kết quả JSON của Claude không đọc được: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, INVALID_REPLY_MESSAGE);
        }
    }
}
