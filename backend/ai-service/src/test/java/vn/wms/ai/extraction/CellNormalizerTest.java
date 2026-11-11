package vn.wms.ai.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Chuẩn hóa số kiểu Việt Nam, phần trăm VAT, độ tin cậy và cảnh báo của từng ô. */
class CellNormalizerTest {

    private static TargetField field(ExtractionDataType type, String name) {
        return type.field(name).orElseThrow();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1.250.000|1250000",
            "1,250,000|1250000",
            "1.250.000,5|1250000.5",
            "1,234.56|1234.56",
            "12,5|12.5",
            "12.50|12.5",
            "150.000|150000",
            "0,500|0.5",
            "150000 đ|150000",
            "VNĐ 99.000|99000",
            "1 250 000|1250000",
            "(1.000)|-1000",
            "8.9385E+12|8938500000000",
            ",5|0.5"})
    void parsesVietnameseAndInternationalNumbers(String raw, String expected) {
        assertThat(NumberParser.parse(raw)).hasValueSatisfying(
                parsed -> assertThat(parsed.value()).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"abc", "1.2.3", "12a", "--", "1,23,456"})
    void rejectsNonNumbers(String raw) {
        assertThat(NumberParser.parse(raw)).isEmpty();
    }

    @Test
    void parsedValuesHaveNoNegativeScale() {
        assertThat(NumberParser.parse("1.000.000").orElseThrow().value().toString()).isEqualTo("1000000");
    }

    @Test
    void number_isNormalizedAndKeepsMappingConfidence() {
        CellNormalizer.Cell cell = CellNormalizer.normalize(field(ExtractionDataType.PRODUCT, "purchasePrice"),
                " 150.000 ", 0.95);

        assertThat(cell.value()).isEqualTo(new BigDecimal("150000"));
        assertThat(cell.confidence()).isEqualTo(0.95);
        assertThat(cell.warnings()).isEmpty();
    }

    @Test
    void number_notANumberKeepsTextWithLowConfidenceAndWarning() {
        CellNormalizer.Cell cell = CellNormalizer.normalize(field(ExtractionDataType.PRODUCT, "sellingPrice"),
                "liên hệ", 0.95);

        assertThat(cell.value()).isEqualTo("liên hệ");
        assertThat(cell.confidence()).isEqualTo(0.2);
        assertThat(cell.warnings()).containsExactly("Giá bán \"liên hệ\" không phải là số");
    }

    @Test
    void number_negativeAndTooManyDecimalsAreFlagged() {
        CellNormalizer.Cell negative = CellNormalizer.normalize(field(ExtractionDataType.SUPPLIER, "creditLimit"),
                "-5.000", 0.9);
        CellNormalizer.Cell decimals = CellNormalizer.normalize(field(ExtractionDataType.PRODUCT, "purchasePrice"),
                "12,345", 0.9);

        assertThat(negative.confidence()).isEqualTo(0.3);
        assertThat(negative.warnings()).containsExactly("Hạn mức công nợ không được âm");
        // "12,345" là 12345 (dấu phẩy hàng nghìn), không phải 12,345
        assertThat(decimals.value()).isEqualTo(new BigDecimal("12345"));
        CellNormalizer.Cell tooPrecise = CellNormalizer.normalize(field(ExtractionDataType.PRODUCT, "purchasePrice"),
                "12,3456", 0.9);
        assertThat(tooPrecise.confidence()).isEqualTo(0.5);
        assertThat(tooPrecise.warnings()).containsExactly("Giá nhập chỉ được tối đa 2 chữ số thập phân");
    }

    @Test
    void percent_acceptsPercentSignPlainAndFraction() {
        TargetField vat = field(ExtractionDataType.PRODUCT, "vatRate");

        assertThat(CellNormalizer.normalize(vat, "10%", 0.9).value()).isEqualTo(new BigDecimal("10"));
        assertThat(CellNormalizer.normalize(vat, "8", 0.9).value()).isEqualTo(new BigDecimal("8"));
        CellNormalizer.Cell fraction = CellNormalizer.normalize(vat, "0,1", 0.9);
        assertThat(fraction.value()).isEqualTo(new BigDecimal("10"));
        assertThat(fraction.confidence()).isEqualTo(0.7);
        assertThat(fraction.warnings()).containsExactly("Thuế suất VAT (%) \"0,1\" được hiểu là 10%");
        CellNormalizer.Cell tooHigh = CellNormalizer.normalize(vat, "150", 0.9);
        assertThat(tooHigh.confidence()).isEqualTo(0.3);
        assertThat(tooHigh.warnings()).containsExactly("Thuế suất VAT (%) phải từ 0 đến 100");
    }

    @Test
    void requiredEmpty_hasZeroConfidenceAndWarning_optionalEmptyKeepsBase() {
        CellNormalizer.Cell required = CellNormalizer.normalize(field(ExtractionDataType.PRODUCT, "name"), "  ", 0.95);
        CellNormalizer.Cell optional = CellNormalizer.normalize(field(ExtractionDataType.PRODUCT, "barcode"), null, 1.0);

        assertThat(required.value()).isNull();
        assertThat(required.confidence()).isZero();
        assertThat(required.warnings()).containsExactly("Thiếu Tên hàng hóa (bắt buộc)");
        assertThat(optional.value()).isNull();
        assertThat(optional.confidence()).isEqualTo(1.0);
        assertThat(optional.warnings()).isEmpty();
    }

    @Test
    void text_tooLongSkuPatternAndEmailAreFlagged() {
        CellNormalizer.Cell longName = CellNormalizer.normalize(field(ExtractionDataType.SUPPLIER, "code"),
                "N".repeat(31), 0.95);
        CellNormalizer.Cell badSku = CellNormalizer.normalize(field(ExtractionDataType.PRODUCT, "sku"),
                "SP 001", 0.95);
        CellNormalizer.Cell badEmail = CellNormalizer.normalize(field(ExtractionDataType.CUSTOMER, "email"),
                "abc@", 0.95);

        assertThat(longName.confidence()).isEqualTo(0.3);
        assertThat(longName.warnings()).containsExactly("Mã nhà cung cấp dài quá 30 ký tự");
        assertThat(badSku.confidence()).isEqualTo(0.4);
        assertThat(badSku.warnings()).hasSize(1).first().asString().contains("chỉ gồm chữ cái");
        assertThat(badEmail.confidence()).isEqualTo(0.4);
    }

    @Test
    void enum_acceptsCodeAndVietnameseWordsAccentInsensitive() {
        TargetField type = field(ExtractionDataType.CUSTOMER, "customerType");

        assertThat(CellNormalizer.normalize(type, "Bán lẻ", 0.9).value()).isEqualTo("RETAIL");
        assertThat(CellNormalizer.normalize(type, "dai ly", 0.9).value()).isEqualTo("WHOLESALE");
        assertThat(CellNormalizer.normalize(type, "wholesale", 0.9).value()).isEqualTo("WHOLESALE");
        CellNormalizer.Cell unknown = CellNormalizer.normalize(type, "VIP", 0.9);
        assertThat(unknown.value()).isEqualTo("VIP");
        assertThat(unknown.confidence()).isEqualTo(0.3);
        assertThat(unknown.warnings().get(0)).contains("WHOLESALE (sỉ), RETAIL (lẻ)");
    }

    @Test
    void foldRemovesVietnameseAccentsAndPunctuation() {
        assertThat(TextNormalizer.fold("  Đơn vị tính (ĐVT) ")).isEqualTo("don vi tinh dvt");
        assertThat(TextNormalizer.fold("Giá nhập/VNĐ")).isEqualTo("gia nhap vnd");
    }
}
