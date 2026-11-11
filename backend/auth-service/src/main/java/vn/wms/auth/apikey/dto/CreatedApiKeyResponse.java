package vn.wms.auth.apikey.dto;

import vn.wms.auth.apikey.entity.ApiKey;

import java.time.Instant;

/**
 * Kết quả {@code POST /api/auth/api-keys}: lần duy nhất hệ thống trả key đầy đủ. Sau đó chỉ còn {@code keyPrefix}.
 *
 * @param id        ID key
 * @param name      tên gợi nhớ
 * @param keyPrefix 8 ký tự đầu của key
 * @param key       key đầy đủ, dạng {@code wms_} cộng 43 ký tự base64url; hệ thống ngoài gửi trong header
 *                  {@code X-Api-Key}
 * @param createdAt thời điểm tạo
 */
public record CreatedApiKeyResponse(
        Long id,
        String name,
        String keyPrefix,
        String key,
        Instant createdAt) {

    public static CreatedApiKeyResponse of(ApiKey apiKey, String plainKey) {
        return new CreatedApiKeyResponse(apiKey.getId(), apiKey.getName(), apiKey.getKeyPrefix(), plainKey,
                apiKey.getCreatedAt());
    }

    /** Không in key ra log dù có ai gọi {@code toString()}. */
    @Override
    public String toString() {
        return "CreatedApiKeyResponse[id=" + id + ", name=" + name + ", keyPrefix=" + keyPrefix
                + ", key=***, createdAt=" + createdAt + "]";
    }
}
