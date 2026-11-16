package vn.wms.inventory.stocktake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.service.IncomingStockService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hàng đang về của đơn mua có cùng một sản phẩm trên nhiều dòng (WMS-20.4): số lượng được cộng dồn theo sản phẩm
 * (50 + 50 là 100, không phải 50), và nhận lại cùng sự kiện không cộng thêm.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncomingStockMultiLineTest extends StocktakeTestSupport {

    @Autowired
    private IncomingStockService incomingStockService;

    private OrderCreatedEvent purchaseOrder(Long warehouseId) {
        return new OrderCreatedEvent(7001L, "PO-20261116-0001", OrderType.PURCHASE, "APPROVED", 5L, null,
                warehouseId, "Kho kiểm kê 1", new BigDecimal("1000000"), "purchasing01", Instant.now(),
                List.of(new OrderLine(PRODUCT_1, "SKU-1", "Sữa tươi", new BigDecimal("50.000"), "hộp", BigDecimal.TEN),
                        new OrderLine(PRODUCT_2, "SKU-2", "Bánh mì", new BigDecimal("20.000"), "gói", BigDecimal.TEN),
                        new OrderLine(PRODUCT_1, "SKU-1", "Sữa tươi", new BigDecimal("50.000"), "hộp", BigDecimal.ONE)));
    }

    private BigDecimal incoming(Long productId) {
        return TenantContext.callAs(COMPANY_A, () ->
                incomingStockService.getProductIncomingSummary(warehouse1.getId(), productId).incomingQuantity());
    }

    @Test
    @DisplayName("Cùng sản phẩm trên hai dòng đơn mua: hàng đang về là tổng hai dòng, nhận lại sự kiện không cộng thêm")
    void sameProductOnTwoLinesIsSummed() {
        TenantContext.runAs(COMPANY_A, () -> incomingStockService.processPurchaseOrderCreated(
                purchaseOrder(warehouse1.getId()), "msg-po-1"));

        assertThat(incoming(PRODUCT_1)).isEqualByComparingTo("100");
        assertThat(incoming(PRODUCT_2)).isEqualByComparingTo("20");

        TenantContext.runAs(COMPANY_A, () -> incomingStockService.processPurchaseOrderCreated(
                purchaseOrder(warehouse1.getId()), "msg-po-1-redelivered"));

        assertThat(incoming(PRODUCT_1)).isEqualByComparingTo("100");
        assertThat(incoming(PRODUCT_2)).isEqualByComparingTo("20");
        assertThat(TenantContext.callAs(COMPANY_A, () -> incomingStockRepository.findByOrderId(7001L))).hasSize(2);
    }
}
