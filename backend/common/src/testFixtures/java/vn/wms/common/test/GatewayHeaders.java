package vn.wms.common.test;

import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.wms.common.constant.Roles;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Gắn header danh tính giống api-gateway cho request MockMvc của test:
 * {@code mockMvc.perform(get("/api/products").with(GatewayHeaders.asRole("SALES")))}.
 *
 * <p>Dùng chung cho mọi service (khai báo {@code testImplementation testFixtures(project(':common'))}), để test gửi
 * danh tính thay vì nới lỏng kiểm tra quyền. Doanh nghiệp mặc định là {@link #DEFAULT_COMPANY_ID}.
 */
public final class GatewayHeaders {

    /** Doanh nghiệp mặc định của test, trùng {@code app.tenant.default-company-id} trong application-test.yml. */
    public static final Long DEFAULT_COMPANY_ID = 1L;

    private GatewayHeaders() {
    }

    /**
     * Người dùng có đúng một vai trò, tên đăng nhập là tên vai trò viết thường, thuộc doanh nghiệp mặc định.
     *
     * @param role         vai trò không tiền tố, ví dụ {@link Roles#WAREHOUSE_STAFF}
     * @param warehouseIds kho được gán (bỏ trống nếu không gán kho)
     */
    public static RequestPostProcessor asRole(String role, Long... warehouseIds) {
        return asUser(role.toLowerCase(Locale.ROOT), DEFAULT_COMPANY_ID, List.of(role), Arrays.asList(warehouseIds));
    }

    /** Người dùng tùy ý: tên đăng nhập, doanh nghiệp, danh sách vai trò và kho được gán. */
    public static RequestPostProcessor asUser(String username, Long companyId, Collection<String> roles,
                                              Collection<Long> warehouseIds) {
        return request -> {
            request.addHeader(UserContext.HEADER_USER_NAME, username);
            request.addHeader(UserContext.HEADER_USER_ROLES, String.join(",", roles));
            if (warehouseIds != null && !warehouseIds.isEmpty()) {
                request.addHeader(UserContext.HEADER_USER_WAREHOUSE_IDS,
                        warehouseIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
            }
            if (companyId != null) {
                request.addHeader(TenantContext.HEADER_COMPANY_ID, String.valueOf(companyId));
            }
            return request;
        };
    }

    /** Hệ thống ngoài gọi bằng API key: gateway gắn vai trò INTEGRATION, tên {@code apikey-<id>}, không gán kho. */
    public static RequestPostProcessor asIntegration(Long companyId) {
        return asUser("apikey-1", companyId, List.of(Roles.INTEGRATION), List.of());
    }

    /** Chỉ có doanh nghiệp, không có người dùng: lời gọi nội bộ từ tác vụ định kỳ hoặc listener. */
    public static RequestPostProcessor companyOnly(Long companyId) {
        return request -> {
            request.addHeader(TenantContext.HEADER_COMPANY_ID, String.valueOf(companyId));
            return request;
        };
    }
}
