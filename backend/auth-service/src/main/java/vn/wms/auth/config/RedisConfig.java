package vn.wms.auth.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Mọi khóa, giá trị, tên trường và giá trị trường của hash đều ghi dạng chuỗi UTF-8.
     *
     * <p>Phải đặt cả hai bộ tuần tự hóa của hash: nếu bỏ sót, {@link RedisTemplate} dùng tuần tự hóa của JDK cho tên
     * trường và giá trị trường, nên các hash auth-service ghi ({@code wms:subscription:{companyId}},
     * {@code wms:apikey:{sha256}}) có tên trường dạng nhị phân. api-gateway và các service khác đọc bằng chuỗi thường
     * sẽ không thấy trường nào, coi như thiếu bộ nhớ đệm và cho qua: giới hạn gói, chế độ chỉ đọc khi hết hạn và
     * xác thực API key đều mất tác dụng. Chiều ngược lại cũng vậy: auth-service không đọc được hash do service khác
     * ghi ({@code wms:usage:{companyId}}, {@code wms:apikey:last-used}).
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer string = new StringRedisSerializer();
        template.setKeySerializer(string);
        template.setValueSerializer(string);
        template.setHashKeySerializer(string);
        template.setHashValueSerializer(string);
        return template;
    }
}
