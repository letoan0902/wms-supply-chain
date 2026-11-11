package vn.wms.notification.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import vn.wms.common.messaging.event.InventoryUpdatedEvent;
import vn.wms.common.messaging.event.LowStockAlertEvent;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm tra phong bì thông báo: loại, mức độ, tiêu đề, nội dung tiếng Việt, thời điểm và JSON gửi cho frontend. */
class RealtimeNotificationFactoryTest {

    private static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");
    private final RealtimeNotificationFactory factory =
            new RealtimeNotificationFactory(Clock.fixed(NOW, ZoneOffset.UTC));

    private final List<OrderLine> items = List.of(
            new OrderLine(100L, "SKU-001", "Thùng carton", new BigDecimal("50"), "cái", new BigDecimal("24000.00")));

    @Test
    void lowStockIsWarningOnAlertsTopicWithReadableMessage() {
        LowStockAlertEvent event = new LowStockAlertEvent(5L, "Kho Hà Nội", 100L, "SKU-001", "Thùng carton",
                new BigDecimal("5.000"), new BigDecimal("20"), Instant.parse("2026-11-05T07:59:00Z"));

        RealtimeNotification notification = factory.lowStock(event, "msg-1", null);

        assertThat(notification.id()).isEqualTo("msg-1");
        assertThat(notification.type()).isEqualTo(RealtimeEventType.LOW_STOCK);
        assertThat(notification.type().topic(1L)).isEqualTo("/topic/company/1/alerts");
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(notification.title()).isEqualTo("Cảnh báo tồn kho thấp");
        assertThat(notification.message()).isEqualTo("SKU-001 Thùng carton tại Kho Hà Nội còn 5, dưới ngưỡng 20");
        assertThat(notification.occurredAt()).isEqualTo(Instant.parse("2026-11-05T07:59:00Z"));
        assertThat(notification.payload()).isSameAs(event);
    }

