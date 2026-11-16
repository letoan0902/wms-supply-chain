package vn.wms.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.wms.ai.config.AiProperties;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.dto.AiUsageResponse;
import vn.wms.common.plan.PlanLimit;
import vn.wms.common.plan.PlanLimitChecker;
import vn.wms.common.plan.SubscriptionCacheKeys;
import vn.wms.common.plan.SubscriptionSnapshot;
import vn.wms.common.tenant.TenantContext;

import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Hạn mức lượt AI theo gói của doanh nghiệp (docs/SAAS_SPEC.md, mục 2.5 và 2.7), bọc {@link PlanLimitChecker}.
 *
 * <p>Quy tắc đếm (giữ lượt trước, trả lại khi không tính):
 * <ul>
 *   <li>{@link #reserve()} trước khi gọi mô hình: đã dùng hết thì {@code PLAN_LIMIT_EXCEEDED} với
 *       {@code limit = AI_REQUESTS}; còn lượt thì tăng nguyên tử (Redis INCR) bộ đếm
 *       {@code wms:usage:ai:{companyId}:{yyyyMM}} rồi mới so với giới hạn, vượt thì trả lại lượt vừa giữ (DECR) và
 *       từ chối. Nhờ vậy nhiều yêu cầu song song của cùng doanh nghiệp không vượt được giới hạn gói;</li>
 *   <li>{@link Reservation#confirm(boolean)} sau khi có câu trả lời: giữ lượt; câu bị mô hình từ chối thì trả lại;</li>
 *   <li>lượt lỗi, quá thời gian chờ (ngoại lệ trước khi {@code confirm}) được trả lại khi đóng {@link Reservation}
 *       (dùng {@code try-with-resources}), nên vẫn không bị tính;</li>
 *   <li>câu trả lời giả lập vẫn bị tính (để thử được giới hạn gói khi chưa có khóa) trừ khi
 *       {@code app.ai.mock-counts-usage=false}: khi đó chỉ kiểm tra, không giữ lượt.</li>
 * </ul>
 * Thiếu bộ nhớ đệm thuê bao hoặc Redis lỗi thì cho qua (fail open), theo quy tắc chung của {@link PlanLimitChecker}.
 */
@Service
public class AiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaService.class);

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Bộ đếm tự hết hạn sau khoảng 40 ngày, giống {@code PlanLimitChecker.AI_USAGE_TTL}. */
    static final Duration AI_USAGE_TTL = Duration.ofDays(40);

    private final ObjectProvider<PlanLimitChecker> planLimitChecker;
    private final StringRedisTemplate redis;
    private final AiRuntime runtime;
    private final AiProperties properties;
    private final Clock clock;

    @Autowired
    public AiQuotaService(ObjectProvider<PlanLimitChecker> planLimitChecker, StringRedisTemplate redis,
                          AiRuntime runtime, AiProperties properties) {
        this(planLimitChecker, redis, runtime, properties, Clock.system(SubscriptionCacheKeys.BUSINESS_ZONE));
    }

    AiQuotaService(ObjectProvider<PlanLimitChecker> planLimitChecker, StringRedisTemplate redis, AiRuntime runtime,
                   AiProperties properties, Clock clock) {
        this.planLimitChecker = planLimitChecker;
        this.redis = redis;
        this.runtime = runtime;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Giữ một lượt AI của doanh nghiệp hiện tại trước khi gọi mô hình. Dùng với {@code try-with-resources}: gọi
     * {@link Reservation#confirm(boolean)} khi đã có câu trả lời; thoát khối mà chưa {@code confirm} (lỗi, quá thời
     * gian chờ) thì lượt được trả lại.
     *
     * @throws vn.wms.common.exception.ApiException {@code COMPANY_REQUIRED} khi chưa xác định được doanh nghiệp,
     *                                              {@code PLAN_LIMIT_EXCEEDED} khi đã dùng hết lượt
     */
    public Reservation reserve() {
        long companyId = TenantContext.requireCompanyId();
        PlanLimitChecker checker = planLimitChecker.getIfAvailable();
        if (checker == null) {
            return new Reservation(null, 0L);
        }
        // Đã hết lượt thì từ chối ngay, không chạm vào bộ đếm.
        checker.assertAiQuota();
        if (runtime.isMock() && !properties.mockCountsUsage()) {
            return new Reservation(null, 0L);
        }
        String key = SubscriptionCacheKeys.aiUsageKey(companyId,
                YearMonth.from(clock.instant().atZone(SubscriptionCacheKeys.BUSINESS_ZONE)));
        Long used;
        try {
            used = redis.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redis.expire(key, AI_USAGE_TTL);
            }
        } catch (RuntimeException ex) {
            log.warn("Không giữ được lượt AI của doanh nghiệp {} trên Redis, cho phép (fail open): {}",
                    companyId, ex.getMessage());
            return new Reservation(null, 0L);
        }
        if (used == null) {
            return new Reservation(null, 0L);
        }
        try {
            // So số lượt TRƯỚC lượt này với giới hạn (cùng thông báo, cùng dữ liệu lỗi như kiểm tra thường).
            checker.assertCanCreate(companyId, PlanLimit.AI_REQUESTS, used - 1);
        } catch (RuntimeException ex) {
            release(key);
            throw ex;
        }
        return new Reservation(key, used);
    }

    private void release(String key) {
        try {
            redis.opsForValue().decrement(key);
        } catch (RuntimeException ex) {
            log.warn("Không trả lại được lượt AI trên Redis: {}", ex.getMessage());
        }
    }

    /**
     * Một lượt AI đang giữ. Không an toàn khi dùng chung giữa nhiều luồng (mỗi yêu cầu một đối tượng).
     */
    public final class Reservation implements AutoCloseable {

        /** Khóa bộ đếm đã tăng, {@code null} khi không giữ lượt (giả lập không tính, Redis lỗi, chưa có checker). */
        private final String key;
        private final long used;
        private boolean settled;

        private Reservation(String key, long used) {
            this.key = key;
            this.used = used;
        }

        /**
         * Kết thúc lượt đã có câu trả lời: câu bị mô hình từ chối thì trả lại lượt, còn lại giữ lượt.
         *
         * @return số lượt đã dùng trong tháng sau lượt này
         */
        public long confirm(boolean refused) {
            if (settled) {
                return currentUsage();
            }
            settled = true;
            if (refused && key != null) {
                release(key);
            }
            return key != null && !refused ? used : currentUsage();
        }

        /** Kết thúc lượt đã hoàn tất mà không có câu bị từ chối (ví dụ một lần trích xuất tài liệu, WMS-24.2). */
        public long confirm() {
            return confirm(false);
        }

        /** Chưa {@code confirm} (lỗi, quá thời gian chờ, dữ liệu AI không dùng được) thì trả lại lượt đã giữ. */
        @Override
        public void close() {
            if (!settled) {
                settled = true;
                if (key != null) {
                    release(key);
                }
            }
        }
    }

    /** Số lượt đã dùng trong tháng của doanh nghiệp hiện tại, 0 khi chưa có hoặc Redis lỗi. */
    public long currentUsage() {
        PlanLimitChecker checker = planLimitChecker.getIfAvailable();
        return checker != null ? checker.currentMonthlyAiUsage() : 0L;
    }

    /** Giới hạn lượt AI của gói hiện tại, {@code -1} là không giới hạn, {@code null} khi chưa có bộ nhớ đệm thuê bao. */
    public Long currentMax() {
        return snapshot().map(s -> s.maxOf(PlanLimit.AI_REQUESTS)).orElse(null);
    }

    /** Mức dùng trong tháng cho {@code GET /api/ai/usage}. */
    public AiUsageResponse usage() {
        Optional<SubscriptionSnapshot> snapshot = snapshot();
        return new AiUsageResponse(
                currentUsage(),
                snapshot.map(s -> s.maxOf(PlanLimit.AI_REQUESTS)).orElse(null),
                YearMonth.now(clock.withZone(SubscriptionCacheKeys.BUSINESS_ZONE)).format(MONTH_FORMAT),
                snapshot.map(SubscriptionSnapshot::plan).orElse(null));
    }

    private Optional<SubscriptionSnapshot> snapshot() {
        long companyId = TenantContext.requireCompanyId();
        PlanLimitChecker checker = planLimitChecker.getIfAvailable();
        return checker != null ? checker.findSnapshot(companyId) : Optional.empty();
    }
}
