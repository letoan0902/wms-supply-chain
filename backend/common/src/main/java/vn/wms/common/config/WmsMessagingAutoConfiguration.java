package vn.wms.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Bộ chuyển đổi JSON dùng chung cho RabbitMQ: mọi service có {@code spring-boot-starter-amqp} sẽ gửi và nhận
 * message dạng JSON (không dùng Java serialization), nhờ đó service viết bằng ngôn ngữ khác hoặc không có
 * class của bên gửi vẫn đọc được.
 *
 * <p>Kiểu đích khi nhận lấy từ tham số của phương thức {@code @RabbitListener} (INFERRED), không phụ thuộc
 * header {@code __TypeId__}; nếu header này trỏ tới record trong gói {@code vn.wms.common.messaging.event}
 * thì cũng được chấp nhận.
 * Service nào tự khai báo bean {@link MessageConverter} riêng thì cấu hình này tự lùi lại.
 * Phải chạy trước {@code RabbitAutoConfiguration} để {@code RabbitTemplate} và listener container nhận được converter.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
        beforeName = "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration")
@ConditionalOnClass({RabbitTemplate.class, Jackson2JsonMessageConverter.class, ObjectMapper.class})
public class WmsMessagingAutoConfiguration {

    /**
     * Gói chứa các record sự kiện được tin cậy khi đọc theo header {@code __TypeId__}
     * (Spring AMQP so khớp đúng tên gói, không phải tiền tố, nên phải ghi đầy đủ).
     */
    public static final String TRUSTED_PACKAGE = "vn.wms.common.messaging.event";

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter wmsJsonMessageConverter(ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(() -> JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        return createJsonMessageConverter(objectMapper);
    }

    /** Tạo converter JSON với cùng quy tắc, dùng lại được trong test hoặc cấu hình riêng của service. */
    public static Jackson2JsonMessageConverter createJsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.addTrustedPackages(TRUSTED_PACKAGE);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
