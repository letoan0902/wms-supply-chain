package vn.wms.ai;

import vn.wms.ai.config.AiEffort;
import vn.wms.ai.config.AiProperties;

import java.time.Duration;
import java.util.Map;

/** Dữ liệu dùng chung cho test của ai-service. */
public final class AiTestSupport {

    private AiTestSupport() {
    }

    /** Cấu hình mặc định giống application.yml: giả lập, claude-sonnet-5-5, effort low, 4096 token. */
    public static AiProperties properties(boolean mock, boolean mockCountsUsage) {
        return new AiProperties(mock, "claude-sonnet-5-5", AiEffort.LOW, 4096, Duration.ofSeconds(60), 1, true,
                mockCountsUsage);
    }

    /** Nội dung hash {@code wms:subscription:{companyId}} do auth-service ghi. */
    public static Map<String, String> subscription(String plan, String maxAiRequests) {
        return Map.of("plan", plan, "status", "ACTIVE", "expiresAt", "",
                "maxUsers", "3", "maxWarehouses", "1", "maxSkus", "200",
                "maxAiRequests", maxAiRequests, "apiAccess", "false");
    }
}
