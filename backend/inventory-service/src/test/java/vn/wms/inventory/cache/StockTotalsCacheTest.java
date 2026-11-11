package vn.wms.inventory.cache;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import vn.wms.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử đơn vị bộ nhớ đệm tổng tồn (WMS-19.1) với Redis giả lập và đồng hồ điều khiển được: trúng, trượt, vô hiệu
 * bằng thế hệ (kể cả ghi muộn của request song song), TTL, sang ngày mới, Redis lỗi, tách doanh nghiệp, công tắc tắt.
 */
class StockTotalsCacheTest {

    private static final long COMPANY_A = 1L;
    private static final long COMPANY_B = 2L;
    private static final long PRODUCT = 501L;
    private static final long OTHER_PRODUCT = 502L;
    private static final Long WAREHOUSE = 11L;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-11T03:00:00Z"));
    private FakeStringRedis redis;
    private StockCacheProperties properties;
    private StockTotalsCache cache;

    /** Cơ sở dữ liệu giả: số tồn theo (doanh nghiệp, sản phẩm), đếm số lần bị đọc. */
    private final Map<String, StockTotals> database = new HashMap<>();
    private final AtomicInteger databaseReads = new AtomicInteger();
    private final List<List<Long>> loadedBatches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        redis = new FakeStringRedis(clock::millis);
        properties = new StockCacheProperties();
        cache = newCache(properties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private StockTotalsCache newCache(StockCacheProperties props) {
        return new StockTotalsCache(props, redis.template(), new SimpleMeterRegistry(), clock);
    }

    private void db(long company, long product, String onHand, String reserved) {
        database.put(company + ":" + product, new StockTotals(new BigDecimal(onHand), new BigDecimal(reserved)));
    }

    private Function<List<Long>, Map<Long, StockTotals>> loader() {
        return ids -> {
            databaseReads.incrementAndGet();
            loadedBatches.add(List.copyOf(ids));
            long company = TenantContext.getCompanyId() == null ? -1 : TenantContext.getCompanyId();
            Map<Long, StockTotals> result = new HashMap<>();
            for (Long id : ids) {
                result.put(id, database.getOrDefault(company + ":" + id, StockTotals.ZERO));
            }
            return result;
        };
    }

    private StockTotals read(long company, long product, Long warehouseId) {
        return TenantContext.callAs(company, () -> cache.productTotals(warehouseId, List.of(product), loader()).get(product));
    }

    @Test
    @DisplayName("Lần đầu trượt và đọc cơ sở dữ liệu, lần sau trúng bộ nhớ đệm; giá trị ghi kèm TTL cấu hình")
    void missThenHit() {
        db(COMPANY_A, PRODUCT, "100.000", "30.000");

        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("100");
        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).reserved()).isEqualByComparingTo("30");

