package vn.wms.common.messaging.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Khóa trừ tồn {@code dispatchKey} của {@code order.dispatched} (WMS-20.4) đi qua JSON, sự kiện dạng cũ vẫn đọc được. */
class OrderDispatchedEventJsonTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void dispatchKeySurvivesJsonRoundTrip() throws Exception {
        OrderDispatchedEvent event = new OrderDispatchedEvent(7L, "SO-1", OrderType.SALES, 3L, "thukho",
                Instant.parse("2026-11-11T08:00:00Z"),
                List.of(new OrderLine(101L, "SKU-1", "Sữa", new BigDecimal("5"), "Hộp", BigDecimal.ONE)), "GDN-42");

        OrderDispatchedEvent read = mapper.readValue(mapper.writeValueAsString(event), OrderDispatchedEvent.class);

        assertThat(read).isEqualTo(event);
        assertThat(read.dispatchKey()).isEqualTo("GDN-42");
    }

    @Test
    void legacyMessageWithoutDispatchKeyIsReadWithNullKey() throws Exception {
        String legacy = "{\"orderId\":7,\"orderNumber\":\"SO-1\",\"orderType\":\"SALES\",\"warehouseId\":3,"
                + "\"dispatchedBy\":\"thukho\",\"dispatchedAt\":\"2026-11-11T08:00:00Z\",\"items\":[]}";

        OrderDispatchedEvent read = mapper.readValue(legacy, OrderDispatchedEvent.class);

        assertThat(read.orderId()).isEqualTo(7L);
        assertThat(read.dispatchKey()).isNull();
        assertThat(new OrderDispatchedEvent(7L, "SO-1", OrderType.SALES, 3L, "a", null, null).dispatchKey()).isNull();
    }
}
