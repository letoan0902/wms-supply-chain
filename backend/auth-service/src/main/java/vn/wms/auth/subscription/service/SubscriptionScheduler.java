package vn.wms.auth.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.entity.SubscriptionStatus;
import vn.wms.auth.subscription.repository.SubscriptionRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * <p>Redis không chạy thì {@link SubscriptionCacheWriter} chỉ ghi cảnh báo; việc chuyển EXPIRED trong cơ sở dữ
     * liệu vẫn được lưu.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void scanAndExpireSubscriptions() {
        Instant now = Instant.now();
        Set<Long> expiredIds = new HashSet<>();

        List<Subscription> expiredTrials = subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(SubscriptionStatus.ACTIVE, now);
        for (Subscription sub : expiredTrials) {
            if (subscriptionService.expireIfDue(sub)) {
                expiredIds.add(sub.getId());
            }
        }

        List<Subscription> expiredPros = subscriptionRepository.findAllByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, now);
        for (Subscription sub : expiredPros) {
            if (subscriptionService.expireIfDue(sub)) {
                expiredIds.add(sub.getId());
            }
        }

        if (!expiredTrials.isEmpty() || !expiredPros.isEmpty()) {
            log.info("SubscriptionScheduler đã quét và cập nhật {} gói dùng thử và {} gói Pro quá hạn.",
                    expiredTrials.size(), expiredPros.size());
        }

        refreshCache(expiredIds);
    }

    /** Ghi lại bộ nhớ đệm của mọi thuê bao, bỏ qua các thuê bao vừa chuyển EXPIRED (đã ghi ở bước trên). */
    private void refreshCache(Set<Long> alreadyWritten) {
        int refreshed = 0;
        for (Subscription sub : subscriptionRepository.findAll()) {
            if (alreadyWritten.contains(sub.getId())) {
                continue;
            }
            try {
                subscriptionService.syncToRedis(sub);
                refreshed++;
            } catch (RuntimeException ex) {
                log.warn("Không ghi lại được bộ nhớ đệm thuê bao ID {}: {}", sub.getId(), ex.getMessage());
            }
        }
        log.debug("SubscriptionScheduler đã ghi lại bộ nhớ đệm của {} thuê bao", refreshed);
    }
}
