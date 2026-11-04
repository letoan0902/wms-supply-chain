package vn.wms.order.dto;

import vn.wms.order.entity.CustomerTransaction;
import vn.wms.order.entity.CustomerTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO lịch sử giao dịch công nợ khách hàng (FR14 - WMS-08.2).
 */
public record CustomerTransactionResponse(
        Long id,
        Long customerId,
        String customerName,
        CustomerTransactionType transactionType,
        String referenceCode,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String note,
        Instant transactionDate,
        Instant createdAt,
        String createdBy
) {
    public static CustomerTransactionResponse from(CustomerTransaction tx) {
        return new CustomerTransactionResponse(
                tx.getId(),
                tx.getCustomer().getId(),
                tx.getCustomer().getName(),
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
