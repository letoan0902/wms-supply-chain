package vn.wms.inventory.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.wms.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Bộ nhớ đệm Redis cho tổng tồn và tồn khả dụng (WMS-19.1). Chỉ phục vụ API đọc; mọi quyết định thay đổi tồn (giữ
 * chỗ, trừ tồn, duyệt điều chỉnh, duyệt điều chuyển) vẫn đọc cơ sở dữ liệu dưới khóa bi quan và không bao giờ đi qua
 * lớp này.
 *
 * <p><b>Vô hiệu bằng thế hệ.</b> Mỗi sản phẩm có một bộ đếm thế hệ ({@link StockCacheKeys#productGeneration}); mỗi
 * doanh nghiệp có một bộ đếm cho số liệu KPI ({@link StockCacheKeys#companyGeneration}). Giá trị đệm ghi kèm thế hệ đã
 * đọc TRƯỚC khi truy vấn cơ sở dữ liệu; khi đọc, giá trị chỉ hợp lệ nếu thế hệ của nó bằng thế hệ hiện tại. Sau khi
 * giao dịch thay đổi tồn commit, {@link #evict} tăng thế hệ nên mọi giá trị cũ (của mọi kho, cả giá trị "mọi kho")
 * mất hiệu lực bằng một lệnh INCR, kể cả giá trị mà một request đọc song song ghi muộn sau lệnh INCR: nó mang thế hệ cũ
 * nên không bao giờ được dùng. Không cần lệnh KEYS hay SCAN.
 *
 * <p><b>Theo ngày.</b> Tồn khả dụng loại lô hết hạn theo ngày hôm nay, nên giá trị đệm ghi kèm ngày tính; sang ngày mới
 * thì coi như chưa có.
 *
 * <p><b>Redis lỗi.</b> Không request nào thất bại vì Redis: lỗi khi đọc hay ghi thì trả số đọc thẳng từ cơ sở dữ liệu,
 * bỏ qua bộ nhớ đệm trong {@code app.stock-cache.retry-after} và chỉ ghi một dòng cảnh báo mỗi khoảng đó. Không vô hiệu
 * được (lỗi khi tăng thế hệ, hoặc đang trong thời gian bỏ qua) thì bỏ qua bộ nhớ đệm thêm ít nhất một TTL, để không đọc
 * lại một giá trị có thể đã cũ của chính instance này.
 *
 * <p>Theo dõi: bộ đếm Micrometer {@code wms.stock.cache.requests} (thẻ {@code result} = {@code hit} hoặc {@code miss}),
 * {@code wms.stock.cache.errors}, {@code wms.stock.cache.evictions}; log mức DEBUG của lớp này ghi từng lần trúng,
 * trượt và vô hiệu.
 */
@Component
public class StockTotalsCache {

    private static final Logger log = LoggerFactory.getLogger(StockTotalsCache.class);

    /** Bộ đếm thế hệ sống lâu hơn nhiều so với giá trị đệm; hết hạn thì coi là thế hệ "0", chỉ gây một lần trượt. */
    static final Duration GENERATION_TTL = Duration.ofDays(1);
    private static final String NO_GENERATION = "0";
    private static final String SEPARATOR = "|";

    private final StockCacheProperties properties;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Counter hits;
    private final Counter misses;
    private final Counter errors;
    private final Counter evictions;

    /** Thời điểm (epoch mili giây) trước đó không dùng Redis vì vừa lỗi hoặc vừa không vô hiệu được. */
    private volatile long bypassUntil;
    /** Lần ghi cảnh báo gần nhất, -1 là chưa ghi lần nào. */
    private volatile long lastWarnAt = -1;

    @Autowired
    public StockTotalsCache(StockCacheProperties properties,
                            ObjectProvider<StringRedisTemplate> redis,
                            ObjectProvider<MeterRegistry> meterRegistry) {
        this(properties, redis.getIfAvailable(), meterRegistry.getIfAvailable(SimpleMeterRegistry::new),
                Clock.systemDefaultZone());
    }

    StockTotalsCache(StockCacheProperties properties, StringRedisTemplate redis, MeterRegistry meterRegistry, Clock clock) {
        this.properties = properties;
        this.redis = redis;
        this.clock = clock;
        this.hits = Counter.builder("wms.stock.cache.requests").tag("result", "hit")
                .description("Số lần đọc tổng tồn trúng bộ nhớ đệm Redis").register(meterRegistry);
        this.misses = Counter.builder("wms.stock.cache.requests").tag("result", "miss")
                .description("Số lần đọc tổng tồn phải truy vấn cơ sở dữ liệu").register(meterRegistry);
        this.errors = Counter.builder("wms.stock.cache.errors")
                .description("Số lần Redis lỗi khi đọc, ghi hoặc vô hiệu bộ nhớ đệm tồn kho").register(meterRegistry);
        this.evictions = Counter.builder("wms.stock.cache.evictions")
                .description("Số sản phẩm bị vô hiệu bộ nhớ đệm sau khi tồn thay đổi").register(meterRegistry);
    }

    /** Công tắc {@code app.stock-cache.enabled}. */
    public boolean isEnabled() {
        return properties.isEnabled() && redis != null;
    }

    // ------------------------------------------------------------------------------------------
    // Đọc
    // ------------------------------------------------------------------------------------------

    /**
     * Tổng tồn của từng sản phẩm trong một kho ({@code warehouseId = null}: mọi kho) của doanh nghiệp hiện tại.
     * Sản phẩm chưa có trong bộ nhớ đệm được nạp bằng {@code loader} (một lần cho cả nhóm) rồi ghi lại.
     *
     * @param loader nhận danh sách sản phẩm cần nạp, trả về tổng tồn đọc từ cơ sở dữ liệu cho TỪNG sản phẩm đó
     * @return tổng tồn theo đúng thứ tự {@code productIds}
     */
    public Map<Long, StockTotals> productTotals(Long warehouseId, List<Long> productIds,
                                                Function<List<Long>, Map<Long, StockTotals>> loader) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Long companyId = TenantContext.getCompanyId();
        if (companyId == null || !usable()) {
            return ordered(productIds, loader.apply(productIds));
        }
        LocalDate today = LocalDate.now(clock);
        List<String> keys = new ArrayList<>(productIds.size() * 2);
        for (Long productId : productIds) {
            keys.add(StockCacheKeys.productGeneration(companyId, productId));
            keys.add(StockCacheKeys.productTotals(companyId, productId, warehouseId));
        }
        List<String> values;
        try {
            values = redis.opsForValue().multiGet(keys);
        } catch (RuntimeException ex) {
            onRedisFailure("đọc", ex, false);
            return ordered(productIds, loader.apply(productIds));
        }
        if (values == null || values.size() != keys.size()) {
            return ordered(productIds, loader.apply(productIds));
        }

        Map<Long, StockTotals> found = new LinkedHashMap<>();
        Map<Long, String> missedGenerations = new LinkedHashMap<>();
        for (int i = 0; i < productIds.size(); i++) {
            String generation = values.get(2 * i) != null ? values.get(2 * i) : NO_GENERATION;
            StockTotals totals = decodeTotals(values.get(2 * i + 1), generation, today);
            if (totals != null) {
                found.put(productIds.get(i), totals);
            } else {
                missedGenerations.put(productIds.get(i), generation);
            }
        }
        hits.increment(found.size());
        misses.increment(missedGenerations.size());
        log.debug("Bộ nhớ đệm tồn kho doanh nghiệp {} kho {}: trúng {}, trượt {} sản phẩm",
                companyId, warehouseId, found.keySet(), missedGenerations.keySet());

        if (!missedGenerations.isEmpty()) {
            Map<Long, StockTotals> loaded = loader.apply(new ArrayList<>(missedGenerations.keySet()));
            for (Map.Entry<Long, String> miss : missedGenerations.entrySet()) {
                StockTotals totals = loaded.getOrDefault(miss.getKey(), StockTotals.ZERO);
                found.put(miss.getKey(), totals);
                store(StockCacheKeys.productTotals(companyId, miss.getKey(), warehouseId),
                        encode(miss.getValue(), today, totals.onHand(), totals.reserved()));
            }
        }
        return ordered(productIds, found);
    }

    /**
     * Số tồn tổng hợp cho KPI của một kho ({@code warehouseId = null}: mọi kho) của doanh nghiệp hiện tại.
     */
    public WarehouseStockFigures warehouseFigures(Long warehouseId, Supplier<WarehouseStockFigures> loader) {
        Long companyId = TenantContext.getCompanyId();
        if (companyId == null || !usable()) {
            return loader.get();
        }
        LocalDate today = LocalDate.now(clock);
        String generationKey = StockCacheKeys.companyGeneration(companyId);
        String dataKey = StockCacheKeys.warehouseFigures(companyId, warehouseId);
        List<String> values;
        try {
            values = redis.opsForValue().multiGet(List.of(generationKey, dataKey));
        } catch (RuntimeException ex) {
            onRedisFailure("đọc", ex, false);
            return loader.get();
        }
        if (values == null || values.size() != 2) {
            return loader.get();
        }
        String generation = values.get(0) != null ? values.get(0) : NO_GENERATION;
        WarehouseStockFigures cached = decodeFigures(values.get(1), generation, today);
        if (cached != null) {
            hits.increment();
            log.debug("Bộ nhớ đệm KPI tồn kho doanh nghiệp {} kho {}: trúng", companyId, warehouseId);
            return cached;
        }
        misses.increment();
        log.debug("Bộ nhớ đệm KPI tồn kho doanh nghiệp {} kho {}: trượt", companyId, warehouseId);
        WarehouseStockFigures loaded = loader.get();
        store(dataKey, encode(generation, today, loaded.availableQuantity(), loaded.totalQuantity(),
                BigDecimal.valueOf(loaded.occupiedLocations())));
        return loaded;
    }

    // ------------------------------------------------------------------------------------------
    // Vô hiệu
    // ------------------------------------------------------------------------------------------

    /**
     * Vô hiệu số đệm của các sản phẩm (mọi kho) và số KPI của doanh nghiệp. Gọi sau khi giao dịch thay đổi tồn đã
     * commit ({@link StockCacheInvalidator}). Không bao giờ ném lỗi.
     */
    public void evict(long companyId, Collection<Long> productIds) {
        if (!isEnabled() || productIds.isEmpty()) {
            return;
        }
        if (clock.millis() < bypassUntil) {
            // Redis vừa lỗi: không tăng được thế hệ, nên tiếp tục bỏ qua bộ nhớ đệm đến khi mọi giá trị cũ hết hạn.
            extendBypass(properties.getTtl());
            log.debug("Bỏ qua vô hiệu bộ nhớ đệm tồn kho doanh nghiệp {} sản phẩm {} vì Redis đang lỗi",
                    companyId, productIds);
            return;
        }
        Collection<Long> distinct = new LinkedHashSet<>(productIds);
        try {
            for (Long productId : distinct) {
                String key = StockCacheKeys.productGeneration(companyId, productId);
                redis.opsForValue().increment(key);
                redis.expire(key, GENERATION_TTL);
            }
            String companyKey = StockCacheKeys.companyGeneration(companyId);
            redis.opsForValue().increment(companyKey);
            redis.expire(companyKey, GENERATION_TTL);
            evictions.increment(distinct.size());
            log.debug("Đã vô hiệu bộ nhớ đệm tồn kho doanh nghiệp {} sản phẩm {}", companyId, productIds);
        } catch (RuntimeException ex) {
            onRedisFailure("vô hiệu", ex, true);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Theo dõi
    // ------------------------------------------------------------------------------------------

    public long hitCount() {
        return (long) hits.count();
    }

    public long missCount() {
        return (long) misses.count();
    }

    public long errorCount() {
        return (long) errors.count();
    }

    // ------------------------------------------------------------------------------------------
    // Nội bộ
    // ------------------------------------------------------------------------------------------

    private boolean usable() {
        return isEnabled() && clock.millis() >= bypassUntil;
    }

    private void store(String key, String value) {
        if (!usable()) {
            return;
        }
        try {
            redis.opsForValue().set(key, value, properties.getTtl());
        } catch (RuntimeException ex) {
            onRedisFailure("ghi", ex, false);
        }
    }

    private void onRedisFailure(String operation, RuntimeException ex, boolean evicting) {
        errors.increment();
        Duration pause = evicting && properties.getTtl().compareTo(properties.getRetryAfter()) > 0
                ? properties.getTtl() : properties.getRetryAfter();
        extendBypass(pause);
        long now = clock.millis();
        long lastWarn = lastWarnAt;
        if (lastWarn < 0 || now - lastWarn >= properties.getRetryAfter().toMillis()) {
            lastWarnAt = now;
            log.warn("Redis lỗi khi {} bộ nhớ đệm tồn kho, đọc thẳng cơ sở dữ liệu trong {} giây: {}",
                    operation, pause.toSeconds(), ex.toString());
        } else {
            log.debug("Redis lỗi khi {} bộ nhớ đệm tồn kho: {}", operation, ex.toString());
        }
    }

    private void extendBypass(Duration pause) {
        long until = clock.millis() + pause.toMillis();
        if (until > bypassUntil) {
            bypassUntil = until;
        }
    }

    private static Map<Long, StockTotals> ordered(List<Long> productIds, Map<Long, StockTotals> totals) {
        Map<Long, StockTotals> result = new LinkedHashMap<>();
        for (Long productId : productIds) {
            result.put(productId, totals.getOrDefault(productId, StockTotals.ZERO));
        }
        return result;
    }

    static String encode(String generation, LocalDate day, BigDecimal... numbers) {
        StringBuilder sb = new StringBuilder(generation).append(SEPARATOR).append(day);
        for (BigDecimal number : numbers) {
            sb.append(SEPARATOR).append(number.toPlainString());
        }
        return sb.toString();
    }

    /** Tách giá trị đệm; sai thế hệ, sai ngày hoặc sai định dạng thì trả {@code null} (coi như trượt). */
    private static String[] decode(String value, String generation, LocalDate today, int numbers) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|");
        if (parts.length != numbers + 2 || !parts[0].equals(generation) || !parts[1].equals(today.toString())) {
            return null;
        }
        return parts;
    }

    private static StockTotals decodeTotals(String value, String generation, LocalDate today) {
        String[] parts = decode(value, generation, today, 2);
        if (parts == null) {
            return null;
        }
        try {
            return new StockTotals(new BigDecimal(parts[2]), new BigDecimal(parts[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static WarehouseStockFigures decodeFigures(String value, String generation, LocalDate today) {
        String[] parts = decode(value, generation, today, 3);
        if (parts == null) {
            return null;
        }
        try {
            return new WarehouseStockFigures(new BigDecimal(parts[2]), new BigDecimal(parts[3]),
                    new BigDecimal(parts[4]).longValueExact());
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
    }
}