        assertThat(databaseReads.get()).isEqualTo(1);
        assertThat(cache.missCount()).isEqualTo(1);
        assertThat(cache.hitCount()).isEqualTo(1);
        String key = StockCacheKeys.productTotals(COMPANY_A, PRODUCT, WAREHOUSE);
        assertThat(key).isEqualTo("wms:stock:1:p:501:w:11");
        assertThat(redis.ttlOf(key)).isEqualTo(Duration.ofSeconds(60));
        // Giữ nguyên độ chính xác thập phân của cơ sở dữ liệu
        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand().toPlainString()).isEqualTo("100.000");
    }

    @Test
    @DisplayName("Đọc theo lô: chỉ nạp các sản phẩm trượt trong một lần, kết quả giữ thứ tự yêu cầu")
    void batchLoadsOnlyMisses() {
        db(COMPANY_A, PRODUCT, "5", "0");
        db(COMPANY_A, OTHER_PRODUCT, "7", "1");
        read(COMPANY_A, PRODUCT, null);

        Map<Long, StockTotals> result = TenantContext.callAs(COMPANY_A,
                () -> cache.productTotals(null, List.of(OTHER_PRODUCT, PRODUCT), loader()));

        assertThat(result.keySet()).containsExactly(OTHER_PRODUCT, PRODUCT);
        assertThat(result.get(OTHER_PRODUCT).onHand()).isEqualByComparingTo("7");
        assertThat(result.get(PRODUCT).onHand()).isEqualByComparingTo("5");
        assertThat(loadedBatches).containsExactly(List.of(PRODUCT), List.of(OTHER_PRODUCT));
    }

    @Test
    @DisplayName("Vô hiệu một sản phẩm làm mất hiệu lực số theo mọi kho và số KPI của doanh nghiệp, sản phẩm khác giữ nguyên")
    void evictInvalidatesAllWarehousesOfProductAndKpi() {
        db(COMPANY_A, PRODUCT, "10", "0");
        db(COMPANY_A, OTHER_PRODUCT, "4", "0");
        read(COMPANY_A, PRODUCT, WAREHOUSE);
        read(COMPANY_A, PRODUCT, null);
        read(COMPANY_A, OTHER_PRODUCT, WAREHOUSE);
        AtomicInteger kpiLoads = new AtomicInteger();
        TenantContext.runAs(COMPANY_A, () -> cache.warehouseFigures(WAREHOUSE, () -> {
            kpiLoads.incrementAndGet();
            return new WarehouseStockFigures(BigDecimal.TEN, BigDecimal.TEN, 1);
        }));

        db(COMPANY_A, PRODUCT, "6", "0");
        cache.evict(COMPANY_A, List.of(PRODUCT));

        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("6");
        assertThat(read(COMPANY_A, PRODUCT, null).onHand()).isEqualByComparingTo("6");
        int readsBefore = databaseReads.get();
        assertThat(read(COMPANY_A, OTHER_PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("4");
        assertThat(databaseReads.get()).isEqualTo(readsBefore);

        WarehouseStockFigures figures = TenantContext.callAs(COMPANY_A, () -> cache.warehouseFigures(WAREHOUSE, () -> {
            kpiLoads.incrementAndGet();
            return new WarehouseStockFigures(new BigDecimal("6"), new BigDecimal("6"), 1);
        }));
        assertThat(kpiLoads.get()).isEqualTo(2);
        assertThat(figures.totalQuantity()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("Request đọc song song ghi số cũ SAU khi giao dịch khác đã vô hiệu: số cũ không bao giờ được dùng")
    void lateWriteOfStaleValueIsIgnored() {
        db(COMPANY_A, PRODUCT, "10", "0");
        // Trong lúc request đọc đang truy vấn cơ sở dữ liệu (đọc được 10), giao dịch khác commit 3 và vô hiệu.
        Function<List<Long>, Map<Long, StockTotals>> slowLoader = ids -> {
            Map<Long, StockTotals> stale = loader().apply(ids);
            db(COMPANY_A, PRODUCT, "3", "0");
            cache.evict(COMPANY_A, List.of(PRODUCT));
            return stale;
        };
        StockTotals first = TenantContext.callAs(COMPANY_A,
                () -> cache.productTotals(WAREHOUSE, List.of(PRODUCT), slowLoader).get(PRODUCT));
        assertThat(first.onHand()).isEqualByComparingTo("10");

        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("Hết TTL thì đọc lại cơ sở dữ liệu (lưới an toàn khi một lần vô hiệu bị mất)")
    void expiresAfterTtl() {
        db(COMPANY_A, PRODUCT, "10", "0");
        read(COMPANY_A, PRODUCT, WAREHOUSE);
        db(COMPANY_A, PRODUCT, "8", "0"); // đổi mà không vô hiệu

        clock.advance(Duration.ofSeconds(59));
        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("10");
        clock.advance(Duration.ofSeconds(2));
        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("Giá trị tính từ hôm trước không được dùng vì lô hết hạn được loại theo ngày")
    void valueFromPreviousDayIsIgnored() {
        db(COMPANY_A, PRODUCT, "10", "0");
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        redis.put(StockCacheKeys.productTotals(COMPANY_A, PRODUCT, WAREHOUSE),
                StockTotalsCache.encode("0", yesterday, new BigDecimal("99"), BigDecimal.ZERO));

        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("10");
        assertThat(databaseReads.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Khóa có ID doanh nghiệp: doanh nghiệp B không bao giờ đọc số đệm của doanh nghiệp A dù trùng ID sản phẩm và kho")
    void companiesNeverShareCachedFigures() {
        db(COMPANY_A, PRODUCT, "100", "0");
        db(COMPANY_B, PRODUCT, "3", "0");

        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("100");
        assertThat(read(COMPANY_B, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("3");
        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("100");
        assertThat(read(COMPANY_B, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("3");

        assertThat(redis.keys()).contains("wms:stock:1:p:501:w:11", "wms:stock:2:p:501:w:11");
        assertThat(redis.keys()).allMatch(k -> k.startsWith("wms:stock:1:") || k.startsWith("wms:stock:2:"));

        // Vô hiệu của A không chạm số của B
        cache.evict(COMPANY_A, List.of(PRODUCT));
        int readsBefore = databaseReads.get();
        read(COMPANY_B, PRODUCT, WAREHOUSE);
        assertThat(databaseReads.get()).isEqualTo(readsBefore);
    }

    @Test
    @DisplayName("Không xác định được doanh nghiệp thì không dùng Redis")
    void noCompanyBypassesRedis() {
        Map<Long, StockTotals> result = cache.productTotals(WAREHOUSE, List.of(PRODUCT), loader());
        assertThat(result.get(PRODUCT)).isEqualTo(StockTotals.ZERO);
        assertThat(redis.operationCount()).isZero();
    }

    @Test
    @DisplayName("Redis lỗi: trả số từ cơ sở dữ liệu, không ném lỗi, bỏ qua Redis trong retry-after, cảnh báo một lần, rồi tự dùng lại")
    void redisFailureFallsBackToDatabase() {
        Logger logger = (Logger) LoggerFactory.getLogger(StockTotalsCache.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            db(COMPANY_A, PRODUCT, "10", "0");
            redis.setFailing(true);

            assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("10");
            int operationsAfterFailure = redis.operationCount();
            for (int i = 0; i < 5; i++) {
                assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("10");
            }
            assertThat(redis.operationCount()).isEqualTo(operationsAfterFailure);
            assertThat(cache.errorCount()).isEqualTo(1);
            assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count()).isEqualTo(1);

            redis.setFailing(false);
            clock.advance(Duration.ofSeconds(31));
            read(COMPANY_A, PRODUCT, WAREHOUSE);
            assertThat(redis.operationCount()).isGreaterThan(operationsAfterFailure);
            int readsBefore = databaseReads.get();
            read(COMPANY_A, PRODUCT, WAREHOUSE);
            assertThat(databaseReads.get()).isEqualTo(readsBefore);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("Không vô hiệu được vì Redis lỗi: bỏ qua bộ nhớ đệm ít nhất một TTL để không đọc lại số cũ")
    void failedEvictionKeepsBypassForTtl() {
        db(COMPANY_A, PRODUCT, "10", "0");
        read(COMPANY_A, PRODUCT, WAREHOUSE);

        db(COMPANY_A, PRODUCT, "4", "0");
        redis.setFailing(true);
        cache.evict(COMPANY_A, List.of(PRODUCT));
        redis.setFailing(false);

        // Sau retry-after (30 giây) nhưng trước TTL (60 giây): vẫn đọc cơ sở dữ liệu, không đọc số 10 còn trên Redis
        clock.advance(Duration.ofSeconds(45));
        int operations = redis.operationCount();
        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("4");
        assertThat(redis.operationCount()).isEqualTo(operations);

        clock.advance(Duration.ofSeconds(16));
        assertThat(read(COMPANY_A, PRODUCT, WAREHOUSE).onHand()).isEqualByComparingTo("4");
        assertThat(redis.operationCount()).isGreaterThan(operations);
    }

    @Test
    @DisplayName("Tắt app.stock-cache.enabled: luôn đọc cơ sở dữ liệu, không gọi Redis lần nào, vô hiệu không làm gì")
    void disabledSwitchNeverTouchesRedis() {
        StockCacheProperties disabled = new StockCacheProperties();
        disabled.setEnabled(false);
        StockTotalsCache off = newCache(disabled);
        db(COMPANY_A, PRODUCT, "10", "0");

        for (int i = 0; i < 3; i++) {
            StockTotals totals = TenantContext.callAs(COMPANY_A,
                    () -> off.productTotals(WAREHOUSE, List.of(PRODUCT), loader()).get(PRODUCT));
            assertThat(totals.onHand()).isEqualByComparingTo("10");
        }
        TenantContext.runAs(COMPANY_A, () -> off.warehouseFigures(null,
                () -> new WarehouseStockFigures(BigDecimal.ONE, BigDecimal.ONE, 0)));
        off.evict(COMPANY_A, List.of(PRODUCT));

        assertThat(off.isEnabled()).isFalse();
        assertThat(databaseReads.get()).isEqualTo(3);
        assertThat(redis.operationCount()).isZero();
    }

    /** Đồng hồ điều khiển được để thử TTL và thời gian bỏ qua Redis. */
    static final class MutableClock extends Clock {
        private final AtomicLong millis;

        MutableClock(Instant start) {
            this.millis = new AtomicLong(start.toEpochMilli());
        }

        void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }
}
