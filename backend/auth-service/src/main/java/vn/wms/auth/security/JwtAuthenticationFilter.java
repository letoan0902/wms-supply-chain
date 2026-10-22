package vn.wms.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.wms.common.constant.Roles;

import java.io.IOException;
import java.util.List;

/**
 * Đọc header Authorization dạng Bearer, nếu token hợp lệ thì đặt Authentication vào SecurityContext.
 * Token sai/hết hạn KHÔNG ném lỗi ở đây: request đi tiếp như chưa đăng nhập, endpoint nào yêu cầu xác thực
 * sẽ trả 401 qua AuthenticationEntryPoint.
 *
 * <p>Không gắn @Component để tránh Spring Boot đăng ký thêm lần nữa như một servlet filter độc lập;
 * bộ lọc được tạo và thêm vào chuỗi bảo mật trong SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = jwtService.parse(header.substring(BEARER_PREFIX.length()));
                List<SimpleGrantedAuthority> authorities = jwtService.rolesOf(claims).stream()
                        .map(role -> new SimpleGrantedAuthority(Roles.toAuthority(role)))
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Bỏ qua token không hợp lệ: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
