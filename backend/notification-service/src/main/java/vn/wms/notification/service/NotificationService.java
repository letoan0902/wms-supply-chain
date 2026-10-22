package vn.wms.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;
import vn.wms.notification.messaging.NotificationMessage;
import vn.wms.notification.repository.NotificationLogRepository;

/**
 * Xử lý một sự kiện cảnh báo: gửi email rồi ghi nhật ký (SENT hoặc FAILED).
 * Lỗi gửi email không ném ra ngoài để tin nhắn không bị đưa lại hàng đợi vô hạn;
 * TODO (Sprint 2): thêm cơ chế gửi lại (retry) cho các bản ghi FAILED.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final EmailService emailService;
    private final NotificationLogRepository logRepository;

    public NotificationService(EmailService emailService, NotificationLogRepository logRepository) {
        this.emailService = emailService;
        this.logRepository = logRepository;
    }

    @Transactional
    public NotificationLog process(NotificationMessage message) {
        NotificationLog entry = new NotificationLog();
        entry.setEventType(message.eventType());
        entry.setRecipient(message.recipient());
        entry.setSubject(message.subject());
        try {
            emailService.send(message.recipient(), message.subject(), message.content());
            entry.setStatus(NotificationStatus.SENT);
        } catch (MailException ex) {
            log.warn("Gửi email tới {} thất bại: {}", message.recipient(), ex.getMessage());
            entry.setStatus(NotificationStatus.FAILED);
            entry.setErrorMessage(truncate(ex.getMessage()));
        }
        return logRepository.save(entry);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
