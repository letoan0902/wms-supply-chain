package vn.wms.auth.company.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.company.dto.CompanyResponse;
import vn.wms.auth.company.dto.CreateCompanyRequest;
import vn.wms.auth.company.dto.UpdateCompanyRequest;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> listCompanies(String keyword, Pageable pageable) {
        Page<Company> page;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            page = companyRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(kw, kw, pageable);
        } else {
            page = companyRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(CompanyResponse::from));
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompanyById(Long id, Long currentCompanyId, boolean isSystemAdmin) {
        validateAccess(id, currentCompanyId, isSystemAdmin);
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy doanh nghiệp có ID: " + id));
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        String cleanCode = request.code().trim().toUpperCase();
        if (companyRepository.existsByCode(cleanCode)) {
            throw ApiException.conflict("Mã doanh nghiệp đã tồn tại: " + cleanCode);
        }
        if (request.taxCode() != null && !request.taxCode().isBlank() && companyRepository.existsByTaxCode(request.taxCode().trim())) {
            throw ApiException.conflict("Mã số thuế đã tồn tại: " + request.taxCode().trim());
        }

        Company company = new Company();
        company.setCode(cleanCode);
        company.setName(request.name().trim());
        company.setTaxCode(request.taxCode() != null ? request.taxCode().trim() : null);
        company.setEmail(request.email() != null ? request.email().trim() : null);
        company.setPhone(request.phone() != null ? request.phone().trim() : null);
        company.setAddress(request.address() != null ? request.address().trim() : null);
        company.setStatus("ACTIVE");

        Company saved = companyRepository.save(company);
        return CompanyResponse.from(saved);
    }

    @Transactional
    public CompanyResponse updateCompany(Long id, UpdateCompanyRequest request, Long currentCompanyId, boolean isSystemAdmin) {
        validateAccess(id, currentCompanyId, isSystemAdmin);
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy doanh nghiệp có ID: " + id));

        if (request.taxCode() != null && !request.taxCode().isBlank()) {
            String cleanTax = request.taxCode().trim();
            if (!cleanTax.equalsIgnoreCase(company.getTaxCode()) && companyRepository.existsByTaxCode(cleanTax)) {
                throw ApiException.conflict("Mã số thuế đã tồn tại: " + cleanTax);
            }
            company.setTaxCode(cleanTax);
        }

        company.setName(request.name().trim());
        company.setEmail(request.email() != null ? request.email().trim() : null);
        company.setPhone(request.phone() != null ? request.phone().trim() : null);
        company.setAddress(request.address() != null ? request.address().trim() : null);

        Company updated = companyRepository.save(company);
        return CompanyResponse.from(updated);
    }

    @Transactional
    public CompanyResponse toggleCompanyStatus(Long id, String status) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy doanh nghiệp có ID: " + id));

        company.setStatus(status != null ? status.trim().toUpperCase() : "ACTIVE");
        Company updated = companyRepository.save(company);
        return CompanyResponse.from(updated);
    }

    private void validateAccess(Long targetCompanyId, Long currentCompanyId, boolean isSystemAdmin) {
        if (isSystemAdmin) {
            return;
        }
        if (currentCompanyId == null || !currentCompanyId.equals(targetCompanyId)) {
            throw ApiException.forbidden("Bạn không có quyền truy cập dữ liệu của doanh nghiệp khác");
        }
    }
}
