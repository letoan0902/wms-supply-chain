package vn.wms.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.UUID;

/**
 * Phát sự kiện nghiệp vụ lên exchange {@link WmsEvents#EXCHANGE} dưới dạng JSON.
 *
 * <p>Mỗi message được gắn {@code messageId} (UUID, consumer dùng để chống xử lý trùng), {@code timestamp},
 * header {@value #HEADER_EVENT_TYPE} (chính là routing key) và {@value #HEADER_SOURCE} (tên service phát).
 * Payload nên là một record trong gói {@code vn.wms.common.messaging.event} để service nhận đọc được bằng cùng kiểu.
 *
 * <p>Trong phương thức {@code @Transactional} hãy dùng {@link #publishAfterCommit}: sự kiện chỉ đi khi dữ liệu
 * đã commit, rollback thì không phát. Bean này do {@code WmsEventPublisherAutoConfiguration} tạo sẵn cho mọi
 * service có {@code spring-boot-starter-amqp}, chỉ cần inject vào service nghiệp vụ.
 */
public class WmsEventPublisher {

    /** Header chứa loại sự kiện (trùng routing key), ví dụ {@code order.created}. */
    public static final String HEADER_EVENT_TYPE = "x-wms-event";
    /** Header chứa tên service phát sự kiện, ví dụ {@code order-service}. */
    public static final String HEADER_SOURCE = "x-wms-source";

    private static final Logger log = LoggerFactory.getLogger(WmsEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String source;

    public WmsEventPublisher(RabbitTemplate rabbitTemplate, String source) {
        Assert.notNull(rabbitTemplate, "rabbitTemplate không được null");
        this.rabbitTemplate = rabbitTemplate;
        this.source = source;
    }

    /**
     * Gửi ngay lập tức. Ném {@link AmqpException} nếu không gửi được (broker không sẵn sàng, exchange chưa có).
     *
     * @param routingKey một hằng số trong {@link WmsEvents.RoutingKeys}
     * @param event      payload, sẽ được chuyển thành JSON
     * @return messageId đã gắn vào message
     */
    public String publish(String routingKey, Object event) {
        Assert.hasText(routingKey, "routingKey không được rỗng");
        Assert.notNull(event, "event không được null");
        String messageId = UUID.randomUUID().toString();
        rabbitTemplate.convertAndSend(WmsEvents.EXCHANGE, routingKey, event, message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setMessageId(messageId);
            properties.setTimestamp(new Date());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setHeader(HEADER_EVENT_TYPE, routingKey);
            if (source != null && !source.isBlank()) {
                properties.setHeader(HEADER_SOURCE, source);
            }
            return message;
        });
        log.info("Đã phát sự kiện {} lên {} (messageId={})", routingKey, WmsEvents.EXCHANGE, messageId);
        return messageId;
    }

    /**
     * Nếu đang trong transaction thì gửi sau khi commit thành công (rollback thì bỏ), ngược lại gửi ngay.
     * Lỗi broker ở bước sau commit chỉ được ghi log vì dữ liệu đã lưu, không thể hoàn tác; khi cần đảm bảo
     * chắc chắn hơn hãy dùng mẫu outbox (ghi sự kiện vào bảng rồi có job gửi lại).
     */
    public void publishAfterCommit(String routingKey, Object event) {
        Assert.hasText(routingKey, "routingKey không được rỗng");
        Assert.notNull(event, "event không được null");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(routingKey, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publish(routingKey, event);
                } catch (AmqpException ex) {
                    log.error("Không gửi được sự kiện {} sau khi commit, dữ liệu đã lưu nhưng service khác chưa nhận: {}",
                            routingKey, ex.getMessage());
                }
            }
        });
    }
}
