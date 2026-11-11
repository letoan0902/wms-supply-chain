package vn.wms.ai.extraction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Chuẩn hóa và kiểm tra một ô theo trường đích, tính độ tin cậy cuối cùng của ô: lấy độ tin cậy gốc (của việc ghép
 * cột hoặc do Claude đánh giá) rồi hạ xuống khi kiểm tra tại chỗ phát hiện vấn đề, kèm cảnh báo tiếng Việt.
 */
public final class CellNormalizer {

    /** Kết quả chuẩn hóa một ô. {@code value} là chuỗi, {@link BigDecimal} hoặc {@code null}. */
    public record Cell(Object value, double confidence, List<String> warnings) {
    }

    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Za-z0-9-_]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CellNormalizer() {
    }

    /**
     * @param field          trường đích
     * @param raw            giá trị gốc đọc từ file (có thể {@code null})
     * @param baseConfidence độ tin cậy gốc, từ 0 đến 1
     */
    public static Cell normalize(TargetField field, String raw, double baseConfidence) {
        double base = clamp(baseConfidence);
        String text = raw == null ? "" : WHITESPACE.matcher(raw).replaceAll(" ").strip();
        if (text.isEmpty()) {
            if (field.required()) {
                return new Cell(null, 0.0, List.of("Thiếu " + field.label() + " (bắt buộc)"));
            }
            return new Cell(null, round(base), List.of());
        }
        return switch (field.kind()) {
            case TEXT, SKU, EMAIL -> text(field, text, base);
            case NUMBER -> number(field, text, base);
            case PERCENT -> percent(field, text, base);
            case ENUM -> enumeration(field, text, base);
        };
    }

    private static Cell text(TargetField field, String text, double base) {
        double confidence = base;
        List<String> warnings = new ArrayList<>();
        if (field.maxLength() != null && text.codePointCount(0, text.length()) > field.maxLength()) {
            confidence = Math.min(confidence, 0.3);
            warnings.add(field.label() + " dài quá " + field.maxLength() + " ký tự");
        }
        if (field.kind() == FieldKind.SKU && !SKU_PATTERN.matcher(text).matches()) {
            confidence = Math.min(confidence, 0.4);
            warnings.add(field.label() + " chỉ gồm chữ cái không dấu, số, dấu gạch ngang và gạch dưới");
        }
        if (field.kind() == FieldKind.EMAIL && !EMAIL_PATTERN.matcher(text).matches()) {
            confidence = Math.min(confidence, 0.4);
            warnings.add(field.label() + " \"" + text + "\" không đúng định dạng email");
        }
        return new Cell(text, round(confidence), warnings);
    }

    private static Cell number(TargetField field, String text, double base) {
        Optional<NumberParser.ParsedNumber> parsed = NumberParser.parse(text);
        if (parsed.isEmpty() || parsed.get().percent()) {
            return new Cell(text, round(Math.min(base, 0.2)),
                    List.of(field.label() + " \"" + text + "\" không phải là số"));
        }
        BigDecimal value = parsed.get().value();
        double confidence = base;
        List<String> warnings = new ArrayList<>();
        if (value.signum() < 0) {
            confidence = Math.min(confidence, 0.3);
            warnings.add(field.label() + " không được âm");
        }
        if (field.scale() != null && value.scale() > field.scale()) {
            confidence = Math.min(confidence, 0.5);
            warnings.add(field.label() + " chỉ được tối đa " + field.scale() + " chữ số thập phân");
        }
        return new Cell(value, round(confidence), warnings);
    }

    private static Cell percent(TargetField field, String text, double base) {
        Optional<NumberParser.ParsedNumber> parsed = NumberParser.parse(text);
        if (parsed.isEmpty()) {
            return new Cell(text, round(Math.min(base, 0.2)),
                    List.of(field.label() + " \"" + text + "\" không phải là số"));
        }
        BigDecimal value = parsed.get().value();
        double confidence = base;
        List<String> warnings = new ArrayList<>();
        if (!parsed.get().percent() && value.signum() > 0 && value.compareTo(BigDecimal.ONE) < 0) {
            // 0,1 trong cột thuế suất gần như chắc chắn là 10% viết dạng tỉ lệ.
            value = clean(value.multiply(HUNDRED));
            confidence = Math.min(confidence, 0.7);
            warnings.add(field.label() + " \"" + text + "\" được hiểu là " + value.toPlainString() + "%");
        }
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            confidence = Math.min(confidence, 0.3);
            warnings.add(field.label() + " phải từ 0 đến 100");
        }
        if (field.scale() != null && value.scale() > field.scale()) {
            confidence = Math.min(confidence, 0.5);
            warnings.add(field.label() + " chỉ được tối đa " + field.scale() + " chữ số thập phân");
        }
        return new Cell(value, round(confidence), warnings);
    }

    private static Cell enumeration(TargetField field, String text, double base) {
        Optional<String> option = matchOption(field, text);
        if (option.isPresent()) {
            return new Cell(option.get(), round(base), List.of());
        }
        String choices = field.options().entrySet().stream()
                .map(entry -> entry.getKey() + " (" + entry.getValue().get(0) + ")")
                .collect(Collectors.joining(", "));
        return new Cell(text, round(Math.min(base, 0.3)),
                List.of(field.label() + " \"" + text + "\" không rõ, chọn một trong: " + choices));
    }

    /** Tìm giá trị hợp lệ của trường ENUM theo mã ("RETAIL") hoặc cách viết tương đương ("bán lẻ"). */
    public static Optional<String> matchOption(TargetField field, String text) {
        String folded = TextNormalizer.fold(text);
        for (Map.Entry<String, List<String>> entry : field.options().entrySet()) {
            if (TextNormalizer.fold(entry.getKey()).equals(folded)) {
                return Optional.of(entry.getKey());
            }
            for (String synonym : entry.getValue()) {
                if (TextNormalizer.fold(synonym).equals(folded)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    static double round(double value) {
        return Math.round(clamp(value) * 100.0) / 100.0;
    }

    private static BigDecimal clean(BigDecimal value) {
        BigDecimal result = value.stripTrailingZeros();
        return result.scale() < 0 ? result.setScale(0) : result;
    }
}
