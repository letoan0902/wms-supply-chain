package vn.wms.ai.extraction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Một trường của dòng gửi API nhập hàng loạt (docs/SAAS_SPEC.md, mục WMS-22.4b).
 *
 * @param name      tên trường đúng như API nhập hàng loạt nhận, ví dụ {@code purchasePrice}
 * @param label     nhãn tiếng Việt cho giao diện và thông báo
 * @param kind      kiểu giá trị
 * @param required  bắt buộc có giá trị
 * @param maxLength độ dài tối đa (trường chuỗi), {@code null} nếu không giới hạn
 * @param scale     số chữ số thập phân tối đa (trường số), {@code null} với trường chuỗi
 * @param options   giá trị hợp lệ của trường {@link FieldKind#ENUM} kèm các cách viết tương đương (tiếng Việt, tiếng
 *                  Anh); rỗng với kiểu khác
 * @param synonyms  tên cột thường gặp (tiếng Việt có dấu, tiếng Anh), dùng ở chế độ giả lập và gợi ý cho Claude
 */
public record TargetField(String name, String label, FieldKind kind, boolean required, Integer maxLength,
                          Integer scale, Map<String, List<String>> options, List<String> synonyms) {

    public TargetField {
        // Giữ thứ tự khai báo của các giá trị hợp lệ (hiển thị và lược đồ JSON ổn định).
        options = options == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
        synonyms = List.copyOf(synonyms);
    }

    static TargetField text(String name, String label, boolean required, int maxLength, String... synonyms) {
        return new TargetField(name, label, FieldKind.TEXT, required, maxLength, null, null, List.of(synonyms));
    }

    static TargetField sku(String name, String label, int maxLength, String... synonyms) {
        return new TargetField(name, label, FieldKind.SKU, true, maxLength, null, null, List.of(synonyms));
    }

    static TargetField email(String name, String label, int maxLength, String... synonyms) {
        return new TargetField(name, label, FieldKind.EMAIL, false, maxLength, null, null, List.of(synonyms));
    }

    static TargetField number(String name, String label, int scale, String... synonyms) {
        return new TargetField(name, label, FieldKind.NUMBER, false, null, scale, null, List.of(synonyms));
    }

    static TargetField percent(String name, String label, String... synonyms) {
        return new TargetField(name, label, FieldKind.PERCENT, false, null, 2, null, List.of(synonyms));
    }

    static TargetField enumeration(String name, String label, boolean required, Map<String, List<String>> options,
                                   String... synonyms) {
        return new TargetField(name, label, FieldKind.ENUM, required, null, null, options, List.of(synonyms));
    }

    /** Trường kiểu số (số hoặc phần trăm). */
    public boolean numeric() {
        return kind == FieldKind.NUMBER || kind == FieldKind.PERCENT;
    }
}
