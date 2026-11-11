package vn.wms.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.CustomerType;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.PurchaseOrder;
import vn.wms.order.entity.PurchaseOrderItem;
import vn.wms.order.entity.SalesOrder;
import vn.wms.order.entity.SalesOrderItem;
import vn.wms.order.entity.Supplier;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.PurchaseOrderRepository;
import vn.wms.order.repository.SalesOrderRepository;
import vn.wms.order.repository.SupplierRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Số câu SQL của các API danh sách chứng từ (WMS-19.3): một trang đơn bán không được nạp toàn bộ đơn khớp điều kiện
 * rồi cắt trang trong bộ nhớ, và dòng hàng của cả trang được nạp bằng một câu (không phải một câu cho mỗi đơn).
 * Nội dung trả về giữ nguyên: mỗi đơn vẫn đủ dòng hàng và thông tin đối tác.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListQueryCountIntegrationTest {

    private static final Long COMPANY = 1L;
    private static final String TAG = "Q193";
    private static final int ORDERS = 7;
    private static final int PAGE_SIZE = 5;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    private final List<Long> salesOrderIds = new ArrayList<>();
    private final List<Long> purchaseOrderIds = new ArrayList<>();
    private Long customerId;
    private Long supplierId;

    @BeforeEach
    void setUp() {
        TenantContext.runAs(COMPANY, () -> {
            Customer customer = new Customer();
            customer.setCode(TAG + "-KH");
            customer.setName("Khách hàng đo truy vấn");
            customer.setCustomerType(CustomerType.WHOLESALE);
            customer.setCreditLimit(BigDecimal.ZERO);
            customer.setCurrentDebt(BigDecimal.ZERO);
            customer.setActive(true);
            customer = customerRepository.save(customer);
            customerId = customer.getId();

            Supplier supplier = new Supplier();
            supplier.setCode(TAG + "-NCC");
            supplier.setName("Nhà cung cấp đo truy vấn");
            supplier.setCreditLimit(BigDecimal.ZERO);
            supplier.setCurrentDebt(BigDecimal.ZERO);
            supplier.setActive(true);
            supplier = supplierRepository.save(supplier);
            supplierId = supplier.getId();

            for (int i = 1; i <= ORDERS; i++) {
                SalesOrder so = new SalesOrder();
                so.setSoNumber("SO-" + TAG + "-" + i);
                so.setCustomer(customer);
                so.setStatus(OrderStatus.CONFIRMED);
                so.setOrderDate(LocalDate.now());
                for (int line = 1; line <= 2; line++) {
                    SalesOrderItem item = new SalesOrderItem();
                    item.setSkuCode("SKU-" + line);
                    item.setProductName("Hàng " + line);
                    item.setQuantity(BigDecimal.ONE);
                    item.setUnitPrice(BigDecimal.TEN);
                    so.addItem(item);
                }
                salesOrderIds.add(salesOrderRepository.save(so).getId());

                PurchaseOrder po = new PurchaseOrder();
                po.setPoNumber("PO-" + TAG + "-" + i);
                po.setSupplier(supplier);
                po.setStatus(OrderStatus.DRAFT);
                po.setOrderDate(LocalDate.now());
                for (int line = 1; line <= 2; line++) {
                    PurchaseOrderItem item = new PurchaseOrderItem();
                    item.setSkuCode("SKU-" + line);
                    item.setProductName("Hàng " + line);
                    item.setQuantity(BigDecimal.ONE);
                    item.setUnitPrice(BigDecimal.TEN);
                    po.addItem(item);
                }
                purchaseOrderIds.add(purchaseOrderRepository.save(po).getId());
            }
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.runAs(COMPANY, () -> {
            salesOrderRepository.deleteAllById(salesOrderIds);
            purchaseOrderRepository.deleteAllById(purchaseOrderIds);
            customerRepository.deleteById(customerId);
            supplierRepository.deleteById(supplierId);
        });
        TenantContext.clear();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private JsonNode list(String url) throws Exception {
        String body = mockMvc.perform(get(url).header("X-Company-Id", COMPANY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    @Test
    @DisplayName("Danh sách đơn bán: chỉ nạp đúng số đơn của trang, dòng hàng cả trang trong một câu, nội dung đủ")
    void salesOrderPageLoadsOnlyThePage() throws Exception {
        Statistics stats = statistics();
        stats.clear();

        JsonNode data = list("/api/orders/sales?keyword=" + TAG + "&size=" + PAGE_SIZE);

        assertThat(data.path("content")).hasSize(PAGE_SIZE);
        assertThat(data.path("totalElements").asLong()).isEqualTo(ORDERS);
        for (JsonNode order : data.path("content")) {
            assertThat(order.path("items")).hasSize(2);
            assertThat(order.path("customerName").asText()).isEqualTo("Khách hàng đo truy vấn");
        }
        // Trước WMS-19.3: nạp kèm dòng hàng khi phân trang làm Hibernate đọc cả 7 đơn rồi cắt trang trong bộ nhớ
        assertThat(stats.getEntityStatistics(SalesOrder.class.getName()).getLoadCount()).isEqualTo(PAGE_SIZE);
        // Một câu lấy trang (kèm khách hàng), một câu đếm, một câu IN lấy dòng hàng của cả trang
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("Danh sách đơn mua: dòng hàng của cả trang nạp trong một câu thay vì một câu cho mỗi đơn")
    void purchaseOrderPageHasNoNPlusOne() throws Exception {
        Statistics stats = statistics();
        stats.clear();

        JsonNode data = list("/api/orders/purchase?keyword=" + TAG + "&size=" + PAGE_SIZE);

        assertThat(data.path("content")).hasSize(PAGE_SIZE);
        for (JsonNode order : data.path("content")) {
            assertThat(order.path("items")).hasSize(2);
            assertThat(order.path("supplierName").asText()).isEqualTo("Nhà cung cấp đo truy vấn");
        }
        // Trước WMS-19.3: 2 câu + 5 câu lấy dòng hàng (một câu cho mỗi đơn)
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }
}
