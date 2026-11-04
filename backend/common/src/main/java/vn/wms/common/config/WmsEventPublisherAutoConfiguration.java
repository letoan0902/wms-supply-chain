package vn.wms.common.config;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import vn.wms.common.messaging.WmsEventPublisher;

/**
 * Tạo sẵn {@link WmsEventPublisher} cho service có {@code RabbitTemplate} (tức là có spring-boot-starter-amqp
 * và cấu hình {@code spring.rabbitmq.*}). Chạy sau {@code RabbitAutoConfiguration} để chắc chắn RabbitTemplate đã có.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration")
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(RabbitTemplate.class)
public class WmsEventPublisherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WmsEventPublisher wmsEventPublisher(RabbitTemplate rabbitTemplate, Environment environment) {
        String source = environment.getProperty("spring.application.name", "unknown-service");
        return new WmsEventPublisher(rabbitTemplate, source);
    }
}
