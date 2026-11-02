-- V2: Thêm các trường quản lý nhà cung cấp và lịch sử giao dịch công nợ (FR09 - WMS-08.1)

ALTER TABLE suppliers
    ADD COLUMN tax_code VARCHAR(30) NULL AFTER name,
    ADD COLUMN contact_name VARCHAR(100) NULL AFTER tax_code,
    ADD COLUMN bank_account VARCHAR(50) NULL AFTER address,
    ADD COLUMN bank_name VARCHAR(100) NULL AFTER bank_account,
    ADD COLUMN payment_terms VARCHAR(50) NULL AFTER bank_name,
    ADD COLUMN credit_limit DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER payment_terms,
    ADD COLUMN current_debt DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER credit_limit;

ALTER TABLE suppliers
    ADD INDEX idx_suppliers_tax_code (tax_code),
    ADD INDEX idx_suppliers_active (active);

-- Bảng lưu lịch sử giao dịch & công nợ nhà cung cấp
CREATE TABLE supplier_transactions (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    supplier_id      BIGINT         NOT NULL,
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
    CONSTRAINT fk_supplier_transactions_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    INDEX idx_supplier_transactions_supplier (supplier_id),
    INDEX idx_supplier_transactions_date (transaction_date)
) ENGINE = InnoDB;
