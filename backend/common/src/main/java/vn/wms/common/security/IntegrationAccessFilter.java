package vn.wms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.constant.Roles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Lớp chặn thứ hai cho vai trò {@link Roles#INTEGRATION} (hệ thống ngoài gọi bằng API key, WMS-25.1).
 *
 * <p>api-gateway đã chỉ cho API key gọi {@code GET} dưới {@code /api/products/**}, {@code /api/inventory/**},
 * {@code /api/orders/**}. Bộ lọc này nhắc lại đúng quy tắc đó ở mỗi service để vai trò INTEGRATION luôn chỉ đọc,
 * kể cả khi request tới service mà không qua gateway hoặc gateway cấu hình sai: request có vai trò INTEGRATION
 * trong header {@code X-User-Roles} mà không phải {@code GET}/{@code HEAD}, hoặc nằm ngoài ba nhóm đường dẫn trên,
 * nhận HTTP 403 mã {@link ErrorCode#FORBIDDEN}. Cần thiết vì một số API ghi chỉ kiểm tra quyền đọc kho
 * ({@code UserContext#isGlobalReadScope} coi INTEGRATION là đọc mọi kho).
 *
 * <p>Request không mang vai trò INTEGRATION đi qua nguyên vẹn.
 */
public class IntegrationAccessFilter extends OncePerRequestFilter {

    /** Nhóm đường dẫn vai trò INTEGRATION được đọc, trùng {@code gateway.api-key.allowed-paths} mặc định. */
    static final List<String> ALLOWED_PATHS = List.of("/api/products/**", "/api/inventory/**", "/api/orders/**");

    static final String MESSAGE =
            "API key chỉ được đọc dữ liệu hàng hóa, tồn kho và đơn hàng (phương thức GET)";

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public IntegrationAccessFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isIntegration(request) && !isAllowed(request)) {
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isIntegration(HttpServletRequest request) {
        String roles = request.getHeader(UserContext.HEADER_USER_ROLES);
        if (roles == null || roles.isBlank()) {
            return false;
        }
        for (String role : roles.split(",")) {
            if (Roles.INTEGRATION.equals(role.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ALLOWED_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void reject(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.FORBIDDEN;
        response.setStatus(code.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, MESSAGE));
    }
}
