package vn.wms.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import vn.wms.common.exception.GlobalExceptionHandler;
import vn.wms.common.exception.SecurityExceptionPassthroughAdvice;

import static org.assertj.core.api.Assertions.assertThat;

class WmsCommonAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WmsCommonAutoConfiguration.class));

    @Test
    void registersExceptionHandlersInServletWebApplication() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            // spring-security-core có trên classpath test nên advice chuyển tiếp lỗi bảo mật cũng được đăng ký
            assertThat(context).hasSingleBean(SecurityExceptionPassthroughAdvice.class);
        });
    }

    @Test
    void jpaAuditingStaysOffWhenThereIsNoEntityManagerFactory() {
        // Không có EntityManagerFactory (ví dụ test của module này) thì không bật auditing, tránh lỗi metamodel rỗng
        webRunner.run(context -> assertThat(context).doesNotHaveBean("wmsAuditorAware"));
    }

    @Test
    void backsOffInNonWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WmsCommonAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }
}
