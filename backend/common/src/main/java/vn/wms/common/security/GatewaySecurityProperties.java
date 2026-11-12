package vn.wms.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình xác thực theo header của api-gateway ở các service nghiệp vụ ({@code app.security.*}, xem docs/SECURITY.md).
 *
 * <pre>
 * app:
 *   security:
 *     # true ở máy phát triển; deploy/docker-compose.prod.yml đặt SECURITY_ALLOW_ANONYMOUS=false cho mọi service
 *     allow-anonymous-without-gateway: ${SECURITY_ALLOW_ANONYMOUS:true}
 *     public-paths: [/ws-wms/**]   # đường dẫn không cần danh tính, cộng thêm vào danh sách mặc định
 * </pre>
 */
@ConfigurationProperties(prefix = "app.security")
public class GatewaySecurityProperties {

    /** Đường dẫn hạ tầng luôn mở: kiểm tra sức khỏe, tài liệu OpenAPI và trang lỗi (trùng danh sách của TenantFilter). */
    public static final List<String> DEFAULT_PUBLIC_PATHS = List.of(
            "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error");

    /** Đường dẫn gọi nội bộ giữa các service, api-gateway không định tuyến tới. */
    public static final List<String> DEFAULT_INTERNAL_PATHS = List.of("/internal/**");

    /**
     * {@code true} (máy phát triển): request không mang header danh tính (gateway tắt kiểm tra JWT hoặc gọi thẳng vào
     * service) được coi là nhà phát triển có đủ sáu vai trò, giữ nguyên quy ước cũ của {@code UserContext} (thiếu người
     * dùng thì các kiểm tra viết tay cho qua). {@code false} (máy chủ): request như vậy nhận 401 UNAUTHORIZED ngay tại
     * service, kể cả khi ai đó gọi thẳng vào cổng của service mà không qua gateway.
     */
    private boolean allowAnonymousWithoutGateway = true;

    /** Đường dẫn không cần danh tính (mẫu kiểu Ant), cộng thêm vào {@link #DEFAULT_PUBLIC_PATHS}. */
    private List<String> publicPaths = new ArrayList<>();

    /**
     * Đường dẫn nội bộ (mẫu kiểu Ant). Request không có người dùng nhưng có doanh nghiệp (header X-Company-Id hoặc
     * doanh nghiệp mặc định) được xác thực là lời gọi nội bộ, không mang vai trò nghiệp vụ nào.
     */
    private List<String> internalPaths = new ArrayList<>(DEFAULT_INTERNAL_PATHS);

    public boolean isAllowAnonymousWithoutGateway() {
        return allowAnonymousWithoutGateway;
    }

    public void setAllowAnonymousWithoutGateway(boolean allowAnonymousWithoutGateway) {
        this.allowAnonymousWithoutGateway = allowAnonymousWithoutGateway;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths != null ? publicPaths : new ArrayList<>();
    }

    public List<String> getInternalPaths() {
        return internalPaths;
    }

    public void setInternalPaths(List<String> internalPaths) {
        this.internalPaths = internalPaths != null ? internalPaths : new ArrayList<>();
    }

    /** Toàn bộ đường dẫn mở: mặc định cộng cấu hình riêng của service. */
    public List<String> allPublicPaths() {
        List<String> paths = new ArrayList<>(DEFAULT_PUBLIC_PATHS);
        paths.addAll(publicPaths);
        return List.copyOf(paths);
    }
}
