package vn.wms.common.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Kiểm tra bộ phát sự kiện mà không cần broker: exchange, routing key, header và hành vi theo transaction. */
@ExtendWith(MockitoExtension.class)
class WmsEventPublisherTest {

    private static final String ROUTING_KEY = WmsEvents.RoutingKeys.ORDER_CREATED;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private WmsEventPublisher publisher;

    private final OrderCreatedEvent event = new OrderCreatedEvent(1L, "PO-20261104-0001", OrderType.PURCHASE,
            "APPROVED", 10L, null, 5L, "Kho Hà Nội", new BigDecimal("1200000.00"), "purchasing",
            Instant.parse("2026-11-04T08:00:00Z"),
            List.of(new OrderLine(100L, "SKU-001", "Thùng carton 60x40", new BigDecimal("50"), "cái",
                    new BigDecimal("24000.00"))));

    @BeforeEach
    void setUp() {
        publisher = new WmsEventPublisher(rabbitTemplate, "order-service");
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishSendsToWmsExchangeWithMetadataHeaders() {
        String messageId = publisher.publish(ROUTING_KEY, event);

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(WmsEvents.EXCHANGE), eq("order.created"), eq(event), captor.capture());
        Message message = captor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        MessageProperties properties = message.getMessageProperties();

        assertThat(messageId).isNotBlank();
        assertThat(properties.getMessageId()).isEqualTo(messageId);
        assertThat(properties.getTimestamp()).isNotNull();
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getHeaders())
                .containsEntry(WmsEventPublisher.HEADER_EVENT_TYPE, "order.created")
                .containsEntry(WmsEventPublisher.HEADER_SOURCE, "order-service");
    }

    @Test
    void publishRejectsBlankRoutingKeyAndNullEvent() {
        assertThatThrownBy(() -> publisher.publish(" ", event)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.publish(ROUTING_KEY, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void publishAfterCommitSendsImmediatelyWhenNoTransaction() {
        publisher.publishAfterCommit(ROUTING_KEY, event);

        verify(rabbitTemplate).convertAndSend(eq(WmsEvents.EXCHANGE), eq(ROUTING_KEY), eq(event),
                any(MessagePostProcessor.class));
    }

    @Test
    void publishAfterCommitWaitsUntilTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(ROUTING_KEY, event);
        verifyNoInteractions(rabbitTemplate);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(rabbitTemplate).convertAndSend(eq(WmsEvents.EXCHANGE), eq(ROUTING_KEY), eq(event),
                any(MessagePostProcessor.class));
    }

    @Test
    void publishAfterCommitSkipsEventWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(ROUTING_KEY, event);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void brokerFailureAfterCommitIsLoggedNotThrown() {
        doThrow(new AmqpException("broker không sẵn sàng")).when(rabbitTemplate)
                .convertAndSend(eq(WmsEvents.EXCHANGE), eq(ROUTING_KEY), eq(event), any(MessagePostProcessor.class));
        TransactionSynchronizationManager.initSynchronization();
        publisher.publishAfterCommit(ROUTING_KEY, event);

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }
}
