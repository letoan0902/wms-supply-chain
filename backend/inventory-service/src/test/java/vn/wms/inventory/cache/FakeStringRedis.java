package vn.wms.inventory.cache;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis giả lập trong bộ nhớ cho test bộ nhớ đệm tồn kho (test không có Redis thật, không gọi mạng). Chỉ cài các lệnh
 * {@link StockTotalsCache} dùng: MGET, SET kèm TTL, INCR, EXPIRE; tôn trọng TTL theo đồng hồ truyền vào; bật
 * {@link #setFailing(boolean)} thì mọi lệnh ném {@link RedisConnectionFailureException} như khi Redis không chạy.
 */
public class FakeStringRedis {

    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final Map<String, Long> expiresAt = new ConcurrentHashMap<>();
    private final Map<String, Duration> lastTtl = new ConcurrentHashMap<>();
    private final LongSupplier clockMillis;
    private final AtomicInteger operations = new AtomicInteger();
    private volatile boolean failing;

    public FakeStringRedis(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /** Tạo một {@link StringRedisTemplate} giả đã nối vào bộ nhớ này. */
    public StringRedisTemplate template() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        attachTo(template);
        return template;
    }

    @SuppressWarnings("unchecked")
    public void attachTo(StringRedisTemplate template) {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.multiGet(anyCollection())).thenAnswer(inv -> {
            touch();
            Collection<String> keys = inv.getArgument(0);
            List<String> result = new ArrayList<>();
            for (String key : keys) {
                result.add(get(key));
            }
            return result;
        });
        when(ops.increment(anyString())).thenAnswer(inv -> {
            touch();
            String key = inv.getArgument(0);
            String current = get(key);
            long next = (current == null ? 0 : Long.parseLong(current)) + 1;
            values.put(key, String.valueOf(next));
            return next;
        });
        doAnswer(inv -> {
            touch();
            String key = inv.getArgument(0);
            Duration ttl = inv.getArgument(2);
            values.put(key, inv.getArgument(1));
            expiresAt.put(key, clockMillis.getAsLong() + ttl.toMillis());
            lastTtl.put(key, ttl);
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        when(template.expire(anyString(), any(Duration.class))).thenAnswer(inv -> {
            touch();
            String key = inv.getArgument(0);
            Duration ttl = inv.getArgument(1);
            if (get(key) == null) {
                return false;
            }
            expiresAt.put(key, clockMillis.getAsLong() + ttl.toMillis());
            lastTtl.put(key, ttl);
            return true;
        });
    }

    private void touch() {
        operations.incrementAndGet();
        if (failing) {
            throw new RedisConnectionFailureException("Redis giả lập đang không kết nối được");
        }
    }

    /** Giá trị hiện tại của khóa (đã hết hạn thì {@code null}), không tính là một lệnh Redis. */
    public String get(String key) {
        Long expiry = expiresAt.get(key);
        if (expiry != null && clockMillis.getAsLong() >= expiry) {
            values.remove(key);
            expiresAt.remove(key);
            return null;
        }
        return values.get(key);
    }

    /** Ghi thẳng một giá trị không hết hạn (dựng tình huống trong test). */
    public void put(String key, String value) {
        values.put(key, value);
        expiresAt.remove(key);
    }

    public Duration ttlOf(String key) {
        return lastTtl.get(key);
    }

    public Set<String> keys() {
        return new TreeSet<>(values.keySet());
    }

    public int operationCount() {
        return operations.get();
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public void clear() {
        values.clear();
        expiresAt.clear();
        lastTtl.clear();
        failing = false;
    }
}
