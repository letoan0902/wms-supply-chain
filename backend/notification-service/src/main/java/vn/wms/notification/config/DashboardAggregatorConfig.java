package vn.wms.notification.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.util.stream.Collectors;

@Configuration
public class DashboardAggregatorConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder()
                .requestInterceptor(tenantAndUserForwardingInterceptor());
    }

    private ClientHttpRequestInterceptor tenantAndUserForwardingInterceptor() {
        return (request, body, execution) -> {
            if (TenantContext.isSet()) {
                request.getHeaders().set("X-Company-Id", String.valueOf(TenantContext.getCompanyId()));
            }
            UserContext.getCurrentUser().ifPresent(user -> {
                request.getHeaders().set(UserContext.HEADER_USER_NAME, user.username());
                if (user.roles() != null && !user.roles().isEmpty()) {
                    request.getHeaders().set(UserContext.HEADER_USER_ROLES, String.join(",", user.roles()));
                }
                if (user.warehouseIds() != null && !user.warehouseIds().isEmpty()) {
                    String whIds = user.warehouseIds().stream().map(String::valueOf).collect(Collectors.joining(","));
                    request.getHeaders().set(UserContext.HEADER_USER_WAREHOUSE_IDS, whIds);
                }
            });
            return execution.execute(request, body);
        };
    }
}
