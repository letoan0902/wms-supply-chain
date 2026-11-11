package vn.wms.ai.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Một công cụ (tool) mô hình được phép gọi trong {@link AiClient#chat(AiChatRequest, AiToolExecutor)}. Công cụ do
 * ai-service tự thực thi; mô hình chỉ đề nghị tên công cụ và tham số.
 *
 * @param name        tên công cụ (chữ thường, gạch dưới), duy nhất trong một yêu cầu
 * @param description mô tả khi nào nên gọi và công cụ trả về gì
 * @param properties  lược đồ JSON của từng tham số (khóa là tên tham số)
 * @param required    các tham số bắt buộc
 */
public record AiTool(String name, String description, Map<String, Object> properties, List<String> required) {

    public AiTool {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        // Thứ tự cố định (theo tên tham số) để định nghĩa công cụ gửi đi giống hệt nhau giữa các lần gọi và các máy
        // chạy service, nếu không bộ nhớ đệm prompt sẽ không dùng lại được.
        properties = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(properties, "properties")));
        required = List.copyOf(Objects.requireNonNull(required, "required"));
    }
}
