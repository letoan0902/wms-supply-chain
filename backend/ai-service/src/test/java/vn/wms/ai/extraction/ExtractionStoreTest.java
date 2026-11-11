package vn.wms.ai.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.wms.ai.AiTestSupport;
import vn.wms.ai.dto.AskResponse;
import vn.wms.ai.dto.ExtractionColumn;
import vn.wms.ai.dto.ExtractionResponse;
import vn.wms.ai.dto.ExtractionRow;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lưu kết quả trích xuất trên Redis: khóa theo doanh nghiệp, hết hạn sau 1 giờ, doanh nghiệp khác không đọc được. */
class ExtractionStoreTest {

    private static final String ID = "3f1c2b9e-1111-4a2b-9c3d-0123456789ab";

    private final Map<String, String> redisData = new HashMap<>();
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ExtractionStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> redisData.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(invocation -> redisData.get(invocation.<String>getArgument(0)));
        store = new ExtractionStore(redis, new ObjectMapper().registerModule(new JavaTimeModule()),
                AiTestSupport.extractionProperties());
    }

    static ExtractionResponse extraction() {
        Map<String, Object> rowValues = new LinkedHashMap<>();
        rowValues.put("sku", "SP001");
        rowValues.put("purchasePrice", new BigDecimal("150000.25"));
        rowValues.put("barcode", null);
        ExtractionRow row = new ExtractionRow(1, 2, rowValues, Map.of("sku", 0.95), List.of());
        ExtractionColumn column = new ExtractionColumn("sku", "Mã SKU", "SKU", true, 64, List.of(), 1, "Mã hàng", 0.95);
        Instant now = Instant.parse("2026-11-12T03:00:00Z");
        return new ExtractionResponse(ID, ExtractionDataType.PRODUCT, "hang.xlsx", "XLSX", "HEADER_MATCHING", 1,
                false, List.of(column), List.of(row), List.of(), now, now.plus(Duration.ofHours(1)),
                new AskResponse.Usage(5, 20L));
    }

    @Test
    void savesWithoutUsageUnderCompanyKeyWithOneHourTtl() {
        store.save(701L, extraction());

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq("wms:ai:extraction:701:" + ID), anyString(), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofHours(1));
        ExtractionResponse loaded = store.find(701L, ID).orElseThrow();
        assertThat(loaded.usage()).isNull();
        assertThat(loaded.rows().get(0).values()).containsEntry("purchasePrice", new BigDecimal("150000.25"))
                .containsEntry("barcode", null);
        assertThat(loaded.withUsage(new AskResponse.Usage(5, 20L))).isEqualTo(extraction());
    }

    @Test
    void otherCompanyAndExpiredResultsAreNotFound() {
        store.save(701L, extraction());

        assertThat(store.find(702L, ID)).isEmpty();
        redisData.clear(); // Redis xóa khóa khi hết TTL
        assertThat(store.find(701L, ID)).isEmpty();
    }

    @Test
    void redisFailureIsServiceUnavailable() {
        doThrow(new RedisConnectionFailureException("down")).when(values).set(anyString(), anyString(),
                any(Duration.class));

        assertThatThrownBy(() -> store.save(701L, extraction()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void importLockIsPerCompanyAndExtraction() {
        when(values.setIfAbsent(eq("wms:ai:extraction:701:" + ID + ":import-lock"), eq("1"), any(Duration.class)))
                .thenReturn(true, false);

        assertThat(store.tryLockImport(701L, ID)).isTrue();
        assertThat(store.tryLockImport(701L, ID)).isFalse();
        store.unlockImport(701L, ID);
        verify(redis).delete("wms:ai:extraction:701:" + ID + ":import-lock");
    }
}
