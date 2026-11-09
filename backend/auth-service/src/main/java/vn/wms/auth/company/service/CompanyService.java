package vn.wms.auth.company.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.company.dto.CompanyResponse;
import vn.wms.auth.company.dto.UpdateCompanyRequest;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

/**
 * Nghiệp vụ doanh nghiệp. Mọi hàm chỉ làm việc trên doanh nghiệp của người đang đăng nhập
 * (companyId lấy từ JWT), hệ thống không có quản trị viên toàn nền tảng.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    /** Thông tin doanh nghiệp của người đang đăng nhập. */
    @Transactional(readOnly = true)
    public CompanyResponse getOwnCompany(Long companyId) {
        return CompanyResponse.from(findOwnCompany(companyId));
    }

    /** ADMIN cập nhật tên, mã số thuế, số điện thoại, địa chỉ của doanh nghiệp mình. */
    @Transactional
    public CompanyResponse updateOwnCompany(Long companyId, UpdateCompanyRequest request) {
        Company company = findOwnCompany(companyId);
        company.setName(request.name().trim());
        company.setTaxCode(trimToNull(request.taxCode()));
        company.setPhone(trimToNull(request.phone()));
        company.setAddress(trimToNull(request.address()));
        return CompanyResponse.from(companyRepository.save(company));
    }

    private Company findOwnCompany(Long companyId) {
        if (companyId == null) {
            throw new ApiException(ErrorCode.COMPANY_REQUIRED, "Tài khoản chưa gắn với doanh nghiệp nào");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy doanh nghiệp của tài khoản"));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
