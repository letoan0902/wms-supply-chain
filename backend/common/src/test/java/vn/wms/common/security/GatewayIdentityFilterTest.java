package vn.wms.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.wms.common.constant.Roles;
import vn.wms.common.tenant.TenantContext;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Đổi header danh tính của api-gateway thành Authentication của Spring Security (WMS-20.3). */
class GatewayIdentityFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static GatewayIdentityFilter filter(boolean allowAnonymous) {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setAllowAnonymousWithoutGateway(allowAnonymous);
        return new GatewayIdentityFilter(properties);
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    private static Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void userHeadersBecomeRoleAuthoritiesWithWarehouseAndCompanyDetails() {
        MockHttpServletRequest request = request("/api/products");
        request.addHeader(UserContext.HEADER_USER_NAME, "manager01");
        request.addHeader(UserContext.HEADER_USER_ROLES, "WAREHOUSE_MANAGER, ROLE_SALES");
        request.addHeader(UserContext.HEADER_USER_WAREHOUSE_IDS, "3,5");
        request.addHeader("X-Company-Id", "7");
        request.addHeader(GatewayIdentityFilter.HEADER_PLAN, "PRO");

        Authentication authentication = filter(false).resolve(request);

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("manager01");
        assertThat(authorities(authentication)).containsExactlyInAnyOrder("ROLE_WAREHOUSE_MANAGER", "ROLE_SALES");
        assertThat(authentication.getDetails()).isEqualTo(new GatewayAuthenticationDetails(Set.of(3L, 5L), 7L, "PRO"));
    }

    @Test
    void companyFromTenantContextWinsOverHeader() {
        TenantContext.setCompanyId(9L);
        MockHttpServletRequest request = request("/api/products");
        request.addHeader(UserContext.HEADER_USER_NAME, "admin");
        request.addHeader(UserContext.HEADER_USER_ROLES, "ADMIN");

        Authentication authentication = filter(false).resolve(request);

        assertThat(((GatewayAuthenticationDetails) authentication.getDetails()).companyId()).isEqualTo(9L);
    }

    @Test
    void integrationRoleKeepsItsOwnAuthorityOnly() {
        MockHttpServletRequest request = request("/api/inventory/stock/atp");
        request.addHeader(UserContext.HEADER_USER_NAME, "apikey-4");
        request.addHeader(UserContext.HEADER_USER_ROLES, Roles.INTEGRATION);

        assertThat(authorities(filter(true).resolve(request))).containsExactly("ROLE_INTEGRATION");
    }

    @Test
    void developerModeGivesEveryAccountRoleWhenNoIdentity() {
        Authentication authentication = filter(true).resolve(request("/api/orders/sales"));

        assertThat(authentication.getName()).isEqualTo(GatewayIdentityFilter.DEVELOPER_PRINCIPAL);
        assertThat(authorities(authentication)).containsExactlyInAnyOrderElementsOf(
                Roles.ALL.stream().map(Roles::toAuthority).toList());
        assertThat(authorities(authentication)).doesNotContain("ROLE_INTEGRATION");
    }

    @Test
    void strictModeLeavesRequestWithoutIdentityUnauthenticated() {
        assertThat(filter(false).resolve(request("/api/orders/sales"))).isNull();
    }

    @Test
    void internalCallWithCompanyButNoUserIsAuthenticatedWithoutBusinessRoles() {
        MockHttpServletRequest request = request("/internal/inventory/kpi-metrics");
        request.addHeader("X-Company-Id", "2");

        Authentication authentication = filter(false).resolve(request);

        assertThat(authentication.getName()).isEqualTo(GatewayIdentityFilter.INTERNAL_SERVICE_PRINCIPAL);
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(((GatewayAuthenticationDetails) authentication.getDetails()).companyId()).isEqualTo(2L);
    }

    @Test
    void internalCallWithoutCompanyIsRejectedInStrictMode() {
        assertThat(filter(false).resolve(request("/internal/inventory/kpi-metrics"))).isNull();
    }

    @Test
    void companyAloneDoesNotAuthenticatePublicApi() {
        MockHttpServletRequest request = request("/api/products");
        request.addHeader("X-Company-Id", "2");

        assertThat(filter(false).resolve(request)).isNull();
    }

    @Test
    void filterStoresAuthenticationForTheRestOfTheChain() throws Exception {
        MockHttpServletRequest request = request("/api/products");
        request.addHeader(UserContext.HEADER_USER_NAME, "sales01");
        request.addHeader(UserContext.HEADER_USER_ROLES, "SALES");
        AtomicReference<Authentication> seen = new AtomicReference<>();

        filter(false).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getName()).isEqualTo("sales01");
    }
}
