package vn.wms.inventory.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;
import vn.wms.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static vn.wms.inventory.alert.StockAlertTestSupport.page;
import static vn.wms.inventory.alert.StockAlertTestSupport.product;

/** Phát hiện hàng cận hạn, hết hạn, tồn lâu và tồn thấp: số lẻ và các ngày biên. */
class StockAlertDetectionTest extends StockAlertIntegrationTestBase {

    @Autowired
    StockAlertDetector detector;

    @Autowired
    ProductCatalog productCatalog;

    private List<StockAlertResponse> detect(Long companyId) {
        return TenantContext.callAs(companyId,
                () -> detector.detect(TODAY, Set.of(), productCatalog.load(false)));
    }

    private static Map<String, StockAlertResponse> byId(List<StockAlertResponse> alerts) {
        return alerts.stream().collect(Collectors.toMap(StockAlertResponse::id, Function.identity()));
    }

    @Test
    @DisplayName("Hạn dùng: hôm nay và hôm qua là hết hạn, ngày mai đến đúng 30 ngày là cận hạn, 31 ngày và dòng hết hàng thì không")
    void expiryBoundaries() {
        long bin = data.warehouseWithBin(COMPANY_A, "WH-EXP", "Kho hạn dùng");
        long expiredYesterday = data.stock(COMPANY_A, bin, 1L, "L-HQ", "1.250", "0", TODAY.minusDays(1), true);
        long expiresToday = data.stock(COMPANY_A, bin, 1L, "L-HN", "0.001", "0", TODAY, false);
        long tomorrow = data.stock(COMPANY_A, bin, 2L, "L-NM", "3.5", "1", TODAY.plusDays(1), false);
        long day30 = data.stock(COMPANY_A, bin, 2L, "L-30", "10", "0", TODAY.plusDays(30), false);
        long day31 = data.stock(COMPANY_A, bin, 2L, "L-31", "10", "0", TODAY.plusDays(31), false);
        long emptyLot = data.stock(COMPANY_A, bin, 3L, "L-RONG", "0", "0", TODAY.plusDays(2), false);
        long noExpiry = data.stock(COMPANY_A, bin, 3L, null, "5", "0", null, false);

        Map<String, StockAlertResponse> alerts = byId(detect(COMPANY_A));

        StockAlertResponse yesterday = alerts.get("EXPIRED:" + expiredYesterday);
        assertThat(yesterday.severity()).isEqualTo(StockAlertSeverity.CRITICAL);
        assertThat(yesterday.daysLeft()).isEqualTo(-1L);
        assertThat(yesterday.quantity()).isEqualByComparingTo("1.250");
        assertThat(yesterday.lotNo()).isEqualTo("L-HQ");
        assertThat(alerts.get("EXPIRED:" + expiresToday).daysLeft()).isZero();
        assertThat(alerts.get("EXPIRED:" + expiresToday).quantity()).isEqualByComparingTo("0.001");

        StockAlertResponse first = alerts.get("NEAR_EXPIRY:" + tomorrow);
        assertThat(first.severity()).isEqualTo(StockAlertSeverity.WARNING);
        assertThat(first.daysLeft()).isEqualTo(1L);
        assertThat(first.availableQuantity()).isEqualByComparingTo("2.5");
        assertThat(first.message()).contains("L-NM").contains("FEFO");
        assertThat(alerts.get("NEAR_EXPIRY:" + day30).daysLeft()).isEqualTo(30L);

        assertThat(alerts).doesNotContainKeys("NEAR_EXPIRY:" + day31, "EXPIRED:" + day31,
                "NEAR_EXPIRY:" + emptyLot, "NEAR_EXPIRY:" + noExpiry, "EXPIRED:" + noExpiry);
        // Dòng hết hạn đứng trước dòng cận hạn
        assertThat(detect(COMPANY_A).get(0).kind()).isEqualTo(StockAlertKind.EXPIRED);
    }