    @Test
    void lowStockAtThresholdAndWithoutWarehouseNameStillReadable() {
        LowStockAlertEvent event = new LowStockAlertEvent(5L, null, 100L, "SKU-001", null,
                new BigDecimal("20"), new BigDecimal("20.000"), null);

        RealtimeNotification notification = factory.lowStock(event, null, null);

        assertThat(notification.message()).isEqualTo("SKU-001 tại kho #5 còn 20, chạm ngưỡng 20");
        // Không có messageId thì sinh UUID mới, không có thời điểm thì dùng đồng hồ hệ thống
        assertThat(notification.id()).matches("[0-9a-f-]{36}");
        assertThat(notification.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void orderCreatedDistinguishesPurchaseAndSales() {
        OrderCreatedEvent purchase = new OrderCreatedEvent(1L, "PO-20261105-0001", OrderType.PURCHASE, "APPROVED", 10L,
                null, 5L, "Kho Hà Nội", new BigDecimal("1200000.00"), "purchasing", null, items);
        OrderCreatedEvent sales = new OrderCreatedEvent(2L, "SO-20261105-0002", OrderType.SALES, "CONFIRMED", null,
                20L, 5L, "Kho Hà Nội", null, "sales", Instant.parse("2026-11-05T07:00:00Z"), List.of());
        Instant messageTimestamp = Instant.parse("2026-11-05T07:30:00Z");

        RealtimeNotification po = factory.orderCreated(purchase, "msg-po", messageTimestamp);
        RealtimeNotification so = factory.orderCreated(sales, "msg-so", messageTimestamp);

        assertThat(po.type().topic(1L)).isEqualTo("/topic/company/1/orders");
        assertThat(po.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(po.title()).isEqualTo("Đơn mua hàng mới");
        assertThat(po.message()).isEqualTo("Đơn PO-20261105-0001 tại Kho Hà Nội, 1 dòng hàng, tổng tiền 1.200.000 VNĐ");
        // createdAt null thì lấy timestamp của message RabbitMQ
        assertThat(po.occurredAt()).isEqualTo(messageTimestamp);
        assertThat(so.title()).isEqualTo("Đơn bán hàng mới");
        assertThat(so.message()).isEqualTo("Đơn SO-20261105-0002 tại Kho Hà Nội");
        assertThat(so.occurredAt()).isEqualTo(Instant.parse("2026-11-05T07:00:00Z"));
    }

    @Test
    void orderDispatchedIsSuccess() {
        OrderDispatchedEvent event = new OrderDispatchedEvent(2L, "SO-20261105-0002", OrderType.SALES, 5L, "staff01",
                Instant.parse("2026-11-05T09:30:00Z"), items);

        RealtimeNotification notification = factory.orderDispatched(event, "msg-2", null);

        assertThat(notification.type()).isEqualTo(RealtimeEventType.ORDER_DISPATCHED);
        assertThat(notification.type().topic(1L)).isEqualTo("/topic/company/1/orders");
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.SUCCESS);
        assertThat(notification.title()).isEqualTo("Đơn bán hàng đã xuất kho");
        assertThat(notification.message())
                .isEqualTo("Đơn SO-20261105-0002 tại kho #5 đã hoàn tất, 1 dòng hàng, người xác nhận staff01");
    }

    @Test
    void inventoryUpdatedDescribesChange() {
        InventoryUpdatedEvent event = new InventoryUpdatedEvent(5L, 100L, "SKU-001", new BigDecimal("50.000"),
                new BigDecimal("45.000"), "GOODS_ISSUE", "SO-20261105-0002", Instant.parse("2026-11-05T09:31:00Z"));

        RealtimeNotification notification = factory.inventoryUpdated(event, "msg-3", null);

        assertThat(notification.type().topic(1L)).isEqualTo("/topic/company/1/inventory");
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(notification.title()).isEqualTo("Tồn kho thay đổi");
        assertThat(notification.message())
                .isEqualTo("SKU-001 tại kho #5 thay đổi từ 50 thành 45 (Xuất kho, chứng từ SO-20261105-0002)");
    }

    @Test
    void kpiUpdatedCarriesSnapshotAsPayload() {
        KpiSnapshot snapshot = new KpiSnapshot(LocalDate.parse("2026-11-05"), 3, 5, 2, 1, 12, NOW);

        RealtimeNotification notification = factory.kpiUpdated(snapshot);

        assertThat(notification.type().topic(1L)).isEqualTo("/topic/company/1/kpi");
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(notification.payload()).isSameAs(snapshot);
        assertThat(notification.occurredAt()).isEqualTo(NOW);
        assertThat(notification.message()).contains("3 đơn mua", "5 đơn bán", "12 lần thay đổi tồn kho");
    }

    @Test
    void serializesToContractJson() throws Exception {
        // Cùng cấu hình Jackson như Spring Boot: thời gian dạng chuỗi ISO 8601
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        KpiSnapshot snapshot = new KpiSnapshot(LocalDate.parse("2026-11-05"), 3, 5, 2, 1, 12, NOW);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(factory.kpiUpdated(snapshot)));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "type", "severity", "title", "message", "occurredAt", "payload");
        assertThat(json.path("type").asText()).isEqualTo("KPI_UPDATED");
        assertThat(json.path("severity").asText()).isEqualTo("info");
        assertThat(json.path("occurredAt").asText()).isEqualTo("2026-11-05T08:00:00Z");
        assertThat(json.path("payload").path("date").asText()).isEqualTo("2026-11-05");
        assertThat(json.path("payload").path("purchaseOrders").asLong()).isEqualTo(3);
        assertThat(json.path("payload").path("inventoryMovements").asLong()).isEqualTo(12);
        assertThat(json.path("payload").path("updatedAt").asText()).isEqualTo("2026-11-05T08:00:00Z");
    }

    @Test
    void formatsMoneyAndQuantity() {
        assertThat(RealtimeNotificationFactory.money(new BigDecimal("1200000.00"))).isEqualTo("1.200.000 VNĐ");
        assertThat(RealtimeNotificationFactory.money(new BigDecimal("1500.5"))).isEqualTo("1.500,5 VNĐ");
        assertThat(RealtimeNotificationFactory.quantity(new BigDecimal("2.500"))).isEqualTo("2.5");
        assertThat(RealtimeNotificationFactory.quantity(new BigDecimal("50"))).isEqualTo("50");
    }
}
