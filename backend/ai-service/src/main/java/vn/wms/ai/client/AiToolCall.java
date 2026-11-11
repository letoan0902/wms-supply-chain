package vn.wms.ai.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Một lần mô hình đề nghị gọi công cụ. Tham số là dữ liệu do mô hình sinh ra: bên thực thi phải tự kiểm tra trước khi
 * dùng.
 *
 * @param id    mã lần gọi, dùng để trả kết quả đúng chỗ
 * @param name  tên công cụ mô hình yêu cầu (có thể không nằm trong danh sách đã khai báo)
 * @param input tham số dạng JSON (object; rỗng khi mô hình không gửi tham số)
 */
public record AiToolCall(String id, String name, JsonNode input) {

    public AiToolCall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(input, "input");
    }

    /** Đọc một tham số chuỗi đã cắt khoảng trắng; {@code null} khi thiếu, rỗng hoặc không phải chuỗi, số. */
    public String text(String field) {
        JsonNode node = input.path(field);
        if (!node.isTextual() && !node.isNumber()) {
            return null;
        }
        String value = node.asText().strip();
        return value.isEmpty() ? null : value;
    }
}
