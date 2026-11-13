package vn.wms.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.wms.common.constant.Roles;
import vn.wms.common.tenant.TenantContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Chuyển danh tính do api-gateway chuyển tiếp thành {@link Authentication} của Spring Security, để
 * {@code @PreAuthorize("hasRole(...)")} trên controller có tác dụng. Chạy trong chuỗi bộ lọc của Spring Security
 * (sau {@code TenantFilter} và {@code IntegrationAccessFilter}), không đăng ký riêng làm servlet filter.
 *
 * <p>Thứ tự xét, dừng ở trường hợp đầu tiên khớp:
 * <ol>
 *   <li>Có header {@code X-User-Name}: principal là tên đăng nhập, authority {@code ROLE_<vai trò>} lấy từ
 *       {@code X-User-Roles}, details là {@link GatewayAuthenticationDetails} (kho, doanh nghiệp, gói).</li>
 *   <li>Đường dẫn nội bộ ({@code /internal/**}) không có người dùng nhưng có doanh nghiệp: principal
 *       {@value #INTERNAL_SERVICE_PRINCIPAL}, không có vai trò nghiệp vụ (tác vụ định kỳ, listener, service khác gọi).</li>
 *   <li>{@code app.security.allow-anonymous-without-gateway=true} (máy phát triển): principal
 *       {@value #DEVELOPER_PRINCIPAL} mang đủ sáu vai trò của {@link Roles#ALL}. {@code UserContext} vẫn trả rỗng như
 *       trước nên các kiểm tra viết tay giữ nguyên quy ước cũ (thiếu người dùng thì cho qua, trừ chỗ bắt buộc đăng nhập).</li>
 *   <li>Còn lại: không xác thực; chuỗi bộ lọc trả 401 UNAUTHORIZED theo khung {@code ApiResponse}.</li>
 * </ol>
 */
public class GatewayIdentityFilter extends OncePerRequestFilter {

    /** Principal của request không có danh tính khi bật chế độ phát triển. */
    public static final String DEVELOPER_PRINCIPAL = "dev-anonymous";

    /** Principal của lời gọi nội bộ không mang người dùng. */
    public static final String INTERNAL_SERVICE_PRINCIPAL = "internal-service";

    /** Header gói dịch vụ do api-gateway gắn (SubscriptionGlobalFilter). */
    public static final String HEADER_PLAN = "X-Plan";

    private final GatewaySecurityProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public GatewayIdentityFilter(GatewaySecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = resolve(request);
        if (authentication != null) {
            SecurityContext context = contextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            contextHolderStrategy.setContext(context);
        }
        chain.doFilter(request, response);
    }

    /** Xác định danh tính của request; {@code null} khi không có (chuỗi bộ lọc sẽ trả 401). */
    Authentication resolve(HttpServletRequest request) {
        Long companyId = companyOf(request);
        GatewayAuthenticationDetails anonymousDetails =
                new GatewayAuthenticationDetails(Set.of(), companyId, request.getHeader(HEADER_PLAN));

        Optional<CurrentUser> user = UserContext.getCurrentUser(request);
        if (user.isPresent()) {
            CurrentUser current = user.get();
            return token(current.username(), authoritiesOf(current.roles()),
                    new GatewayAuthenticationDetails(current.warehouseIds(), companyId, request.getHeader(HEADER_PLAN)));
        }
        if (companyId != null && isInternal(request)) {
            return token(INTERNAL_SERVICE_PRINCIPAL, List.of(), anonymousDetails);
        }
        if (properties.isAllowAnonymousWithoutGateway()) {
            return token(DEVELOPER_PRINCIPAL, authoritiesOf(Roles.ALL), anonymousDetails);
        }
        return null;
    }

    private static Long companyOf(HttpServletRequest request) {
        Long companyId = TenantContext.getCompanyId();
        return companyId != null ? companyId : TenantContext.parse(request.getHeader(TenantContext.HEADER_COMPANY_ID));
    }

    private boolean isInternal(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return properties.getInternalPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private static Authentication token(String principal, Collection<? extends GrantedAuthority> authorities,
                                        GatewayAuthenticationDetails details) {
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        token.setDetails(details);
        return token;
    }

    /** Vai trò không tiền tố ("ADMIN") thành authority "ROLE_ADMIN"; giá trị đã có tiền tố giữ nguyên. */
    static List<GrantedAuthority> authoritiesOf(Collection<String> roles) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String trimmed = role.trim();
            String authority = trimmed.startsWith(Roles.AUTHORITY_PREFIX) ? trimmed : Roles.toAuthority(trimmed);
            authorities.add(new SimpleGrantedAuthority(authority));
        }
        return authorities;
    }
}
