package vn.wms.order.dto;

import vn.wms.order.entity.Supplier;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thông tin chi tiết nhà cung cấp (FR09 - WMS-08.1).
 */
public record SupplierResponse(
        Long id,
        String code,
        String name,
        String taxCode,
        String contactName,
        String email,
        String phone,
        String address,
        String bankAccount,
        String bankName,
        String paymentTerms,
        BigDecimal creditLimit,
        BigDecimal currentDebt,
        boolean isOverCreditLimit,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static SupplierResponse from(Supplier s) {
        boolean overLimit = s.getCreditLimit() != null
                && s.getCreditLimit().compareTo(BigDecimal.ZERO) > 0
                && s.getCurrentDebt() != null
                && s.getCurrentDebt().compareTo(s.getCreditLimit()) > 0;

        return new SupplierResponse(
                s.getId(),
                s.getCode(),
                s.getName(),
                s.getTaxCode(),
                s.getContactName(),
                s.getEmail(),
                s.getPhone(),
                s.getAddress(),
                s.getBankAccount(),
                s.getBankName(),
                s.getPaymentTerms(),
                s.getCreditLimit() != null ? s.getCreditLimit() : BigDecimal.ZERO,
                s.getCurrentDebt() != null ? s.getCurrentDebt() : BigDecimal.ZERO,
                overLimit,
                s.isActive(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
