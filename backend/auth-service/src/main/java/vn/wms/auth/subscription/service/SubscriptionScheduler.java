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
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    /**
     * Tác vụ quét tự động 5 phút một lần để xử lý các thuê bao đã quá hạn.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void scanAndExpireSubscriptions() {
        Instant now = Instant.now();

        List<Subscription> expiredTrials = subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(SubscriptionStatus.ACTIVE, now);
        for (Subscription sub : expiredTrials) {
            subscriptionService.expireIfDue(sub);
        }

        List<Subscription> expiredPros = subscriptionRepository.findAllByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, now);
        for (Subscription sub : expiredPros) {
            subscriptionService.expireIfDue(sub);
        }

        if (!expiredTrials.isEmpty() || !expiredPros.isEmpty()) {
            log.info("SubscriptionScheduler đã quét và cập nhật {} gói dùng thử và {} gói Pro quá hạn.",
                    expiredTrials.size(), expiredPros.size());
        }
    }
}
