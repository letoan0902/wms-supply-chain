package vn.wms.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.order.dto.CreateCustomerRequest;
import vn.wms.order.dto.RecordCustomerTransactionRequest;
import vn.wms.order.dto.UpdateCustomerRequest;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.CustomerTransactionType;
import vn.wms.order.entity.CustomerType;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.CustomerTransactionRepository;
import vn.wms.order.service.CustomerService;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerTransactionRepository transactionRepository;

    @Autowired
    private CustomerService customerService;

    @Test
    void createCustomerSuccessfully() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "cus-dl-01",
                "Đại lý Bánh kẹo Miền Bắc",
                CustomerType.WHOLESALE,
                "0108899771",
                "Nguyễn Văn Bình",
                "binh@dailybanhkeo.vn",
                "0912345678",
                "Số 12 Cầu Giấy, Hà Nội",
                "NET_30",
                new BigDecimal("200000000.00"),
                new BigDecimal("15000000.00"),
                "Đại lý cấp 1 phân phối độc quyền"
        );

        mockMvc.perform(post("/api/orders/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("CUS-DL-01"))
                .andExpect(jsonPath("$.data.name").value("Đại lý Bánh kẹo Miền Bắc"))
                .andExpect(jsonPath("$.data.customerType").value("WHOLESALE"))
                .andExpect(jsonPath("$.data.taxCode").value("0108899771"))
                .andExpect(jsonPath("$.data.creditLimit").value(200000000.00))
                .andExpect(jsonPath("$.data.currentDebt").value(15000000.00))
                .andExpect(jsonPath("$.data.isOverCreditLimit").value(false))
                .andExpect(jsonPath("$.data.debtLocked").value(false));

        // Kiểm tra đã tự sinh giao dịch công nợ ban đầu (ADJUSTMENT)
        Customer customer = customerRepository.findByCode("CUS-DL-01").orElseThrow();
        assertThat(transactionRepository.findByCustomerIdOrderByIdDesc(customer.getId(), null).getContent())
                .hasSize(1);
    }

    @Test
    void createCustomerDuplicateCodeReturnsConflict() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-DUP");
        c.setName("Khách hàng A");
        customerRepository.save(c);

        CreateCustomerRequest request = new CreateCustomerRequest(
                "cus-dup",
                "Khách hàng trùng mã",
                CustomerType.RETAIL,
                null, null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/orders/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void createCustomerDuplicateTaxCodeReturnsConflict() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-01");
        c.setName("Công ty Alpha");
        c.setTaxCode("0101112223");
        customerRepository.save(c);

        CreateCustomerRequest request = new CreateCustomerRequest(
                "CUS-02",
                "Công ty Beta",
                CustomerType.WHOLESALE,
                "0101112223",
                null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/orders/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void updateCustomerSuccessfully() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-UPD");
        c.setName("Khách lẻ cũ");
        c.setCustomerType(CustomerType.RETAIL);
        c.setCreditLimit(BigDecimal.ZERO);
        Customer saved = customerRepository.save(c);

        UpdateCustomerRequest updateReq = new UpdateCustomerRequest(
                "Đại lý nâng cấp mới",
                CustomerType.WHOLESALE,
                "0309991112",
                "Trần Thị Mai",
                "mai@daily.vn",
                "0987111222",
                "Quận 1, TP.HCM",
                "NET_15",
                new BigDecimal("50000000.00"),
                false,
                "Nâng cấp lên đại lý sỉ",
                true
        );

        mockMvc.perform(put("/api/orders/customers/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Đại lý nâng cấp mới"))
                .andExpect(jsonPath("$.data.customerType").value("WHOLESALE"))
                .andExpect(jsonPath("$.data.taxCode").value("0309991112"))
                .andExpect(jsonPath("$.data.creditLimit").value(50000000.00));
    }

    @Test
    void softDeleteCustomerSuccessfully() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-DEL");
        c.setName("Khách hàng cần dừng hợp tác");
        c.setActive(true);
        Customer saved = customerRepository.save(c);

        mockMvc.perform(delete("/api/orders/customers/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Customer afterDel = customerRepository.findById(saved.getId()).orElseThrow();
        assertThat(afterDel.isActive()).isFalse();

        // Lọc danh sách active = true không chứa khách hàng đã bị xóa mềm
        mockMvc.perform(get("/api/orders/customers").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.code == 'CUS-DEL')]").doesNotExist());
    }

    @Test
    void searchAndFilterCustomers() throws Exception {
        Customer c1 = new Customer();
        c1.setCode("CUS-SEARCH-1");
        c1.setName("Siêu thị Co.opmart Cống Quỳnh");
        c1.setCustomerType(CustomerType.WHOLESALE);
        c1.setTaxCode("0300123456");
        customerRepository.save(c1);

        Customer c2 = new Customer();
        c2.setCode("CUS-SEARCH-2");
        c2.setName("Cửa hàng Tạp hóa Lan Anh");
        c2.setCustomerType(CustomerType.RETAIL);
        c2.setTaxCode("0300987654");
        customerRepository.save(c2);

        // Tìm theo tên
        mockMvc.perform(get("/api/orders/customers").param("keyword", "Co.opmart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("CUS-SEARCH-1"));

        // Lọc theo CustomerType = RETAIL
        mockMvc.perform(get("/api/orders/customers").param("type", "RETAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.code == 'CUS-SEARCH-2')]").exists());
    }

    @Test
    void creditCheckAllowedWhenUnderLimit() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-CREDIT-OK");
        c.setName("Đại lý Thành Công");
        c.setCreditLimit(new BigDecimal("100000000.00"));
        c.setCurrentDebt(new BigDecimal("20000000.00"));
        c.setActive(true);
        c.setDebtLocked(false);
        Customer saved = customerRepository.save(c);

        // Đơn hàng 10,000,000 -> Tổng nợ dự kiến 30,000,000 <= 100,000,000
        mockMvc.perform(get("/api/orders/customers/" + saved.getId() + "/credit-check")
                        .param("orderAmount", "10000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ALLOWED"))
                .andExpect(jsonPath("$.data.canProceed").value(true))
                .andExpect(jsonPath("$.data.availableCredit").value(80000000.00))
                .andExpect(jsonPath("$.data.projectedDebt").value(30000000.00));
    }

    @Test
    void creditCheckWarningWhenNearLimit() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-CREDIT-WARN");
        c.setName("Đại lý Cảnh Báo Nợ");
        c.setCreditLimit(new BigDecimal("100000000.00"));
        c.setCurrentDebt(new BigDecimal("85000000.00")); // 85%
        c.setActive(true);
        c.setDebtLocked(false);
        Customer saved = customerRepository.save(c);

        // Thêm đơn 10,000,000 -> 95,000,000 >= 90% (90M) nhưng <= 100M -> WARNING
        mockMvc.perform(get("/api/orders/customers/" + saved.getId() + "/credit-check")
                        .param("orderAmount", "10000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WARNING_NEAR_LIMIT"))
                .andExpect(jsonPath("$.data.canProceed").value(true));
    }

    @Test
    void creditCheckBlockedWhenOverLimit() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-CREDIT-OVER");
        c.setName("Đại lý Vượt Hạn Mức");
        c.setCreditLimit(new BigDecimal("50000000.00"));
        c.setCurrentDebt(new BigDecimal("40000000.00"));
        c.setActive(true);
        c.setDebtLocked(false);
        Customer saved = customerRepository.save(c);

        // Thêm đơn 20,000,000 -> 60,000,000 > 50,000,000 -> BLOCKED_OVER_LIMIT
        mockMvc.perform(get("/api/orders/customers/" + saved.getId() + "/credit-check")
                        .param("orderAmount", "20000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED_OVER_LIMIT"))
                .andExpect(jsonPath("$.data.canProceed").value(false));
    }

    @Test
    void creditCheckBlockedWhenDebtLocked() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-CREDIT-LOCKED");
        c.setName("Đại lý Bị Khóa Nợ");
        c.setCreditLimit(new BigDecimal("200000000.00"));
        c.setCurrentDebt(new BigDecimal("10000000.00"));
        c.setActive(true);
        c.setDebtLocked(true); // Cờ khóa nợ chủ động do quá hạn
        Customer saved = customerRepository.save(c);

        mockMvc.perform(get("/api/orders/customers/" + saved.getId() + "/credit-check")
                        .param("orderAmount", "5000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED_DEBT_LOCKED"))
                .andExpect(jsonPath("$.data.canProceed").value(false));
    }

    @Test
    void recordCustomerTransactionsAndCalculateBalance() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-FLOW-TX");
        c.setName("Đại lý Giao Dịch Thường Xuyên");
        c.setCreditLimit(new BigDecimal("100000000.00"));
        c.setCurrentDebt(BigDecimal.ZERO);
        Customer saved = customerRepository.save(c);

        // 1. Xuất đơn bán hàng ghi nợ (+ 50,000,000)
        RecordCustomerTransactionRequest tx1 = new RecordCustomerTransactionRequest(
                CustomerTransactionType.SALES_INVOICE,
                new BigDecimal("50000000.00"),
                "SO-20261103-01",
                "Xuất kho đơn hàng bán sỉ số 1"
        );
        mockMvc.perform(post("/api/orders/customers/" + saved.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tx1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceAfter").value(50000000.00));

        // 2. Khách chuyển khoản thanh toán (- 30,000,000) -> nợ còn 20,000,000
        RecordCustomerTransactionRequest tx2 = new RecordCustomerTransactionRequest(
                CustomerTransactionType.PAYMENT,
                new BigDecimal("30000000.00"),
                "PAY-00889",
                "Thanh toán chuyển khoản ngân hàng BIDV"
        );
        mockMvc.perform(post("/api/orders/customers/" + saved.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tx2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceAfter").value(20000000.00));

        // 3. Khách trả hàng hoàn tiền cấn trừ công nợ (- 5,000,000) -> nợ còn 15,000,000
        RecordCustomerTransactionRequest tx3 = new RecordCustomerTransactionRequest(
                CustomerTransactionType.RETURN,
                new BigDecimal("5000000.00"),
                "RET-SO-01",
                "Nhập trả 5 thùng bao bì lỗi"
        );
        mockMvc.perform(post("/api/orders/customers/" + saved.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tx3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceAfter").value(15000000.00));

        // Kiểm tra chi tiết khách hàng có số dư nợ hiện tại là 15,000,000
        mockMvc.perform(get("/api/orders/customers/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentDebt").value(15000000.00));

        // Kiểm tra danh sách lịch sử giao dịch (3 giao dịch)
        mockMvc.perform(get("/api/orders/customers/" + saved.getId() + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void customerDebtSummary() throws Exception {
        Customer c1 = new Customer();
        c1.setCode("CUS-SUM-1");
        c1.setName("Đại lý 1");
        c1.setCreditLimit(new BigDecimal("40000000.00"));
        c1.setCurrentDebt(new BigDecimal("60000000.00")); // Vượt hạn mức
        c1.setActive(true);
        customerRepository.save(c1);

        Customer c2 = new Customer();
        c2.setCode("CUS-SUM-2");
        c2.setName("Đại lý 2");
        c2.setCreditLimit(new BigDecimal("100000000.00"));
        c2.setCurrentDebt(new BigDecimal("30000000.00")); // Trong hạn mức
        c2.setActive(true);
        customerRepository.save(c2);

        mockMvc.perform(get("/api/orders/customers/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overLimitCustomers").value(1))
                .andExpect(jsonPath("$.data.totalDebt").value(90000000.00));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void recordTransactionsConcurrentlyMaintainsConsistentDebt() throws Exception {
        Customer c = new Customer();
        c.setCode("CUS-CONCUR");
        c.setName("Khách Hàng Đồng Thời");
        c.setCurrentDebt(BigDecimal.ZERO);
        Customer saved = customerRepository.saveAndFlush(c);

        int threadCount = 4;
        BigDecimal amountPerThread = new BigDecimal("1000000.00");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        customerService.recordTransaction(saved.getId(), new RecordCustomerTransactionRequest(
                                CustomerTransactionType.SALES_INVOICE,
                                amountPerThread,
                                "SO-CONCUR-" + index,
                                "Giao dịch bán hàng đồng thời " + index
                        ));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            Customer reloaded = customerRepository.findById(saved.getId()).orElseThrow();
            // 4 luồng ghi nhận SALES_INVOICE 1,000,000 -> Tổng nợ chính xác là 4,000,000 nhờ PESSIMISTIC_WRITE
            assertThat(reloaded.getCurrentDebt()).isEqualByComparingTo(new BigDecimal("4000000.00"));
        } finally {
            transactionRepository.deleteAll();
            customerRepository.delete(saved);
        }
    }
}
