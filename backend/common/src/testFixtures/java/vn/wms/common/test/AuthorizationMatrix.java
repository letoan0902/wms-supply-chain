package vn.wms.common.test;

import org.junit.jupiter.params.provider.Arguments;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.wms.common.constant.Roles;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bộ kiểm tra ma trận phân quyền dùng chung cho test của các service (WMS-20.3, docs/SECURITY.md).
 *
 * <p>Mỗi {@link Endpoint} là một API đại diện kèm tập vai trò được gọi. {@link #cases(List)} sinh mọi cặp (API, vai
 * trò) trên sáu vai trò tài khoản và INTEGRATION; {@link #assertAccess(MockMvc, Endpoint, String)} gửi request với
 * header danh tính của gateway (người dùng được gán kho 1) rồi kiểm tra: vai trò được phép không nhận 401/403 (kết quả
 * nghiệp vụ như 200, 400, 404 đều chấp nhận), vai trò khác nhận 403 FORBIDDEN theo khung ApiResponse.
 */
public final class AuthorizationMatrix {

    /** Sáu vai trò tài khoản và vai trò INTEGRATION của API key. */
    public static final List<String> EVERY_ROLE = Stream.concat(Roles.ALL.stream(), Stream.of(Roles.INTEGRATION))
            .toList();

    /** Kho được gán cho người dùng trong test ma trận. */
    public static final Long ASSIGNED_WAREHOUSE_ID = 1L;

    private AuthorizationMatrix() {
    }

    /** Một API đại diện và các vai trò được gọi. */
    public record Endpoint(String name, Supplier<MockHttpServletRequestBuilder> request, Set<String> allowed) {

        @Override
        public String toString() {
            return name;
        }
    }

    public static Endpoint endpoint(String name, Supplier<MockHttpServletRequestBuilder> request, String... allowed) {
        return new Endpoint(name, request, Set.of(allowed));
    }

    /** Mọi cặp (API, vai trò) làm nguồn cho {@code @ParameterizedTest}. */
    public static Stream<Arguments> cases(List<Endpoint> endpoints) {
        return endpoints.stream().flatMap(endpoint -> EVERY_ROLE.stream().map(role -> Arguments.of(endpoint, role)));
    }

    /** Header danh tính của một vai trò: INTEGRATION không gán kho, vai trò khác được gán kho 1. */
    public static org.springframework.test.web.servlet.request.RequestPostProcessor identity(String role) {
        if (Roles.INTEGRATION.equals(role)) {
            return GatewayHeaders.asIntegration(GatewayHeaders.DEFAULT_COMPANY_ID);
        }
        return GatewayHeaders.asUser(role.toLowerCase(Locale.ROOT), GatewayHeaders.DEFAULT_COMPANY_ID,
                List.of(role), List.of(ASSIGNED_WAREHOUSE_ID));
    }

    public static void assertAccess(MockMvc mockMvc, Endpoint endpoint, String role) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(endpoint.request().get().with(identity(role)))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        if (endpoint.allowed().contains(role)) {
            assertThat(response.getStatus())
                    .as("%s với vai trò %s phải được phép, nhận %s: %s", endpoint, role, response.getStatus(), body)
                    .isNotIn(401, 403);
        } else {
            assertThat(response.getStatus())
                    .as("%s với vai trò %s phải bị từ chối, nhận %s: %s", endpoint, role, response.getStatus(), body)
                    .isEqualTo(403);
            assertThat(body).as("thân lỗi 403 theo khung ApiResponse").contains("\"code\":\"FORBIDDEN\"");
        }
    }

    /**
     * Request có doanh nghiệp nhưng không có người dùng khi service chạy chế độ máy chủ: 401 UNAUTHORIZED theo khung
     * ApiResponse (thiếu cả doanh nghiệp thì TenantFilter trả 403 COMPANY_REQUIRED trước).
     */
    public static void assertRejectedWithoutIdentity(MockMvc mockMvc, Endpoint endpoint) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(endpoint.request().get()
                        .with(GatewayHeaders.companyOnly(GatewayHeaders.DEFAULT_COMPANY_ID)))
                .andReturn().getResponse();
        assertThat(response.getStatus()).as("%s không có danh tính", endpoint).isEqualTo(401);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"code\":\"UNAUTHORIZED\"");
    }

    /**
     * Các API của service (gói {@code basePackage}) thiếu {@code @PreAuthorize}, trừ đường dẫn nội bộ {@code /internal/**}.
     * Test gọi hàm này và yêu cầu kết quả rỗng, để API thêm sau này không lọt khỏi ma trận phân quyền.
     */
    public static List<String> handlersWithoutPreAuthorize(RequestMappingHandlerMapping mapping, String basePackage) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getName().startsWith(basePackage)) {
                continue;
            }
            Set<String> patterns = entry.getKey().getPatternValues();
            if (!patterns.isEmpty() && patterns.stream().allMatch(path -> path.startsWith("/internal/"))) {
                continue;
            }
            boolean secured = AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PreAuthorize.class);
            if (!secured) {
                missing.add(handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName() + " " + patterns);
            }
        }
        return missing;
    }
}