    @Test
    @DisplayName("Tồn thấp: so tồn dùng được (bỏ lô khóa, lô hết hạn) với mức an toàn và điểm đặt hàng lại, có số lẻ")
    void lowStockWithDecimals() {
        long bin = data.warehouseWithBin(COMPANY_A, "WH-LOW", "Kho tồn thấp");
        data.stock(COMPANY_A, bin, 11L, null, "6.250", "0", null, false);
        data.stock(COMPANY_A, bin, 11L, "L2", "4.250", "1.000", null, false);       // tổng 10.5 = mức an toàn
        data.stock(COMPANY_A, bin, 12L, null, "20.250", "0", null, false);          // bằng điểm đặt hàng lại
        data.stock(COMPANY_A, bin, 13L, null, "20.251", "0", null, false);          // vượt điểm đặt hàng lại 0.001
        data.stock(COMPANY_A, bin, 14L, null, "0", "0", null, false);               // chưa cấu hình ngưỡng
        data.stock(COMPANY_A, bin, 15L, "HH", "100", "0", TODAY.minusDays(3), true); // hết hạn, không dùng được
        data.stock(COMPANY_A, bin, 15L, null, "5", "0", null, false);
        data.stock(COMPANY_A, bin, 16L, null, "1", "0", null, false);               // sản phẩm ngừng kinh doanh
        catalogs.put(COMPANY_A, page(
                product(11L, "SKU-11", "10.5", "20"),
                product(12L, "SKU-12", "10", "20.25"),
                product(13L, "SKU-13", "10", "20.25"),
                product(14L, "SKU-14", "0", "0"),
                product(15L, "SKU-15", "10", "30"),
                new AlertProductInfo(16L, "SKU-16", "Ngừng bán", new BigDecimal("5"), new BigDecimal("8"), false)));

        Map<Long, StockAlertResponse> lowStock = detect(COMPANY_A).stream()
                .filter(a -> a.kind() == StockAlertKind.LOW_STOCK)
                .collect(Collectors.toMap(StockAlertResponse::productId, Function.identity()));

        assertThat(lowStock).containsOnlyKeys(11L, 12L, 15L);
        assertThat(lowStock.get(11L).severity()).isEqualTo(StockAlertSeverity.CRITICAL);
        assertThat(lowStock.get(11L).quantity()).isEqualByComparingTo("10.5");
        assertThat(lowStock.get(11L).availableQuantity()).isEqualByComparingTo("9.5");
        assertThat(lowStock.get(11L).safetyStock()).isEqualByComparingTo("10.5");
        assertThat(lowStock.get(11L).message()).startsWith("Khẩn cấp: Mặt hàng SKU-11 tại Kho tồn thấp");
        assertThat(lowStock.get(12L).severity()).isEqualTo(StockAlertSeverity.WARNING);
        assertThat(lowStock.get(12L).message()).startsWith("Cảnh báo: Mặt hàng SKU-12").contains("Còn: 20.25");
        assertThat(lowStock.get(15L).severity()).isEqualTo(StockAlertSeverity.CRITICAL);
        assertThat(lowStock.get(15L).quantity()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Tồn thấp không tính khi không đọc được danh mục sản phẩm; cảnh báo hạn dùng vẫn có, SKU để trống")
    void lowStockSkippedWhenProductServiceIsDown() {
        long bin = data.warehouseWithBin(COMPANY_A, "WH-DOWN", "Kho mất kết nối");
        data.stock(COMPANY_A, bin, 21L, "L1", "1", "0", TODAY.plusDays(5), false);
        when(productClient.listProducts(anyInt(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("product-service không phản hồi"));

        List<StockAlertResponse> alerts = detect(COMPANY_A);

        assertThat(alerts).extracting(StockAlertResponse::kind).containsExactly(StockAlertKind.NEAR_EXPIRY);
        assertThat(alerts.get(0).skuCode()).isNull();
        assertThat(alerts.get(0).message()).contains("#21");
    }

    @Test
    @DisplayName("Hàng tồn lâu: không biến động đúng 90 ngày là cảnh báo, 89 ngày thì không, dòng đã về 0 không tính")
    void deadStockBoundary() {
        long bin = data.warehouseWithBin(COMPANY_A, "WH-DEAD", "Kho tồn lâu");
        long idle90 = data.stock(COMPANY_A, bin, 31L, null, "7.125", "0", null, false);
        long idle89 = data.stock(COMPANY_A, bin, 32L, null, "4", "0", null, false);
        long idleEmpty = data.stock(COMPANY_A, bin, 33L, null, "0", "0", null, false);
        // Mặt hàng 34 có một dòng cũ và một dòng vừa nhập: lần biến động gần nhất là hôm nay
        long oldRow = data.stock(COMPANY_A, bin, 34L, "CU", "3", "0", null, false);
        data.stock(COMPANY_A, bin, 34L, "MOI", "2", "0", null, false);
        data.lastChangedOn(idle90, TODAY.minusDays(90));
        data.lastChangedOn(idle89, TODAY.minusDays(89));
        data.lastChangedOn(idleEmpty, TODAY.minusDays(200));
        data.lastChangedOn(oldRow, TODAY.minusDays(300));

        List<StockAlertResponse> dead = detect(COMPANY_A).stream()
                .filter(a -> a.kind() == StockAlertKind.DEAD_STOCK).toList();

        assertThat(dead).hasSize(1);
        StockAlertResponse alert = dead.get(0);
        assertThat(alert.productId()).isEqualTo(31L);
        assertThat(alert.severity()).isEqualTo(StockAlertSeverity.INFO);
        assertThat(alert.daysWithoutMovement()).isEqualTo(90L);
        assertThat(alert.quantity()).isEqualByComparingTo("7.125");
        assertThat(alert.lastMovementAt()).isNotNull();
        assertThat(alert.message()).contains("90 ngày");
    }
}
