package vn.wms.ai.client;

import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

/**
 * Cài đặt giả lập: không gọi mạng, luôn trả cùng một câu trả lời mẫu cho cùng một câu hỏi (nhắc lại một đoạn ngắn
 * của câu hỏi) và 0 token. Dùng khi chưa có khóa AI để nhóm phát triển giao diện và thử giới hạn gói.
 */
public class MockAiClient implements AiClient {

    /** Số ký tự tối đa của đoạn câu hỏi được nhắc lại. */
    static final int EXCERPT_LENGTH = 80;

    static final String STOP_REASON = "end_turn";

    @Override
    public AiReply ask(AiRequest request) {
        String answer = "[Chế độ giả lập] Bạn hỏi: \"" + excerpt(request.userMessage()) + "\". "
                + "Đây là câu trả lời mẫu, hệ thống chưa gọi Claude. Khi quản trị viên cấu hình khóa AI và tắt "
                + "chế độ giả lập, trợ lý sẽ trả lời thật tại đây.";
        return AiReply.answered(answer, STOP_REASON, 0L, 0L, false);
    }

    /**
     * Chế độ giả lập không bịa kết quả JSON (ví dụ dòng dữ liệu đọc từ PDF). Việc ghép cột của file Excel, CSV ở chế
     * độ giả lập được làm tại chỗ theo tên cột ({@code vn.wms.ai.extraction.HeaderSynonymMatcher}), không qua đây.
     */
    @Override
    public AiJsonReply generateJson(AiJsonRequest request) {
        throw new ApiException(ErrorCode.BAD_REQUEST, NEEDS_REAL_AI_MESSAGE);
    }

    /**
     * Trợ lý AI ở chế độ giả lập không đi qua đây: {@code vn.wms.ai.assistant.MockAssistant} tự nhận diện câu hỏi và
     * chạy thẳng công cụ tra cứu, nên không có câu trả lời nào được bịa ra.
     */
    @Override
    public AiChatReply chat(AiChatRequest request, AiToolExecutor executor) {
        throw new ApiException(ErrorCode.BAD_REQUEST, "Chế độ AI giả lập không hỗ trợ hội thoại có công cụ.");
    }

    /** Thông báo khi một chức năng chỉ chạy được với Claude thật. */
    public static final String NEEDS_REAL_AI_MESSAGE = "Hệ thống đang chạy chế độ AI giả lập nên chỉ đọc được file "
            + "Excel (xlsx, xls) và CSV. File PDF và hình ảnh cần AI thật: quản trị viên cần cấu hình khóa AI và tắt "
            + "chế độ giả lập. Lượt này không bị tính.";

    /** Gộp khoảng trắng và cắt câu hỏi còn tối đa {@value #EXCERPT_LENGTH} ký tự. */
    static String excerpt(String question) {
        String normalized = question == null ? "" : question.strip().replaceAll("\\s+", " ");
        if (normalized.codePointCount(0, normalized.length()) <= EXCERPT_LENGTH) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, EXCERPT_LENGTH);
        return normalized.substring(0, end).stripTrailing() + "...";
    }
}
