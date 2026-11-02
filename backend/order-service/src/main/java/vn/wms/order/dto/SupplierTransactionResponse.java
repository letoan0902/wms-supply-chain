package vn.wms.order.dto;

import vn.wms.order.entity.SupplierTransaction;
import vn.wms.order.entity.SupplierTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thông tin giao dịch công nợ nhà cung cấp trả về client (FR09 - WMS-08.1).
 */
public record SupplierTransactionResponse(
        Long id,
        Long supplierId,
        String supplierName,
        SupplierTransactionType transactionType,
        String referenceCode,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String note,
        Instant transactionDate,
        Instant createdAt,
        String createdBy
) {
    public static SupplierTransactionResponse from(SupplierTransaction tx) {
        return new SupplierTransactionResponse(
                tx.getId(),
                tx.getSupplier().getId(),
                tx.getSupplier().getName(),
                tx.getTransactionType(),
                tx.getReferenceCode(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getNote(),
                tx.getTransactionDate(),
                tx.getCreatedAt(),
                tx.getCreatedBy()
        );
    }
}
