package vn.wms.common.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;

import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Kiểm tra giới hạn của gói dịch vụ trước khi tạo mới (WMS-23.2, docs/SAAS_SPEC.md mục 2.4, 2.5, 2.7).
 *
 * <p>Cách dùng ở service nghiệp vụ: đếm số bản ghi đang hoạt động của doanh nghiệp (truy vấn JPQL đã tự lọc theo
 * doanh nghiệp) rồi gọi {@link #assertCanCreate(PlanLimit, long)} trước khi lưu; sau mỗi lần số lượng thay đổi
 * (tạo, bật lại, ngưng, xóa) gọi {@link #reportUsage(PlanLimit, long)} để auth-service hiển thị mức sử dụng.
 *
 * <pre>
 * planLimitChecker.assertCanCreate(PlanLimit.WAREHOUSES, warehouseRepository.countByActiveTrue());
 * </pre>
 *
 * <p>Khóa Redis đọc: hash {@code wms:subscription:{companyId}} (auth-service ghi). Khóa Redis ghi: hash
 * {@code wms:usage:{companyId}} (trường {@code users}, {@code warehouses}, {@code skus}) và bộ đếm
 * {@code wms:usage:ai:{companyId}:{yyyyMM}}.
 *
 * <p><b>Cho qua khi thiếu dữ liệu (fail open).</b> Khi hash thuê bao không tồn tại hoặc không kết nối được Redis,
 * thao tác được cho phép và chỉ ghi một dòng cảnh báo cho mỗi lần gọi. Lý do: auth-service ghi lại hash này mỗi lần
 * đăng nhập và định kỳ 5 phút, nên thiếu khóa chỉ xảy ra trong khoảng ngắn (Redis vừa khởi động lại, doanh nghiệp
 * chưa ai đăng nhập lại); chặn người dùng vì sự cố hạ tầng gây hại nhiều hơn để lọt vài bản ghi vượt giới hạn.
 */
public class PlanLimitChecker {

    /** Bộ đếm lượt AI tự hết hạn sau khoảng 40 ngày, đủ dài để còn xem được trong tháng kế tiếp. */
    static final Duration AI_USAGE_TTL = Duration.ofDays(40);

    private static final Logger log = LoggerFactory.getLogger(PlanLimitChecker.class);

    private final StringRedisTemplate redisTemplate;
    private final SubscriptionSnapshotReader snapshotReader;
    private final Clock clock;

    public PlanLimitChecker(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.system(SubscriptionCacheKeys.BUSINESS_ZONE));
    }

    /** Dùng trong test để cố định thời điểm (tính tháng của bộ đếm AI). */
    PlanLimitChecker(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.snapshotReader = new SubscriptionSnapshotReader(redisTemplate);
        this.clock = clock;
    }

    /**
     * Kiểm tra doanh nghiệp hiện tại còn được tạo thêm một bản ghi hay không, đồng thời ghi {@code currentCount}
     * vào hash mức sử dụng.
     *
     * @param limit        giới hạn cần kiểm tra
     * @param currentCount số bản ghi đang hoạt động TRƯỚC khi tạo mới
     * @throws ApiException {@link ErrorCode#COMPANY_REQUIRED} khi chưa xác định được doanh nghiệp;
     *                      {@link ErrorCode#PLAN_LIMIT_EXCEEDED} kèm {@link PlanLimitViolation} khi
     *                      {@code currentCount >= max} và {@code max != -1}
     */
    public void assertCanCreate(PlanLimit limit, long currentCount) {
        long companyId = TenantContext.requireCompanyId();
        SubscriptionSnapshot snapshot;
        try {
            if (limit.usageField() != null) {
                writeUsage(companyId, limit, currentCount);
            }
            snapshot = snapshotReader.read(companyId).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Không đọc được thuê bao của doanh nghiệp {} trên Redis, cho phép tạo {} (fail open): {}",
                    companyId, limit, ex.getMessage());
            return;
        }
        enforce(companyId, snapshot, limit, currentCount);
    }

    /**
     * Giống {@link #assertCanCreate(PlanLimit, long)} nhưng bỏ qua (không đếm, không kiểm tra) khi luồng hiện tại
     * chưa có doanh nghiệp. Dành cho service chưa chuyển sang mô hình nhiều doanh nghiệp (product-service trước
     * WMS-22.4b) để request không qua gateway hoặc test cũ không bị lỗi {@code COMPANY_REQUIRED}.
     *
     * @param currentCount hàm đếm, chỉ được gọi khi có doanh nghiệp
     */
    public void assertCanCreateIfTenant(PlanLimit limit, LongSupplier currentCount) {
        if (!TenantContext.isSet()) {
            log.debug("Bỏ qua kiểm tra giới hạn {} vì chưa xác định được doanh nghiệp", limit);
            return;
        }
        assertCanCreate(limit, currentCount.getAsLong());
    }

    /**
     * Ghi số lượng đang hoạt động vào hash {@code wms:usage:{companyId}} để auth-service hiển thị mức sử dụng.
     * Không có doanh nghiệp trong ngữ cảnh thì bỏ qua; lỗi Redis chỉ ghi cảnh báo, không làm hỏng nghiệp vụ.
     *
     * @throws IllegalArgumentException khi {@code limit} không ghi vào hash này ({@code AI_REQUESTS},
     *                                  {@code API_ACCESS})
     */
    public void reportUsage(PlanLimit limit, long count) {
        if (limit.usageField() == null) {
            throw new IllegalArgumentException("Giới hạn " + limit + " không ghi vào hash mức sử dụng");
        }
        Long companyId = TenantContext.getCompanyId();
        if (companyId == null) {
            log.debug("Bỏ qua ghi mức sử dụng {} vì chưa xác định được doanh nghiệp", limit);
            return;
        }
        try {
            writeUsage(companyId, limit, count);
        } catch (RuntimeException ex) {
            log.warn("Không ghi được mức sử dụng {} của doanh nghiệp {} lên Redis: {}",
                    limit, companyId, ex.getMessage());
        }
    }

    /**
     * Tăng bộ đếm lượt AI của tháng hiện tại (giờ Việt Nam) cho doanh nghiệp trong ngữ cảnh. Gọi sau khi lượt AI
     * thành công; lượt lỗi do hệ thống không tính.
     *
     * @return số lượt đã dùng trong tháng sau khi tăng, {@code -1} khi không ghi được lên Redis
     */
    public long incrementMonthlyAiUsage() {
        long companyId = TenantContext.requireCompanyId();
        String key = SubscriptionCacheKeys.aiUsageKey(companyId, currentMonth());
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redisTemplate.expire(key, AI_USAGE_TTL);
            }
            return value != null ? value : -1L;
        } catch (RuntimeException ex) {
            log.warn("Không tăng được bộ đếm lượt AI của doanh nghiệp {} trên Redis: {}", companyId, ex.getMessage());
            return -1L;
        }
    }

    /**
     * Kiểm tra doanh nghiệp còn lượt AI trong tháng hay không (gọi trước khi gửi yêu cầu tới mô hình AI).
     *
     * @throws ApiException {@link ErrorCode#PLAN_LIMIT_EXCEEDED} với {@code limit = AI_REQUESTS} khi đã dùng hết
     */
    public void assertAiQuota() {
        long companyId = TenantContext.requireCompanyId();
        SubscriptionSnapshot snapshot;
        long used;
        try {
            snapshot = snapshotReader.read(companyId).orElse(null);
            used = readAiUsage(companyId);
        } catch (RuntimeException ex) {
            log.warn("Không đọc được thuê bao hoặc lượt AI của doanh nghiệp {} trên Redis, cho phép (fail open): {}",
                    companyId, ex.getMessage());
            return;
        }
        enforce(companyId, snapshot, PlanLimit.AI_REQUESTS, used);
    }

    /**
     * Số lượt AI doanh nghiệp hiện tại đã dùng trong tháng, {@code 0} khi chưa có hoặc khi không đọc được Redis.
     */
    public long currentMonthlyAiUsage() {
        long companyId = TenantContext.requireCompanyId();
        try {
            return readAiUsage(companyId);
        } catch (RuntimeException ex) {
            log.warn("Không đọc được lượt AI của doanh nghiệp {} trên Redis: {}", companyId, ex.getMessage());
            return 0L;
        }
    }

    /** Ảnh chụp thuê bao của một doanh nghiệp, rỗng khi khóa không tồn tại hoặc Redis lỗi. */
    public Optional<SubscriptionSnapshot> findSnapshot(long companyId) {
        try {
            return snapshotReader.read(companyId);
        } catch (RuntimeException ex) {
            log.warn("Không đọc được thuê bao của doanh nghiệp {} trên Redis: {}", companyId, ex.getMessage());
            return Optional.empty();
        }
    }

    private void enforce(long companyId, SubscriptionSnapshot snapshot, PlanLimit limit, long current) {
        if (snapshot == null) {
            log.warn("Chưa có bộ nhớ đệm thuê bao {} trên Redis, cho phép {} (fail open)",
                    SubscriptionCacheKeys.subscriptionKey(companyId), limit);
            return;
        }
        long max = snapshot.maxOf(limit);
        if (max != SubscriptionCacheKeys.UNLIMITED && current >= max) {
            throw ApiException.withData(ErrorCode.PLAN_LIMIT_EXCEEDED, messageOf(snapshot, limit, max),
                    new PlanLimitViolation(limit, current, max, snapshot.plan()));
        }
    }

    static String messageOf(SubscriptionSnapshot snapshot, PlanLimit limit, long max) {
        if (limit == PlanLimit.API_ACCESS) {
            return "Gói " + snapshot.planDisplayName() + " không hỗ trợ API key. Nâng cấp gói để dùng API key.";
        }
        return "Gói " + snapshot.planDisplayName() + " chỉ cho phép tối đa " + max + " " + limit.unit()
                + ". Nâng cấp gói để " + limit.upgradeAction() + ".";
    }

    private void writeUsage(long companyId, PlanLimit limit, long count) {
        redisTemplate.opsForHash().put(SubscriptionCacheKeys.usageKey(companyId), limit.usageField(),
                String.valueOf(count));
    }

    private long readAiUsage(long companyId) {
        String value = redisTemplate.opsForValue().get(SubscriptionCacheKeys.aiUsageKey(companyId, currentMonth()));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private YearMonth currentMonth() {
        return YearMonth.from(clock.instant().atZone(SubscriptionCacheKeys.BUSINESS_ZONE));
    }
}
