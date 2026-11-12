package vn.wms.ai.config;

/**
 * Chế độ chạy đã chốt lúc khởi động. Không chứa khóa API.
 *
 * @param mode  {@link AiMode#MOCK} hoặc {@link AiMode#LIVE}
 * @param model mã mô hình Claude đang cấu hình
 */
public record AiRuntime(AiMode mode, String model) {

    public boolean isMock() {
        return mode == AiMode.MOCK;
    }
}
