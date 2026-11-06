package vn.wms.auth.company.dto;

import vn.wms.auth.company.entity.Company;

import java.time.Instant;

public record CompanyResponse(
        Long id,
        String code,
        String name,
        String taxCode,
        String email,
        String phone,
        String address,
        String status,
        Instant createdAt
) {
    public static CompanyResponse from(Company company) {
        if (company == null) {
            return null;
        }
        return new CompanyResponse(
                company.getId(),
                company.getCode(),
                company.getName(),
                company.getTaxCode(),
                company.getEmail(),
                company.getPhone(),
                company.getAddress(),
                company.getStatus(),
                company.getCreatedAt()
        );
    }
}
