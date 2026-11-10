package vn.wms.auth.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    public static final String PLAN_FREE = "FREE";
    public static final String PLAN_TRIAL = "TRIAL";
    public static final String PLAN_PRO = "PRO";
    private static final long SECONDS_PER_DAY = 86_400L;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final SubscriptionCacheWriter cacheWriter;
    private final SubscriptionUsageReader usageReader;

    @Transactional
    public Subscription startTrial(Company company) {
        Plan trialPlan = planRepository.findById(PLAN_TRIAL)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cấu hình gói dùng thử (TRIAL)"));

        // Làm tròn tới giây để mốc lưu DB và chuỗi ISO-8601 trên Redis giống nhau (ví dụ 2026-12-09T03:00:00Z)
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
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

    /**
     * Kích hoạt hoặc gia hạn gói trả phí cho doanh nghiệp (dùng chung cho renew-mock và webhook PayOS ở WMS-23.5a).
     * Công thức mục 2.6: {@code expires_at = max(hiện tại, mốc hết hạn đang còn hiệu lực) + 30 hoặc 365 ngày};
     * mốc đang còn hiệu lực là ngày hết dùng thử (TRIAL) hoặc ngày hết hạn (PRO) khi thuê bao còn ACTIVE.
     * Sau đó đặt gói, trạng thái ACTIVE và ghi lại bộ nhớ đệm Redis.
     */
    @Transactional
    public Subscription activate(Long companyId, String planCode, BillingCycle cycle) {
        if (!PLAN_PRO.equals(planCode)) {
            throw ApiException.badRequest("Chỉ gói Chuyên Nghiệp (PRO) được kích hoạt qua thanh toán");
        }
        if (cycle == null) {
            throw ApiException.badRequest("Chu kỳ thanh toán không được để trống");
        }
        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông tin thuê bao của doanh nghiệp"));

        Plan plan = planRepository.findById(planCode)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy gói dịch vụ: " + planCode));

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long daysToAdd = (cycle == BillingCycle.YEARLY) ? 365L : 30L;

        // Công thức: expires_at = max(hiện tại, mốc hết hạn đang còn hiệu lực) + số ngày
        Instant currentExpiry = subscription.getStatus() == SubscriptionStatus.ACTIVE
                ? SubscriptionCacheWriter.effectiveExpiry(subscription)
                : null;
        Instant baseExpiry = (currentExpiry != null && currentExpiry.isAfter(now)) ? currentExpiry : now;

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

        // Gói đã quá hạn nhưng tác vụ định kỳ chưa chạy thì chuyển EXPIRED trước khi xét
        expireIfDue(subscription);

        // Chỉ cho phép chuyển về FREE khi đang TRIAL hoặc thuê bao đã EXPIRED (mục 2.6)
        boolean allowed = PLAN_TRIAL.equals(subscription.getPlan().getCode())
                || subscription.getStatus() == SubscriptionStatus.EXPIRED;
        if (!allowed) {
            throw ApiException.conflict(PLAN_PRO.equals(subscription.getPlan().getCode())
                    ? "Không thể chuyển về gói Khởi Đầu khi gói Chuyên Nghiệp đang còn hiệu lực"
                    : "Doanh nghiệp đang dùng gói Khởi Đầu");
        }

        Plan freePlan = planRepository.findById(PLAN_FREE)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cấu hình gói Khởi Đầu (FREE)"));

        subscription.setPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setTrialEndsAt(null);
        subscription.setExpiresAt(null);

        subscription = subscriptionRepository.save(subscription);
        syncToRedis(subscription);
        return subscription;
    }

    /** Chuyển thuê bao sang EXPIRED nếu đã quá hạn và ghi lại Redis; trả true khi có thay đổi. */
    @Transactional
    public boolean expireIfDue(Subscription subscription) {
        if (subscription == null || subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            return false;
        }
        Instant now = Instant.now();
        if (subscription.isExpired(now)) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            syncToRedis(subscription);
            log.info("Thuê bao của công ty ID {} đã chuyển sang trạng thái EXPIRED", subscription.getCompany().getId());
            return true;
        }
        return false;
    }

    /**
     * Dùng khi đăng nhập và làm mới token: đọc thuê bao của doanh nghiệp, chuyển EXPIRED nếu đã quá hạn
     * và luôn ghi lại bộ nhớ đệm Redis (mục 2.7). Doanh nghiệp chưa có thuê bao thì trả rỗng.
     */
    @Transactional
    public Optional<Subscription> refreshForLogin(Long companyId) {
        Optional<Subscription> subscription = subscriptionRepository.findByCompanyId(companyId);
        subscription.ifPresent(sub -> {
            if (!expireIfDue(sub)) {
                syncToRedis(sub);
            }
        });
        return subscription;
    }

    /** Thuê bao hiện tại của doanh nghiệp (thực thể), không thay đổi trạng thái. */
    @Transactional(readOnly = true)
    public Optional<Subscription> findByCompanyId(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId);
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

        // Số ngày còn lại làm tròn lên: vừa đăng ký dùng thử 30 ngày thì trả 30, không phải 29
        Instant now = Instant.now();
        Long daysRemaining = null;
        Instant expiry = SubscriptionCacheWriter.effectiveExpiry(subscription);
        if (expiry != null) {
            long seconds = Duration.between(now, expiry).getSeconds();
            daysRemaining = seconds > 0 ? (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY : 0L;
        }

        // Người dùng đếm trong cơ sở dữ liệu; kho, SKU, lượt AI đọc từ Redis do service nghiệp vụ ghi (mục 2.7)
        long activeUsers = userRepository.countByCompanyIdAndEnabledTrue(companyId);
        SubscriptionUsageReader.Usage usage = usageReader.read(companyId);

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
                        .warehouses(usage.warehouses())
                        .skus(usage.skus())
                        .aiRequests(usage.aiRequests())
                        .build())
                .build();
    }

    /** Ghi lại hash {@code wms:subscription:{companyId}} (xem {@link SubscriptionCacheWriter}). */
    public void syncToRedis(Subscription subscription) {
        cacheWriter.write(subscription);
    }
}
