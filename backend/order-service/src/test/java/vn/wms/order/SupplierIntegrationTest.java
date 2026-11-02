package vn.wms.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.order.dto.CreateSupplierRequest;
import vn.wms.order.dto.RecordSupplierTransactionRequest;
import vn.wms.order.dto.UpdateSupplierRequest;
import vn.wms.order.entity.Supplier;
import vn.wms.order.entity.SupplierTransactionType;
import vn.wms.order.repository.SupplierRepository;
import vn.wms.order.repository.SupplierTransactionRepository;

import java.math.BigDecimal;

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
class SupplierIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SupplierTransactionRepository transactionRepository;

    @Autowired
    private vn.wms.order.service.SupplierService supplierService;

    @Test
    void createSupplierSuccessfully() throws Exception {
        CreateSupplierRequest request = new CreateSupplierRequest(
                "sup-vina",
                "Công ty CP Sữa Việt Nam",
                "0300588569",
                "Nguyễn Văn A",
                "vina@vinamilk.com",
                "02854155555",
                "Quận 7, TP.HCM",
                "1903456789",
                "Techcombank",
                "NET_30",
                new BigDecimal("500000000.00"),
                new BigDecimal("20000000.00")
        );

        mockMvc.perform(post("/api/orders/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SUP-VINA"))
                .andExpect(jsonPath("$.data.name").value("Công ty CP Sữa Việt Nam"))
                .andExpect(jsonPath("$.data.taxCode").value("0300588569"))
                .andExpect(jsonPath("$.data.currentDebt").value(20000000.00))
                .andExpect(jsonPath("$.data.isOverCreditLimit").value(false));

        // Kiểm tra đã tự sinh giao dịch công nợ ban đầu (ADJUSTMENT)
        Supplier supplier = supplierRepository.findByCode("SUP-VINA").orElseThrow();
        assertThat(transactionRepository.findBySupplierIdOrderByIdDesc(supplier.getId(), null).getContent())
                .hasSize(1);
    }

    @Test
    void createSupplierDuplicateCodeReturnsConflict() throws Exception {
        Supplier s = new Supplier();
        s.setCode("SUP-DUP");
        s.setName("Nhà cung cấp 1");
        supplierRepository.save(s);

        CreateSupplierRequest request = new CreateSupplierRequest(
                "sup-dup",
                "Nhà cung cấp trùng mã",
                null, null, null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/orders/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void createSupplierDuplicateTaxCodeReturnsConflict() throws Exception {
        Supplier s = new Supplier();
        s.setCode("SUP-01");
        s.setName("Công ty ABC");
        s.setTaxCode("0102030405");
        supplierRepository.save(s);

        CreateSupplierRequest request = new CreateSupplierRequest(
                "SUP-02",
                "Công ty XYZ",
                "0102030405",
                null, null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/orders/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void updateSupplierSuccessfully() throws Exception {
        Supplier s = new Supplier();
        s.setCode("SUP-UPD");
        s.setName("Tên cũ");
        s.setCreditLimit(new BigDecimal("100000000.00"));
        Supplier saved = supplierRepository.save(s);

        UpdateSupplierRequest updateReq = new UpdateSupplierRequest(
                "Tên mới cập nhật",
                "999888777",
                "Trần Văn B",
                "b@test.com",
                "0987654321",
                "Hà Nội",
                "12345678",
                "Vietcombank",
                "NET_45",
                new BigDecimal("200000000.00"),
                true
        );

        mockMvc.perform(put("/api/orders/suppliers/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Tên mới cập nhật"))
                .andExpect(jsonPath("$.data.taxCode").value("999888777"))
                .andExpect(jsonPath("$.data.contactName").value("Trần Văn B"))
                .andExpect(jsonPath("$.data.creditLimit").value(200000000.00));
    }

    @Test
    void softDeleteSupplierSuccessfully() throws Exception {
        Supplier s = new Supplier();
        s.setCode("SUP-DEL");
        s.setName("Nhà cung cấp cần xóa");
        s.setActive(true);
        Supplier saved = supplierRepository.save(s);

        mockMvc.perform(delete("/api/orders/suppliers/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Supplier afterDel = supplierRepository.findById(saved.getId()).orElseThrow();
        assertThat(afterDel.isActive()).isFalse();

        // Kiểm tra lọc danh sách đang hoạt động (active = true) không chứa nhà cung cấp đã xóa mềm
        mockMvc.perform(get("/api/orders/suppliers").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.code == 'SUP-DEL')]").doesNotExist());
    }

    @Test
    void searchAndFilterSuppliers() throws Exception {
        Supplier s1 = new Supplier();
        s1.setCode("SUP-SEARCH-1");
        s1.setName("Công ty Bao Bì Thăng Long");
        s1.setTaxCode("0109998881");
        supplierRepository.save(s1);

        Supplier s2 = new Supplier();
        s2.setCode("SUP-SEARCH-2");
        s2.setName("Công ty Nhựa Rạng Đông");
        s2.setTaxCode("0109998882");
        supplierRepository.save(s2);

        // Tìm theo tên
        mockMvc.perform(get("/api/orders/suppliers").param("keyword", "Thăng Long"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("SUP-SEARCH-1"));

        // Tìm theo MST
        mockMvc.perform(get("/api/orders/suppliers").param("keyword", "0109998882"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("SUP-SEARCH-2"));
    }

    @Test
    void recordDebtTransactionsAndCalculateBalance() throws Exception {
        Supplier s = new Supplier();
        s.setCode("SUP-FLOW");
        s.setName("Công ty Nguyên Liệu ABC");
        s.setCreditLimit(new BigDecimal("100000000.00"));
        s.setCurrentDebt(BigDecimal.ZERO);
        Supplier saved = supplierRepository.save(s);

        // 1. Nhập hàng theo hóa đơn (+ 80,000,000 nợ)
        RecordSupplierTransactionRequest tx1 = new RecordSupplierTransactionRequest(
                SupplierTransactionType.PURCHASE_INVOICE,
                new BigDecimal("80000000.00"),
                "HD-20261102-01",
                "Hóa đơn nhập đợt 1"
        );
        mockMvc.perform(post("/api/orders/suppliers/" + saved.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tx1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceAfter").value(80000000.00));

        // 2. Thanh toán tiền hàng (- 50,000,000 nợ) -> nợ còn 30,000,000
        RecordSupplierTransactionRequest tx2 = new RecordSupplierTransactionRequest(
                SupplierTransactionType.PAYMENT,
                new BigDecimal("50000000.00"),
                "UNC-00129",
                "Ủy nhiệm chi Vietcombank"
        );
        mockMvc.perform(post("/api/orders/suppliers/" + saved.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tx2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceAfter").value(30000000.00));

        // 3. Trả hàng xuất hóa đơn điều chỉnh giảm (- 5,000,000 nợ) -> nợ còn 25,000,000
        RecordSupplierTransactionRequest tx3 = new RecordSupplierTransactionRequest(
                SupplierTransactionType.RETURN,
                new BigDecimal("5000000.00"),
                "RTN-001",
                "Trả lại 10 thùng hỏng"
        );
        mockMvc.perform(post("/api/orders/suppliers/" + saved.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tx3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceAfter").value(25000000.00));

        // Kiểm tra chi tiết nhà cung cấp nợ hiện tại là 25,000,000
        mockMvc.perform(get("/api/orders/suppliers/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentDebt").value(25000000.00));

        // Kiểm tra danh sách lịch sử giao dịch (có đủ 3 giao dịch)
        mockMvc.perform(get("/api/orders/suppliers/" + saved.getId() + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void supplierDebtSummary() throws Exception {
        Supplier s1 = new Supplier();
        s1.setCode("SUP-SUM-1");
        s1.setName("NCC 1");
        s1.setCreditLimit(new BigDecimal("50000000.00"));
        s1.setCurrentDebt(new BigDecimal("70000000.00")); // Vượt hạn mức
        s1.setActive(true);
        supplierRepository.save(s1);

        Supplier s2 = new Supplier();
        s2.setCode("SUP-SUM-2");
        s2.setName("NCC 2");
        s2.setCreditLimit(new BigDecimal("100000000.00"));
        s2.setCurrentDebt(new BigDecimal("30000000.00")); // Trong hạn mức
        s2.setActive(true);
        supplierRepository.save(s2);

        mockMvc.perform(get("/api/orders/suppliers/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overLimitSuppliers").value(1))
                .andExpect(jsonPath("$.data.totalDebt").value(100000000.00));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void recordTransactionsConcurrentlyMaintainsConsistentDebt() throws Exception {
        Supplier s = new Supplier();
        s.setCode("SUP-CONCUR");
        s.setName("Công ty Đồng thời");
        s.setCurrentDebt(BigDecimal.ZERO);
        Supplier saved = supplierRepository.saveAndFlush(s);

        int threadCount = 4;
        BigDecimal amountPerThread = new BigDecimal("1000000.00");
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        supplierService.recordTransaction(saved.getId(), new RecordSupplierTransactionRequest(
                                SupplierTransactionType.PURCHASE_INVOICE,
                                amountPerThread,
                                "REF-" + index,
                                "Giao dịch đồng thời " + index
                        ));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();

            Supplier reloaded = supplierRepository.findById(saved.getId()).orElseThrow();
            // 4 luồng mỗi luồng tăng 1,000,000 -> Tổng nợ chính xác là 4,000,000 nhờ PESSIMISTIC_WRITE
            assertThat(reloaded.getCurrentDebt()).isEqualByComparingTo(new BigDecimal("4000000.00"));
        } finally {
            transactionRepository.deleteAll();
            supplierRepository.delete(saved);
        }
    }
}
