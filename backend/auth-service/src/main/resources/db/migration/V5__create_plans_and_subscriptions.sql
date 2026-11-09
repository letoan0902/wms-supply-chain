-- ============================================================================
-- Migration V5: Bảng plans, subscriptions và seed gói dịch vụ cho công ty (WMS-23.1)
-- ============================================================================

-- 1. Bảng plans: Định nghĩa danh mục gói dịch vụ SaaS
CREATE TABLE plans (
    code             VARCHAR(20)    NOT NULL,
    name             VARCHAR(100)   NOT NULL,
    price_monthly    DECIMAL(18,2)  NOT NULL DEFAULT 0,
    price_yearly     DECIMAL(18,2)  NOT NULL DEFAULT 0,
    max_users        INT            NOT NULL DEFAULT 3,
    max_warehouses   INT            NOT NULL DEFAULT 1,
    max_skus         INT            NOT NULL DEFAULT 200,
    max_ai_requests  INT            NOT NULL DEFAULT 20,
    api_access       BOOLEAN        NOT NULL DEFAULT FALSE,
    trial_days       INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (code)
) ENGINE = InnoDB;

-- 2. Seed 3 gói dịch vụ chuẩn theo quy định hệ thống
INSERT INTO plans (code, name, price_monthly, price_yearly, max_users, max_warehouses, max_skus, max_ai_requests, api_access, trial_days)
VALUES
    ('FREE',  'Khởi Đầu',      0.00,        0.00,        3,  1,  200, 20,  FALSE, 0),
    ('TRIAL', 'Dùng Thử',      0.00,        0.00,        50, 20, -1,  500, TRUE,  30),
    ('PRO',   'Chuyên Nghiệp', 499000.00,   4790000.00,  50, 20, -1,  500, TRUE,  0)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 3. Bảng subscriptions: Trạng thái gói dịch vụ của từng doanh nghiệp
CREATE TABLE subscriptions (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT       NOT NULL,
    plan_code     VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    trial_ends_at DATETIME(6)  NULL,
    expires_at    DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at    DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by    VARCHAR(50)  NULL,
    updated_by    VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subscriptions_company UNIQUE (company_id),
    CONSTRAINT fk_subscriptions_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_code) REFERENCES plans (code)
) ENGINE = InnoDB;

-- 4. Seed dữ liệu thuê bao cho 2 công ty mẫu:
--    - Công ty 1 (LOGIX): Gói PRO, hạn đến hết 31/12/2026
--    - Công ty 2 (FASTMOVE): Gói FREE, vô thời hạn
INSERT INTO subscriptions (company_id, plan_code, status, trial_ends_at, expires_at, created_by, updated_by)
VALUES
    (1, 'PRO',  'ACTIVE', NULL, '2026-12-31 23:59:59', 'system', 'system'),
    (2, 'FREE', 'ACTIVE', NULL, NULL,                  'system', 'system')
ON DUPLICATE KEY UPDATE plan_code = VALUES(plan_code);
