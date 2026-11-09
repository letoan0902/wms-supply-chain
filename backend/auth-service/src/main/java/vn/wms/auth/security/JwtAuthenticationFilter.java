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
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;

    public JwtAuthenticationFilter(JwtService jwtService, org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate) {
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = jwtService.parse(header.substring(BEARER_PREFIX.length()), redisTemplate);
                Long userId = claims.get("userId", Long.class);
                Long companyId = claims.get("companyId", Long.class);
                String plan = jwtService.planOf(claims);
                String username = claims.getSubject();
                List<SimpleGrantedAuthority> authorities = jwtService.rolesOf(claims).stream()
                        .map(role -> new SimpleGrantedAuthority(Roles.toAuthority(role)))
                        .toList();

                UserPrincipal userPrincipal = new UserPrincipal(userId, username, companyId, plan, authorities);
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userPrincipal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);

            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Bỏ qua token không hợp lệ: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
