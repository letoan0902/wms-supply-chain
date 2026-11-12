package vn.wms.product.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Sinh mã cho danh mục và đơn vị tính được tạo tự động từ tên khi nhập hàng loạt: bỏ dấu tiếng Việt, viết hoa, thay
 * ký tự khác chữ và số bằng dấu gạch ngang, cắt theo độ dài cột. Mã đã có trong doanh nghiệp thì thêm hậu tố
 * {@code -2}, {@code -3}... Ví dụ "Thùng carton" thành {@code THUNG-CARTON}.
 */
final class CatalogCodeGenerator {

    /** Số lần thử hậu tố tối đa, đủ lớn để không bao giờ chạm tới với dữ liệu thật. */
    private static final int MAX_ATTEMPTS = 1000;

    private CatalogCodeGenerator() {
        // Lớp tiện ích không khởi tạo
    }

    /**
     * Mã chưa tồn tại sinh từ tên.
     *
     * @param name      tên gốc
     * @param maxLength độ dài tối đa của cột mã
     * @param fallback  mã dùng khi tên không còn ký tự chữ, số nào sau khi chuẩn hóa
     * @param exists    hàm kiểm tra mã đã có trong doanh nghiệp hiện tại
     */
    static String uniqueCode(String name, int maxLength, String fallback, Predicate<String> exists) {
        String base = toCode(name, maxLength, fallback);
        if (!exists.test(base)) {
            return base;
        }
        for (int attempt = 2; attempt <= MAX_ATTEMPTS; attempt++) {
            String suffix = "-" + attempt;
            String candidate = truncate(base, maxLength - suffix.length()) + suffix;
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không sinh được mã chưa dùng cho tên: " + name);
    }

    /** Chuẩn hóa tên thành mã gồm chữ in hoa không dấu, số và dấu gạch ngang. */
    static String toCode(String name, int maxLength, String fallback) {
        String source = name == null ? "" : name.replace('đ', 'd').replace('Đ', 'D');
        String withoutAccents = Normalizer.normalize(source, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String code = withoutAccents.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (code.isEmpty()) {
            code = fallback;
        }
        return truncate(code, maxLength);
    }

    private static String truncate(String code, int maxLength) {
        if (code.length() <= maxLength) {
            return code;
        }
        return code.substring(0, maxLength).replaceAll("-+$", "");
    }
}
