package vn.wms.notification.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import vn.wms.notification.config.NotificationProperties;

/**
 * Gửi email văn bản thuần qua SMTP (cấu hình spring.mail.*, mặc định localhost:1025 - dùng được với Mailpit/MailHog).
 * TODO (Sprint 2): dùng mẫu email HTML (Thymeleaf) và gửi bất đồng bộ khi khối lượng tăng.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public EmailService(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /** Gửi một email; ném MailException nếu SMTP lỗi. */
    public void send(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }
}
