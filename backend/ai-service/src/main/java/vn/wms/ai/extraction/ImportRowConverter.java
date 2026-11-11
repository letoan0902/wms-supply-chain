package vn.wms.ai.extraction;

import vn.wms.ai.dto.ImportRequest;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Đổi các dòng người dùng đã sửa thành {@code items} của API nhập hàng loạt: chỉ giữ trường của loại dữ liệu, bỏ ô
 * trống, đổi chuỗi số ("150.000") thành số và cách viết loại khách hàng ("bán lẻ") thành mã ({@code RETAIL}).
 *
 * <p>Ô số hoặc ô loại khách hàng không đọc được làm cả lần nhập trả {@code 400 VALIDATION_ERROR} kèm danh sách ô lỗi:
 * nếu gửi đi, Jackson ở service phía sau sẽ từ chối cả body thay vì báo lỗi từng dòng. Các kiểm tra còn lại (bắt buộc,
 * độ dài, trùng mã) do API nhập hàng loạt làm cho từng dòng.
 */
public final class ImportRowConverter {

    private ImportRowConverter() {
    }

    public static List<Map<String, Object>> toItems(ExtractionDataType type, List<ImportRequest.Row> rows) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, String> errors = new LinkedHashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> values = rows.get(index).values();
            Map<String, Object> item = new LinkedHashMap<>();
            for (TargetField field : type.fields()) {
                Object value = values.get(field.name());
                if (value == null || (value instanceof String text && text.isBlank())) {
                    continue;
                }
                String errorKey = "rows[" + index + "].values." + field.name();
                convert(field, value).ifPresentOrElse(
                        converted -> item.put(field.name(), converted),
                        () -> errors.put(errorKey, errorMessage(field)));
            }
            items.add(item);
        }
        if (!errors.isEmpty()) {
            throw ApiException.withData(ErrorCode.VALIDATION_ERROR,
                    "Có ô không đúng kiểu dữ liệu, hãy sửa trong bảng xem trước rồi nhập lại.", errors);
        }
        return items;
    }

    static Optional<Object> convert(TargetField field, Object value) {
        return switch (field.kind()) {
            case NUMBER, PERCENT -> number(value);
            case ENUM -> value instanceof String text
                    ? CellNormalizer.matchOption(field, text).map(option -> (Object) option)
                    : Optional.empty();
            case TEXT, SKU, EMAIL -> text(value);
        };
    }

    private static Optional<Object> number(Object value) {
        if (value instanceof BigDecimal decimal) {
            return Optional.of(decimal);
        }
        if (value instanceof Number number) {
            // Số JSON đọc vào Map là Integer, Long hoặc Double: đổi qua chuỗi để không mang sai số nhị phân.
            BigDecimal decimal = new BigDecimal(number.toString()).stripTrailingZeros();
            return Optional.of(decimal.scale() < 0 ? decimal.setScale(0) : decimal);
        }
        if (value instanceof String text) {
            return NumberParser.parse(text).map(parsed -> (Object) parsed.value());
        }
        return Optional.empty();
    }

    private static Optional<Object> text(Object value) {
        if (value instanceof String text) {
            return Optional.of(text.strip());
        }
        if (value instanceof BigDecimal decimal) {
            return Optional.of(decimal.toPlainString());
        }
        if (value instanceof Number || value instanceof Boolean) {
            return Optional.of(value.toString());
        }
        return Optional.empty();
    }

    private static String errorMessage(TargetField field) {
        return switch (field.kind()) {
            case NUMBER, PERCENT -> field.label() + " phải là số";
            case ENUM -> field.label() + " phải là một trong: " + String.join(", ", field.options().keySet());
            default -> field.label() + " không hợp lệ";
        };
    }
}
