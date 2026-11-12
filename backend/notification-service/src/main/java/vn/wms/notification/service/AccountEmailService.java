package vn.wms.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import vn.wms.common.messaging.event.AccountEmailKind;
import vn.wms.common.messaging.event.AccountEmailRequestedEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.repository.NotificationLogRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * Gửi email tài khoản do auth-service yêu cầu (WMS-23.6): liên kết kích hoạt doanh nghiệp và liên kết đặt lại mật khẩu.
 *
 * <ul>
 *   <li>Người gọi (listener) phải đặt {@link TenantContext} theo doanh nghiệp của sự kiện: bản ghi
 *       {@code notification_logs} thuộc doanh nghiệp đó.</li>
 *   <li>SMTP được coi là đã cấu hình khi {@code spring.mail.host} có giá trị và có tài khoản
 *       {@code spring.mail.username}, hoặc host là máy cục bộ (Mailpit, MailHog không cần xác thực). Chưa cấu hình thì
 *       không gửi, ghi đúng một dòng INFO gồm người nhận và liên kết đầy đủ để nhóm thử luồng, và ghi nhật ký
 *       {@code LOGGED} (cùng trạng thái với email cảnh báo tồn kho khi chưa có SMTP).</li>
 *   <li>SMTP lỗi: ghi nhật ký {@code FAILED}, không ném lỗi ra ngoài để tin nhắn không bị đưa lại hàng đợi mãi; người
 *       dùng tự yêu cầu gửi lại từ giao diện.</li>
 *   <li>Broker giao lại cùng tin (cùng {@code messageId}) thì không gửi lần hai: khóa Redis
 *       {@code wms:notification:account-email:processed:<messageId>} giữ 1 ngày; Redis lỗi thì vẫn gửi.</li>
 * </ul>
 */
