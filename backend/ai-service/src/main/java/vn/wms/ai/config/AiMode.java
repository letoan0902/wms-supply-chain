package vn.wms.ai.config;

/** Chế độ chạy của ai-service, trả trong {@code GET /api/ai/health}. */
public enum AiMode {
    /** Trả câu trả lời mẫu, không gọi Claude (không có khóa hoặc {@code AI_MOCK=true}). */
    MOCK,
    /** Gọi Claude thật qua SDK chính thức của Anthropic. */
    LIVE
}
