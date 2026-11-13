package vn.wms.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import vn.wms.common.tenant.TenantFeignRequestInterceptor;
import vn.wms.common.tenant.TenantFilter;
import vn.wms.common.tenant.TenantIdentifierResolver;
import vn.wms.common.tenant.TenantMessagePostProcessor;
import vn.wms.common.tenant.TenantProperties;

/**
 * Tự cấu hình cơ chế tách dữ liệu theo doanh nghiệp (multi-tenant) cho mọi service dùng module common:
 * <ul>
 *   <li>ứng dụng web servlet: {@link TenantFilter} đọc header X-Company-Id vào TenantContext;</li>
 *   <li>service dùng Hibernate: {@link TenantIdentifierResolver} để {@code @TenantId} tự gán và tự lọc;</li>
 *   <li>service có listener RabbitMQ: đặt lại doanh nghiệp từ header của message trước khi xử lý;</li>
 *   <li>service dùng OpenFeign: chuyển tiếp header doanh nghiệp và người dùng sang service được gọi.</li>
 * </ul>
 * Mỗi phần chỉ kích hoạt khi thư viện tương ứng có trên classpath. Xem docs/MULTI_TENANT.md.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
@EnableConfigurationProperties(TenantProperties.class)
public class WmsTenantAutoConfiguration {

    /** Thứ tự của bộ lọc: chạy sớm, trước chuỗi bộ lọc của Spring Security (-100). */
    public static final int TENANT_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 50;

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    static class TenantWebConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "wmsTenantFilterRegistration")
        FilterRegistrationBean<TenantFilter> wmsTenantFilterRegistration(TenantProperties properties,
                                                                          ObjectProvider<ObjectMapper> objectMapper) {
            TenantFilter filter = new TenantFilter(properties,
                    objectMapper.getIfAvailable(() -> JsonMapper.builder().findAndAddModules().build()));
            FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(TENANT_FILTER_ORDER);
            registration.addUrlPatterns("/*");
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.hibernate.context.spi.CurrentTenantIdentifierResolver",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer"})
    static class TenantHibernateConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TenantIdentifierResolver wmsTenantIdentifierResolver() {
            return new TenantIdentifierResolver();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer")
    static class TenantMessagingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TenantMessagePostProcessor wmsTenantMessagePostProcessor() {
            return new TenantMessagePostProcessor();
        }

        /**
         * Spring Boot áp bean này cho listener container mặc định. Service nào tự khai báo
         * {@code ContainerCustomizer} riêng thì phải tự gọi {@code addAfterReceivePostProcessors}.
         */
        @Bean
        @ConditionalOnMissingBean(name = "wmsTenantContainerCustomizer")
        ContainerCustomizer<SimpleMessageListenerContainer> wmsTenantContainerCustomizer(
                TenantMessagePostProcessor postProcessor) {
            return container -> container.addAfterReceivePostProcessors(postProcessor);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class TenantFeignConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TenantFeignRequestInterceptor wmsTenantFeignRequestInterceptor() {
            return new TenantFeignRequestInterceptor();
        }
    }
}
