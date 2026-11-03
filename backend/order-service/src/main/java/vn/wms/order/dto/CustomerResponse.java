package vn.wms.order.dto;

import vn.wms.order.entity.Customer;
import vn.wms.order.entity.CustomerType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO thông tin chi tiết hồ sơ khách hàng / đại lý (FR14 - WMS-08.2).
 */
public record CustomerResponse(
        Long id,
        String code,
        String name,
        CustomerType customerType,
        String taxCode,
        String contactName,
        String email,
        String phone,
        String address,
        String paymentTerms,
        BigDecimal creditLimit,
        BigDecimal currentDebt,
        BigDecimal availableCredit,
        boolean isOverCreditLimit,
        boolean debtLocked,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerResponse from(Customer c) {
        BigDecimal limit = c.getCreditLimit() != null ? c.getCreditLimit() : BigDecimal.ZERO;
        BigDecimal debt = c.getCurrentDebt() != null ? c.getCurrentDebt() : BigDecimal.ZERO;

        boolean overLimit = limit.compareTo(BigDecimal.ZERO) > 0 && debt.compareTo(limit) > 0;
        BigDecimal available = limit.compareTo(BigDecimal.ZERO) > 0
                ? limit.subtract(debt).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        return new CustomerResponse(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getCustomerType(),
                c.getTaxCode(),
                c.getContactName(),
                c.getEmail(),
                c.getPhone(),
                c.getAddress(),
                c.getPaymentTerms(),
                limit,
                debt,
                available,
                overLimit,
                c.isDebtLocked(),
                c.getNotes(),
                c.isActive(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
