package vn.wms.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật TẠM THỜI của notification-service.
 *
 * <p>TODO (Sprint 1): hiện cho phép mọi request (permitAll) để nhóm phát triển nghiệp vụ không bị chặn.
 * Khi RBAC hoàn thiện: đọc danh tính từ header X-User-Name / X-User-Roles do api-gateway chuyển tiếp
 * (hoặc tự kiểm tra JWT giống auth-service), rồi phân quyền bằng @PreAuthorize theo các vai trò trong
 * vn.wms.common.constant.Roles.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
