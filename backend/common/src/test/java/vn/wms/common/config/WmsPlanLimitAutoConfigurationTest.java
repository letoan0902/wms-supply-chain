package vn.wms.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.wms.common.plan.PlanLimitChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WmsPlanLimitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WmsPlanLimitAutoConfiguration.class));

    @Test
    void registersCheckerWhenStringRedisTemplateBeanExists() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> assertThat(context).hasSingleBean(PlanLimitChecker.class));
    }

    @Test
    void skipsCheckerWithoutStringRedisTemplateBean() {
        runner.run(context -> assertThat(context).doesNotHaveBean(PlanLimitChecker.class));
    }

    @Test
    void skipsCheckerWhenRedisIsNotOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.data.redis"))
                .run(context -> assertThat(context).doesNotHaveBean(PlanLimitChecker.class));
    }

    @Test
    void canBeSwitchedOffByProperty() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("app.plan-limit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PlanLimitChecker.class));
    }
}
