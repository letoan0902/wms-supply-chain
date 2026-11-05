package vn.wms.notification.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * Bộ đếm KPI trong ngày (theo giờ Việt Nam) lưu ở Redis, tăng theo từng sự kiện RabbitMQ.
 *
 * <ul>
 *   <li>Mỗi ngày một hash {@code wms:notification:kpi:<yyyy-MM-dd>}, trường là tên chỉ số trong {@link KpiMetric}
 *       cộng thêm {@code updatedAt}; hash hết hạn sau 2 ngày.</li>
 *   <li>Chống đếm trùng khi broker giao lại: {@code SET NX} khóa {@code wms:notification:kpi:processed:<messageId>}
 *       giữ 1 ngày. Sự kiện không có messageId thì cứ đếm.</li>
 *   <li>Redis lỗi không làm hỏng việc tiêu thụ sự kiện: {@link #increment} ghi log warn và trả rỗng.</li>
 * </ul>
 */
@Service
public class RealtimeKpiService {

    /** Múi giờ chốt ngày của bộ đếm. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String KEY_PREFIX = "wms:notification:kpi:";
    static final String PROCESSED_PREFIX = KEY_PREFIX + "processed:";
    static final String FIELD_UPDATED_AT = "updatedAt";
    static final Duration COUNTER_TTL = Duration.ofDays(2);
    static final Duration PROCESSED_TTL = Duration.ofDays(1);

    private static final Logger log = LoggerFactory.getLogger(RealtimeKpiService.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RealtimeKpiService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    /**
     * Tăng một chỉ số rồi trả snapshot mới của ngày hiện tại.
     *
     * @return snapshot sau khi tăng; rỗng nếu sự kiện đã được đếm (trùng messageId) hoặc Redis lỗi
     */
    public Optional<KpiSnapshot> increment(KpiMetric metric, String messageId) {
        try {
            if (StringUtils.hasText(messageId)) {
                Boolean firstTime = redis.opsForValue().setIfAbsent(PROCESSED_PREFIX + messageId, "1", PROCESSED_TTL);
                if (Boolean.FALSE.equals(firstTime)) {
                    log.info("Bỏ qua đếm KPI cho sự kiện đã xử lý (messageId={})", messageId);
                    return Optional.empty();
                }
            }
            Instant now = clock.instant();
            LocalDate today = LocalDate.ofInstant(now, BUSINESS_ZONE);
            String key = keyOf(today);
            redis.opsForHash().increment(key, metric.field(), 1);
            redis.opsForHash().put(key, FIELD_UPDATED_AT, now.toString());
            redis.expire(key, COUNTER_TTL);
            return Optional.of(toSnapshot(today, redis.opsForHash().entries(key)));
        } catch (RuntimeException ex) {
            log.warn("Không cập nhật được bộ đếm KPI {} trên Redis (messageId={}): {}",
                    metric, messageId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Snapshot KPI của ngày hiện tại (dùng khi giao diện vừa mở trang).
     * Ném lại lỗi Redis để controller trả 503 thay vì báo số 0 sai.
     */
    public KpiSnapshot currentSnapshot() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
        return toSnapshot(today, redis.opsForHash().entries(keyOf(today)));
    }

    static String keyOf(LocalDate date) {
        return KEY_PREFIX + date;
    }

    private static KpiSnapshot toSnapshot(LocalDate date, Map<Object, Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return KpiSnapshot.empty(date);
        }
        return new KpiSnapshot(date,
                count(entries, KpiMetric.PURCHASE_ORDERS),
                count(entries, KpiMetric.SALES_ORDERS),
                count(entries, KpiMetric.DISPATCHED_ORDERS),
                count(entries, KpiMetric.LOW_STOCK_ALERTS),
                count(entries, KpiMetric.INVENTORY_MOVEMENTS),
                instant(entries.get(FIELD_UPDATED_AT)));
    }

    private static long count(Map<Object, Object> entries, KpiMetric metric) {
        Object value = entries.get(metric.field());
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.toString());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
