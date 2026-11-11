-- V14__create_stock_alert_runs.sql: dấu chống gửi trùng của tác vụ quét cảnh báo tồn kho (WMS-15.1).
-- Mỗi (doanh nghiệp, ngày, loại cảnh báo) chỉ có một dòng. Tác vụ thêm dòng TRƯỚC khi phát sự kiện alert.stock-summary,
-- nên chạy lại trong ngày (quét tay, hai instance cùng chạy lúc 00:00) gặp ràng buộc duy nhất và không phát lại.
-- alert_kind: NEAR_EXPIRY, EXPIRED, DEAD_STOCK, LOW_STOCK. status: RUNNING, COMPLETED.

CREATE TABLE stock_alert_runs (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    company_id      BIGINT       NOT NULL,
    alert_date      DATE         NOT NULL,
    alert_kind      VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    warehouse_count INT          NOT NULL DEFAULT 0,
    item_count      INT          NOT NULL DEFAULT 0,
    email_count     INT          NOT NULL DEFAULT 0,
    triggered_by    VARCHAR(50)  NULL,
    note            VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at      DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by      VARCHAR(50)  NULL,
    updated_by      VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_alert_runs_company_date_kind UNIQUE (company_id, alert_date, alert_kind)
) ENGINE = InnoDB;
