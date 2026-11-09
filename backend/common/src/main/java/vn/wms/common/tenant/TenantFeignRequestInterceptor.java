package vn.wms.common.tenant;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.common.security.UserContext;

import java.util.List;

/**
 * Chuyển tiếp danh tính sang service được gọi qua OpenFeign: header {@value TenantContext#HEADER_COMPANY_ID}
 * lấy từ {@link TenantContext}, các header người dùng (X-User-Name, X-User-Roles, X-User-Warehouse-Ids) lấy từ
 * request đang xử lý nếu có. Nhờ đó service phía sau lọc dữ liệu theo đúng doanh nghiệp và đúng người gọi.
 */
public class TenantFeignRequestInterceptor implements RequestInterceptor {

    private static final List<String> USER_HEADERS = List.of(
            UserContext.HEADER_USER_NAME, UserContext.HEADER_USER_ROLES, UserContext.HEADER_USER_WAREHOUSE_IDS);

    @Override
    public void apply(RequestTemplate template) {
        Long companyId = TenantContext.getCompanyId();
        if (companyId != null) {
            template.removeHeader(TenantContext.HEADER_COMPANY_ID);
            template.header(TenantContext.HEADER_COMPANY_ID, String.valueOf(companyId));
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            for (String name : USER_HEADERS) {
                String value = request.getHeader(name);
                if (value != null && !value.isBlank() && !template.headers().containsKey(name)) {
                    template.header(name, value);
                }
            }
        }
    }
}
