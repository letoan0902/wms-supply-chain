package vn.wms.auth.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.wms.auth.subscription.repository.SubscriptionRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    /**
     * Tác vụ quét tự động 5 phút một lần: chuyển EXPIRED các thuê bao đã quá hạn, sau đó ghi lại hash
     * {@code wms:subscription:{companyId}} của mọi thuê bao còn lại (WMS-23.2). Nhờ vậy Redis bị xóa dữ liệu hoặc
     * khởi động lại thì bộ nhớ đệm tự lành trong vòng 5 phút mà không cần ai đăng nhập lại. Số doanh nghiệp nhỏ nên
     * ghi lại toàn bộ mỗi lần chạy vẫn rẻ.
     *
     * <p>Mỗi thuê bao được xử lý trong một giao dịch ngắn riêng, đọc lại dưới khóa dòng
     * ({@link SubscriptionService#expireOrResync}); không có giao dịch dài bao cả lượt quét, nên một webhook PayOS commit
     * gói PRO trong lúc quét không bị ghi đè thành EXPIRED và Redis không nhận bản chụp cũ. Lỗi ở một thuê bao không
     * làm hỏng các thuê bao khác.
     *
     * <p>Redis không chạy thì {@link SubscriptionCacheWriter} chỉ ghi cảnh báo; việc chuyển EXPIRED trong cơ sở dữ
     * liệu vẫn được lưu.
     */
    @Scheduled(fixedRate = 300000)
    public void scanAndExpireSubscriptions() {
        int expired = 0;
        int refreshed = 0;
        for (Long id : subscriptionRepository.findAllIds()) {
            try {
                if (subscriptionService.expireOrResync(id)) {
                    expired++;
                } else {
                    refreshed++;
                }
            } catch (RuntimeException ex) {
                log.warn("Không xử lý được thuê bao ID {}: {}", id, ex.getMessage());
            }
        }
        if (expired > 0) {
            log.info("SubscriptionScheduler đã chuyển {} thuê bao quá hạn sang EXPIRED.", expired);
        }
        log.debug("SubscriptionScheduler đã ghi lại bộ nhớ đệm của {} thuê bao", refreshed);
    }
}
