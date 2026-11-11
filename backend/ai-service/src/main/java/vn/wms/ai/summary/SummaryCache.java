package vn.wms.ai.summary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.wms.ai.config.AiSummaryProperties;
import vn.wms.ai.dto.SummaryResponse;
import vn.wms.common.constant.Roles;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Bộ nhớ đệm bản tóm tắt dashboard trên Redis ({@code app.ai.summary.cache-ttl}, mặc định 30 phút). Khóa
 * {@value #KEY_PREFIX}{companyId}:{nhóm vai trò}:{phạm vi} gồm:
 * <ul>
 *   <li>doanh nghiệp (docs/SAAS_SPEC.md, mục 2.3, quy tắc 8);</li>
 *   <li>nhóm vai trò {@code FIN} (ADMIN, ACCOUNTANT: thấy chỉ số tài chính) hoặc {@code OPS} (vai trò khác: thẻ tài
 *       chính bị che), để bản viết từ số liệu của quản trị viên không đến tay nhân viên kho;</li>
 *   <li>phạm vi kho: {@code w{id}} khi chọn một kho; không chọn kho thì {@code all} với vai trò xem mọi kho, hoặc
 *       {@code a{các kho được gán}} với vai trò theo kho (notification-service chọn kho theo danh sách được gán).</li>
 * </ul>
 * Redis lỗi thì coi như không có bộ nhớ đệm (tạo bản mới), ghi lỗi chỉ ghi log.
 */
@Component
public class SummaryCache {

    static final String KEY_PREFIX = "wms:ai:summary:";

    private static final Logger log = LoggerFactory.getLogger(SummaryCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiSummaryProperties properties;

    public SummaryCache(StringRedisTemplate redis, ObjectMapper objectMapper, AiSummaryProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.properties = properties;
    }

    /** Nhóm vai trò dùng trong khóa. */
    static String roleGroup(CurrentUser user) {
        return user != null && (user.hasRole(Roles.ADMIN) || user.hasRole(Roles.ACCOUNTANT)) ? "FIN" : "OPS";
    }

    /** Khóa của người đang xem (đọc vai trò, kho được gán từ header do api-gateway gắn). */
    public static String keyForCurrentUser(long companyId, Long warehouseId) {
        return key(companyId, UserContext.getCurrentUser().orElse(null), warehouseId);
    }

    static String key(long companyId, CurrentUser user, Long warehouseId) {
        String scope;
        if (warehouseId != null) {
            scope = "w" + warehouseId;
        } else if (user == null || UserContext.isGlobalScope(user)) {
            scope = "all";
        } else {
            scope = "a" + user.warehouseIds().stream().sorted().map(String::valueOf)
                    .collect(Collectors.joining("-"));
        }
        return KEY_PREFIX + companyId + ":" + roleGroup(user) + ":" + scope;
    }

    public Optional<SummaryResponse> find(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, SummaryResponse.class));
        } catch (DataAccessException | JsonProcessingException ex) {
            log.warn("Không đọc được bản tóm tắt dashboard từ Redis: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Lưu bản tóm tắt (không kèm {@code usage}). */
    public void save(String key, SummaryResponse summary) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(summary.withUsage(null)),
                    properties.cacheTtl());
        } catch (DataAccessException | JsonProcessingException ex) {
            log.warn("Không lưu được bản tóm tắt dashboard lên Redis: {}", ex.getClass().getSimpleName());
        }
    }
}
