-- V9__create_stock_receipts.sql: Nhật ký phiếu nhập kho (GRN) đã cộng tồn từ sự kiện order.received (WMS-10.3)
-- Mỗi GRN của một doanh nghiệp chỉ được cộng tồn một lần: sự kiện giao lại (redelivery, restart, phát lại từ
-- dead-letter) gặp ràng buộc duy nhất (company_id, grn_id) nên bị bỏ qua, không cộng tồn hai lần.

CREATE TABLE stock_receipts (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    company_id   BIGINT       NOT NULL,
    grn_id       BIGINT       NOT NULL,
    grn_number   VARCHAR(30)  NULL,
    po_id        BIGINT       NULL,
    warehouse_id BIGINT       NOT NULL,
    message_id   VARCHAR(100) NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at   DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by   VARCHAR(50)  NULL,
    updated_by   VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_receipts_company_grn UNIQUE (company_id, grn_id),
    INDEX idx_stock_receipts_company (company_id)
) ENGINE = InnoDB;
