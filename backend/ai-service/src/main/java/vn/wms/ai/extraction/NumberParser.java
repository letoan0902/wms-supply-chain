package vn.wms.ai.extraction;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Đọc số viết theo thói quen Việt Nam lẫn quốc tế: "1.250.000", "1,250,000", "1.250.000,5", "12,5", "150.000 đ",
 * "10%", "(1.000)". Quy tắc khi chỉ có một loại dấu: xuất hiện nhiều lần là dấu phân cách hàng nghìn; xuất hiện
 * một lần và theo sau đúng ba chữ số (phần nguyên khác 0) là hàng nghìn; còn lại là dấu thập phân. Khi có cả hai loại
 * dấu thì dấu đứng sau cùng là dấu thập phân.
 */
public final class NumberParser {

    /** Kết quả đọc số. {@code percent} cho biết chuỗi gốc có ký hiệu %. */
    public record ParsedNumber(BigDecimal value, boolean percent) {
    }

    private static final Pattern CURRENCY = Pattern.compile("(?iu)(vn[dđ]|[đ₫])");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u202F]+");
    private static final Pattern NUMBER_CHARS = Pattern.compile("[+-]?[0-9.,]*[0-9][0-9.,]*");
    private static final Pattern SCIENTIFIC = Pattern.compile("[+-]?[0-9]+(\\.[0-9]+)?[eE][+-]?[0-9]+");
    private static final Pattern THOUSANDS_GROUPS = Pattern.compile("[+-]?[0-9]{1,3}(?:[.,][0-9]{3})+");

    private NumberParser() {
    }

    /** Đọc số từ chuỗi; không phải số thì rỗng. Giá trị trả về không có số mũ âm (an toàn khi ghi JSON). */
    public static Optional<ParsedNumber> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = SPACES.matcher(CURRENCY.matcher(raw).replaceAll("")).replaceAll("");
        if (text.isEmpty()) {
            return Optional.empty();
        }
        boolean percent = false;
        if (text.endsWith("%")) {
            percent = true;
            text = text.substring(0, text.length() - 1);
        }
        boolean negative = false;
        if (text.startsWith("(") && text.endsWith(")") && text.length() > 2) {
            negative = true;
            text = text.substring(1, text.length() - 1);
        }
        if (SCIENTIFIC.matcher(text).matches()) {
            return Optional.of(new ParsedNumber(clean(new BigDecimal(text), negative), percent));
        }
        if (!NUMBER_CHARS.matcher(text).matches()) {
            return Optional.empty();
        }
        String normalized = normalizeSeparators(text);
        if (normalized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ParsedNumber(clean(new BigDecimal(normalized), negative), percent));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** Đổi chuỗi số có dấu phân cách về dạng {@link BigDecimal} đọc được ("1234.5"); sai định dạng thì {@code null}. */
    static String normalizeSeparators(String text) {
        int lastDot = text.lastIndexOf('.');
        int lastComma = text.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            char decimal = lastDot > lastComma ? '.' : ',';
            char grouping = decimal == '.' ? ',' : '.';
            String integerPart = text.substring(0, Math.max(lastDot, lastComma));
            String fraction = text.substring(Math.max(lastDot, lastComma) + 1);
            if (integerPart.indexOf(decimal) >= 0 || fraction.indexOf(grouping) >= 0
                    || !THOUSANDS_GROUPS.matcher(integerPart).matches()) {
                return null;
            }
            return integerPart.replace(String.valueOf(grouping), "") + "." + fraction;
        }
        char separator = lastDot >= 0 ? '.' : (lastComma >= 0 ? ',' : 0);
        if (separator == 0) {
            return text;
        }
        long count = text.chars().filter(ch -> ch == separator).count();
        if (count > 1) {
            return THOUSANDS_GROUPS.matcher(text).matches() ? text.replace(String.valueOf(separator), "") : null;
        }
        int index = text.indexOf(separator);
        String integerPart = text.substring(0, index);
        String fraction = text.substring(index + 1);
        String digits = integerPart.replace("+", "").replace("-", "");
        boolean thousands = fraction.length() == 3 && !digits.isEmpty() && !digits.matches("0+");
        if (thousands) {
            return integerPart + fraction;
        }
        if (fraction.isEmpty()) {
            return integerPart.isEmpty() ? null : integerPart;
        }
        return (digits.isEmpty() ? integerPart + "0" : integerPart) + "." + fraction;
    }

    private static BigDecimal clean(BigDecimal value, boolean negative) {
        BigDecimal result = negative ? value.negate() : value;
        result = result.stripTrailingZeros();
        return result.scale() < 0 ? result.setScale(0) : result;
    }
}
