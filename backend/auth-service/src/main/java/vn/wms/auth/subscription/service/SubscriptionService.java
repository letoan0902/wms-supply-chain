package vn.wms.auth.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.subscription.dto.SubscriptionResponse;
import vn.wms.auth.subscription.entity.BillingCycle;
import vn.wms.auth.subscription.entity.Plan;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.entity.SubscriptionStatus;
import vn.wms.auth.subscription.repository.PlanRepository;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.common.exception.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public Subscription startTrial(Company company) {
        Plan trialPlan = planRepository.findById("TRIAL")
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cấu hình gói dùng thử (TRIAL)"));

        Instant now = Instant.now();
        Instant trialEndsAt = now.plus(30, ChronoUnit.DAYS);

        Subscription subscription = Subscription.builder()
                .company(company)
                .plan(trialPlan)
                .status(SubscriptionStatus.ACTIVE)
                .trialEndsAt(trialEndsAt)
                .expiresAt(null)
                .build();

        subscription = subscriptionRepository.save(subscription);
        syncToRedis(subscription);
        return subscription;
    }

    @Transactional
    public Subscription activate(Long companyId, String planCode, BillingCycle cycle) {
        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông tin thuê bao của doanh nghiệp"));

        Plan plan = planRepository.findById(planCode)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy gói dịch vụ: " + planCode));

        Instant now = Instant.now();
        long daysToAdd = (cycle == BillingCycle.YEARLY) ? 365L : 30L;

        // Công thức: expires_at = max(hiện tại, hạn đang có) + số ngày
        Instant baseExpiry = (subscription.getExpiresAt() != null && subscription.getExpiresAt().isAfter(now))
                ? subscription.getExpiresAt()
                : now;

        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setExpiresAt(baseExpiry.plus(daysToAdd, ChronoUnit.DAYS));
        subscription.setTrialEndsAt(null);

        subscription = subscriptionRepository.save(subscription);
        syncToRedis(subscription);
        return subscription;
    }

    @Transactional
    public Subscription downgradeToFree(Long companyId) {
        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông tin thuê bao của doanh nghiệp"));

        // Chỉ cho phép chuyển về FREE khi đang TRIAL hoặc thuê bao đã EXPIRED
        if ("PRO".equalsIgnoreCase(subscription.getPlan().getCode()) && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            throw ApiException.conflict("Không thể chuyển về gói Khởi Đầu khi gói Chuyên Nghiệp đang còn hiệu lực");
        }

        Plan freePlan = planRepository.findById("FREE")
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cấu hình gói Khởi Đầu (FREE)"));

        subscription.setPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setTrialEndsAt(null);
        subscription.setExpiresAt(null);

        subscription = subscriptionRepository.save(subscription);
        syncToRedis(subscription);
        return subscription;
    }

    @Transactional
    public void expireIfDue(Subscription subscription) {
        if (subscription == null || subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            return;
        }
        Instant now = Instant.now();
        if (subscription.isExpired(now)) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            syncToRedis(subscription);
            log.info("Thuê bao của công ty ID {} đã chuyển sang trạng thái EXPIRED", subscription.getCompany().getId());
        }
    }

    @Transactional
    public Subscription demoExpire(Long companyId) {
        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông tin thuê bao của doanh nghiệp"));

        Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        if (subscription.getTrialEndsAt() != null) {
            subscription.setTrialEndsAt(past);
        }
        if (subscription.getExpiresAt() != null) {
            subscription.setExpiresAt(past);
        }

        subscription = subscriptionRepository.save(subscription);
        syncToRedis(subscription);
        log.info("Demo expire kích hoạt thành công cho công ty ID {}", companyId);
        return subscription;
    }

    @Transactional
    public SubscriptionResponse getCurrent(Long companyId) {
        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông tin thuê bao của doanh nghiệp"));

        expireIfDue(subscription);

        Instant now = Instant.now();
        Long daysRemaining = null;
        if ("TRIAL".equalsIgnoreCase(subscription.getPlan().getCode()) && subscription.getTrialEndsAt() != null) {
            daysRemaining = subscription.getTrialEndsAt().isAfter(now)
                    ? Duration.between(now, subscription.getTrialEndsAt()).toDays()
                    : 0L;
        } else if ("PRO".equalsIgnoreCase(subscription.getPlan().getCode()) && subscription.getExpiresAt() != null) {
            daysRemaining = subscription.getExpiresAt().isAfter(now)
                    ? Duration.between(now, subscription.getExpiresAt()).toDays()
                    : 0L;
        }

        long activeUsers = userRepository.countByCompanyIdAndEnabledTrue(companyId);

        Plan plan = subscription.getPlan();

        return SubscriptionResponse.builder()
                .plan(plan.getCode())
                .planName(plan.getName())
                .status(subscription.getStatus().name())
                .trialEndsAt(subscription.getTrialEndsAt())
                .expiresAt(subscription.getExpiresAt())
                .daysRemaining(daysRemaining)
                .readOnly(subscription.getStatus() == SubscriptionStatus.EXPIRED)
                .limits(SubscriptionResponse.LimitsDto.builder()
                        .maxUsers(plan.getMaxUsers())
                        .maxWarehouses(plan.getMaxWarehouses())
                        .maxSkus(plan.getMaxSkus())
                        .maxAiRequests(plan.getMaxAiRequests())
                        .apiAccess(plan.getApiAccess())
                        .build())
                .usage(SubscriptionResponse.UsageDto.builder()
                        .users(activeUsers)
                        .warehouses(null)
                        .skus(null)
                        .aiRequests(null)
                        .build())
                .build();
    }

    public void syncToRedis(Subscription subscription) {
        if (subscription == null || subscription.getCompany() == null) {
            return;
        }
        try {
            String key = "wms:subscription:" + subscription.getCompany().getId();
            Map<String, String> map = new HashMap<>();
            map.put("plan", subscription.getPlan().getCode());
            map.put("status", subscription.getStatus().name());

            String expiryStr = "";
            if (subscription.getExpiresAt() != null) {
                expiryStr = subscription.getExpiresAt().toString();
            } else if (subscription.getTrialEndsAt() != null) {
                expiryStr = subscription.getTrialEndsAt().toString();
            }
            map.put("expiresAt", expiryStr);

            map.put("maxUsers", String.valueOf(subscription.getPlan().getMaxUsers()));
            map.put("maxWarehouses", String.valueOf(subscription.getPlan().getMaxWarehouses()));
            map.put("maxSkus", String.valueOf(subscription.getPlan().getMaxSkus()));
            map.put("maxAiRequests", String.valueOf(subscription.getPlan().getMaxAiRequests()));
            map.put("apiAccess", String.valueOf(subscription.getPlan().getApiAccess()));

            redisTemplate.opsForHash().putAll(key, map);
        } catch (Exception e) {
            log.warn("Không thể đồng bộ trạng thái thuê bao vào Redis: {}", e.getMessage());
        }
    }
}
