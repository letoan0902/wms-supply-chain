-- ============================================================================
-- Migration V7: Bảng api_keys cho hệ thống ngoài đọc dữ liệu của doanh nghiệp (WMS-25.1, docs/SAAS_SPEC.md)
-- ============================================================================
-- Mỗi dòng là một API key của một doanh nghiệp. Key đầy đủ chỉ trả cho ADMIN đúng một lần lúc tạo; bảng chỉ lưu
-- 8 ký tự đầu (key_prefix, để nhận diện trên giao diện) và chuỗi hex SHA-256 của key (key_hash, 64 ký tự).
-- revoked_at khác NULL nghĩa là key đã bị thu hồi. last_used_at do auth-service cập nhật định kỳ từ Redis
-- (gateway ghi thời điểm dùng vào hash wms:apikey:last-used), không ghi cơ sở dữ liệu ở mỗi request.

CREATE TABLE api_keys (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    name             VARCHAR(100)  NOT NULL,
    key_prefix       VARCHAR(8)    NOT NULL,
    key_hash         VARCHAR(64)   NOT NULL,
    last_used_at     DATETIME(6)   NULL,
    revoked_at       DATETIME(6)   NULL,
    created_by_user  BIGINT        NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at       DATETIME(6)   NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by       VARCHAR(50)   NULL,
    updated_by       VARCHAR(50)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_api_keys_key_hash UNIQUE (key_hash),
    CONSTRAINT fk_api_keys_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_api_keys_created_by_user FOREIGN KEY (created_by_user) REFERENCES users (id)
) ENGINE = InnoDB;

-- Danh sách key của một doanh nghiệp và đếm key đang hoạt động (revoked_at IS NULL)
CREATE INDEX idx_api_keys_company_revoked ON api_keys (company_id, revoked_at);
