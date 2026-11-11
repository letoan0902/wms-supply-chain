package vn.wms.auth.apikey.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.apikey.dto.ApiKeyResponse;
import vn.wms.auth.apikey.dto.CreateApiKeyRequest;
import vn.wms.auth.apikey.dto.CreatedApiKeyResponse;
import vn.wms.auth.apikey.entity.ApiKey;
import vn.wms.auth.apikey.repository.ApiKeyRepository;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.subscription.entity.Plan;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.entity.SubscriptionStatus;
import vn.wms.auth.subscription.service.SubscriptionService;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.plan.PlanLimit;
import vn.wms.common.plan.PlanLimitViolation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quản lý API key của doanh nghiệp cho hệ thống ngoài (WMS-25.1, docs/SAAS_SPEC.md).
 *
 * <p>Key có dạng {@code wms_} cộng 43 ký tự base64url của 32 byte ngẫu nhiên sinh bằng {@link SecureRandom}
 * (256 bit). Cơ sở dữ liệu chỉ lưu 8 ký tự đầu và SHA-256 của key; key đầy đủ chỉ trả về đúng một lần trong kết quả
 * tạo và không bao giờ được ghi log. Mọi hàm nhận {@code companyId} của ADMIN đang đăng nhập (lấy từ JWT), key của
 * doanh nghiệp khác coi như không tồn tại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    /** Tiền tố nhận diện key của hệ thống, giúp công cụ quét bí mật nhận ra key bị lộ. */
    public static final String KEY_PREFIX = "wms_";
    /** Số byte ngẫu nhiên của một key. */
    static final int RANDOM_BYTES = 32;
    /** Độ dài phần nhận diện lưu trong cơ sở dữ liệu. */
    static final int PREFIX_LENGTH = 8;
    /** Số key đang hoạt động tối đa của một doanh nghiệp. */
    public static final int MAX_ACTIVE_KEYS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final ApiKeyCacheWriter cacheWriter;

    /**
     * Tạo key mới cho doanh nghiệp.
     *
     * @throws ApiException {@code SUBSCRIPTION_EXPIRED} khi thuê bao đã hết hạn; {@code PLAN_LIMIT_EXCEEDED} với
     *                      {@code data.limit = API_ACCESS} khi gói không có quyền dùng API key (gói FREE);
     *                      {@code CONFLICT} khi doanh nghiệp đã có {@value #MAX_ACTIVE_KEYS} key đang hoạt động
     */
    @Transactional
    public CreatedApiKeyResponse create(Long companyId, Long userId, CreateApiKeyRequest request) {
        // Khóa dòng doanh nghiệp trước khi đếm để hai lần tạo đồng thời không cùng vượt giới hạn
        apiKeyRepository.lockCompany(companyId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy doanh nghiệp"));
        long active = apiKeyRepository.countByCompanyIdAndRevokedAtIsNull(companyId);
        assertPlanAllowsApiKeys(companyId, active);
        if (active >= MAX_ACTIVE_KEYS) {
            throw ApiException.conflict("Mỗi doanh nghiệp chỉ có tối đa " + MAX_ACTIVE_KEYS
                    + " API key đang hoạt động. Hãy thu hồi một key không dùng nữa trước khi tạo key mới.");
        }

        String plainKey = generateKey();
        ApiKey apiKey = apiKeyRepository.save(ApiKey.builder()
                .companyId(companyId)
                .name(request.name().trim())
                .keyPrefix(plainKey.substring(0, PREFIX_LENGTH))
                .keyHash(sha256Hex(plainKey))
                .createdByUser(userId)
                .build());
        cacheWriter.sync(apiKey);
        log.info("Doanh nghiệp {} tạo API key ID {} (tiền tố {})", companyId, apiKey.getId(), apiKey.getKeyPrefix());
        return CreatedApiKeyResponse.of(apiKey, plainKey);
    }

    /** Mọi key của doanh nghiệp, mới nhất trước, không kèm key hay chuỗi băm. */
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(Long companyId) {
        List<ApiKey> keys = apiKeyRepository.findByCompanyIdOrderByCreatedAtDescIdDesc(companyId);
        List<Long> creatorIds = keys.stream().map(ApiKey::getCreatedByUser).filter(Objects::nonNull).distinct()
                .toList();
        Map<Long, String> usernames = userRepository.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        return keys.stream()
                .map(key -> ApiKeyResponse.from(key, usernames.get(key.getCreatedByUser())))
                .toList();
    }

    /**
     * Thu hồi key. Gọi lại với key đã thu hồi thì trả kết quả hiện tại, không đổi thời điểm thu hồi.
     *
     * @throws ApiException {@code NOT_FOUND} khi key không tồn tại hoặc thuộc doanh nghiệp khác
     */
    @Transactional
    public ApiKeyResponse revoke(Long companyId, Long id) {
        ApiKey apiKey = apiKeyRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy API key"));
        if (apiKey.isActive()) {
            // Giữ lại lần dùng gần nhất chưa kịp đổ vào cơ sở dữ liệu trước khi xóa khỏi Redis
            Instant usedAt = cacheWriter.readLastUsed().get(apiKey.getId());
            if (usedAt != null && (apiKey.getLastUsedAt() == null || usedAt.isAfter(apiKey.getLastUsedAt()))) {
                apiKey.setLastUsedAt(usedAt);
            }
            apiKey.setRevokedAt(Instant.now());
            apiKey = apiKeyRepository.save(apiKey);
            log.info("Doanh nghiệp {} thu hồi API key ID {} (tiền tố {})", companyId, apiKey.getId(),
                    apiKey.getKeyPrefix());
        }
        // Luôn xóa lại khỏi Redis, kể cả khi key đã thu hồi từ trước mà lần xóa trước bị lỗi
        cacheWriter.sync(apiKey);
        String createdBy = apiKey.getCreatedByUser() == null ? null
                : userRepository.findById(apiKey.getCreatedByUser()).map(User::getUsername).orElse(null);
        return ApiKeyResponse.from(apiKey, createdBy);
    }

    /**
     * Ghi lại Redis cho mọi key (key đang hoạt động được ghi, key đã thu hồi được xóa) để Redis bị xóa dữ liệu hoặc
     * một lần ghi lỗi tự lành. Trả số key đã xử lý.
     */
    @Transactional(readOnly = true)
    public int syncAllToRedis() {
        List<ApiKey> keys = apiKeyRepository.findAll();
        keys.forEach(cacheWriter::sync);
        return keys.size();
    }

    /**
     * Đổ thời điểm dùng gần nhất do gateway ghi trên Redis vào cột {@code last_used_at}. Chỉ ghi khi mới hơn giá trị
     * đang có. Trả số dòng đã cập nhật.
     */
    @Transactional
    public int flushLastUsed() {
        int updated = 0;
        for (Map.Entry<Long, Instant> entry : cacheWriter.readLastUsed().entrySet()) {
            updated += apiKeyRepository.updateLastUsedAt(entry.getKey(), entry.getValue());
        }
        return updated;
    }

    /**
     * Gói hiện tại phải có quyền dùng API key và thuê bao chưa hết hạn. Đọc từ cơ sở dữ liệu (auth-service là nơi
     * ghi thuê bao) chứ không đọc bộ nhớ đệm Redis, nên không có trường hợp cho qua khi thiếu dữ liệu.
     */
    private void assertPlanAllowsApiKeys(Long companyId, long activeKeys) {
        Subscription subscription = subscriptionService.findByCompanyId(companyId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông tin thuê bao của doanh nghiệp"));
        subscriptionService.expireIfDue(subscription);
        Plan plan = subscription.getPlan();
        if (!Boolean.TRUE.equals(plan.getApiAccess())) {
            throw ApiException.withData(ErrorCode.PLAN_LIMIT_EXCEEDED,
                    "Gói " + plan.getName() + " không hỗ trợ API key. Nâng cấp gói để dùng API key.",
                    new PlanLimitViolation(PlanLimit.API_ACCESS, activeKeys, 0L, plan.getCode()));
        }
        if (subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new ApiException(ErrorCode.SUBSCRIPTION_EXPIRED,
                    "Gói dịch vụ đã hết hạn. Vui lòng gia hạn để tạo API key mới.");
        }
    }

    /** Sinh key mới: tiền tố {@code wms_} cộng base64url (không đệm) của 32 byte ngẫu nhiên. */
    static String generateKey() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return KEY_PREFIX + BASE64_URL.encodeToString(bytes);
    }

    /** SHA-256 của key (UTF-8), dạng hex chữ thường 64 ký tự; api-gateway tính đúng cách này để tra Redis. */
    public static String sha256Hex(String plainKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plainKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", ex);
        }
    }
}
