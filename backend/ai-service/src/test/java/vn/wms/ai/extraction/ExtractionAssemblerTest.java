package vn.wms.ai.extraction;

import org.junit.jupiter.api.Test;
import vn.wms.ai.dto.ExtractionColumn;
import vn.wms.ai.dto.ExtractionRow;
import vn.wms.common.exception.ApiException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ghép cột theo tên (chế độ giả lập), áp phép ghép cho mọi dòng, độ tin cậy, cảnh báo và giới hạn 200 dòng. */
class ExtractionAssemblerTest {

    private static SheetTable table(List<String> headers, List<List<String>> rows, int total) {
        List<SheetTable.SheetRow> sheetRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            sheetRows.add(new SheetTable.SheetRow(i + 2, rows.get(i)));
        }
        return new SheetTable(headers, sheetRows, total);
    }

    @Test
    void headerMatching_isAccentInsensitiveAndPrefersExactAndLongerSynonyms() {
        List<String> headers = List.of("STT", "Ma hang", "TÊN SẢN PHẨM", "ĐVT", "Giá nhập (VNĐ)", "Đơn giá",
                "Mã vạch EAN", "% VAT", "Ghi chú");

        ColumnMapping mapping = HeaderSynonymMatcher.match(ExtractionDataType.PRODUCT, headers);

        assertThat(mapping.column("sku")).hasValueSatisfying(c -> {
            assertThat(c.column()).isEqualTo(1);
            assertThat(c.confidence()).isEqualTo(0.95);
        });
        assertThat(mapping.column("name").orElseThrow().column()).isEqualTo(2);
        assertThat(mapping.column("uomName").orElseThrow().column()).isEqualTo(3);
        assertThat(mapping.column("purchasePrice")).hasValueSatisfying(c -> {
            assertThat(c.column()).isEqualTo(4);
            assertThat(c.confidence()).isEqualTo(0.8);
        });
        assertThat(mapping.column("sellingPrice").orElseThrow().column()).isEqualTo(5);
        assertThat(mapping.column("barcode").orElseThrow().column()).isEqualTo(6);
        assertThat(mapping.column("vatRate").orElseThrow().column()).isEqualTo(7);
        assertThat(mapping.column("description").orElseThrow().column()).isEqualTo(8);
        assertThat(mapping.columns().values()).noneMatch(c -> c.column() == 0);
    }

    @Test
    void headerMatching_isDeterministicAndOneColumnPerField() {
        List<String> headers = List.of("Mã", "Mã số thuế", "Tên nhà cung cấp", "SĐT", "Email", "Ngân hàng", "STK");

        ColumnMapping first = HeaderSynonymMatcher.match(ExtractionDataType.SUPPLIER, headers);
        ColumnMapping second = HeaderSynonymMatcher.match(ExtractionDataType.SUPPLIER, headers);

        assertThat(first).isEqualTo(second);
        assertThat(first.column("code").orElseThrow().column()).isZero();
        assertThat(first.column("taxCode").orElseThrow().column()).isEqualTo(1);
        assertThat(first.column("phone").orElseThrow().column()).isEqualTo(3);
        assertThat(first.column("bankAccount").orElseThrow().column()).isEqualTo(6);
        assertThat(first.columns().values()).extracting(ColumnMapping.MappedColumn::column).doesNotHaveDuplicates();
    }

    @Test
    void fromSpreadsheet_appliesMappingToEveryRowWithConfidenceAndWarnings() {
        SheetTable table = table(List.of("Mã hàng", "Tên hàng", "Giá bán", "Cột lạ"), List.of(
                List.of("SP001", "Nước suối", "5.000", "x"),
                List.of("SP002", "", "liên hệ", "y"),
                List.of("sp001", "Bánh", "12000", "z")), 3);
        ColumnMapping mapping = HeaderSynonymMatcher.match(ExtractionDataType.PRODUCT, table.headers());

        ExtractionAssembler.Preview preview = ExtractionAssembler.fromSpreadsheet(ExtractionDataType.PRODUCT, table,
                mapping);

        assertThat(preview.columns()).extracting(ExtractionColumn::field)
                .containsExactly("sku", "name", "barcode", "description", "categoryName", "uomName",
                        "purchasePrice", "sellingPrice", "vatRate", "safetyStock", "reorderPoint", "maxStock");
        ExtractionColumn skuColumn = preview.columns().get(0);
        assertThat(skuColumn.sourceColumn()).isEqualTo(1);
        assertThat(skuColumn.sourceHeader()).isEqualTo("Mã hàng");
        assertThat(skuColumn.mappingConfidence()).isEqualTo(0.95);
        assertThat(preview.columns().get(5).sourceColumn()).isNull();

        ExtractionRow first = preview.rows().get(0);
        assertThat(first.row()).isEqualTo(1);
        assertThat(first.sourceRow()).isEqualTo(2);
        assertThat(first.values()).containsEntry("sku", "SP001").containsEntry("name", "Nước suối")
                .containsEntry("sellingPrice", new BigDecimal("5000")).containsEntry("barcode", null);
        assertThat(first.values()).hasSize(12);
        assertThat(first.confidence()).containsEntry("sku", 0.95).containsEntry("barcode", 1.0)
                .containsEntry("uomName", 0.0);
        assertThat(first.warnings()).containsExactly("Thiếu Đơn vị tính (bắt buộc)");

        ExtractionRow second = preview.rows().get(1);
        assertThat(second.confidence()).containsEntry("name", 0.0).containsEntry("sellingPrice", 0.2);
        assertThat(second.warnings()).contains("Thiếu Tên hàng hóa (bắt buộc)", "Giá bán \"liên hệ\" không phải là số");

        ExtractionRow third = preview.rows().get(2);
        assertThat(third.confidence().get("sku")).isEqualTo(0.5);
        assertThat(third.warnings()).contains("Mã SKU \"sp001\" trùng với dòng 1, khi nhập dòng này sẽ bị báo lỗi");

        assertThat(preview.warnings()).containsExactly(
                "Không tìm thấy cột Đơn vị tính (bắt buộc) trong file, hãy nhập bổ sung trong bảng xem trước.");
        assertThat(preview.truncated()).isFalse();
    }

    @Test
    void fromSpreadsheet_truncatedTableAddsWarning() {
        SheetTable table = table(List.of("Mã", "Tên", "Loại"), List.of(List.of("KH1", "An", "lẻ")), 350);

        ExtractionAssembler.Preview preview = ExtractionAssembler.fromSpreadsheet(ExtractionDataType.CUSTOMER, table,
                HeaderSynonymMatcher.match(ExtractionDataType.CUSTOMER, table.headers()));

        assertThat(preview.truncated()).isTrue();
        assertThat(preview.totalRows()).isEqualTo(350);
        assertThat(preview.warnings()).contains("File có 350 dòng dữ liệu, hệ thống chỉ lấy 200 dòng đầu. "
                + "Hãy tách phần còn lại thành file khác để trích xuất tiếp.");
        assertThat(preview.rows().get(0).values()).containsEntry("customerType", "RETAIL");
    }

    @Test
    void fromSpreadsheet_noMatchingColumnIsBadRequest() {
        SheetTable table = table(List.of("Foo", "Bar"), List.of(List.of("1", "2")), 1);
        ColumnMapping mapping = HeaderSynonymMatcher.match(ExtractionDataType.CUSTOMER, table.headers());

        assertThatThrownBy(() -> ExtractionAssembler.fromSpreadsheet(ExtractionDataType.CUSTOMER, table, mapping))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getHttpStatus()).isEqualTo(400))
                .hasMessageContaining("Không tìm thấy cột nào phù hợp với dữ liệu khách hàng")
                .hasMessageContaining("không bị tính");
    }

    private static ClaudeExtractor.RawRow rawRow(String code, String name, double confidence) {
        Map<String, String> values = new HashMap<>();
        Map<String, Double> confidences = new HashMap<>();
        for (TargetField field : ExtractionDataType.SUPPLIER.fields()) {
            values.put(field.name(), null);
            confidences.put(field.name(), 1.0);
        }
        values.put("code", code);
        values.put("name", name);
        confidences.put("name", confidence);
        return new ClaudeExtractor.RawRow(values, confidences, List.of("Chữ hơi mờ"));
    }

    @Test
    void fromDocument_usesClaudeConfidenceCapsAt200RowsAndSkipsEmptyRows() {
        List<ClaudeExtractor.RawRow> rows = new ArrayList<>();
        rows.add(rawRow(null, null, 1.0));
        for (int i = 1; i <= 205; i++) {
            rows.add(rawRow("NCC" + i, "Nhà cung cấp " + i, 0.6));
        }

        ExtractionAssembler.Preview preview = ExtractionAssembler.fromDocument(ExtractionDataType.SUPPLIER,
                new ClaudeExtractor.DocumentResult(rows, List.of("Trang 3 bị mờ")));

        assertThat(preview.rows()).hasSize(200);
        assertThat(preview.totalRows()).isEqualTo(205);
        assertThat(preview.truncated()).isTrue();
        assertThat(preview.warnings()).hasSize(2).last().isEqualTo("Trang 3 bị mờ");
        ExtractionRow first = preview.rows().get(0);
        assertThat(first.sourceRow()).isNull();
        assertThat(first.values()).containsEntry("code", "NCC1");
        assertThat(first.confidence()).containsEntry("name", 0.6).containsEntry("code", 1.0);
        assertThat(first.warnings()).containsExactly("Chữ hơi mờ");
        assertThat(preview.columns()).allMatch(column -> column.sourceColumn() == null);
    }

    @Test
    void fromDocument_withoutRowsIsBadRequest() {
        assertThatThrownBy(() -> ExtractionAssembler.fromDocument(ExtractionDataType.PRODUCT,
                new ClaudeExtractor.DocumentResult(Arrays.asList(), List.of())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Không tìm thấy dữ liệu hàng hóa nào");
    }
}
