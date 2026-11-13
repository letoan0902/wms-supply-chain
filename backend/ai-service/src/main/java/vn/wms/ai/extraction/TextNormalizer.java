package vn.wms.ai.extraction;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Chuẩn hóa chuỗi để so khớp không phân biệt hoa thường và dấu tiếng Việt ("Đơn vị tính" thành "don vi tinh"). */
public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private TextNormalizer() {
    }

    /** Bỏ dấu, đổi đ thành d, viết thường, thay mọi ký tự không phải chữ, số bằng một khoảng trắng. */
    public static String fold(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return NON_ALPHANUMERIC.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
