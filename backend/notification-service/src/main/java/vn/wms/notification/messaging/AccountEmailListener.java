package vn.wms.notification.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.AccountEmailRequestedEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.service.AccountEmailService;

/**
 * Consumer duy nhất của queue {@code notification.auth-emails} (binding {@code auth.#}, WMS-23.6): nhận yêu cầu gửi
 * email kích hoạt doanh nghiệp và đặt lại mật khẩu từ auth-service ({@link AccountEmailRequestedEvent},
 * docs/MESSAGING.md mục 3) rồi chuyển cho {@link AccountEmailService} trong ngữ cảnh doanh nghiệp của sự kiện.
 *
 * <p>Doanh nghiệp đọc từ header {@code x-wms-company-id}, không có thì trường {@code companyId} của payload. Không có
 * doanh nghiệp, thiếu người nhận hoặc liên kết thì ghi cảnh báo và bỏ qua (ack). JSON hỏng thì chuyển dead-letter.
 * Không ghi liên kết ra log ở lớp này.
 */
@Component
public class AccountEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailListener.class);

    private final ObjectReader reader;
    private final AccountEmailService accountEmailService;

    public AccountEmailListener(ObjectMapper objectMapper, AccountEmailService accountEmailService) {
        // Bỏ qua trường lạ để auth-service thêm trường mới không làm hỏng việc đọc
        this.reader = objectMapper.reader().without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.accountEmailService = accountEmailService;
    }

    @RabbitListener(queues = WmsEvents.Queues.NOTIFICATION_AUTH_EMAILS, id = "notificationAuthEmails")
    public void onMessage(Message message) {
        String eventType = EventMessages.eventType(message.getMessageProperties());
        String messageId = message.getMessageProperties().getMessageId();
        AccountEmailRequestedEvent event = EventMessages.read(reader, message, AccountEmailRequestedEvent.class);
        Long companyId = EventMessages.companyId(reader, message);
        if (companyId == null) {
            log.warn("Bỏ qua yêu cầu email tài khoản không có doanh nghiệp: routingKey='{}' messageId={}",
                    eventType, messageId);
            return;
        }
        if (event.kind() == null || !StringUtils.hasText(event.recipientEmail()) || !StringUtils.hasText(event.link())) {
            log.warn("Bỏ qua yêu cầu email tài khoản thiếu loại, người nhận hoặc liên kết: routingKey='{}' messageId={}",
                    eventType, messageId);
            return;
        }
        log.info("Nhận yêu cầu email tài khoản {} cho {} (routingKey='{}', messageId={}, companyId={})",
                event.kind(), event.recipientEmail(), eventType, messageId, companyId);
        TenantContext.runAs(companyId, () -> accountEmailService.send(event, messageId));
    }
}
