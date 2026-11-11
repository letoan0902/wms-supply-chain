package vn.wms.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.client.AtpResponse;
import vn.wms.order.client.InventoryGateway;
import vn.wms.order.client.WarehouseSummary;
import vn.wms.order.dto.CreateCustomerRequest;
import vn.wms.order.dto.CreateGdnItemRequest;
import vn.wms.order.dto.CreateGdnRequest;
import vn.wms.order.dto.CreateGrnItemRequest;
import vn.wms.order.dto.CreateGrnRequest;
import vn.wms.order.dto.CreatePurchaseOrderRequest;
import vn.wms.order.dto.CreateSalesOrderRequest;
import vn.wms.order.dto.CreateSupplierRequest;
import vn.wms.order.dto.CustomerResponse;
import vn.wms.order.dto.GdnResponse;
import vn.wms.order.dto.GrnResponse;
import vn.wms.order.dto.PurchaseOrderItemRequest;
import vn.wms.order.dto.PurchaseOrderResponse;
import vn.wms.order.dto.RecordBillPaymentRequest;
import vn.wms.order.dto.RecordSupplierTransactionRequest;
import vn.wms.order.dto.SalesOrderItemRequest;
import vn.wms.order.dto.SalesOrderResponse;
import vn.wms.order.dto.SupplierResponse;
import vn.wms.order.dto.UpdateCustomerRequest;
import vn.wms.order.dto.UpdateSupplierRequest;
import vn.wms.order.entity.CustomerType;
import vn.wms.order.entity.GoodsDeliveryStatus;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.PurchaseBill;
import vn.wms.order.entity.SupplierTransactionType;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.CustomerTransactionRepository;
import vn.wms.order.repository.DocumentSequenceRepository;
import vn.wms.order.repository.GoodsDeliveryNoteRepository;
import vn.wms.order.repository.GoodsReceiptNoteRepository;
import vn.wms.order.repository.PurchaseBillRepository;
import vn.wms.order.repository.PurchaseOrderRepository;
import vn.wms.order.repository.SalesOrderRepository;
import vn.wms.order.repository.SupplierRepository;
import vn.wms.order.repository.SupplierTransactionRepository;
import vn.wms.order.service.CustomerService;
import vn.wms.order.service.GoodsDeliveryService;
import vn.wms.order.service.GoodsReceiptService;
import vn.wms.order.service.PartnerBulkImportService;
import vn.wms.order.service.PurchaseOrderService;
import vn.wms.order.service.SalesOrderService;
import vn.wms.order.service.SupplierService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra cách ly dữ liệu giữa hai doanh nghiệp trong order-service (WMS-22.4b, TASK-137).
 *
 * <p>Lớp test KHÔNG đặt {@code @Transactional}: mỗi lời gọi service, repository hay MockMvc mở phiên làm việc riêng
 * như request thật, nên Hibernate đọc đúng doanh nghiệp của từng lời gọi. Hai doanh nghiệp được tạo bộ dữ liệu giống
 * hệt nhau (cùng mã đối tác, cùng mã số thuế) qua đúng các service nghiệp vụ, để kiểm tra luôn việc sinh số chứng từ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderTenantIsolationTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;
    private static final LocalDate DOC_DATE = LocalDate.of(2026, 11, 10);
    private static final String TODAY = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private SupplierRepository supplierRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SupplierTransactionRepository supplierTransactionRepository;
    @Autowired private CustomerTransactionRepository customerTransactionRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private SalesOrderRepository salesOrderRepository;
    @Autowired private GoodsReceiptNoteRepository goodsReceiptNoteRepository;
    @Autowired private GoodsDeliveryNoteRepository goodsDeliveryNoteRepository;
    @Autowired private PurchaseBillRepository purchaseBillRepository;
    @Autowired private DocumentSequenceRepository documentSequenceRepository;

    @Autowired private SupplierService supplierService;
    @Autowired private CustomerService customerService;
    @Autowired private PurchaseOrderService purchaseOrderService;
    @Autowired private SalesOrderService salesOrderService;
    @Autowired private GoodsReceiptService goodsReceiptService;
    @Autowired private GoodsDeliveryService goodsDeliveryService;
    @Autowired private PartnerBulkImportService bulkImportService;

    @MockBean private InventoryGateway inventoryGateway;
    @MockBean private WmsEventPublisher wmsEventPublisher;

    /** Mỗi sự kiện phát ra được ghi lại dạng "routingKey@doanhNghiệp" lấy từ TenantContext lúc phát. */
    private final List<String> publishedEvents = Collections.synchronizedList(new ArrayList<>());

    private CompanyData dataOfA;
    private CompanyData dataOfB;

    private record CompanyData(Long supplierId, Long customerId, Long poId, String poNumber, Long grnId,
                               String grnNumber, Long billId, String billNumber, Long soId, String soNumber,
                               Long gdnId, String gdnNumber) {
    }

    @BeforeEach
    void setUp() {
        when(inventoryGateway.requireActiveWarehouse(anyLong()))
                .thenAnswer(inv -> new WarehouseSummary(inv.getArgument(0), "WH01", "Kho tổng", "Hà Nội", true));
        when(inventoryGateway.getAtp(any(), any())).thenAnswer(inv -> new AtpResponse(
                inv.getArgument(0), inv.getArgument(1), new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1000")));
        when(wmsEventPublisher.publish(anyString(), any())).thenAnswer(inv -> {
            publishedEvents.add(inv.getArgument(0) + "@" + TenantContext.getCompanyId());
            return "message-id";
        });
        doAnswer(inv -> {
            publishedEvents.add(inv.getArgument(0) + "@" + TenantContext.getCompanyId());
            return null;
        }).when(wmsEventPublisher).publishAfterCommit(anyString(), any());

        cleanUpAllData();
        dataOfA = TenantContext.callAs(COMPANY_A, () -> createDocuments("A"));
        dataOfB = TenantContext.callAs(COMPANY_B, () -> createDocuments("B"));
    }

    @AfterEach
    void tearDown() {
        cleanUpAllData();
    }

    /** Tạo cho doanh nghiệp hiện tại: nhà cung cấp, khách hàng, PO đã duyệt, GRN (kèm hóa đơn), SO đã xác nhận, GDN. */
    private CompanyData createDocuments(String label) {
        SupplierResponse supplier = supplierService.create(supplierRequest("NCC-01", "Nhà cung cấp của " + label, "0101010101"));
        CustomerResponse customer = customerService.create(customerRequest("KH-01", "Khách hàng của " + label, "0202020202"));

        PurchaseOrderResponse po = purchaseOrderService.create(new CreatePurchaseOrderRequest(
                null, supplier.id(), 11L, "Kho tổng", DOC_DATE, null, null, OrderStatus.PENDING,
                List.of(new PurchaseOrderItemRequest(100L, "SKU-01", "Hàng mẫu", "Hộp",
                        new BigDecimal("10"), new BigDecimal("1000"), null, null, null, null, null))));
        purchaseOrderService.approve(po.id(), "truongkho");

        GrnResponse grn = goodsReceiptService.create(new CreateGrnRequest(po.id(), DOC_DATE, null,
                List.of(new CreateGrnItemRequest(po.items().get(0).id(), new BigDecimal("10"), BigDecimal.ZERO,
                        null, "LOT-01", null, null, null, null, null))), "thukho");
        PurchaseBill bill = purchaseBillRepository.findByGoodsReceiptNoteId(grn.id()).orElseThrow();

        SalesOrderResponse so = salesOrderService.create(new CreateSalesOrderRequest(
                null, customer.id(), 11L, null, "Địa chỉ giao", "Người nhận", "0900000000", null, null, null,
                OrderStatus.CONFIRMED, false,
                List.of(new SalesOrderItemRequest(100L, "SKU-01", "Hàng mẫu", "Hộp",
                        new BigDecimal("4"), new BigDecimal("2000"), null, null, null, null, null))), "banhang");

        GdnResponse gdn = goodsDeliveryService.create(new CreateGdnRequest(so.id(), DOC_DATE, GoodsDeliveryStatus.PICKING,
                "Người nhận", "0900000000", "Địa chỉ giao", null,
                List.of(new CreateGdnItemRequest(so.items().get(0).id(), 100L, "SKU-01", "Hàng mẫu", "Hộp",
                        new BigDecimal("4"), new BigDecimal("2000"), null, null, null, null))), "thukho");

        return new CompanyData(supplier.id(), customer.id(), po.id(), po.poNumber(), grn.id(), grn.grnNumber(),
                bill.getId(), bill.getBillNumber(), so.id(), so.soNumber(), gdn.id(), gdn.gdnNumber());
    }

    private void cleanUpAllData() {
        for (Long companyId : List.of(COMPANY_A, COMPANY_B)) {
            TenantContext.runAs(companyId, () -> {
                purchaseBillRepository.deleteAll();
                goodsReceiptNoteRepository.deleteAll();
                goodsDeliveryNoteRepository.deleteAll();
                supplierTransactionRepository.deleteAll();
                customerTransactionRepository.deleteAll();
                salesOrderRepository.deleteAll();
                purchaseOrderRepository.deleteAll();
                supplierRepository.deleteAll();
                customerRepository.deleteAll();
                documentSequenceRepository.deleteAll();
            });
        }
        publishedEvents.clear();
    }

    @Test
    @DisplayName("Hai doanh nghiệp dùng trùng mã đối tác, mã số thuế và nhận cùng số chứng từ; trong một doanh nghiệp vẫn chặn trùng")
    void sameCodesTaxCodesAndDocumentNumbersAllowedAcrossCompanies() {
        assertThat(dataOfA.poNumber()).isEqualTo("PO-20261110-0001").isEqualTo(dataOfB.poNumber());
        assertThat(dataOfA.grnNumber()).isEqualTo("GRN-20261110-0001").isEqualTo(dataOfB.grnNumber());
        assertThat(dataOfA.billNumber()).isEqualTo("PB-20261110-0001").isEqualTo(dataOfB.billNumber());
        assertThat(dataOfA.soNumber()).isEqualTo("SO-" + TODAY + "-0001").isEqualTo(dataOfB.soNumber());
        assertThat(dataOfA.gdnNumber()).isEqualTo("GDN-" + TODAY + "-0001").isEqualTo(dataOfB.gdnNumber());
        assertThat(dataOfA.supplierId()).isNotEqualTo(dataOfB.supplierId());

        // Số tiếp theo của A là 0002, không bị ảnh hưởng bởi số đã cấp cho B
        assertThat(TenantContext.callAs(COMPANY_A, () -> purchaseOrderService.generatePoNumber(DOC_DATE)))
                .isEqualTo("PO-20261110-0002");

        // Trong cùng doanh nghiệp: trùng mã, trùng mã số thuế đều bị từ chối
        assertConflict(() -> TenantContext.runAs(COMPANY_A,
                () -> supplierService.create(supplierRequest("NCC-01", "Trùng mã", null))));
        assertConflict(() -> TenantContext.runAs(COMPANY_A,
                () -> supplierService.create(supplierRequest("NCC-02", "Trùng mã số thuế", "0101010101"))));
        assertConflict(() -> TenantContext.runAs(COMPANY_B,
                () -> customerService.create(customerRequest("KH-01", "Trùng mã", null))));
        assertConflict(() -> TenantContext.runAs(COMPANY_B,
                () -> customerService.create(customerRequest("KH-02", "Trùng mã số thuế", "0202020202"))));
    }

    @Test
    @DisplayName("B không đọc, sửa, xóa được nhà cung cấp và khách hàng của A")
    void companyBCannotTouchPartnersOfCompanyA() throws Exception {
        mockMvc.perform(get("/api/orders/suppliers").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(dataOfB.supplierId().intValue())));
        mockMvc.perform(get("/api/orders/suppliers/code/NCC-01").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(dataOfB.supplierId().intValue())));
        mockMvc.perform(get("/api/orders/suppliers/" + dataOfA.supplierId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/orders/suppliers/" + dataOfA.supplierId()).header("X-Company-Id", COMPANY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateSupplierRequest("Bị B đổi tên", null, null, null, null, null,
                                null, null, null, null, false))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/orders/suppliers/" + dataOfA.supplierId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/suppliers/" + dataOfA.supplierId() + "/transactions")
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/suppliers/" + dataOfA.supplierId() + "/transactions")
                        .header("X-Company-Id", COMPANY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RecordSupplierTransactionRequest(SupplierTransactionType.PAYMENT,
                                new BigDecimal("1000"), "PC-01", null))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders/customers").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(dataOfB.customerId().intValue())));
        mockMvc.perform(get("/api/orders/customers/" + dataOfA.customerId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/orders/customers/" + dataOfA.customerId()).header("X-Company-Id", COMPANY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateCustomerRequest("Bị B đổi tên", CustomerType.RETAIL, null, null,
                                null, null, null, null, null, true, null, false))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/orders/customers/" + dataOfA.customerId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/customers/" + dataOfA.customerId() + "/credit-check")
                        .param("orderAmount", "1000").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());

        // Dữ liệu của A còn nguyên
        TenantContext.runAs(COMPANY_A, () -> {
            var supplier = supplierRepository.findById(dataOfA.supplierId()).orElseThrow();
            assertThat(supplier.getName()).isEqualTo("Nhà cung cấp của A");
            assertThat(supplier.isActive()).isTrue();
            var customer = customerRepository.findById(dataOfA.customerId()).orElseThrow();
            assertThat(customer.getName()).isEqualTo("Khách hàng của A");
            assertThat(customer.isDebtLocked()).isFalse();
            assertThat(customer.isActive()).isTrue();
        });
    }

    @Test
    @DisplayName("B không đọc, sửa, xóa, duyệt được đơn mua, đơn bán, phiếu nhập, phiếu xuất và hóa đơn của A")
    void companyBCannotTouchDocumentsOfCompanyA() throws Exception {
        // Danh sách của B chỉ có chứng từ của B
        mockMvc.perform(get("/api/orders/purchase").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(dataOfB.poId().intValue())));
        mockMvc.perform(get("/api/orders/sales").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(dataOfB.soId().intValue())));
        mockMvc.perform(get("/api/orders/goods-receipt-notes").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        mockMvc.perform(get("/api/orders/goods-delivery-notes").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        mockMvc.perform(get("/api/orders/purchase-bills").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(dataOfB.billId().intValue())));

        // Tra theo số chứng từ trùng nhau thì B chỉ thấy chứng từ của mình
        mockMvc.perform(get("/api/orders/purchase/number/" + dataOfA.poNumber()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(dataOfB.poId().intValue())));
        mockMvc.perform(get("/api/orders/goods-delivery-notes/number/" + dataOfA.gdnNumber())
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(dataOfB.gdnId().intValue())));

        // Đơn mua của A
        mockMvc.perform(get("/api/orders/purchase/" + dataOfA.poId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/purchase/" + dataOfA.poId() + "/cancel").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/orders/purchase/" + dataOfA.poId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        // Lập phiếu nhập cho đơn mua của A (khóa bi quan, truy vấn đã lọc theo doanh nghiệp)
        assertNotFound(() -> TenantContext.runAs(COMPANY_B, () -> goodsReceiptService.create(new CreateGrnRequest(
                dataOfA.poId(), DOC_DATE, null, List.of(new CreateGrnItemRequest(1L, BigDecimal.ONE, BigDecimal.ZERO,
                        null, null, null, null, null, null, null))), "thukho")));
        // Lập đơn mua với nhà cung cấp của A
        assertNotFound(() -> TenantContext.runAs(COMPANY_B, () -> purchaseOrderService.create(new CreatePurchaseOrderRequest(
                null, dataOfA.supplierId(), 11L, null, DOC_DATE, null, null, OrderStatus.DRAFT,
                List.of(new PurchaseOrderItemRequest(100L, "SKU-01", "Hàng mẫu", "Hộp", BigDecimal.ONE,
                        BigDecimal.ONE, null, null, null, null, null))))));

        // Đơn bán của A
        mockMvc.perform(get("/api/orders/sales/" + dataOfA.soId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/sales/" + dataOfA.soId() + "/cancel").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/orders/sales/" + dataOfA.soId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        assertNotFound(() -> TenantContext.runAs(COMPANY_B, () -> goodsDeliveryService.create(new CreateGdnRequest(
                dataOfA.soId(), DOC_DATE, GoodsDeliveryStatus.PICKING, null, null, null, null,
                List.of(new CreateGdnItemRequest(1L, 100L, "SKU-01", "Hàng mẫu", "Hộp", BigDecimal.ONE,
                        BigDecimal.ONE, null, null, null, null))), "thukho")));

        // Phiếu nhập, phiếu xuất, hóa đơn của A
        mockMvc.perform(get("/api/orders/goods-receipt-notes/" + dataOfA.grnId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/goods-delivery-notes/" + dataOfA.gdnId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/goods-delivery-notes/" + dataOfA.gdnId() + "/dispatch")
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/purchase-bills/" + dataOfA.billId()).header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/purchase-bills/" + dataOfA.billId() + "/payments")
                        .header("X-Company-Id", COMPANY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RecordBillPaymentRequest(new BigDecimal("1000"), "CASH", null, null, null))))
                .andExpect(status().isNotFound());

        // B xác nhận xuất kho phiếu của chính mình: khóa bi quan findByIdForUpdate vẫn chạy đúng với bộ lọc doanh nghiệp
        mockMvc.perform(post("/api/orders/goods-delivery-notes/" + dataOfB.gdnId() + "/dispatch")
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        // Dữ liệu của A không đổi
        TenantContext.runAs(COMPANY_A, () -> {
            assertThat(purchaseOrderRepository.findById(dataOfA.poId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.COMPLETED);
            assertThat(salesOrderRepository.findById(dataOfA.soId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CONFIRMED);
            assertThat(goodsDeliveryNoteRepository.findById(dataOfA.gdnId()).orElseThrow().getStatus())
                    .isEqualTo(GoodsDeliveryStatus.PICKING);
            assertThat(purchaseBillRepository.findById(dataOfA.billId()).orElseThrow().getPaidAmount())
                    .isEqualByComparingTo("0");
        });
    }

    @Test
    @DisplayName("Không xác định doanh nghiệp: danh sách rỗng, tạo mới bị từ chối COMPANY_REQUIRED")
    void noCompanyMeansEmptyListsAndCompanyRequiredOnCreate() {
        TenantContext.clear();

        assertThat(supplierRepository.findAll()).isEmpty();
        assertThat(customerRepository.findAll()).isEmpty();
        assertThat(purchaseOrderRepository.findAll()).isEmpty();
        assertThat(salesOrderRepository.findAll()).isEmpty();
        assertThat(goodsDeliveryNoteRepository.findAll()).isEmpty();
        assertThat(purchaseBillRepository.findAll()).isEmpty();
        assertThat(supplierService.list(null, null, PageRequest.of(0, 20)).content()).isEmpty();
        assertThat(customerService.list(null, null, null, null, null, PageRequest.of(0, 20)).content()).isEmpty();

        assertCompanyRequired(() -> supplierService.create(supplierRequest("NCC-99", "Không doanh nghiệp", null)));
        assertCompanyRequired(() -> customerService.create(customerRequest("KH-99", "Không doanh nghiệp", null)));
        assertCompanyRequired(() -> purchaseOrderService.generatePoNumber(DOC_DATE));
        assertCompanyRequired(() -> bulkImportService.importSuppliers(
                List.of(supplierRequest("NCC-98", "Không doanh nghiệp", null))));
    }

    @Test
    @DisplayName("Endpoint KPI nội bộ chỉ tổng hợp số liệu của doanh nghiệp gọi")
    void internalKpiMetricsOnlyCountCallerCompany() throws Exception {
        // Chỉ A có thêm công nợ phải thu và phải trả
        TenantContext.runAs(COMPANY_A, () -> {
            supplierService.create(new CreateSupplierRequest("NCC-NO", "Nhà cung cấp còn nợ", null, null, null,
                    null, null, null, null, null, null, new BigDecimal("5000000")));
            customerService.create(new CreateCustomerRequest("KH-NO", "Khách hàng còn nợ", CustomerType.RETAIL,
                    null, null, null, null, null, null, null, new BigDecimal("3000000"), null));
        });

        JsonNode metricsA = kpiOf(COMPANY_A);
        JsonNode metricsB = kpiOf(COMPANY_B);

        // Phải trả: hóa đơn từ GRN 10 x 1.000 = 10.000 cho mỗi bên, A thêm 5.000.000
        assertThat(metricsA.path("totalPayables").decimalValue()).isEqualByComparingTo("5010000");
        assertThat(metricsB.path("totalPayables").decimalValue()).isEqualByComparingTo("10000");
        assertThat(metricsA.path("totalReceivables").decimalValue()).isEqualByComparingTo("3000000");
        assertThat(metricsB.path("totalReceivables").decimalValue()).isEqualByComparingTo("0");
        assertThat(metricsA.path("dueSoonSuppliersCount").asLong()).isEqualTo(2);
        assertThat(metricsB.path("dueSoonSuppliersCount").asLong()).isEqualTo(1);
        // Mỗi bên đúng một đơn bán đã xác nhận 4 x 2.000 = 8.000 trong tháng này
        assertThat(metricsA.path("pendingSalesOrdersCount").asLong()).isEqualTo(1);
        assertThat(metricsB.path("pendingSalesOrdersCount").asLong()).isEqualTo(1);
        assertThat(metricsA.path("currentMonthRevenue").decimalValue()).isEqualByComparingTo("8000");
        assertThat(metricsB.path("currentMonthRevenue").decimalValue()).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("Sự kiện order-service phát ra mang đúng doanh nghiệp của chứng từ")
    void publishedEventsCarryCompanyOfDocument() {
        // Mỗi doanh nghiệp: order.created khi duyệt PO, order.received khi lập GRN, order.created khi SO xác nhận
        assertThat(publishedEvents).containsExactlyInAnyOrder(
                WmsEvents.RoutingKeys.ORDER_CREATED + "@" + COMPANY_A,
                WmsEvents.RoutingKeys.ORDER_RECEIVED + "@" + COMPANY_A,
                WmsEvents.RoutingKeys.ORDER_CREATED + "@" + COMPANY_A,
                WmsEvents.RoutingKeys.ORDER_CREATED + "@" + COMPANY_B,
                WmsEvents.RoutingKeys.ORDER_RECEIVED + "@" + COMPANY_B,
                WmsEvents.RoutingKeys.ORDER_CREATED + "@" + COMPANY_B);
    }

    private JsonNode kpiOf(Long companyId) throws Exception {
        String body = mockMvc.perform(get("/internal/orders/kpi-metrics").header("X-Company-Id", companyId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private static CreateSupplierRequest supplierRequest(String code, String name, String taxCode) {
        return new CreateSupplierRequest(code, name, taxCode, null, null, null, null, null, null,
                "30 ngày", null, null);
    }

    private static CreateCustomerRequest customerRequest(String code, String name, String taxCode) {
        return new CreateCustomerRequest(code, name, CustomerType.WHOLESALE, taxCode, null, null, null, null,
                null, null, null, null);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static void assertCompanyRequired(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.COMPANY_REQUIRED);
    }
}
