package vn.wms.notification.stockalert;

import vn.wms.common.messaging.event.StockAlertDigestEvent;
import vn.wms.common.messaging.event.StockAlertItem;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;
import vn.wms.common.messaging.event.StockAlertSummaryEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Sự kiện mẫu cho test cảnh báo tồn kho phía notification-service. */
final class StockAlertTestData {

    static final LocalDate DAY = LocalDate.of(2026, 11, 11);
    static final Instant DETECTED_AT = Instant.parse("2026-11-10T17:00:00Z");

    private StockAlertTestData() {
    }

    static StockAlertSummaryEvent nearExpiry(Long companyId, Long warehouseId, String warehouseName) {
        return new StockAlertSummaryEvent(companyId, DAY, StockAlertKind.NEAR_EXPIRY, StockAlertSeverity.WARNING,
                warehouseId, "WH-" + warehouseId, warehouseName, 3, new BigDecimal("12.750"), 30,
                List.of(new StockAlertItem(100L, "SKU-<SUA>", "Sữa tươi & bánh", "L01", "WH-1-Z1-R1-B1",
                                new BigDecimal("2.500"), new BigDecimal("2.500"), DAY.plusDays(3), 3L, null, null, null,
                                null, StockAlertSeverity.WARNING),
                        new StockAlertItem(101L, "SKU-101", "Bánh quy", "L02", "WH-1-Z1-R1-B2",
                                new BigDecimal("10.250"), new BigDecimal("8"), DAY.plusDays(20), 20L, null, null, null,
                                null, StockAlertSeverity.WARNING)),
                DETECTED_AT);
    }

    static StockAlertSummaryEvent lowStock(Long companyId, Long warehouseId) {
        return new StockAlertSummaryEvent(companyId, DAY, StockAlertKind.LOW_STOCK, StockAlertSeverity.CRITICAL,
                warehouseId, "WH-" + warehouseId, "Kho " + warehouseId, 1, new BigDecimal("4"), null,
                List.of(new StockAlertItem(200L, "SKU-200", "Nước suối", null, null, new BigDecimal("4.000"),
                        new BigDecimal("3"), null, null, null, null, new BigDecimal("5"), new BigDecimal("10"),
                        StockAlertSeverity.CRITICAL)),
                DETECTED_AT);
    }

    static StockAlertDigestEvent digest(Long companyId, String email, StockAlertSummaryEvent... sections) {
        return new StockAlertDigestEvent(companyId, DAY, sections[0].kind(), email, "Nguyễn Văn Quản Lý",
                "WAREHOUSE_MANAGER", List.of(sections), DETECTED_AT);
    }
}
