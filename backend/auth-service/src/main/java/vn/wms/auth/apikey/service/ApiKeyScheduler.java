package vn.wms.auth.apikey.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hai tác vụ định kỳ của API key (WMS-25.1):
 *
 * <ul>
 *   <li>Mỗi {@code app.api-key.sync-interval-ms} (mặc định 5 phút): ghi lại khóa tra cứu của mọi key đang hoạt động và
 *       xóa khóa của key đã thu hồi. Redis bị xóa dữ liệu hoặc khởi động lại thì gateway nhận lại key trong vòng một
 *       chu kỳ; một lần xóa bị lỗi lúc thu hồi cũng được sửa ở chu kỳ kế tiếp. Cùng ý tưởng với
 *       {@code SubscriptionScheduler}.</li>
 *   <li>Mỗi {@code app.api-key.last-used-flush-ms} (mặc định 1 phút): đổ thời điểm dùng gần nhất do gateway ghi trên
 *       Redis vào cột {@code last_used_at}, thay vì ghi cơ sở dữ liệu ở mỗi request.</li>
 * </ul>
 *
 * <p>Redis không chạy thì chỉ ghi cảnh báo, không ném lỗi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyScheduler {

    private final ApiKeyService apiKeyService;

    @Scheduled(initialDelayString = "${app.api-key.sync-initial-delay-ms:60000}",
            fixedRateString = "${app.api-key.sync-interval-ms:300000}")
    public void syncToRedis() {
        try {
            int count = apiKeyService.syncAllToRedis();
            log.debug("Đã đồng bộ {} API key lên Redis", count);
        } catch (RuntimeException ex) {
            log.warn("Không đồng bộ được API key lên Redis: {}", ex.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${app.api-key.last-used-flush-ms:60000}",
            fixedRateString = "${app.api-key.last-used-flush-ms:60000}")
    public void flushLastUsed() {
        try {
            int updated = apiKeyService.flushLastUsed();
            if (updated > 0) {
                log.debug("Đã cập nhật lần dùng gần nhất của {} API key", updated);
            }
        } catch (RuntimeException ex) {
            log.warn("Không cập nhật được lần dùng gần nhất của API key: {}", ex.getMessage());
        }
    }
}
