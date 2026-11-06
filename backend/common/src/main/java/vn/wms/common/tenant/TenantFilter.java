package vn.wms.common.tenant;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc header {@value TenantContext#HEADER_COMPANY_ID} của mỗi request, đặt vào {@link TenantContext} và xóa khi
 * xử lý xong. Thiếu header thì dùng {@code app.tenant.default-company-id} nếu có cấu hình.
 *
 * <p>Khi {@code app.tenant.required=true}, request tới API nghiệp vụ mà không xác định được doanh nghiệp nhận
 * HTTP 403 với mã {@link ErrorCode#COMPANY_REQUIRED}. Các đường dẫn hạ tầng (actuator, Swagger, trang lỗi) và
 * đường dẫn khai báo trong {@code app.tenant.public-paths} được bỏ qua.
 */
public class TenantFilter extends OncePerRequestFilter {

    /** Đường dẫn luôn được bỏ qua kiểm tra bắt buộc. */
    static final List<String> DEFAULT_PUBLIC_PATHS = List.of(
            "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error");

    private final TenantProperties properties;
    private final ObjectMapper objectMapper;
    private final List<String> publicPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TenantFilter(TenantProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        List<String> paths = new ArrayList<>(DEFAULT_PUBLIC_PATHS);
        paths.addAll(properties.getPublicPaths());
        this.publicPaths = List.copyOf(paths);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long companyId = TenantContext.parse(request.getHeader(TenantContext.HEADER_COMPANY_ID));
        if (companyId == null) {
            companyId = properties.getDefaultCompanyId();
        }
        if (companyId == null && properties.isRequired() && !isExempt(request)) {
            reject(response);
            return;
        }
        TenantContext.setCompanyId(companyId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isExempt(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void reject(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.COMPANY_REQUIRED;
        response.setStatus(code.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, code.getDefaultMessage()));
    }
}
