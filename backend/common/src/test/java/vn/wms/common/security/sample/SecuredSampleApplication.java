package vn.wms.common.security.sample;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;

/**
 * Ứng dụng tối thiểu chỉ dùng trong test của module common: bật method security như các service nghiệp vụ và dùng
 * chuỗi bộ lọc dùng chung của {@code WmsSecurityAutoConfiguration}.
 */
@SpringBootApplication
@EnableMethodSecurity
public class SecuredSampleApplication {

    @RestController
    static class SecuredSampleController {

        @GetMapping("/api/products/sample")
        @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'INTEGRATION')")
        public ApiResponse<String> read(Authentication authentication) {
            return ApiResponse.ok(authentication.getName());
        }

        @PostMapping("/api/products/sample")
        @PreAuthorize("hasRole('ADMIN')")
        public ApiResponse<String> write(Authentication authentication) {
            return ApiResponse.ok(authentication.getName());
        }

        @GetMapping("/internal/sample")
        public ApiResponse<String> internal(Authentication authentication) {
            return ApiResponse.ok(authentication.getName());
        }

        @GetMapping("/api/products/sample/boom")
        @PreAuthorize("hasRole('ADMIN')")
        public ApiResponse<String> boom() {
            throw new IllegalStateException("chi tiết nội bộ không được lộ ra ngoài");
        }
    }
}