@Service
public class AccountEmailService {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailService.class);

    static final String PROCESSED_KEY_PREFIX = "wms:notification:account-email:processed:";
    static final String SMTP_NOT_CONFIGURED = "Chưa cấu hình SMTP, liên kết đã ghi ra log của notification-service";
    private static final Duration PROCESSED_TTL = Duration.ofDays(1);
    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final EmailService emailService;
    private final NotificationLogRepository logRepository;
    private final StringRedisTemplate redis;
    private final boolean smtpConfigured;

    public AccountEmailService(EmailService emailService, NotificationLogRepository logRepository,
                               StringRedisTemplate redis,
                               @Value("${spring.mail.host:}") String mailHost,
                               @Value("${spring.mail.username:}") String mailUsername) {
        this.emailService = emailService;
        this.logRepository = logRepository;
        this.redis = redis;
        this.smtpConfigured = isSmtpConfigured(mailHost, mailUsername);
    }

    /** SMTP dùng được: có host và có tài khoản, hoặc host cục bộ không cần xác thực. */
    static boolean isSmtpConfigured(String host, String username) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        return StringUtils.hasText(username) || LOCAL_HOSTS.contains(host.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Gửi một email tài khoản và ghi nhật ký dưới doanh nghiệp trong ngữ cảnh.
     *
     * @return bản ghi nhật ký, {@code null} khi tin đã được xử lý trước đó (broker giao lại)
     */
    @Transactional
    public NotificationLog send(AccountEmailRequestedEvent event, String messageId) {
        Long companyId = TenantContext.requireCompanyId();
        if (alreadyProcessed(messageId)) {
            log.info("Bỏ qua email tài khoản {} đã xử lý (messageId={}, companyId={})", event.kind(), messageId,
                    companyId);
            return null;
        }
        String subject = subjectOf(event);
        NotificationLog entry = new NotificationLog();
        entry.setEventType(eventTypeOf(event.kind()));
        entry.setRecipient(truncate(event.recipientEmail(), MAX_TEXT_LENGTH));
        entry.setSubject(truncate(subject, MAX_TEXT_LENGTH));
        if (!smtpConfigured) {
            log.info("SMTP chưa cấu hình, không gửi email {}. Người nhận: {}, liên kết: {}", event.kind(),
                    event.recipientEmail(), event.link());
            entry.setStatus(NotificationStatus.LOGGED);
            entry.setErrorMessage(SMTP_NOT_CONFIGURED);
        } else {
            try {
                EmailService.DeliveryResult result =
                        emailService.sendHtml(event.recipientEmail(), subject, htmlOf(event));
                entry.setStatus(result == EmailService.DeliveryResult.LOGGED
                        ? NotificationStatus.LOGGED : NotificationStatus.SENT);
            } catch (MailException ex) {
                // Không ghi liên kết khi lỗi: chỉ người nhận và nguyên nhân
                log.warn("Gửi email {} tới {} thất bại (companyId={}): {}", event.kind(), event.recipientEmail(),
                        companyId, ex.getMessage());
                entry.setStatus(NotificationStatus.FAILED);
                entry.setErrorMessage(truncate(ex.getMessage(), MAX_ERROR_LENGTH));
            }
        }
        NotificationLog saved = logRepository.save(entry);
        markProcessed(messageId);
        return saved;
    }

    static String eventTypeOf(AccountEmailKind kind) {
        return kind == AccountEmailKind.ACTIVATION ? "ACCOUNT_ACTIVATION" : "PASSWORD_RESET";
    }

    static String subjectOf(AccountEmailRequestedEvent event) {
        if (event.kind() == AccountEmailKind.ACTIVATION) {
            return "Kích hoạt doanh nghiệp "
                    + (StringUtils.hasText(event.companyName()) ? event.companyName() + " " : "")
                    + "trên WMS Supply Chain";
        }
        return "Đặt lại mật khẩu tài khoản WMS Supply Chain";
    }

    /** Nội dung HTML ngắn; mọi giá trị từ sự kiện đều được thoát ký tự HTML. */
    static String htmlOf(AccountEmailRequestedEvent event) {
        boolean activation = event.kind() == AccountEmailKind.ACTIVATION;
        String name = StringUtils.hasText(event.recipientName()) ? event.recipientName() : event.recipientEmail();
        String company = StringUtils.hasText(event.companyName()) ? event.companyName() : "doanh nghiệp của bạn";
        String intro = activation
                ? "Cảm ơn bạn đã đăng ký <strong>" + escape(company) + "</strong> trên WMS Supply Chain. "
                + "Bấm nút bên dưới để kích hoạt doanh nghiệp và bắt đầu 30 ngày dùng thử."
                : "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn tại <strong>"
                + escape(company) + "</strong>. Bấm nút bên dưới để đặt mật khẩu mới.";
        String button = activation ? "Kích hoạt doanh nghiệp" : "Đặt lại mật khẩu";
        String ignore = activation
                ? "Nếu bạn không đăng ký, hãy bỏ qua email này."
                : "Nếu bạn không yêu cầu, hãy bỏ qua email này, mật khẩu hiện tại vẫn giữ nguyên.";
        String expiry = event.expiresAt() == null ? ""
                : "<p style=\"color:#64748b;font-size:13px\">Liên kết chỉ dùng được một lần và hết hiệu lực lúc "
                + EXPIRY_FORMAT.format(event.expiresAt()) + " (giờ Việt Nam).</p>";
        String link = escape(event.link());
        return "<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"font-family:Arial,Helvetica,sans-serif;color:#0f172a;line-height:1.5\">"
                + "<p>Xin chào " + escape(name) + ",</p>"
                + "<p>" + intro + "</p>"
                + "<p><a href=\"" + link + "\" style=\"display:inline-block;padding:10px 20px;background:#2563eb;"
                + "color:#ffffff;text-decoration:none;border-radius:6px\">" + button + "</a></p>"
                + "<p style=\"font-size:13px\">Nếu nút không bấm được, mở địa chỉ sau trong trình duyệt:<br>"
                + "<a href=\"" + link + "\">" + link + "</a></p>"
                + expiry
                + "<p style=\"color:#64748b;font-size:13px\">" + ignore + "</p>"
                + "<p>WMS Supply Chain</p></body></html>";
    }

    /** Chỉ thoát ký tự đặc biệt của HTML, giữ nguyên chữ tiếng Việt (thư mã hóa UTF-8). */
    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }

    private boolean alreadyProcessed(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(PROCESSED_KEY_PREFIX + messageId));
        } catch (RuntimeException ex) {
            log.warn("Không đọc được Redis để chống gửi trùng email tài khoản, vẫn gửi: {}", ex.getMessage());
            return false;
        }
    }

    private void markProcessed(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return;
        }
        try {
            redis.opsForValue().set(PROCESSED_KEY_PREFIX + messageId, Instant.now().toString(), PROCESSED_TTL);
        } catch (RuntimeException ex) {
            log.warn("Không ghi được Redis để chống gửi trùng email tài khoản: {}", ex.getMessage());
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
