package vn.wms.auth.company.dto;

import vn.wms.auth.company.entity.Company;

/**
 * Thông tin rút gọn của doanh nghiệp, trả trong trường {@code company} của kết quả đăng nhập
 * và của GET /api/auth/me.
 */
public record CompanySummary(Long id, String code, String name) {

    public static CompanySummary from(Company company) {
        if (company == null) {
            return null;
        }
        return new CompanySummary(company.getId(), company.getCode(), company.getName());
    }
}
