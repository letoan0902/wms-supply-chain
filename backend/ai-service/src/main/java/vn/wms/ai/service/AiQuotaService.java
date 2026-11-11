package vn.wms.ai.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.wms.ai.client.AiReply;
import vn.wms.ai.config.AiProperties;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.dto.AiUsageResponse;
import vn.wms.common.plan.PlanLimit;
import vn.wms.common.plan.PlanLimitChecker;
import vn.wms.common.plan.SubscriptionCacheKeys;
import vn.wms.common.plan.SubscriptionSnapshot;
import vn.wms.common.tenant.TenantContext;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Hạn mức lượt AI theo gói của doanh nghiệp (docs/SAAS_SPEC.md, mục 2.5 và 2.7), bọc {@link PlanLimitChecker}.
 *
 * <p>Quy tắc đếm:
 * <ul>
 *   <li>{@link #checkQuota()} trước khi gọi mô hình: đã dùng hết thì {@code PLAN_LIMIT_EXCEEDED} với
 *       {@code limit = AI_REQUESTS};</li>
 *   <li>{@link #recordSuccess(AiReply)} sau khi có câu trả lời: tăng bộ đếm {@code wms:usage:ai:{companyId}:{yyyyMM}};</li>
 *   <li>lượt lỗi, quá thời gian chờ hoặc bị mô hình từ chối không bị tính;</li>
 *   <li>câu trả lời giả lập vẫn bị tính (để thử được giới hạn gói khi chưa có khóa) trừ khi
 *       {@code app.ai.mock-counts-usage=false}.</li>
 * </ul>
 * Thiếu bộ nhớ đệm thuê bao hoặc Redis lỗi thì cho qua (fail open), theo quy tắc chung của {@link PlanLimitChecker}.
 */
@Service
public class AiQuotaService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ObjectProvider<PlanLimitChecker> planLimitChecker;
    private final AiRuntime runtime;
    private final AiProperties properties;
    private final Clock clock;

    @Autowired
    public AiQuotaService(ObjectProvider<PlanLimitChecker> planLimitChecker, AiRuntime runtime,
                          AiProperties properties) {
        this(planLimitChecker, runtime, properties, Clock.system(SubscriptionCacheKeys.BUSINESS_ZONE));
    }

    AiQuotaService(ObjectProvider<PlanLimitChecker> planLimitChecker, AiRuntime runtime, AiProperties properties,
                   Clock clock) {
        this.planLimitChecker = planLimitChecker;
        this.runtime = runtime;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Kiểm tra doanh nghiệp hiện tại còn lượt AI trong tháng.
     *
     * @throws vn.wms.common.exception.ApiException {@code COMPANY_REQUIRED} khi chưa xác định được doanh nghiệp,
     *                                              {@code PLAN_LIMIT_EXCEEDED} khi đã dùng hết lượt
     */
    public void checkQuota() {
        TenantContext.requireCompanyId();
        PlanLimitChecker checker = planLimitChecker.getIfAvailable();
        if (checker != null) {
            checker.assertAiQuota();
        }
    }

    /**
     * Ghi nhận một lượt AI sau khi có câu trả lời. Câu trả lời bị từ chối không bị tính; câu trả lời giả lập chỉ
     * bị tính khi {@code app.ai.mock-counts-usage=true}.
     *
     * @return số lượt đã dùng trong tháng sau lần ghi nhận này
     */
    public long recordSuccess(AiReply reply) {
        PlanLimitChecker checker = planLimitChecker.getIfAvailable();
        if (checker == null) {
            return 0L;
        }
        if (reply.refused() || (runtime.isMock() && !properties.mockCountsUsage())) {
            return checker.currentMonthlyAiUsage();
        }
        long used = checker.incrementMonthlyAiUsage();
        // -1: không ghi được lên Redis (đã có cảnh báo trong log), trả số đọc được để giao diện vẫn hiển thị.
        return used >= 0 ? used : checker.currentMonthlyAiUsage();
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
