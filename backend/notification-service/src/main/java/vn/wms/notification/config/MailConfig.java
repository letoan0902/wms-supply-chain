package vn.wms.notification.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Đăng ký bộ chuẩn hóa tài khoản SMTP (xem {@link MailSenderCredentialsPostProcessor}). */
@Configuration
public class MailConfig {

    /** Khai báo static để BeanPostProcessor được tạo sớm mà không kéo theo khởi tạo sớm lớp cấu hình. */
    @Bean
    static BeanPostProcessor mailSenderCredentialsPostProcessor() {
        return new MailSenderCredentialsPostProcessor();
    }
}
