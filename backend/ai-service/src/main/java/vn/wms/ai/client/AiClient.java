package vn.wms.ai.client;

import vn.wms.common.exception.ApiException;

/**
 * Cổng gọi mô hình AI. Hai cài đặt, chọn lúc khởi động theo {@code app.ai.mock} và biến {@code ANTHROPIC_API_KEY}:
 * {@link AnthropicAiClient} (gọi Claude thật) và {@link MockAiClient} (câu trả lời mẫu).
 *
 * <p>WMS-24.2 (trích xuất tài liệu) và WMS-24.4 (trợ lý) dùng lại giao diện này; khi cần thêm tài liệu, hình ảnh hay
 * công cụ thì mở rộng {@link AiRequest}, không gọi SDK trực tiếp từ service nghiệp vụ.
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
}
