-- V3: Thêm các trường quản lý khách hàng sỉ/lẻ, hạn mức tín dụng và lịch sử công nợ (FR14 - WMS-08.2)

ALTER TABLE customers
    ADD COLUMN customer_type VARCHAR(20) NOT NULL DEFAULT 'RETAIL' AFTER name,
    ADD COLUMN tax_code VARCHAR(30) NULL AFTER customer_type,
    ADD COLUMN contact_name VARCHAR(100) NULL AFTER tax_code,
    ADD COLUMN payment_terms VARCHAR(50) NULL AFTER address,
    ADD COLUMN credit_limit DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER payment_terms,
    ADD COLUMN current_debt DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER credit_limit,
    ADD COLUMN debt_locked BOOLEAN NOT NULL DEFAULT FALSE AFTER current_debt,
    ADD COLUMN notes VARCHAR(500) NULL AFTER debt_locked;

ALTER TABLE customers
    ADD INDEX idx_customers_type (customer_type),
    ADD INDEX idx_customers_tax_code (tax_code),
    ADD INDEX idx_customers_active (active),
    ADD INDEX idx_customers_debt_locked (debt_locked);

-- Bảng lưu lịch sử giao dịch & công nợ khách hàng (FR14)
CREATE TABLE customer_transactions (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    customer_id      BIGINT         NOT NULL,
    transaction_type VARCHAR(30)    NOT NULL,
    reference_code   VARCHAR(50)    NULL,
    amount           DECIMAL(18, 2) NOT NULL,
    balance_after    DECIMAL(18, 2) NOT NULL,
    note             VARCHAR(500)   NULL,
    transaction_date DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_at       DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at       DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by       VARCHAR(50)    NULL,
    updated_by       VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_customer_transactions_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    INDEX idx_customer_transactions_customer (customer_id),
    INDEX idx_customer_transactions_date (transaction_date)
) ENGINE = InnoDB;
