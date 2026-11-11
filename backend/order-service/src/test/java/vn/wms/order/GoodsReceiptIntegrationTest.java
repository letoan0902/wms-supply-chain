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
import vn.wms.order.entity.GoodsReceiptNote;
import vn.wms.order.entity.GoodsReceiptStatus;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.PurchaseOrder;
import vn.wms.order.entity.PurchaseOrderItem;
import vn.wms.order.entity.Supplier;
import vn.wms.order.repository.GoodsReceiptNoteRepository;
import vn.wms.order.repository.PurchaseOrderRepository;
import vn.wms.order.repository.SupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoodsReceiptIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private GoodsReceiptNoteRepository goodsReceiptNoteRepository;

    private Supplier createSampleSupplier(String code, String name) {
        Supplier s = new Supplier();
        s.setCode(code);
        s.setName(name);
        s.setCreditLimit(new BigDecimal("500000000.00"));
        s.setCurrentDebt(BigDecimal.ZERO);
        s.setActive(true);
        return supplierRepository.save(s);
    }

    private PurchaseOrder createSampleApprovedPo(String poNumber, Supplier supplier) {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(poNumber);
        po.setSupplier(supplier);
        po.setWarehouseId(1L);
        po.setWarehouseName("Kho Tổng Cát Lái");
        po.setStatus(OrderStatus.APPROVED);
        po.setOrderDate(LocalDate.now());
        po.setExpectedDate(LocalDate.now().plusDays(5));

        PurchaseOrderItem item1 = new PurchaseOrderItem();
        item1.setProductId(101L);
        item1.setSkuCode("SKU-MILK-100");
        item1.setProductName("Sữa tươi tiệt trùng Vinamilk 1L");
        item1.setUom("Hộp");
        item1.setQuantity(new BigDecimal("100.000"));
        item1.setReceivedQuantity(BigDecimal.ZERO);
        item1.setUnitPrice(new BigDecimal("30000.00"));
        item1.setLineTotal(new BigDecimal("3000000.00"));
        po.addItem(item1);

        PurchaseOrderItem item2 = new PurchaseOrderItem();
        item2.setProductId(102L);
        item2.setSkuCode("SKU-COFFEE-200");
        item2.setProductName("Cà phê đen hòa tan Trung Nguyên");
        item2.setUom("Gói");
        item2.setQuantity(new BigDecimal("50.000"));
        item2.setReceivedQuantity(BigDecimal.ZERO);
        item2.setUnitPrice(new BigDecimal("50000.00"));
        item2.setLineTotal(new BigDecimal("2500000.00"));
        po.addItem(item2);

        po.setSubtotalAmount(new BigDecimal("5500000.00"));
        po.setTotalAmount(new BigDecimal("5500000.00"));
        return purchaseOrderRepository.save(po);
    }

    @Test
    @DisplayName("Tạo GRN nhập đủ 100% mặt hàng -> PO tự động chuyển sang COMPLETED (Full Receipt)")
    void createGrn_fullReceipt_completesPo() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-VNM-01", "Công ty CP Sữa Vinamilk");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-0001", supplier);

        PurchaseOrderItem item1 = po.getItems().get(0);
        PurchaseOrderItem item2 = po.getItems().get(1);

        CreateGrnRequest req = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Nhập đủ đợt 1 xe hàng",
                List.of(
                        new CreateGrnItemRequest(
                                item1.getId(),
                                new BigDecimal("100.000"),
                                BigDecimal.ZERO,
                                null,
                                "LOT-MILK-01",
                                LocalDate.now().minusDays(5),
                                LocalDate.now().plusMonths(6),
                                1L,
                                "BIN-A-01",
                                "Kiểm đếm đủ 100 hộp"
                        ),
                        new CreateGrnItemRequest(
                                item2.getId(),
                                new BigDecimal("50.000"),
                                BigDecimal.ZERO,
                                null,
                                "LOT-COF-01",
                                LocalDate.now().minusDays(10),
                                LocalDate.now().plusYears(1),
                                2L,
                                "BIN-A-02",
                                "Kiểm đếm đủ 50 gói"
                        )
                )
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Name", "thukho_viet")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.poId").value(po.getId()))
                .andExpect(jsonPath("$.data.poNumber").value("PO-20261106-0001"))
                .andExpect(jsonPath("$.data.grnNumber").isString())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalAcceptedQuantity").value(150.0))
                .andExpect(jsonPath("$.data.totalRejectedQuantity").value(0.0))
                .andExpect(jsonPath("$.data.totalReceivedQuantity").value(150.0))
                .andExpect(jsonPath("$.data.totalAmount").value(5500000.0))
                .andExpect(jsonPath("$.data.items", hasSize(2)));

        // Kiểm tra PO đã chuyển sang COMPLETED và po_items cập nhật receivedQuantity
        PurchaseOrder updatedPo = purchaseOrderRepository.findById(po.getId()).orElseThrow();
        assertThat(updatedPo.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(updatedPo.getItems().get(0).getReceivedQuantity()).isEqualByComparingTo("100.000");
        assertThat(updatedPo.getItems().get(1).getReceivedQuantity()).isEqualByComparingTo("50.000");
        assertThat(updatedPo.getItems().get(0).getRemainingQuantity()).isEqualByComparingTo("0.000");
        assertThat(updatedPo.getItems().get(1).getRemainingQuantity()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("Tạo GRN từng đợt (Partial Receipt) -> PO chuyển PARTIALLY_RECEIVED, nhập tiếp đợt 2 thì COMPLETED")
    void createGrn_partialReceipt_workflow() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-TH-01", "Công ty Sữa TH True Milk");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-PARTIAL", supplier);

        PurchaseOrderItem item1 = po.getItems().get(0); // 100 hộp
        PurchaseOrderItem item2 = po.getItems().get(1); // 50 gói

        // ĐỢT 1: Nhập 60 hộp item1 và 20 gói item2
        CreateGrnRequest batch1 = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Nhập đợt 1 (Partial)",
                List.of(
                        new CreateGrnItemRequest(item1.getId(), new BigDecimal("60.000"), BigDecimal.ZERO, null, "LOT-1", null, null, null, null, null),
                        new CreateGrnItemRequest(item2.getId(), new BigDecimal("20.000"), BigDecimal.ZERO, null, "LOT-1", null, null, null, null, null)
                )
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAcceptedQuantity").value(80.0));

        // Kiểm tra PO chuyển sang PARTIALLY_RECEIVED
        PurchaseOrder poAfterBatch1 = purchaseOrderRepository.findById(po.getId()).orElseThrow();
        assertThat(poAfterBatch1.getStatus()).isEqualTo(OrderStatus.PARTIALLY_RECEIVED);

        // Kiểm tra API theo dõi tiến độ /po/{poId}/progress
        mockMvc.perform(get("/api/orders/goods-receipt-notes/po/{poId}/progress", po.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.data.totalOrderedQuantity").value(150.0))
                .andExpect(jsonPath("$.data.totalReceivedQuantity").value(80.0))
                .andExpect(jsonPath("$.data.totalRemainingQuantity").value(70.0))
                .andExpect(jsonPath("$.data.isFullyReceived").value(false))
                .andExpect(jsonPath("$.data.items[0].remainingQuantity").value(40.0))
                .andExpect(jsonPath("$.data.items[1].remainingQuantity").value(30.0));

        // ĐỢT 2: Nhập nốt 40 hộp item1 và 30 gói item2
        CreateGrnRequest batch2 = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Nhập đợt 2 (Hoàn tất đơn)",
                List.of(
                        new CreateGrnItemRequest(item1.getId(), new BigDecimal("40.000"), BigDecimal.ZERO, null, "LOT-2", null, null, null, null, null),
                        new CreateGrnItemRequest(item2.getId(), new BigDecimal("30.000"), BigDecimal.ZERO, null, "LOT-2", null, null, null, null, null)
                )
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAcceptedQuantity").value(70.0));

        // Kiểm tra PO đã được chuyển sang COMPLETED
        PurchaseOrder poAfterBatch2 = purchaseOrderRepository.findById(po.getId()).orElseThrow();
        assertThat(poAfterBatch2.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // Kiểm tra lịch sử GRN theo PO: phải có 2 phiếu
        mockMvc.perform(get("/api/orders/goods-receipt-notes/po/{poId}", po.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("Ghi nhận số lượng phế phẩm / lỗi hỏng (Rejected) kèm lý do từ chối")
    void createGrn_withRejectedQuantity_recordsDefectDetails() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-DEF-01", "Nhà cung cấp Bao bì");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-DEFECT", supplier);

        PurchaseOrderItem item1 = po.getItems().get(0); // 100 hộp

        // Nhập 80 đạt, 20 hỏng
        CreateGrnRequest req = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Phát hiện hàng bẹp móp khi giao",
                List.of(
                        new CreateGrnItemRequest(
                                item1.getId(),
                                new BigDecimal("80.000"),
                                new BigDecimal("20.000"),
                                "Bao bì rách, móp góc do va đập vận chuyển",
                                "LOT-B1",
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                )
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAcceptedQuantity").value(80.0))
                .andExpect(jsonPath("$.data.totalRejectedQuantity").value(20.0))
                .andExpect(jsonPath("$.data.totalReceivedQuantity").value(100.0))
                .andExpect(jsonPath("$.data.totalAmount").value(2400000.0)) // 80 * 30000
                .andExpect(jsonPath("$.data.items[0].rejectedQuantity").value(20.0))
                .andExpect(jsonPath("$.data.items[0].rejectedReason").value("Bao bì rách, móp góc do va đập vận chuyển"));
    }

    @Test
    @DisplayName("Tạo GRN khi PO chưa duyệt (DRAFT / PENDING) -> Báo lỗi 400 Bad Request")
    void createGrn_whenPoNotApproved_returnsBadRequest() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-DRAFT-01", "NCC DRAFT");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-DRAFT", supplier);
        po.setStatus(OrderStatus.DRAFT);
        purchaseOrderRepository.save(po);

        CreateGrnRequest req = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Nhập thử đơn nháp",
                List.of(
                        new CreateGrnItemRequest(po.getItems().get(0).getId(), new BigDecimal("10.000"), BigDecimal.ZERO, null, null, null, null, null, null, null)
                )
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @DisplayName("Nhập vượt quá số lượng còn lại của PO -> Báo lỗi 400 Bad Request")
    void createGrn_whenQuantityExceedsRemaining_returnsBadRequest() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-EXCEED-01", "NCC Exceed");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-EXCEED", supplier);

        PurchaseOrderItem item1 = po.getItems().get(0); // quantity = 100

        // Cố tình nhập 150 hộp (vượt quá 100)
        CreateGrnRequest req = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Nhập vượt số lượng",
                List.of(
                        new CreateGrnItemRequest(item1.getId(), new BigDecimal("150.000"), BigDecimal.ZERO, null, null, null, null, null, null, null)
                )
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Tìm kiếm, phân trang và báo cáo thống kê GRN (/summary)")
    void searchAndSummaryGrn() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-SUM-01", "NCC Thống Kê");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-SUM", supplier);

        CreateGrnRequest req = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Nhập kho test summary",
                List.of(
                        new CreateGrnItemRequest(po.getItems().get(0).getId(), new BigDecimal("50.000"), new BigDecimal("5.000"), "Lỗi", null, null, null, null, null, null)
                )
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Test tìm kiếm theo PO Number
        mockMvc.perform(get("/api/orders/goods-receipt-notes")
                        .param("keyword", "PO-20261106-SUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].poNumber").value("PO-20261106-SUM"));

        // Test Summary metrics
        mockMvc.perform(get("/api/orders/goods-receipt-notes/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalGrn").isNumber())
                .andExpect(jsonPath("$.data.completedGrn").isNumber())
                .andExpect(jsonPath("$.data.totalAcceptedQuantity").isNumber())
                .andExpect(jsonPath("$.data.totalRejectedQuantity").isNumber());
    }

    @Test
    @DisplayName("Từ chối lập GRN nếu đơn mua PO chưa được gán warehouseId -> Báo lỗi 400 (WMS-10.2)")
    void createGrn_whenPoHasNoWarehouseId_returnsBadRequest() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-NO-WH", "NCC Không Kho");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-NOWH", supplier);
        po.setWarehouseId(null);
        po.setWarehouseName(null);
        purchaseOrderRepository.save(po);

        CreateGrnRequest req = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Thử lập phiếu khi chưa có kho",
                List.of(new CreateGrnItemRequest(po.getItems().get(0).getId(), BigDecimal.TEN, BigDecimal.ZERO, null, null, null, null, null, null, null))
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("kho nhận hàng")));
    }

    @Test
    @DisplayName("Từ chối lập GRN khi hạn sử dụng (expDate) trước ngày sản xuất (mfgDate) -> Báo lỗi 400 (WMS-10.2)")
    void createGrn_whenExpDateBeforeMfgDate_returnsBadRequest() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-DATE-01", "NCC Kiểm tra Date");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-DATE1", supplier);

        CreateGrnRequest req = new CreateGrnRequest(
                po.getId(),
                LocalDate.now(),
                "Nhập sai mfg và exp",
                List.of(new CreateGrnItemRequest(
                        po.getItems().get(0).getId(),
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        null,
                        "LOT-DATE-01",
                        LocalDate.of(2026, 12, 1), // Mfg
                        LocalDate.of(2026, 11, 1), // Exp < Mfg
                        null, null, null
                ))
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không được trước ngày sản xuất")));
    }

    @Test
    @DisplayName("Từ chối lập GRN khi hàng đã hết hạn sử dụng tính đến ngày nhận (expDate <= receiptDate) -> Báo lỗi 400 (WMS-10.2)")
    void createGrn_whenProductAlreadyExpired_returnsBadRequest() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-DATE-02", "NCC Hàng Quá Hạn");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-DATE2", supplier);

        CreateGrnRequest req = new CreateGrnRequest(
                po.getId(),
                LocalDate.of(2026, 11, 6),
                "Hàng đã hết hạn",
                List.of(new CreateGrnItemRequest(
                        po.getItems().get(0).getId(),
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        null,
                        "LOT-EXPIRED-01",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 11, 5), // Hết hạn hôm qua
                        null, null, null
                ))
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("đã hết hạn sử dụng")));
    }

    @Test
    @DisplayName("Cho phép lập GRN khi hàng hết hạn sử dụng nhưng số lượng đạt bằng 0 (accepted = 0, rejected > 0) để lưu vết hàng bị từ chối/trả lại")
    void createGrn_whenProductExpiredButAcceptedZero_allowsCreationForReturns() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-DATE-03", "NCC Hàng Trả Lại");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-DATE3", supplier);

        CreateGrnRequest req = new CreateGrnRequest(
                po.getId(),
                LocalDate.of(2026, 11, 6),
                "Hàng giao bị hết hạn nên từ chối toàn bộ",
                List.of(new CreateGrnItemRequest(
                        po.getItems().get(0).getId(),
                        BigDecimal.ZERO, // Accepted = 0
                        BigDecimal.TEN,  // Rejected = 10
                        "Hàng đã hết hạn sử dụng",
                        "LOT-EXPIRED-RETURN",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 11, 5), // Đã hết hạn
                        null, null, null
                ))
        );

        mockMvc.perform(post("/api/orders/goods-receipt-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAcceptedQuantity").value(0))
                .andExpect(jsonPath("$.data.totalRejectedQuantity").value(10));
    }

    @Test
    @DisplayName("Chặn xóa đơn mua hàng (PO) khi đơn đang ở trạng thái PARTIALLY_RECEIVED -> Báo lỗi 400 (WMS-10.2)")
    void deletePurchaseOrder_whenPartiallyReceived_isBlocked() throws Exception {
        Supplier supplier = createSampleSupplier("NCC-PART-DEL", "NCC Đơn Dở Dang");
        PurchaseOrder po = createSampleApprovedPo("PO-20261106-PARTDEL", supplier);
        po.setStatus(OrderStatus.PARTIALLY_RECEIVED);
        purchaseOrderRepository.save(po);

        mockMvc.perform(delete("/api/orders/purchase/{id}", po.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không thể xóa đơn mua hàng")));
    }
}
