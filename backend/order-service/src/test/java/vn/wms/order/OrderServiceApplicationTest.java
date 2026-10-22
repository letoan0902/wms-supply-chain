package vn.wms.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.order.client.ProductClient;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.PurchaseOrder;
import vn.wms.order.entity.Supplier;
import vn.wms.order.repository.PurchaseOrderRepository;
import vn.wms.order.repository.SupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khởi động context thật với H2; mỗi test chạy trong transaction và tự rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderServiceApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProductClient productClient;

    @Test
    void feignClientToProductServiceIsRegistered() {
        assertThat(productClient).isNotNull();
    }

    @Test
    void listPurchaseOrdersReturnsEmptyPageWhenNoData() throws Exception {
        mockMvc.perform(get("/api/orders/purchase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void listPurchaseOrdersMapsSupplierAndStatus() throws Exception {
        Supplier supplier = new Supplier();
        supplier.setCode("SUP01");
        supplier.setName("Công ty Hưng Thịnh");
        supplierRepository.save(supplier);

        PurchaseOrder order = new PurchaseOrder();
        order.setPoNumber("PO-2026-0001");
        order.setSupplier(supplier);
        order.setOrderDate(LocalDate.of(2026, 10, 1));
        order.setTotalAmount(new BigDecimal("1500000.00"));
        purchaseOrderRepository.save(order);

        mockMvc.perform(get("/api/orders/purchase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].poNumber").value("PO-2026-0001"))
                .andExpect(jsonPath("$.data.content[0].supplierName").value("Công ty Hưng Thịnh"))
                .andExpect(jsonPath("$.data.content[0].status").value(OrderStatus.DRAFT.name()));
    }

    @Test
    void livenessProbeIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
