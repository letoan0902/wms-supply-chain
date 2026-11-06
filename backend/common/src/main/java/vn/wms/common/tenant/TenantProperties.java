package vn.wms.common.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình tách dữ liệu theo doanh nghiệp của một service ({@code app.tenant.*}).
 *
 * <pre>
 * app:
 *   tenant:
 *     required: true                                    # bật khi service đã chuyển sang TenantEntity
 *     default-company-id: ${TENANT_DEFAULT_COMPANY_ID:} # chỉ đặt ở máy cá nhân, không đặt trên máy chủ
 * </pre>
 */
@ConfigurationProperties(prefix = "app.tenant")
public class TenantProperties {

    /**
     * {@code true}: request tới API nghiệp vụ không xác định được doanh nghiệp bị từ chối với mã COMPANY_REQUIRED.
     * Mặc định {@code false} để service chưa chuyển đổi vẫn chạy như cũ.
     */
    private boolean required = false;

    /**
     * Doanh nghiệp dùng khi request không có header X-Company-Id. Chỉ dành cho môi trường phát triển
     * (chạy không qua gateway hoặc gateway tắt kiểm tra JWT). Để trống trên máy chủ.
     */
    private Long defaultCompanyId;

    /** Các đường dẫn không cần doanh nghiệp (mẫu kiểu Ant), cộng thêm vào danh sách mặc định. */
    private List<String> publicPaths = new ArrayList<>();

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Long getDefaultCompanyId() {
        return defaultCompanyId;
    }

    public void setDefaultCompanyId(Long defaultCompanyId) {
        this.defaultCompanyId = defaultCompanyId;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths != null ? publicPaths : new ArrayList<>();
    }
}
