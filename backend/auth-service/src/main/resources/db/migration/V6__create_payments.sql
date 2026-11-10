-- ============================================================================
-- Migration V6: Bảng payments lưu giao dịch thanh toán PayOS (WMS-23.5a, docs/SAAS_SPEC.md)
-- ============================================================================
-- Mỗi lần doanh nghiệp bấm thanh toán sinh một dòng PENDING. Webhook hợp lệ của PayOS (hoặc mock-confirm khi
-- PAYOS_MOCK=true) chuyển dòng đó sang PAID và kích hoạt gói. Số tiền tính bằng VND, không có phần thập phân.
-- order_code là số nguyên dương duy nhất toàn hệ thống (PayOS yêu cầu tối đa 9007199254740991).

CREATE TABLE payments (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    order_code       BIGINT        NOT NULL,
    plan_code        VARCHAR(20)   NOT NULL,
    cycle            VARCHAR(10)   NOT NULL,
    amount           BIGINT        NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    checkout_url     VARCHAR(500)  NULL,
    qr_code          TEXT          NULL,
    payment_link_id  VARCHAR(100)  NULL,
    reference        VARCHAR(100)  NULL,
    paid_at          DATETIME(6)   NULL,
    expires_at       DATETIME(6)   NOT NULL,
    created_by_user  BIGINT        NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at       DATETIME(6)   NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by       VARCHAR(50)   NULL,
    updated_by       VARCHAR(50)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_code UNIQUE (order_code),
    CONSTRAINT fk_payments_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_payments_plan FOREIGN KEY (plan_code) REFERENCES plans (code),
    CONSTRAINT fk_payments_created_by_user FOREIGN KEY (created_by_user) REFERENCES users (id)
) ENGINE = InnoDB;

-- Lịch sử giao dịch của một doanh nghiệp, mới nhất trước
CREATE INDEX idx_payments_company_created ON payments (company_id, created_at);
-- Tìm giao dịch PENDING đã quá hạn để chuyển EXPIRED
CREATE INDEX idx_payments_status_expires ON payments (status, expires_at);
