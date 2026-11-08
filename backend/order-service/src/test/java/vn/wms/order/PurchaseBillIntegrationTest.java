package vn.wms.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.order.dto.CreateGrnItemRequest;
import vn.wms.order.dto.CreateGrnRequest;
import vn.wms.order.dto.RecordBillPaymentRequest;
import vn.wms.order.entity.GoodsReceiptNote;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.PurchaseBill;
import vn.wms.order.entity.PurchaseBillStatus;
import vn.wms.order.entity.PurchaseOrder;
import vn.wms.order.entity.PurchaseOrderItem;
import vn.wms.order.entity.Supplier;
import vn.wms.order.entity.SupplierTransaction;
import vn.wms.order.entity.SupplierTransactionType;
import vn.wms.order.repository.PurchaseBillRepository;
import vn.wms.order.repository.PurchaseOrderRepository;
import vn.wms.order.repository.SupplierRepository;
import vn.wms.order.repository.SupplierTransactionRepository;
import vn.wms.order.service.GoodsReceiptService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseBillIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseBillRepository purchaseBillRepository;

    @Autowired
    private SupplierTransactionRepository transactionRepository;

    @Autowired
    private GoodsReceiptService goodsReceiptService;

    private Supplier createSupplier(String code, String name, BigDecimal initialDebt) {
        Supplier s = new Supplier();
        s.setCode(code);
        s.setName(name);
        s.setCreditLimit(new BigDecimal("100000000.00"));
        s.setCurrentDebt(initialDebt);
        s.setPaymentTerms("30 ngày kể từ ngày nhận hàng");
        s.setActive(true);
        return supplierRepository.save(s);
    }

    private PurchaseOrder createApprovedPo(Supplier supplier, BigDecimal qty, BigDecimal unitPrice) {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber("PO-TEST-" + System.currentTimeMillis());
        po.setSupplier(supplier);
        po.setWarehouseId(1L);
        po.setWarehouseName("Kho Tổng Hà Nội");
        po.setOrderDate(LocalDate.now());
        po.setExpectedDate(LocalDate.now().plusDays(7));
        po.setStatus(OrderStatus.APPROVED);
        po.setTotalAmount(qty.multiply(unitPrice));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(po);
        item.setProductId(200L);
        item.setSkuCode("SKU-MAT-01");
        item.setProductName("Nguyên liệu A");
        item.setUom("kg");
        item.setQuantity(qty);
        item.setReceivedQuantity(BigDecimal.ZERO);
        item.setUnitPrice(unitPrice);
        item.setLineTotal(qty.multiply(unitPrice));
        po.addItem(item);

        return purchaseOrderRepository.save(po);
    }

    @Test
    @DisplayName("Khi lập GRN thành công: Tự động sinh Hóa đơn mua (Purchase Bill) liên kết GRN và cập nhật tăng công nợ NCC (FR11, FR13)")
    void testCreateGrn_AutomaticallyCreatesPurchaseBillAndIncreasesDebt() {
        Supplier supplier = createSupplier("NCC-PB-01", "Công ty Hóa Chất Hà Nội", BigDecimal.ZERO);
        PurchaseOrder po = createApprovedPo(supplier, new BigDecimal("100.000"), new BigDecimal("50000.00")); // 5,000,000 VNĐ

        PurchaseOrderItem poItem = po.getItems().get(0);

        CreateGrnRequest request = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Nhập toàn bộ đơn nguyên liệu",
                List.of(new CreateGrnItemRequest(
                        poItem.getId(),
                        new BigDecimal("100.000"),
                        BigDecimal.ZERO,
                        null,
                        "LOT-CHEM-01",
                        LocalDate.now().minusDays(5),
                        LocalDate.now().plusMonths(12),
                        10L,
                        "WH-HN-A01",
                        "Đạt chuẩn"
                ))
        );

        goodsReceiptService.create(request, "admin");

        // 1. Kiểm tra Purchase Bill tự động tạo liên kết GRN
        List<PurchaseBill> bills = purchaseBillRepository.findByPurchaseOrderIdOrderByIdDesc(po.getId());
        assertThat(bills).hasSize(1);
        PurchaseBill bill = bills.get(0);
        assertThat(bill.getBillNumber()).startsWith("PB-");
        assertThat(bill.getPoNumber()).isEqualTo(po.getPoNumber());
        assertThat(bill.getTotalAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
        assertThat(bill.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getRemainingAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
        assertThat(bill.getStatus()).isEqualTo(PurchaseBillStatus.UNPAID);
        assertThat(bill.getItems()).hasSize(1);
        assertThat(bill.getItems().get(0).getSkuCode()).isEqualTo("SKU-MAT-01");
        assertThat(bill.getItems().get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("100.000"));

        // 2. Kiểm tra công nợ Nhà cung cấp được cập nhật tăng 5,000,000 VNĐ
        Supplier updatedSupplier = supplierRepository.findById(supplier.getId()).orElseThrow();
        assertThat(updatedSupplier.getCurrentDebt()).isEqualByComparingTo(new BigDecimal("5000000.00"));

        // 3. Kiểm tra lịch sử giao dịch công nợ loại PURCHASE_INVOICE
        List<SupplierTransaction> txs = transactionRepository.findBySupplierIdOrderByIdDesc(supplier.getId());
        assertThat(txs).isNotEmpty();
        SupplierTransaction invoiceTx = txs.get(0);
        assertThat(invoiceTx.getTransactionType()).isEqualTo(SupplierTransactionType.PURCHASE_INVOICE);
        assertThat(invoiceTx.getAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
        assertThat(invoiceTx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("5000000.00"));
    }

    @Test
    @DisplayName("Ghi nhận thanh toán từng phần & toàn phần cho Hóa đơn mua: Chuyển trạng thái PAID khi trả đủ và giảm công nợ NCC (FR13)")
    void testRecordBillPayment_TransitionsStatusAndReducesDebt() throws Exception {
        Supplier supplier = createSupplier("NCC-PB-02", "Công ty Bao Bì Nam Định", BigDecimal.ZERO);
        PurchaseOrder po = createApprovedPo(supplier, new BigDecimal("200.000"), new BigDecimal("10000.00")); // 2,000,000 VNĐ

        PurchaseOrderItem poItem = po.getItems().get(0);
        CreateGrnRequest grnReq = new CreateGrnRequest(
                po.getId(), LocalDate.now(), null,
                List.of(new CreateGrnItemRequest(
                        poItem.getId(), new BigDecimal("200.000"), BigDecimal.ZERO, null,
                        null, null, null, null, null, null
                ))
        );
        goodsReceiptService.create(grnReq, "admin");

        PurchaseBill bill = purchaseBillRepository.findByPurchaseOrderIdOrderByIdDesc(po.getId()).get(0);
        assertThat(bill.getStatus()).isEqualTo(PurchaseBillStatus.UNPAID);

        // Thanh toán đợt 1: 800,000 VNĐ -> Trạng thái PARTIALLY_PAID
        RecordBillPaymentRequest payReq1 = new RecordBillPaymentRequest(
                new BigDecimal("800000.00"),
                "BANK_TRANSFER",
                "UNC-TEST-001",
                "Thanh toán đợt 1 qua ngân hàng",
                LocalDate.now()
        );

        mockMvc.perform(post("/api/orders/purchase-bills/" + bill.getId() + "/payments")
                        .header("X-User-Roles", "ACCOUNTANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount", is(800000.0)))
                .andExpect(jsonPath("$.data.remainingAmount", is(1200000.0)))
                .andExpect(jsonPath("$.data.status", is("PARTIALLY_PAID")));

        // Công nợ NCC giảm còn 1,200,000 VNĐ
        Supplier sAfter1 = supplierRepository.findById(supplier.getId()).orElseThrow();
        assertThat(sAfter1.getCurrentDebt()).isEqualByComparingTo(new BigDecimal("1200000.00"));

        // Thanh toán đợt 2: trả nốt 1,200,000 VNĐ -> Trạng thái PAID
        RecordBillPaymentRequest payReq2 = new RecordBillPaymentRequest(
                new BigDecimal("1200000.00"),
                "BANK_TRANSFER",
                "UNC-TEST-002",
                "Thanh toán tất toán đợt 2",
                LocalDate.now()
        );

        mockMvc.perform(post("/api/orders/purchase-bills/" + bill.getId() + "/payments")
                        .header("X-User-Roles", "ACCOUNTANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount", is(2000000.0)))
                .andExpect(jsonPath("$.data.remainingAmount", is(0.0)))
                .andExpect(jsonPath("$.data.status", is("PAID")));

        // Công nợ NCC giảm về 0 VNĐ
        Supplier sAfter2 = supplierRepository.findById(supplier.getId()).orElseThrow();
        assertThat(sAfter2.getCurrentDebt()).isEqualByComparingTo(BigDecimal.ZERO);

        // Cố thanh toán thêm khi đã PAID -> Bị từ chối
        mockMvc.perform(post("/api/orders/purchase-bills/" + bill.getId() + "/payments")
                        .header("X-User-Roles", "ACCOUNTANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Từ chối thanh toán số tiền lớn hơn số tiền còn nợ của hóa đơn mua")
    void testRecordPayment_ExceedingRemaining_RejectsBadRequest() throws Exception {
        Supplier supplier = createSupplier("NCC-PB-03", "Công ty Sắt Thép An Khánh", BigDecimal.ZERO);
        PurchaseOrder po = createApprovedPo(supplier, new BigDecimal("10.000"), new BigDecimal("100000.00")); // 1,000,000 VNĐ

        goodsReceiptService.create(new CreateGrnRequest(
                po.getId(), LocalDate.now(), null,
                List.of(new CreateGrnItemRequest(
                        po.getItems().get(0).getId(), new BigDecimal("10.000"), BigDecimal.ZERO, null,
                        null, null, null, null, null, null
                ))
        ), "admin");

        PurchaseBill bill = purchaseBillRepository.findByPurchaseOrderIdOrderByIdDesc(po.getId()).get(0);

        RecordBillPaymentRequest overPayment = new RecordBillPaymentRequest(
                new BigDecimal("1500000.00"), // Nợ 1 triệu nhưng trả 1.5 triệu
                "CASH", null, "Trả thừa", LocalDate.now()
        );

        mockMvc.perform(post("/api/orders/purchase-bills/" + bill.getId() + "/payments")
                        .header("X-User-Roles", "ACCOUNTANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overPayment)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tra cứu danh sách và thẻ thống kê tổng quan Hóa đơn mua hàng (/summary)")
    void testGetSummaryAndList() throws Exception {
        Supplier supplier = createSupplier("NCC-PB-04", "Công ty Thiết Bị Quang Minh", BigDecimal.ZERO);
        PurchaseOrder po = createApprovedPo(supplier, new BigDecimal("50.000"), new BigDecimal("20000.00")); // 1,000,000 VNĐ

        goodsReceiptService.create(new CreateGrnRequest(
                po.getId(), LocalDate.now(), null,
                List.of(new CreateGrnItemRequest(
                        po.getItems().get(0).getId(), new BigDecimal("50.000"), BigDecimal.ZERO, null,
                        null, null, null, null, null, null
                ))
        ), "admin");

        mockMvc.perform(get("/api/orders/purchase-bills/summary")
                        .header("X-User-Roles", "PURCHASING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBills").isNumber())
                .andExpect(jsonPath("$.data.unpaidBills").isNumber())
                .andExpect(jsonPath("$.data.totalPayablesAmount").isNumber());

        mockMvc.perform(get("/api/orders/purchase-bills")
                        .header("X-User-Roles", "PURCHASING")
                        .param("status", "UNPAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
