package vn.wms.notification.stockalert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.messaging.event.StockAlertDigestEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.repository.NotificationLogRepository;
import vn.wms.notification.service.EmailService;

import java.util.Locale;

/**
 * Gửi email cảnh báo tồn kho (WMS-15.2) cho một {@link StockAlertDigestEvent}: MỘT email cho mỗi (người nhận, loại, ngày)
 * trong một doanh nghiệp, nhờ khóa {@code EMAIL:<loại>:<ngày>:<email>} trong {@code notification_logs}.
 *
 * <p>Dòng nhật ký được ghi và flush TRƯỚC khi gửi để giữ chỗ; tin giao lại hoặc hai lượt xử lý cùng lúc gặp khóa trùng
 * và không gửi lần hai (lượt thua nhận {@code DataIntegrityViolationException}, listener coi là đã gửi). SMTP lỗi thì
 * ghi FAILED và trả kết quả bình thường, không ném lỗi để listener không thử lại vô hạn. SMTP chưa cấu hình thì ghi
 * LOGGED (nội dung nằm trong log ứng dụng).
 */
@Service
public class StockAlertEmailService {

    private static final Logger log = LoggerFactory.getLogger(StockAlertEmailService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    /** Kết quả xử lý một yêu cầu gửi email. */
    public enum Outcome {
        SENT, LOGGED, FAILED, DUPLICATE, SKIPPED
    }

    private final NotificationLogRepository repository;
    private final EmailService emailService;
    private final StockAlertEmailComposer composer;

    public StockAlertEmailService(NotificationLogRepository repository, EmailService emailService,
                                  StockAlertEmailComposer composer) {
        this.repository = repository;
        this.emailService = emailService;
        this.composer = composer;
    }

    @Transactional
    public Outcome send(StockAlertDigestEvent digest, String messageId) {
        Long companyId = TenantContext.requireCompanyId();
        if (digest.companyId() != null && !digest.companyId().equals(companyId)) {
            log.warn("Bỏ qua email cảnh báo có companyId {} khác doanh nghiệp của tin {} (messageId={})",
                    digest.companyId(), companyId, messageId);
            return Outcome.SKIPPED;
        }
        if (digest.kind() == null || digest.recipientEmail() == null || digest.recipientEmail().isBlank()
                || digest.sections().isEmpty()) {
            log.warn("Bỏ qua email cảnh báo thiếu loại, người nhận hoặc nội dung (messageId={})", messageId);
            return Outcome.SKIPPED;
        }
        String recipient = digest.recipientEmail().trim();
        String dedupKey = "EMAIL:" + digest.kind().name() + ":" + digest.alertDate() + ":"
                + recipient.toLowerCase(Locale.ROOT);
        if (repository.existsByDedupKey(dedupKey)) {
            log.info("Email cảnh báo {} ngày {} đã gửi cho người nhận này, bỏ qua (messageId={}, companyId={})",
                    digest.kind(), digest.alertDate(), messageId, companyId);
            return Outcome.DUPLICATE;
        }

        String subject = composer.subject(digest);
        NotificationLog entry = new NotificationLog();
        entry.setChannel(NotificationLog.CHANNEL_EMAIL);
        entry.setEventType("STOCK_ALERT_" + digest.kind().name());
        entry.setRecipient(recipient);
        entry.setSubject(subject);
        entry.setAlertKind(digest.kind().name());
        entry.setAlertDate(digest.alertDate());
        entry.setWarehouseId(digest.sections().size() == 1 ? digest.sections().get(0).warehouseId() : null);
        entry.setDedupKey(dedupKey);
        entry.setStatus(NotificationStatus.FAILED);
        entry.setErrorMessage("Đang gửi");
        repository.saveAndFlush(entry);

        Outcome outcome;
        try {
            EmailService.DeliveryResult result = emailService.sendHtml(recipient, subject, composer.html(digest));
            outcome = result == EmailService.DeliveryResult.LOGGED ? Outcome.LOGGED : Outcome.SENT;
            entry.setStatus(outcome == Outcome.LOGGED ? NotificationStatus.LOGGED : NotificationStatus.SENT);
            entry.setErrorMessage(null);
        } catch (MailException ex) {
            log.warn("Gửi email cảnh báo {} thất bại (companyId={}, messageId={}): {}",
                    digest.kind(), companyId, messageId, ex.getMessage());
            outcome = Outcome.FAILED;
            entry.setStatus(NotificationStatus.FAILED);
            entry.setErrorMessage(StockAlertNotificationService.truncate(
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(), MAX_ERROR_LENGTH));
        }
        repository.save(entry);
        return outcome;
    }
}
