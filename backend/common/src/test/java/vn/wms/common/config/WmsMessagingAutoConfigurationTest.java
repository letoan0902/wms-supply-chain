package vn.wms.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm tra auto-configuration messaging của common trong một context Spring Boot thu nhỏ, không cần broker. */
class WmsMessagingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("spring.application.name=order-service")
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class,
                    WmsMessagingAutoConfiguration.class, WmsEventPublisherAutoConfiguration.class));

    private final OrderCreatedEvent event = new OrderCreatedEvent(1L, "PO-20261104-0001", OrderType.PURCHASE,
            "APPROVED", 10L, null, 5L, "Kho Hà Nội", new BigDecimal("1200000.00"), "purchasing",
            Instant.parse("2026-11-04T08:00:00Z"),
            List.of(new OrderLine(100L, "SKU-001", "Thùng carton 60x40", new BigDecimal("50"), "cái",
                    new BigDecimal("24000.00"))));

    @Test
    void registersJsonConverterOnRabbitTemplateAndCreatesPublisher() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MessageConverter.class).hasSingleBean(WmsEventPublisher.class);
            assertThat(context.getBean(MessageConverter.class)).isInstanceOf(Jackson2JsonMessageConverter.class);
            assertThat(context.getBean(RabbitTemplate.class).getMessageConverter())
                    .isSameAs(context.getBean(MessageConverter.class));
        });
    }

    @Test
    void serializesEventAsJsonAndReadsBackByListenerParameterType() {
        runner.run(context -> {
            MessageConverter converter = context.getBean(MessageConverter.class);

            Message outgoing = converter.toMessage(event, new MessageProperties());
            String json = new String(outgoing.getBody(), StandardCharsets.UTF_8);
            assertThat(outgoing.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
            assertThat(json)
                    .contains("\"orderNumber\":\"PO-20261104-0001\"")
                    .contains("\"orderType\":\"PURCHASE\"")
                    .contains("\"createdAt\":\"2026-11-04T08:00:00Z\"");

            // Service nhận không có class của bên gửi: header __TypeId__ lạ phải bị bỏ qua, dùng kiểu tham số listener
            MessageProperties incoming = new MessageProperties();
            incoming.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            incoming.setHeader("__TypeId__", "vn.wms.order.internal.PurchaseOrderPayload");
            incoming.setInferredArgumentType(OrderCreatedEvent.class);
            assertThat(converter.fromMessage(new Message(outgoing.getBody(), incoming))).isEqualTo(event);
        });
    }

    @Test
    void trustsEventClassesOfCommonPackageWhenNoParameterTypeIsKnown() {
        runner.run(context -> {
            MessageConverter converter = context.getBean(MessageConverter.class);
            Message outgoing = converter.toMessage(event, new MessageProperties());

            MessageProperties incoming = new MessageProperties();
            incoming.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            incoming.setHeader("__TypeId__", OrderCreatedEvent.class.getName());

            assertThat(converter.fromMessage(new Message(outgoing.getBody(), incoming))).isEqualTo(event);
        });
    }

    @Test
    void backsOffWhenServiceDefinesItsOwnConverter() {
        runner.withBean("serviceConverter", MessageConverter.class, SimpleMessageConverter::new).run(context -> {
            assertThat(context).hasSingleBean(MessageConverter.class);
            assertThat(context.getBean(MessageConverter.class)).isInstanceOf(SimpleMessageConverter.class);
        });
    }

    @Test
    void publisherIsSkippedWithoutRabbitTemplateButConverterStillWorks() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WmsMessagingAutoConfiguration.class,
                        WmsEventPublisherAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WmsEventPublisher.class).hasSingleBean(MessageConverter.class);
                    // Không có ObjectMapper của Spring Boot thì dùng ObjectMapper dự phòng, vẫn đọc được java.time
                    MessageConverter converter = context.getBean(MessageConverter.class);
                    Message outgoing = converter.toMessage(event, new MessageProperties());
                    MessageProperties incoming = new MessageProperties();
                    incoming.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    incoming.setInferredArgumentType(OrderCreatedEvent.class);
                    assertThat(converter.fromMessage(new Message(outgoing.getBody(), incoming))).isEqualTo(event);
                });
    }
}
