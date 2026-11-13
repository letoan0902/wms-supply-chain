package vn.wms.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vn.wms.notification.config.NotificationProperties;

import java.nio.charset.StandardCharsets;

/**
 * Gửi email qua SMTP (Spring Mail, cấu hình từ MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM).
 *
 * <p>SMTP chưa cấu hình ({@code MAIL_HOST} rỗng): service vẫn khởi động bình thường, email không được gửi mà nội dung
 * (tiêu đề, số người nhận, thân thư) được ghi vào log để nhóm kiểm tra, kết quả là {@link DeliveryResult#LOGGED}.
 * SMTP lỗi thì ném {@code MailException}, người gọi ghi nhật ký FAILED và không ném tiếp (tránh requeue vô hạn).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String DEFAULT_FROM = "no-reply@wms.local";

    /** Kết quả gửi một email. */
    public enum DeliveryResult {
        /** Đã giao cho máy chủ SMTP. */
        SENT,
        /** SMTP chưa cấu hình: chỉ ghi nội dung vào log. */
        LOGGED
    }

    private final ObjectProvider<JavaMailSender> mailSender;
    private final NotificationProperties properties;
    private final String mailHost;
    private final String mailUsername;

    public EmailService(ObjectProvider<JavaMailSender> mailSender, NotificationProperties properties,
                        @Value("${spring.mail.host:}") String mailHost,
                        @Value("${spring.mail.username:}") String mailUsername) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
    }

    /** SMTP đã cấu hình (có MAIL_HOST và có bean JavaMailSender). */
    public boolean isConfigured() {
        return StringUtils.hasText(mailHost) && mailSender.getIfAvailable() != null;
    }

    /** Địa chỉ người gửi: MAIL_FROM, không có thì MAIL_USERNAME, cuối cùng là địa chỉ no-reply mặc định. */
    public String fromAddress() {
        String from = properties.mail().from();
        if (StringUtils.hasText(from)) {
            return from.trim();
        }
        return StringUtils.hasText(mailUsername) ? mailUsername.trim() : DEFAULT_FROM;
    }

    /** Gửi một email văn bản thuần; ném MailException nếu SMTP lỗi. */
    public DeliveryResult send(String to, String subject, String content) {
        if (!isConfigured()) {
            logInsteadOfSending(to, subject, content);
            return DeliveryResult.LOGGED;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.getObject().send(message);
        return DeliveryResult.SENT;
    }

    /**
     * Gửi một email HTML (UTF-8): email cảnh báo tồn kho (WMS-15.2), email kích hoạt và đặt lại mật khẩu (WMS-23.6);
     * ném MailException nếu SMTP lỗi hoặc không dựng được thư.
     */
    public DeliveryResult sendHtml(String to, String subject, String html) {
        if (!isConfigured()) {
            logInsteadOfSending(to, subject, html);
            return DeliveryResult.LOGGED;
        }
        JavaMailSender sender = mailSender.getObject();
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
        } catch (MessagingException ex) {
            throw new MailPreparationException("Không dựng được email: " + ex.getMessage(), ex);
        }
        sender.send(message);
        return DeliveryResult.SENT;
    }

    private static void logInsteadOfSending(String to, String subject, String body) {
        log.info("SMTP chưa cấu hình (MAIL_HOST rỗng), không gửi email mà ghi nội dung: tiêu đề='{}', số người nhận={}, "
                + "nội dung:\n{}", subject, StringUtils.hasText(to) ? 1 : 0, body);
    }
}
