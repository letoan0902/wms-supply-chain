package vn.wms.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import vn.wms.common.tenant.TenantFeignRequestInterceptor;
import vn.wms.common.tenant.TenantFilter;
import vn.wms.common.tenant.TenantIdentifierResolver;
import vn.wms.common.tenant.TenantMessagePostProcessor;
import vn.wms.common.tenant.TenantProperties;

import static org.assertj.core.api.Assertions.assertThat;

class WmsTenantAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WmsTenantAutoConfiguration.class));

    @Test
    @SuppressWarnings("unchecked")
    void registersAllPartsInServletApplicationWithDefaults() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(TenantIdentifierResolver.class);
            assertThat(context).hasSingleBean(TenantMessagePostProcessor.class);
            assertThat(context).hasSingleBean(ContainerCustomizer.class);
            assertThat(context).hasSingleBean(TenantFeignRequestInterceptor.class);

            FilterRegistrationBean<TenantFilter> registration =
                    context.getBean("wmsTenantFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(TenantFilter.class);
            assertThat(registration.getOrder()).isEqualTo(WmsTenantAutoConfiguration.TENANT_FILTER_ORDER);

            // Mặc định không bắt buộc và không có doanh nghiệp mặc định: service chưa chuyển đổi chạy như cũ
            TenantProperties properties = context.getBean(TenantProperties.class);
            assertThat(properties.isRequired()).isFalse();
            assertThat(properties.getDefaultCompanyId()).isNull();
            assertThat(properties.getPublicPaths()).isEmpty();
        });
    }

    @Test
    void bindsProperties() {
        webRunner.withPropertyValues(
                        "app.tenant.required=true",
                        "app.tenant.default-company-id=1",
                        "app.tenant.public-paths=/api/auth/plans,/api/auth/companies/**")
                .run(context -> {
                    TenantProperties properties = context.getBean(TenantProperties.class);
                    assertThat(properties.isRequired()).isTrue();
                    assertThat(properties.getDefaultCompanyId()).isEqualTo(1L);
                    assertThat(properties.getPublicPaths())
                            .containsExactly("/api/auth/plans", "/api/auth/companies/**");
                });
    }

    @Test
    void emptyDefaultCompanyPropertyMeansNoDefault() {
        // Trên máy chủ biến TENANT_DEFAULT_COMPANY_ID không được đặt nên giá trị là chuỗi rỗng
        webRunner.withPropertyValues("app.tenant.default-company-id=")
                .run(context -> assertThat(context.getBean(TenantProperties.class).getDefaultCompanyId()).isNull());
    }

    @Test
    void filterIsNotRegisteredOutsideServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WmsTenantAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean("wmsTenantFilterRegistration");
                    // Phần Hibernate và RabbitMQ vẫn cần cho tác vụ nền, listener
                    assertThat(context).hasSingleBean(TenantIdentifierResolver.class);
                    assertThat(context).hasSingleBean(TenantMessagePostProcessor.class);
                });
    }

    @Test
    void optionalPartsBackOffWhenLibrariesAreMissing() {
        webRunner.withClassLoader(new FilteredClassLoader("feign", "org.hibernate", "org.springframework.amqp"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TenantFeignRequestInterceptor.class);
                    assertThat(context).doesNotHaveBean(TenantIdentifierResolver.class);
                    assertThat(context).doesNotHaveBean(TenantMessagePostProcessor.class);
                    assertThat(context).hasBean("wmsTenantFilterRegistration");
                });
    }
}
