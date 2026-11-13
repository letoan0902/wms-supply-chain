package vn.wms.common.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

import java.util.Map;

/**
 * Cho Hibernate biết doanh nghiệp hiện tại mỗi khi mở phiên làm việc, lấy từ {@link TenantContext}.
 * Khi chưa xác định doanh nghiệp thì trả {@link TenantContext#NO_COMPANY}: truy vấn trên entity kế thừa
 * {@link TenantEntity} sẽ không trả về dòng nào.
 *
 * <p>Đồng thời là {@link HibernatePropertiesCustomizer} để Spring Boot tự đăng ký bộ phân giải này với Hibernate.
 * Service không có entity nào dùng {@code @TenantId} không bị ảnh hưởng.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long companyId = TenantContext.getCompanyId();
        return companyId != null ? companyId : TenantContext.NO_COMPANY;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
