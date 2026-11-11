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

    /**
     * Gửi một email HTML mã hóa UTF-8 (email kích hoạt và đặt lại mật khẩu, WMS-23.6); ném MailException nếu SMTP lỗi
     * hoặc không dựng được thư.
     */
    public void sendHtml(String to, String subject, String html) {
        jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
        try {
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
        } catch (jakarta.mail.MessagingException ex) {
            throw new org.springframework.mail.MailPreparationException("Không dựng được email HTML", ex);
        }
        mailSender.send(message);
    }
}
