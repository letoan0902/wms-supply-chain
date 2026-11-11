package vn.wms.ai.config;

/**
 * Mức suy nghĩ của Claude, gửi qua {@code output_config.effort}. Chỉ cho phép ba mức; cấu hình sai (ví dụ
 * {@code AI_EFFORT=max}) làm service không khởi động được thay vì âm thầm tốn thêm chi phí.
 */
public enum AiEffort {
    LOW,
    MEDIUM,
    HIGH
}
