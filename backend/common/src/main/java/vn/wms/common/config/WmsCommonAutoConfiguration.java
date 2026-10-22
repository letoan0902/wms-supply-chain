package vn.wms.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.servlet.DispatcherServlet;
import vn.wms.common.exception.GlobalExceptionHandler;
import vn.wms.common.exception.SecurityExceptionPassthroughAdvice;
import vn.wms.common.persistence.RequestHeaderAuditorAware;

/**
 * Tự cấu hình cho thư viện common: các service chỉ cần thêm {@code implementation project(':common')},
 * không phải khai báo {@code scanBasePackages}. Chỉ kích hoạt với ứng dụng web servlet (Spring MVC).
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
public class WmsCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * Bật JPA auditing cho các entity kế thừa AuditableEntity (created_at, updated_at, created_by, updated_by).
     * Chỉ kích hoạt khi service dùng Spring Data JPA và đã có EntityManagerFactory (nên auto-configuration này
     * được xếp chạy sau HibernateJpaAutoConfiguration).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.jpa.repository.config.EnableJpaAuditing")
    @ConditionalOnBean(type = "jakarta.persistence.EntityManagerFactory")
    @EnableJpaAuditing(auditorAwareRef = "wmsAuditorAware")
    static class JpaAuditingConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "wmsAuditorAware")
        AuditorAware<String> wmsAuditorAware() {
            return new RequestHeaderAuditorAware();
        }
    }

    /** Chỉ đăng ký khi Spring Security có mặt (service không dùng Security sẽ bỏ qua). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    static class SecurityPassthroughConfiguration {

        @Bean
        @ConditionalOnMissingBean(SecurityExceptionPassthroughAdvice.class)
        SecurityExceptionPassthroughAdvice securityExceptionPassthroughAdvice() {
            return new SecurityExceptionPassthroughAdvice();
        }
    }
}
