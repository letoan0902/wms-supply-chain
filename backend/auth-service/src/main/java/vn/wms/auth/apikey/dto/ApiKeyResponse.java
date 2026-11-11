package vn.wms.auth.apikey.dto;

import vn.wms.auth.apikey.entity.ApiKey;

import java.time.Instant;

/**
 * Một API key trong danh sách {@code GET /api/auth/api-keys}. Không bao giờ chứa key đầy đủ hay chuỗi băm.
 *
 * @param id         ID key, dùng để thu hồi
 * @param name       tên gợi nhớ
 * @param keyPrefix  8 ký tự đầu của key, ví dụ {@code wms_Ab3x}
 * @param active     {@code true} khi chưa thu hồi
 * @param createdAt  thời điểm tạo
 * @param createdBy  tên đăng nhập của ADMIN đã tạo, {@code null} nếu không còn tìm thấy tài khoản
 * @param lastUsedAt lần dùng gần nhất (cập nhật định kỳ, có thể trễ khoảng một phút), {@code null} nếu chưa dùng
 * @param revokedAt  thời điểm thu hồi, {@code null} nếu đang hoạt động
 */
public record ApiKeyResponse(
        Long id,
        String name,
        String keyPrefix,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant lastUsedAt,
        Instant revokedAt) {

    public static ApiKeyResponse from(ApiKey apiKey, String createdByUsername) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                apiKey.isActive(),
                apiKey.getCreatedAt(),
                createdByUsername,
                apiKey.getLastUsedAt(),
                apiKey.getRevokedAt());
    }
}
