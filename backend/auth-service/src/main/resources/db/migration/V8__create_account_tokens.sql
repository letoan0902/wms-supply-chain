-- ============================================================================
-- Migration V8: Bảng account_tokens cho liên kết dùng một lần gửi qua email (WMS-23.6, docs/SAAS_SPEC.md)
-- ============================================================================
-- Mỗi dòng là một mã trong liên kết kích hoạt doanh nghiệp (type = COMPANY_ACTIVATION, hiệu lực 24 giờ) hoặc
-- đặt lại mật khẩu (type = PASSWORD_RESET, hiệu lực 30 phút). Không lưu mã gốc: chỉ lưu chuỗi hex SHA-256 của mã
-- (token_hash, 64 ký tự). used_at khác NULL nghĩa là mã đã dùng hoặc đã bị thay bằng mã mới hơn.
-- Doanh nghiệp đang có (hai công ty mẫu và các doanh nghiệp đã đăng ký trước migration này) giữ nguyên ACTIVE:
-- migration không đổi bảng companies.

CREATE TABLE account_tokens (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    company_id   BIGINT       NOT NULL,
    user_id      BIGINT       NULL,
    type         VARCHAR(30)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    used_at      DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at   DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by   VARCHAR(50)  NULL,
    updated_by   VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_account_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_tokens_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- Thu hồi mã chưa dùng khi phát mã mới: kích hoạt tra theo doanh nghiệp, đặt lại mật khẩu tra theo tài khoản
CREATE INDEX idx_account_tokens_company_type ON account_tokens (company_id, type, used_at);
CREATE INDEX idx_account_tokens_user_type ON account_tokens (user_id, type, used_at);
