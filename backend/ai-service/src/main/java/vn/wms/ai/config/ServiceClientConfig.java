package vn.wms.ai.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.util.List;

/**
 * Client gọi service khác qua Eureka (địa chỉ dạng {@code http://product-service}), dùng cho bước nhập hàng loạt
 * (WMS-24.2). Mỗi lời gọi mang nguyên danh tính của người đang thao tác để service phía sau tự kiểm tra quyền, giới
 * hạn gói và lọc theo doanh nghiệp: X-Company-Id lấy từ {@link TenantContext}, X-User-Name, X-User-Roles,
 * X-User-Warehouse-Ids và X-Plan chép từ request đang xử lý (đều do api-gateway gắn).
 */
@Configuration(proxyBeanMethods = false)
public class ServiceClientConfig {

    /** Header gói dịch vụ do api-gateway gắn (docs/SAAS_SPEC.md, mục 2.2). */
    public static final String HEADER_PLAN = "X-Plan";

    static final List<String> FORWARDED_HEADERS = List.of(UserContext.HEADER_USER_NAME,
            UserContext.HEADER_USER_ROLES, UserContext.HEADER_USER_WAREHOUSE_IDS, HEADER_PLAN);

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder().requestInterceptor(identityForwardingInterceptor());
    }

    static ClientHttpRequestInterceptor identityForwardingInterceptor() {
        return (request, body, execution) -> {
            Long companyId = TenantContext.getCompanyId();
            if (companyId != null) {
                request.getHeaders().set(TenantContext.HEADER_COMPANY_ID, String.valueOf(companyId));
            }
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest current = servletAttributes.getRequest();
                for (String name : FORWARDED_HEADERS) {
                    String value = current.getHeader(name);
                    if (value != null && !value.isBlank()) {
                        request.getHeaders().set(name, value);
                    }
                }
            }
            return execution.execute(request, body);
        };
    }
}
