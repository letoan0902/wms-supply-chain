package vn.wms.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.wms.auth.security.JwtAuthenticationFilter;
import vn.wms.auth.security.JwtService;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Cấu hình Spring Security cho auth-service: API không trạng thái (stateless) dùng JWT.
 * Cho phép /api/auth/**, actuator và Swagger; riêng /api/auth/me yêu cầu đã đăng nhập.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper, org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate)
            throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/auth/register").hasRole("ADMIN")
                    .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                    .requestMatchers("/api/auth/**", "/actuator/**",
                            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint((request, response, ex) ->
                            writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED))
                    .accessDeniedHandler((request, response, ex) ->
                            writeError(response, objectMapper, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN)))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, redisTemplate), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Mã hóa mật khẩu bằng BCrypt (dùng khi triển khai đăng nhập/đăng ký thật dựa trên bảng users). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper,
                                   HttpStatus status, ErrorCode code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, code.getDefaultMessage()));
    }
}
