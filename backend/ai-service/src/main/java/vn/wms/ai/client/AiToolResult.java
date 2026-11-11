package vn.wms.ai.client;

import java.util.Objects;

/**
 * Kết quả thực thi một công cụ, gửi lại mô hình trong khối {@code tool_result}.
 *
 * @param content nội dung (JSON đã rút gọn hoặc câu thông báo)
 * @param error   {@code true} khi công cụ không lấy được dữ liệu (không tìm thấy, không có quyền, tham số sai...)
 */
public record AiToolResult(String content, boolean error) {

    public AiToolResult {
        Objects.requireNonNull(content, "content");
    }

    public static AiToolResult ok(String content) {
        return new AiToolResult(content, false);
    }

    public static AiToolResult error(String content) {
        return new AiToolResult(content, true);
    }
}
