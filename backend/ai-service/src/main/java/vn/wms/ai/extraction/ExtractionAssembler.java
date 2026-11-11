package vn.wms.ai.extraction;

import vn.wms.ai.dto.ExtractionColumn;
import vn.wms.ai.dto.ExtractionRow;
import vn.wms.common.exception.ApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dựng bảng xem trước từ dữ liệu thô: áp phép ghép cột cho mọi dòng của bảng tính (chính xác, nhanh, không tốn lượt
 * gọi AI cho từng dòng) hoặc nhận các dòng Claude đọc từ tài liệu; sau đó chuẩn hóa từng ô bằng
 * {@link CellNormalizer}, đánh dấu mã trùng trong cùng file và gom cảnh báo.
 */
public final class ExtractionAssembler {

    /**
     * Bảng xem trước đã dựng xong.
     *
     * @param columns   các cột
     * @param rows      các dòng
     * @param totalRows số dòng dữ liệu tìm thấy trong file
     * @param truncated đã cắt bớt vì quá {@link ExtractionLimits#MAX_ROWS} dòng
     * @param warnings  cảnh báo chung
     */
    public record Preview(List<ExtractionColumn> columns, List<ExtractionRow> rows, int totalRows, boolean truncated,
                          List<String> warnings) {
    }

    private ExtractionAssembler() {
    }

    /** Áp phép ghép cột cho mọi dòng của bảng tính. */
    public static Preview fromSpreadsheet(ExtractionDataType type, SheetTable table, ColumnMapping mapping) {
        if (mapping.isEmpty()) {
            throw ApiException.badRequest("Không tìm thấy cột nào phù hợp với dữ liệu " + type.label()
                    + ". Hãy kiểm tra dòng tiêu đề của file (ví dụ: " + exampleHeaders(type)
                    + ") hoặc chọn đúng loại dữ liệu. Lượt này không bị tính.");
        }
        List<ExtractionColumn> columns = new ArrayList<>();
        for (TargetField field : type.fields()) {
            ColumnMapping.MappedColumn mapped = mapping.columns().get(field.name());
            columns.add(column(field, mapped));
        }
        List<RowDraft> drafts = new ArrayList<>();
        for (SheetTable.SheetRow sheetRow : table.rows()) {
            Map<String, String> raw = new LinkedHashMap<>();
            Map<String, Double> base = new LinkedHashMap<>();
            for (TargetField field : type.fields()) {
                ColumnMapping.MappedColumn mapped = mapping.columns().get(field.name());
                raw.put(field.name(), mapped != null ? sheetRow.cells().get(mapped.column()) : null);
                // Trường không có cột: ô trống là chắc chắn (1), trừ trường bắt buộc (CellNormalizer hạ về 0).
                base.put(field.name(), mapped != null ? mapped.confidence() : 1.0);
            }
            drafts.add(new RowDraft(sheetRow.sourceRow(), raw, base, List.of()));
        }
        List<String> warnings = new ArrayList<>();
        if (table.truncated()) {
            warnings.add(truncatedWarning(table.totalDataRows()));
        }
        for (TargetField field : type.fields()) {
            if (field.required() && !mapping.columns().containsKey(field.name())) {
                warnings.add("Không tìm thấy cột " + field.label() + " (bắt buộc) trong file, hãy nhập bổ sung "
                        + "trong bảng xem trước.");
            }
        }
        warnings.addAll(mapping.notes());
        return new Preview(columns, build(type, drafts), table.totalDataRows(), table.truncated(), warnings);
    }

    /** Nhận các dòng Claude đọc từ PDF, hình ảnh. */
    public static Preview fromDocument(ExtractionDataType type, ClaudeExtractor.DocumentResult result) {
        List<RowDraft> drafts = new ArrayList<>();
        for (ClaudeExtractor.RawRow row : result.rows()) {
            boolean hasValue = row.values().values().stream().anyMatch(value -> value != null && !value.isBlank());
            if (hasValue) {
                drafts.add(new RowDraft(null, row.values(), row.confidence(), row.warnings()));
            }
        }
        if (drafts.isEmpty()) {
            throw ApiException.badRequest("Không tìm thấy dữ liệu " + type.label() + " nào trong tài liệu. "
                    + "Hãy kiểm tra file hoặc chọn đúng loại dữ liệu. Lượt này không bị tính.");
        }
        int total = drafts.size();
        boolean truncated = total > ExtractionLimits.MAX_ROWS;
        List<String> warnings = new ArrayList<>();
        if (truncated) {
            drafts = drafts.subList(0, ExtractionLimits.MAX_ROWS);
            warnings.add(truncatedWarning(total));
        }
        warnings.addAll(result.notes());
        List<ExtractionColumn> columns = type.fields().stream().map(field -> column(field, null)).toList();
        return new Preview(columns, build(type, drafts), total, truncated, warnings);
    }

    /** Một dòng trước khi chuẩn hóa: giá trị gốc và độ tin cậy gốc của từng ô. */
    private record RowDraft(Integer sourceRow, Map<String, String> raw, Map<String, Double> base,
                            List<String> extraWarnings) {
    }

    private static List<ExtractionRow> build(ExtractionDataType type, List<RowDraft> drafts) {
        TargetField identity = type.fields().get(0);
        Map<String, Integer> firstRowByCode = new HashMap<>();
        List<ExtractionRow> rows = new ArrayList<>();
        int index = 0;
        for (RowDraft draft : drafts) {
            index++;
            Map<String, Object> values = new LinkedHashMap<>();
            Map<String, Double> confidence = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();
            for (TargetField field : type.fields()) {
                Double base = draft.base().get(field.name());
                CellNormalizer.Cell cell = CellNormalizer.normalize(field, draft.raw().get(field.name()),
                        base != null ? base : 0.0);
                values.put(field.name(), cell.value());
                confidence.put(field.name(), cell.confidence());
                warnings.addAll(cell.warnings());
            }
            if (values.get(identity.name()) instanceof String code) {
                String key = code.toLowerCase(Locale.ROOT);
                Integer first = firstRowByCode.putIfAbsent(key, index);
                if (first != null) {
                    confidence.put(identity.name(), Math.min(confidence.get(identity.name()), 0.5));
                    warnings.add(identity.label() + " \"" + code + "\" trùng với dòng " + first
                            + ", khi nhập dòng này sẽ bị báo lỗi");
                }
            }
            warnings.addAll(draft.extraWarnings());
            rows.add(new ExtractionRow(index, draft.sourceRow(), values, confidence, warnings));
        }
        return rows;
    }

    private static ExtractionColumn column(TargetField field, ColumnMapping.MappedColumn mapped) {
        return new ExtractionColumn(field.name(), field.label(), field.kind().name(), field.required(),
                field.maxLength(), List.copyOf(field.options().keySet()),
                mapped != null ? mapped.column() + 1 : null,
                mapped != null ? mapped.header() : null,
                mapped != null ? mapped.confidence() : null);
    }

    private static String truncatedWarning(int total) {
        return "File có " + total + " dòng dữ liệu, hệ thống chỉ lấy " + ExtractionLimits.MAX_ROWS
                + " dòng đầu. Hãy tách phần còn lại thành file khác để trích xuất tiếp.";
    }

    private static String exampleHeaders(ExtractionDataType type) {
        return type.fields().stream().filter(TargetField::required).map(TargetField::label)
                .reduce((a, b) -> a + ", " + b).orElse("");
    }
}
