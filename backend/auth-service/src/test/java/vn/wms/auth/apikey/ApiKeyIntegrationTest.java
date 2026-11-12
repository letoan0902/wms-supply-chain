package vn.wms.auth.apikey;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.apikey.entity.ApiKey;
import vn.wms.auth.apikey.repository.ApiKeyRepository;
import vn.wms.auth.apikey.service.ApiKeyService;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.entity.SubscriptionStatus;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.common.plan.ApiKeyCacheKeys;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp API key cho hệ thống ngoài (WMS-25.1): tạo, xem, thu hồi, giới hạn gói, giới hạn 5 key,
 * cách ly doanh nghiệp, phân quyền ADMIN, chỉ lưu chuỗi băm và bộ nhớ đệm Redis cho gateway.
 */
class ApiKeyIntegrationTest extends BaseIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /** Doanh nghiệp mới tự đăng ký (thuê bao TRIAL có quyền dùng API key), ADMIN đầu tiên và token của ADMIN. */
    private record Tenant(long companyId, String username, String token) {
    }

    /** Key vừa tạo: ID và key đầy đủ (chỉ có trong kết quả tạo). */
    private record CreatedKey(long id, String key) {
    }

    @BeforeEach
    void stubRedisDelete() {
        // Xóa khóa tra cứu khi thu hồi: giả lập Redis xóa cả hash
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            boolean removed = mockRedisHashMap.remove(key) != null;
            return mockRedisStore.remove(key) != null || removed;
        });
    }

    private Tenant registerTenant() throws Exception {
        String suffix = System.nanoTime() + "_" + SEQ.incrementAndGet();
        String username = "key_" + suffix;
        RegisterCompanyRequest req = RegisterCompanyRequest.builder()
                .companyName("Công ty API " + suffix)
                .fullName("Quản trị " + suffix)
                .username(username)
                .email(username + "@wms.vn")
                .password(DEFAULT_PASSWORD)
                .build();
        String reg = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(reg).path("data").path("company").path("id").asLong();
        activateCompanyByEmail(username + "@wms.vn");
        return new Tenant(companyId, username, loginAndGetToken(username, DEFAULT_PASSWORD));
    }

    private ResultActions createKey(String token, String name) throws Exception {
        return mockMvc.perform(post("/api/auth/api-keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name))));
    }

    private CreatedKey createKeyOk(String token, String name) throws Exception {
        String res = createKey(token, name).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(res).path("data");
        return new CreatedKey(data.path("id").asLong(), data.path("key").asText());
    }

    private ResultActions listKeys(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/api-keys").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions revokeKey(String token, long id) throws Exception {
        return mockMvc.perform(delete("/api/auth/api-keys/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Map<String, String> lookupHash(String plainKey) {
        return mockRedisHashMap.get(ApiKeyCacheKeys.lookupKey(ApiKeyService.sha256Hex(plainKey)));
    }

    @Test
    @DisplayName("Tạo key: trả key đầy đủ một lần, chỉ lưu tiền tố và SHA-256, ghi khóa tra cứu lên Redis")
    void createReturnsFullKeyOnceAndStoresOnlyHash() throws Exception {
        Tenant tenant = registerTenant();

        String res = createKey(tenant.token(), "  Website bán hàng  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Website bán hàng"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(res).path("data");
        String key = data.path("key").asText();
        long id = data.path("id").asLong();

        // wms_ cộng 43 ký tự base64url của 32 byte ngẫu nhiên
        assertThat(key).startsWith("wms_").hasSize(47).matches("wms_[A-Za-z0-9_-]{43}");
        assertThat(data.path("keyPrefix").asText()).isEqualTo(key.substring(0, 8));
        assertThat(data.path("createdAt").asText()).isNotBlank();
        assertThat(data.has("keyHash")).isFalse();

        // Cơ sở dữ liệu: chỉ có tiền tố và chuỗi băm, không cột nào chứa key gốc
        ApiKey stored = apiKeyRepository.findById(id).orElseThrow();
        assertThat(stored.getCompanyId()).isEqualTo(tenant.companyId());
        assertThat(stored.getKeyHash()).isEqualTo(ApiKeyService.sha256Hex(key)).hasSize(64).isNotEqualTo(key);
        assertThat(stored.getKeyPrefix()).isEqualTo(key.substring(0, 8));
        assertThat(stored.getRevokedAt()).isNull();
        assertThat(stored.getCreatedByUser()).isEqualTo(userRepository.findByUsername(tenant.username())
                .orElseThrow().getId());
        Integer rowsWithPlainKey = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_keys WHERE key_hash = ? OR key_prefix = ? OR name = ?",
                Integer.class, key, key, key);
        assertThat(rowsWithPlainKey).isZero();

        // Redis: gateway tra được doanh nghiệp và ID key theo SHA-256, key gốc không nằm trên Redis
        assertThat(lookupHash(key))
                .containsEntry(ApiKeyCacheKeys.FIELD_COMPANY_ID, String.valueOf(tenant.companyId()))
                .containsEntry(ApiKeyCacheKeys.FIELD_KEY_ID, String.valueOf(id))
                .containsEntry(ApiKeyCacheKeys.FIELD_PREFIX, key.substring(0, 8));
        assertThat(mockRedisHashMap.keySet()).noneMatch(redisKey -> redisKey.contains(key));
        assertThat(mockRedisHashMap.values()).noneMatch(fields -> fields.containsValue(key));

        // Hai key khác nhau dù cùng tên
        CreatedKey second = createKeyOk(tenant.token(), "Website bán hàng");
        assertThat(second.key()).isNotEqualTo(key);
    }

    @Test
    @DisplayName("Danh sách: không có key đầy đủ, có tiền tố, tên, người tạo, lần dùng và trạng thái thu hồi")
    void listShowsMetadataWithoutKey() throws Exception {
        Tenant tenant = registerTenant();
        CreatedKey first = createKeyOk(tenant.token(), "Hệ thống kế toán");
        CreatedKey second = createKeyOk(tenant.token(), "Website");

        String res = listKeys(tenant.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                // Mới nhất trước
                .andExpect(jsonPath("$.data[0].id").value(second.id()))
                .andExpect(jsonPath("$.data[0].name").value("Website"))
                .andExpect(jsonPath("$.data[0].keyPrefix").value(second.key().substring(0, 8)))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].createdBy").value(tenant.username()))
                .andExpect(jsonPath("$.data[0].lastUsedAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].revokedAt").doesNotExist())
                .andExpect(jsonPath("$.data[1].id").value(first.id()))
                .andReturn().getResponse().getContentAsString();
        assertThat(res).doesNotContain(first.key()).doesNotContain(second.key())
                .doesNotContain(ApiKeyService.sha256Hex(first.key())).doesNotContain("keyHash");
    }

    @Test
    @DisplayName("Thu hồi: đặt revoked_at, xóa khóa tra cứu trên Redis, gọi lại vẫn trả 200")
    void revokeMarksKeyAndRemovesCache() throws Exception {
        Tenant tenant = registerTenant();
        CreatedKey created = createKeyOk(tenant.token(), "Ứng dụng di động");
        assertThat(lookupHash(created.key())).isNotNull();

        revokeKey(tenant.token(), created.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(created.id()))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.revokedAt").isNotEmpty());

        ApiKey revoked = apiKeyRepository.findById(created.id()).orElseThrow();
        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(lookupHash(created.key())).isNull();

        // Thu hồi lần hai: không lỗi, không đổi thời điểm thu hồi
        revokeKey(tenant.token(), created.id()).andExpect(status().isOk());
        assertThat(apiKeyRepository.findById(created.id()).orElseThrow().getRevokedAt())
                .isEqualTo(revoked.getRevokedAt());

        listKeys(tenant.token())
                .andExpect(jsonPath("$.data[0].active").value(false))
                .andExpect(jsonPath("$.data[0].revokedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Gói Khởi Đầu không có quyền dùng API key: 403 PLAN_LIMIT_EXCEEDED với limit = API_ACCESS")
    void freePlanCannotCreateKeys() throws Exception {
        Tenant tenant = registerTenant();
        mockMvc.perform(post("/api/auth/subscription/downgrade")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk());

        createKey(tenant.token(), "Website")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.data.limit").value("API_ACCESS"))
                .andExpect(jsonPath("$.data.plan").value("FREE"))
                .andExpect(jsonPath("$.data.max").value(0))
                .andExpect(jsonPath("$.data.current").value(0));

        assertThat(apiKeyRepository.countByCompanyIdAndRevokedAtIsNull(tenant.companyId())).isZero();
        // Danh sách vẫn xem được (rỗng) để ADMIN thấy trang và lời nhắc nâng cấp
        listKeys(tenant.token()).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("Thuê bao đã hết hạn: không tạo được key mới (SUBSCRIPTION_EXPIRED) nhưng vẫn thu hồi được")
    void expiredSubscriptionCannotCreateButCanRevoke() throws Exception {
        Tenant tenant = registerTenant();
        CreatedKey created = createKeyOk(tenant.token(), "Website");
        Subscription subscription = subscriptionRepository.findByCompanyId(tenant.companyId()).orElseThrow();
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscriptionRepository.save(subscription);

        createKey(tenant.token(), "Key mới")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_EXPIRED"));
        revokeKey(tenant.token(), created.id()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tối đa 5 key đang hoạt động: key thứ 6 nhận 409, thu hồi một key thì tạo lại được")
    void atMostFiveActiveKeys() throws Exception {
        Tenant tenant = registerTenant();
        long firstId = 0;
        for (int i = 1; i <= ApiKeyService.MAX_ACTIVE_KEYS; i++) {
            long id = createKeyOk(tenant.token(), "Key " + i).id();
            if (i == 1) {
                firstId = id;
            }
        }

        createKey(tenant.token(), "Key 6")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("tối đa 5")));
        assertThat(apiKeyRepository.countByCompanyIdAndRevokedAtIsNull(tenant.companyId())).isEqualTo(5);

        revokeKey(tenant.token(), firstId).andExpect(status().isOk());
        createKey(tenant.token(), "Key 6").andExpect(status().isCreated());
        // Key đã thu hồi vẫn nằm trong danh sách: 6 dòng, 5 đang hoạt động
        listKeys(tenant.token()).andExpect(jsonPath("$.data.length()").value(6));
        assertThat(apiKeyRepository.countByCompanyIdAndRevokedAtIsNull(tenant.companyId())).isEqualTo(5);
    }

    @Test
    @DisplayName("Cách ly: doanh nghiệp B không thấy và không thu hồi được key của A (404)")
    void otherCompanyCannotSeeOrRevokeKey() throws Exception {
        Tenant a = registerTenant();
        Tenant b = registerTenant();
        CreatedKey keyOfA = createKeyOk(a.token(), "Key của A");
        createKeyOk(b.token(), "Key của B");

        revokeKey(b.token(), keyOfA.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        listKeys(b.token())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Key của B"));

        assertThat(apiKeyRepository.findById(keyOfA.id()).orElseThrow().isActive()).isTrue();
        assertThat(lookupHash(keyOfA.key())).isNotNull();
    }

    @Test
    @DisplayName("Chỉ ADMIN: tài khoản khác nhận 403, chưa đăng nhập nhận 401, tên trống nhận 400")
    void onlyAdminCanManageKeys() throws Exception {
        String staffToken = loginAndGetToken("staff", DEFAULT_PASSWORD);
        createKey(staffToken, "Website")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        listKeys(staffToken).andExpect(status().isForbidden());
        revokeKey(staffToken, 1L).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/api-keys"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        Tenant tenant = registerTenant();
        createKey(tenant.token(), " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Đồng bộ định kỳ: Redis mất dữ liệu thì key đang hoạt động được ghi lại, key đã thu hồi không")
    void periodicSyncHealsRedis() throws Exception {
        Tenant tenant = registerTenant();
        CreatedKey active = createKeyOk(tenant.token(), "Đang dùng");
        CreatedKey revoked = createKeyOk(tenant.token(), "Đã thu hồi");
        revokeKey(tenant.token(), revoked.id()).andExpect(status().isOk());

        mockRedisHashMap.clear();
        // Giả lập một lần xóa bị lỗi: khóa của key đã thu hồi còn sót trên Redis
        mockRedisHashMap.put(ApiKeyCacheKeys.lookupKey(ApiKeyService.sha256Hex(revoked.key())),
                new java.util.HashMap<>(Map.of(ApiKeyCacheKeys.FIELD_KEY_ID, String.valueOf(revoked.id()))));

        apiKeyService.syncAllToRedis();

        assertThat(lookupHash(active.key()))
                .containsEntry(ApiKeyCacheKeys.FIELD_KEY_ID, String.valueOf(active.id()));
        assertThat(lookupHash(revoked.key())).isNull();
    }

    @Test
    @DisplayName("Lần dùng gần nhất: gateway ghi trên Redis, auth-service đổ vào cơ sở dữ liệu, không lùi thời điểm")
    void lastUsedIsFlushedFromRedis() throws Exception {
        Tenant tenant = registerTenant();
        CreatedKey created = createKeyOk(tenant.token(), "Website");
        Instant usedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        mockRedisHashMap.computeIfAbsent(ApiKeyCacheKeys.LAST_USED_KEY, k -> new java.util.HashMap<>())
                .put(String.valueOf(created.id()), String.valueOf(usedAt.toEpochMilli()));
        // Trường hỏng bị bỏ qua
        mockRedisHashMap.get(ApiKeyCacheKeys.LAST_USED_KEY).put("abc", "xyz");

        assertThat(apiKeyService.flushLastUsed()).isGreaterThanOrEqualTo(1);
        assertThat(apiKeyRepository.findById(created.id()).orElseThrow().getLastUsedAt()).isEqualTo(usedAt);

        // Giá trị cũ hơn không ghi đè
        mockRedisHashMap.get(ApiKeyCacheKeys.LAST_USED_KEY)
                .put(String.valueOf(created.id()), String.valueOf(usedAt.minusSeconds(3600).toEpochMilli()));
        apiKeyService.flushLastUsed();
        assertThat(apiKeyRepository.findById(created.id()).orElseThrow().getLastUsedAt()).isEqualTo(usedAt);

        listKeys(tenant.token()).andExpect(jsonPath("$.data[0].lastUsedAt").isNotEmpty());
        List<ApiKey> keys = apiKeyRepository.findByCompanyIdOrderByCreatedAtDescIdDesc(tenant.companyId());
        assertThat(keys).hasSize(1);
    }
}
