package vn.wms.notification.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * Chuẩn hóa JavaMailSender theo biến MAIL_USERNAME / MAIL_PASSWORD để cùng một cấu hình dùng được cho cả
 * SMTP không xác thực (Mailpit, MailHog khi dev) lẫn SMTP có xác thực như Gmail:
 *
 * <ul>
 *   <li>Không có tài khoản (rỗng): bỏ username/password để không gửi lệnh AUTH với chuỗi rỗng.</li>
 *   <li>Có tài khoản: bật xác thực SMTP; cổng 587 bật thêm STARTTLS, cổng 465 bật SSL.</li>
 * </ul>
 *
 * Giá trị đã khai báo tường minh trong spring.mail.properties.* luôn được giữ nguyên.
 */
class MailSenderCredentialsPostProcessor implements BeanPostProcessor {

    private static final int SUBMISSION_PORT = 587;
    private static final int SMTPS_PORT = 465;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof JavaMailSenderImpl sender) {
            normalize(sender);
        }
        return bean;
    }

    static void normalize(JavaMailSenderImpl sender) {
        if (!StringUtils.hasText(sender.getUsername())) {
            sender.setUsername(null);
            sender.setPassword(null);
            return;
        }
        Properties properties = sender.getJavaMailProperties();
        properties.putIfAbsent("mail.smtp.auth", "true");
        if (sender.getPort() == SUBMISSION_PORT) {
            properties.putIfAbsent("mail.smtp.starttls.enable", "true");
        } else if (sender.getPort() == SMTPS_PORT) {
            properties.putIfAbsent("mail.smtp.ssl.enable", "true");
        }
    }
}
