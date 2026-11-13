package vn.wms.ai.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.wms.ai.config.AiExtractionProperties;
import vn.wms.ai.dto.ExtractionResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.time.Duration;
import java.util.Optional;

/**
 * Giữ kết quả trích xuất trên Redis trong {@code app.ai.extraction.result-ttl} (mặc định 1 giờ) để xem lại và bấm nhập.
 * Khóa có ID doanh nghiệp (docs/SAAS_SPEC.md, mục 2.3, quy tắc 8): {@value #KEY_PREFIX}{companyId}:{extractionId}, nên
 * doanh nghiệp khác đọc bằng đúng mã trích xuất cũng không thấy (404).
 */
@Component
public class ExtractionStore {

    static final String KEY_PREFIX = "wms:ai:extraction:";
    private static final String LOCK_SUFFIX = ":import-lock";
    /** Khóa chống bấm nhập hai lần cùng lúc, tự hết hạn nếu service dừng giữa chừng. */
    static final Duration IMPORT_LOCK_TTL = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(ExtractionStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiExtractionProperties properties;

    public ExtractionStore(StringRedisTemplate redis, ObjectMapper objectMapper, AiExtractionProperties properties) {
        this.redis = redis;
        // Đọc số thập phân thành BigDecimal để giá trị trong bảng xem trước không bị sai lệch khi đọc lại.
        this.objectMapper = objectMapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.properties = properties;
    }

    static String key(long companyId, String extractionId) {
        return KEY_PREFIX + companyId + ":" + extractionId;
    }

    /** Lưu kết quả (không kèm {@code usage}) với thời hạn cấu hình. */
    public void save(long companyId, ExtractionResponse extraction) {
        try {
            redis.opsForValue().set(key(companyId, extraction.extractionId()),
                    objectMapper.writeValueAsString(extraction.withUsage(null)), properties.resultTtl());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không ghi được kết quả trích xuất ra JSON", ex);
        } catch (DataAccessException ex) {
            log.warn("Không lưu được kết quả trích xuất lên Redis: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Không lưu được kết quả trích xuất để xem lại và "
                    + "nhập, lượt này không bị tính. Vui lòng thử lại sau.");
        }
    }

    /** Đọc kết quả của doanh nghiệp; hết hạn, không tồn tại hoặc của doanh nghiệp khác đều trả rỗng. */
    public Optional<ExtractionResponse> find(long companyId, String extractionId) {
        String json;
        try {
            json = redis.opsForValue().get(key(companyId, extractionId));
        } catch (DataAccessException ex) {
            log.warn("Không đọc được kết quả trích xuất từ Redis: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Không đọc được kết quả trích xuất, vui lòng thử lại sau.");
        }
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ExtractionResponse.class));
        } catch (JsonProcessingException ex) {
            log.warn("Kết quả trích xuất trên Redis bị hỏng: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Giữ khóa nhập của một lần trích xuất. {@code false} khi đang có một lần nhập khác chạy (bấm hai lần).
     */
    public boolean tryLockImport(long companyId, String extractionId) {
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key(companyId, extractionId) + LOCK_SUFFIX, "1",
                    IMPORT_LOCK_TTL);
            return !Boolean.FALSE.equals(acquired);
        } catch (DataAccessException ex) {
            log.warn("Không giữ được khóa nhập trên Redis: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Không bắt đầu nhập được, vui lòng thử lại sau.");
        }
    }

    public void unlockImport(long companyId, String extractionId) {
        try {
            redis.delete(key(companyId, extractionId) + LOCK_SUFFIX);
        } catch (DataAccessException ex) {
            // Khóa tự hết hạn sau IMPORT_LOCK_TTL.
            log.warn("Không gỡ được khóa nhập trên Redis: {}", ex.getClass().getSimpleName());
        }
    }
}
