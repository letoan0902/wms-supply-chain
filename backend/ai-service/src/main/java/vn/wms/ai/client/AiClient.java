package vn.wms.ai.client;

import vn.wms.common.exception.ApiException;

/**
 * Cổng gọi mô hình AI. Hai cài đặt, chọn lúc khởi động theo {@code app.ai.mock} và biến {@code ANTHROPIC_API_KEY}:
 * {@link AnthropicAiClient} (gọi Claude thật) và {@link MockAiClient} (câu trả lời mẫu).
 *
 * <p>Service nghiệp vụ không gọi SDK trực tiếp: câu hỏi tự do dùng {@link #ask(AiRequest)}, tác vụ cần kết quả máy đọc
 * được (trích xuất tài liệu, WMS-24.2) dùng {@link #generateJson(AiJsonRequest)}. Cần thêm loại nội dung hay công cụ thì
 * mở rộng hai kiểu yêu cầu này.
 */
public interface AiClient {

    /**
     * Gửi một câu hỏi tới mô hình và chờ câu trả lời đầy đủ (không stream).
     *
     * @return câu trả lời; khi mô hình từ chối thì {@link AiReply#refused()} là {@code true} và
     *         {@link AiReply#text()} là lời xin lỗi soạn sẵn bằng tiếng Việt
     * @throws ApiException {@code SERVICE_UNAVAILABLE} khi không gọi được mô hình (quá thời gian chờ, mất kết nối,
     *                      khóa sai, vượt hạn mức của nhà cung cấp)
     */
    AiReply ask(AiRequest request);

    /**
     * Gửi nội dung (văn bản, PDF, hình ảnh) và yêu cầu mô hình trả về một đối tượng JSON đúng lược đồ.
     *
     * @return văn bản JSON cùng {@code stop_reason}; bên gọi tự quyết định xử lý khi bị từ chối hoặc bị cắt
     * @throws ApiException {@code SERVICE_UNAVAILABLE} khi không gọi được mô hình; {@code BAD_REQUEST} khi cài đặt
     *                      hiện tại không hỗ trợ (chế độ giả lập)
     */
    AiJsonReply generateJson(AiJsonRequest request);
}
