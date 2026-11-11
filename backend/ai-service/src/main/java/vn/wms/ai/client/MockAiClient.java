package vn.wms.ai.client;

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
