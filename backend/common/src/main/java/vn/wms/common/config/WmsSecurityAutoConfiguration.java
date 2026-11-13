package vn.wms.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import vn.wms.common.security.ApiResponseSecurityErrorHandler;
import vn.wms.common.security.GatewayIdentityFilter;
import vn.wms.common.security.GatewaySecurityProperties;

/**
 * Chuỗi bộ lọc Spring Security dùng chung cho các service nghiệp vụ (product, inventory, order, notification, ai):
 * <ul>
 *   <li>{@link GatewayIdentityFilter} đổi header danh tính của api-gateway thành {@code Authentication} với authority
 *       {@code ROLE_<vai trò>}, nhờ đó {@code @PreAuthorize} có tác dụng khi service bật {@code @EnableMethodSecurity};</li>
 *   <li>đường dẫn hạ tầng ({@link GatewaySecurityProperties#DEFAULT_PUBLIC_PATHS} cộng {@code app.security.public-paths})
 *       luôn mở; mọi đường dẫn khác cần danh tính, thiếu thì 401 UNAUTHORIZED (chỉ xảy ra khi
 *       {@code app.security.allow-anonymous-without-gateway=false});</li>
 *   <li>401 và 403 trả theo khung {@code ApiResponse} qua {@link ApiResponseSecurityErrorHandler}.</li>
 * </ul>
 * Service tự khai báo {@link SecurityFilterChain} riêng (auth-service kiểm tra JWT) thì chuỗi này không được tạo.
 * Xem docs/SECURITY.md.
 */
// Phải chạy trước cả chuỗi mặc định của actuator (ManagementWebSecurityAutoConfiguration), nếu không chuỗi đó được tạo
// trước và chuỗi dùng chung bị bỏ qua (@ConditionalOnMissingBean)
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.security.web.SecurityFilterChain",
        "org.springframework.security.config.annotation.web.builders.HttpSecurity"})
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class WmsSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiResponseSecurityErrorHandler wmsSecurityErrorHandler(ObjectProvider<ObjectMapper> objectMapper) {
        return new ApiResponseSecurityErrorHandler(
                objectMapper.getIfAvailable(() -> JsonMapper.builder().findAndAddModules().build()));
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain wmsGatewaySecurityFilterChain(HttpSecurity http, GatewaySecurityProperties properties,
                                                             ApiResponseSecurityErrorHandler errorHandler)
            throws Exception {
        RequestMatcher[] publicPaths = properties.allPublicPaths().stream()
                .map(AntPathRequestMatcher::antMatcher)
                .toArray(RequestMatcher[]::new);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new GatewayIdentityFilter(properties), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Trang lỗi nội bộ của Spring Boot: lỗi gốc đã được kiểm tra quyền ở lần dispatch đầu
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Preflight CORS do api-gateway trả lời; service không chặn nếu nhận được
                        .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.OPTIONS, "/**")).permitAll()
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }
}
