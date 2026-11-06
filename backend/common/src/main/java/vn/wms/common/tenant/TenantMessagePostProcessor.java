package vn.wms.common.tenant;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import vn.wms.common.messaging.WmsEventPublisher;

/**
 * Chạy trên luồng của listener ngay sau khi nhận message và trước khi gọi phương thức {@code @RabbitListener}:
 * đặt {@link TenantContext} theo header {@value WmsEventPublisher#HEADER_COMPANY_ID} của message. Message không
 * có header thì xóa ngữ cảnh, để luồng consumer không mang doanh nghiệp của message trước sang message sau.
 */
public class TenantMessagePostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) {
        TenantContext.setCompanyId(companyIdOf(message));
        return message;
    }

    /** Đọc ID doanh nghiệp từ header của message, {@code null} nếu không có hoặc không hợp lệ. */
    public static Long companyIdOf(Message message) {
        Object value = message.getMessageProperties().getHeader(WmsEventPublisher.HEADER_COMPANY_ID);
        if (value instanceof Number number) {
            return number.longValue() > 0 ? number.longValue() : null;
        }
        return value != null ? TenantContext.parse(value.toString()) : null;
    }
}
