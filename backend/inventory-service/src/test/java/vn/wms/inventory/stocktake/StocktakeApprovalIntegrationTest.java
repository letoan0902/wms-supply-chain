package vn.wms.inventory.stocktake;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.InventoryUpdatedEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.entity.InventoryLot;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StocktakeLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp WMS-14.2: kết thúc đếm và tính chênh lệch (số thập phân), đếm lại, duyệt hai cấp
 * (Quản lý kho rồi Kế toán), cập nhật tồn đúng một lần (duyệt hai lần, duyệt đồng thời), quy tắc giữ chỗ,
 * từ chối và hủy mở đóng băng, phân quyền, phân tách doanh nghiệp và sự kiện inventory.updated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StocktakeApprovalIntegrationTest extends StocktakeTestSupport {

    private static final String BASE = "/api/inventory/stocktakes";

    @MockBean
    private WmsEventPublisher wmsEventPublisher;

    private StockItem itemA1;
    private StockItem itemA2;
    private StockItem itemB1;

    /**
     * Đợt toàn kho 1 đã đếm xong một phần: A1 (P1, lô L1, tồn 10.000, giữ 2.000) đếm 9.995; A2 (P2, tồn 4.250)
     * đếm 5.5; B1 (P1, tồn 1.000) không đếm; hàng phát sinh B1 (P2, lô NEW) đếm 0.75.
     */
    private long prepareCountingSession() throws Exception {
        itemA1 = stock(COMPANY_A, binA1, PRODUCT_1, "L1", "10.000", "2.000");
        itemA2 = stock(COMPANY_A, binA2, PRODUCT_2, null, "4.250", "0");
        itemB1 = stock(COMPANY_A, binB1, PRODUCT_1, null, "1.000", "0");
        long id = createAndStart(warehouseScope(warehouse1.getId()), COMPANY_A, manager1);
        count(id, binA1.getId(), PRODUCT_1, "L1", "9.995");
        count(id, binA2.getId(), PRODUCT_2, null, "5.5");
        count(id, binB1.getId(), PRODUCT_2, "NEW", "0.75");
        return id;
    }

    private void count(long id, Long binId, Long productId, String lotNo, String quantity) throws Exception {
        postJson(BASE + "/" + id + "/counts", countBody(binId, productId, lotNo, quantity, "SET"), COMPANY_A, staff1)
                .andExpect(status().isOk());
    }

    private void finishConfirmed(long id, Long company, TestUser user) throws Exception {
        postJson(BASE + "/" + id + "/finish", Map.of("confirmUncountedAsZero", true), company, user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COUNTED")));
    }

    private long lineId(long sessionId, Long binId, Long productId) {
        return TenantContext.callAs(COMPANY_A, () -> lineRepository.findBySessionIdOrdered(sessionId).stream()
                .filter(l -> l.getBinLocation().getId().equals(binId) && l.getProductId().equals(productId))
                .findFirst().orElseThrow().getId());
    }

    private DeductStockRequest deductOne(Long binId) {
        return new DeductStockRequest(null, "M-" + binId, warehouse1.getId(), "MANUAL", null,
                List.of(new DeductStockItemRequest(PRODUCT_1, "SKU-1", new BigDecimal("0.001"), binId, null)));
    }

    @Test
    @DisplayName("Luồng đầy đủ: kết thúc đếm, chênh lệch thập phân, Quản lý kho duyệt sinh phiếu, Kế toán duyệt cập nhật tồn đúng một lần và mở đóng băng")
    void fullApprovalFlow() throws Exception {
        long id = prepareCountingSession();

        postJson(BASE + "/" + id + "/finish", null, COMPANY_A, staff1).andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/finish", null, COMPANY_A, manager1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.uncountedLines", is(1)));
        getAs(BASE + "/" + id + "/variances", COMPANY_A, manager1).andExpect(status().isConflict());
        finishConfirmed(id, COMPANY_A, manager1);

        getAs(BASE + "/" + id + "/variances", COMPANY_A, staff1).andExpect(status().isForbidden());
        getAs(BASE + "/" + id + "/variances?onlyDifferences=true", COMPANY_A, manager1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalLines", is(4)))
                .andExpect(jsonPath("$.data.summary.countedLines", is(4)))
                .andExpect(jsonPath("$.data.summary.linesWithVariance", is(4)))
                .andExpect(jsonPath("$.data.summary.increaseLines", is(2)))
                .andExpect(jsonPath("$.data.summary.decreaseLines", is(2)))
                .andExpect(jsonPath("$.data.summary.totalIncreaseQuantity", is(2.0)))
                .andExpect(jsonPath("$.data.summary.totalDecreaseQuantity", is(1.005)))
                .andExpect(jsonPath("$.data.summary.netVarianceQuantity", is(0.995)))
                .andExpect(jsonPath("$.data.summary.reservedConflictLines", is(0)))
                .andExpect(jsonPath("$.data.lines.content", hasSize(4)));
        JsonNode variances = data(getAs(BASE + "/" + id + "/variances?binCode=" + binA1.getCode(), COMPANY_A, accountant));
        JsonNode a1 = variances.path("lines").path("content").get(0);
        assertThat(a1.path("systemQuantity").decimalValue()).isEqualByComparingTo("10.000");
        assertThat(a1.path("systemReservedQuantity").decimalValue()).isEqualByComparingTo("2.000");
        assertThat(a1.path("countedQuantity").decimalValue()).isEqualByComparingTo("9.995");
        assertThat(a1.path("varianceQuantity").decimalValue()).isEqualByComparingTo("-0.005");

        long a1Line = lineId(id, binA1.getId(), PRODUCT_1);
        mockMvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(BASE + "/" + id + "/lines/" + a1Line + "/reason")
                        .contentType("application/json").content("{\"reason\":\"Hao hụt khi bốc xếp\"}"), COMPANY_A, manager1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason", is("Hao hụt khi bốc xếp")));

        postJson(BASE + "/" + id + "/adjustment/approve", null, COMPANY_A, accountant).andExpect(status().isConflict());
        postJson(BASE + "/" + id + "/approve", null, COMPANY_A, staff1).andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/approve", null, COMPANY_A, accountant).andExpect(status().isForbidden());
        JsonNode pending = data(postJson(BASE + "/" + id + "/approve", Map.of("notes", "Đã đối soát"), COMPANY_A, manager1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_ACCOUNTING")))
                .andExpect(jsonPath("$.data.managerApprovedBy", is("manager01")))
                .andExpect(jsonPath("$.data.adjustmentStatus", is("PENDING_APPROVAL")))
                .andExpect(jsonPath("$.data.adjustmentCode",
                        startsWith("ADJ-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-"))));
        long adjustmentId = pending.path("adjustmentId").asLong();
        postJson(BASE + "/" + id + "/approve", null, COMPANY_A, manager1).andExpect(status().isConflict());

        // Chưa Kế toán duyệt: tồn chưa đổi, vị trí vẫn đóng băng
        assertThat(onHand(COMPANY_A, itemA1.getId())).isEqualByComparingTo("10.000");
        postJson("/api/inventory/stock/deduct", deductOne(binA1.getId()), COMPANY_A, admin).andExpect(status().isConflict());
        postJson(BASE + "/" + id + "/adjustment/approve", null, COMPANY_A, manager1).andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/cancel", null, COMPANY_A, manager1).andExpect(status().isConflict());

        postJson(BASE + "/" + id + "/adjustment/approve", null, COMPANY_A, accountant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.approvedBy", is("acc01")))
                .andExpect(jsonPath("$.data.adjustmentStatus", is("APPROVED")))
                .andExpect(jsonPath("$.data.locations[0].frozen", is(false)));

        assertStockAfterApproval();

        // Duyệt lần hai không áp dụng lại
        postJson(BASE + "/" + id + "/adjustment/approve", null, COMPANY_A, admin).andExpect(status().isConflict());
        assertStockAfterApproval();

        getAs("/api/inventory/stock-adjustments/" + adjustmentId, COMPANY_A, accountant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.sessionId", is((int) id)))
                .andExpect(jsonPath("$.data.requestedBy", is("manager01")))
                .andExpect(jsonPath("$.data.approvedBy", is("acc01")))
                .andExpect(jsonPath("$.data.notes", is("Đã đối soát")))
                .andExpect(jsonPath("$.data.totalIncreaseQuantity", is(2.0)))
                .andExpect(jsonPath("$.data.totalDecreaseQuantity", is(1.005)))
                .andExpect(jsonPath("$.data.lines", hasSize(4)));
        JsonNode adjustment = data(getAs("/api/inventory/stock-adjustments/" + adjustmentId, COMPANY_A, manager1));
        JsonNode adjA1 = null;
        for (JsonNode line : adjustment.path("lines")) {
            if (line.path("binLocationId").asLong() == binA1.getId()) {
                adjA1 = line;
            }
        }
        assertThat(adjA1).isNotNull();
        assertThat(adjA1.path("quantityBefore").decimalValue()).isEqualByComparingTo("10.000");
        assertThat(adjA1.path("quantityAfter").decimalValue()).isEqualByComparingTo("9.995");
        assertThat(adjA1.path("differenceQuantity").decimalValue()).isEqualByComparingTo("-0.005");
        assertThat(adjA1.path("reason").asText()).isEqualTo("Hao hụt khi bốc xếp");

        getAs("/api/inventory/stock-adjustments/" + adjustmentId, COMPANY_A, staff1).andExpect(status().isForbidden());
        getAs("/api/inventory/stock-adjustments", COMPANY_A, staff1).andExpect(status().isForbidden());
        getAs("/api/inventory/stock-adjustments", COMPANY_A, accountant)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].lines").doesNotExist());
        getAs("/api/inventory/stock-adjustments?status=APPROVED&warehouseId=" + warehouse1.getId(), COMPANY_A, manager1)
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        getAs("/api/inventory/stock-adjustments", COMPANY_A, manager2).andExpect(jsonPath("$.data.content", hasSize(0)));
        getAs("/api/inventory/stock-adjustments?status=PENDING_APPROVAL", COMPANY_A, admin)
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        // Đã mở đóng băng: xuất kho tại vị trí vừa kiểm hoạt động lại
        postJson("/api/inventory/stock/deduct", deductOne(binA1.getId()), COMPANY_A, admin).andExpect(status().isOk());

        // Sự kiện inventory.updated cho từng sản phẩm, lý do ADJUSTMENT, tồn trước và sau theo kho
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(wmsEventPublisher, atLeastOnce()).publishAfterCommit(eq(WmsEvents.RoutingKeys.INVENTORY_UPDATED), events.capture());
        List<InventoryUpdatedEvent> adjustmentEvents = events.getAllValues().stream()
                .filter(InventoryUpdatedEvent.class::isInstance).map(InventoryUpdatedEvent.class::cast)
                .filter(e -> "ADJUSTMENT".equals(e.reason())).toList();
        assertThat(adjustmentEvents).hasSize(2);
        InventoryUpdatedEvent p1 = adjustmentEvents.stream().filter(e -> e.productId().equals(PRODUCT_1)).findFirst().orElseThrow();
        assertThat(p1.warehouseId()).isEqualTo(warehouse1.getId());
        assertThat(p1.quantityBefore()).isEqualByComparingTo("11.000");
        assertThat(p1.quantityAfter()).isEqualByComparingTo("9.995");
        assertThat(p1.referenceNumber()).isEqualTo(adjustment.path("code").asText());
        InventoryUpdatedEvent p2 = adjustmentEvents.stream().filter(e -> e.productId().equals(PRODUCT_2)).findFirst().orElseThrow();
        assertThat(p2.quantityBefore()).isEqualByComparingTo("4.250");
        assertThat(p2.quantityAfter()).isEqualByComparingTo("6.250");
    }

    private void assertStockAfterApproval() {
        TenantContext.runAs(COMPANY_A, () -> {
            StockItem a1 = stockItemRepository.findById(itemA1.getId()).orElseThrow();
            assertThat(a1.getQuantity()).isEqualByComparingTo("9.995");
            assertThat(a1.getReservedQuantity()).isEqualByComparingTo("2.000");
            assertThat(stockItemRepository.findById(itemA2.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("5.500");
            assertThat(stockItemRepository.findById(itemB1.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("0");
            List<StockItem> unexpected = stockItemRepository.findByBinLocationIdAndProductId(binB1.getId(), PRODUCT_2);
            assertThat(unexpected).hasSize(1);
            assertThat(unexpected.get(0).getLotNo()).isEqualTo("NEW");
            assertThat(unexpected.get(0).getQuantity()).isEqualByComparingTo("0.750");
            InventoryLot lotL1 = inventoryLotRepository.findByLotNumberAndBinLocationIdAndProductId("L1", binA1.getId(), PRODUCT_1).orElseThrow();
            assertThat(lotL1.getQuantity()).isEqualByComparingTo("9.995");
            InventoryLot lotNew = inventoryLotRepository.findByLotNumberAndBinLocationIdAndProductId("NEW", binB1.getId(), PRODUCT_2).orElseThrow();
            assertThat(lotNew.getQuantity()).isEqualByComparingTo("0.750");
        });
    }

    @Test
    @DisplayName("Hai người duyệt phiếu điều chỉnh đồng thời: đúng một lần thành công, tồn chỉ đổi một lần")
    void concurrentAdjustmentApprovalAppliesOnce() throws Exception {
        long id = prepareCountingSession();
        finishConfirmed(id, COMPANY_A, manager1);
        postJson(BASE + "/" + id + "/approve", null, COMPANY_A, manager1).andExpect(status().isOk());

        List<TestUser> approvers = List.of(accountant, admin);
        ExecutorService pool = Executors.newFixedThreadPool(approvers.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(approvers.size());
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        for (TestUser approver : approvers) {
            pool.submit(() -> {
                try {
                    start.await();
                    statuses.add(postJson(BASE + "/" + id + "/adjustment/approve", null, COMPANY_A, approver)
                            .andReturn().getResponse().getStatus());
                } catch (Exception ex) {
                    statuses.add(-1);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(statuses).hasSize(2);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s != 200).count()).isEqualTo(1);
        assertStockAfterApproval();
        getAs(BASE + "/" + id, COMPANY_A, admin).andExpect(jsonPath("$.data.status", is("COMPLETED")));
    }

    @Test
    @DisplayName("Người đã duyệt cấp Quản lý kho không được duyệt tiếp cấp Kế toán; đợt không lệch thì Quản lý kho duyệt là hoàn tất")
    void separationOfApproversAndNoVarianceSession() throws Exception {
        long id = prepareCountingSession();
        finishConfirmed(id, COMPANY_A, admin);
        postJson(BASE + "/" + id + "/approve", null, COMPANY_A, admin).andExpect(status().isOk());
        postJson(BASE + "/" + id + "/adjustment/approve", null, COMPANY_A, admin).andExpect(status().isForbidden());
        assertThat(onHand(COMPANY_A, itemA1.getId())).isEqualByComparingTo("10.000");
        postJson(BASE + "/" + id + "/adjustment/approve", null, COMPANY_A, accountant).andExpect(status().isOk());
        assertStockAfterApproval();

        StockItem w2 = stock(COMPANY_A, binW2, PRODUCT_1, null, "3.000", "0");
        long noVariance = createAndStart(locationScope(warehouse2.getId(), List.of(binW2.getId())), COMPANY_A, manager2);
        postJson(BASE + "/" + noVariance + "/counts", countBody(binW2.getId(), PRODUCT_1, null, "3", "SET"), COMPANY_A, manager2)
                .andExpect(status().isOk());
        postJson(BASE + "/" + noVariance + "/finish", null, COMPANY_A, manager2).andExpect(status().isOk());
        postJson(BASE + "/" + noVariance + "/approve", null, COMPANY_A, manager2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.adjustmentId", nullValue()))
                .andExpect(jsonPath("$.data.locations[0].frozen", is(false)));
        assertThat(onHand(COMPANY_A, w2.getId())).isEqualByComparingTo("3.000");
    }

    @Test
    @DisplayName("Số đếm nhỏ hơn giữ chỗ chặn duyệt (409 kèm danh sách dòng); đếm lại dòng đó rồi duyệt được")
    void reservedConflictBlocksApprovalUntilRecount() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "6.000");
        long id = createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, null, "4", "SET"), COMPANY_A, staff1);
        postJson(BASE + "/" + id + "/finish", null, COMPANY_A, manager1).andExpect(status().isOk());
        getAs(BASE + "/" + id + "/variances", COMPANY_A, manager1)
                .andExpect(jsonPath("$.data.summary.reservedConflictLines", is(1)))
                .andExpect(jsonPath("$.data.lines.content[0].reservedConflict", is(true)));
        postJson(BASE + "/" + id + "/approve", null, COMPANY_A, manager1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data[0].reservedQuantity", is(6.0)))
                .andExpect(jsonPath("$.data[0].newQuantity", is(4.0)));
        getAs(BASE + "/" + id, COMPANY_A, manager1).andExpect(jsonPath("$.data.status", is("COUNTED")));

        long line = lineId(id, binA1.getId(), PRODUCT_1);
        postJson(BASE + "/" + id + "/recount", Map.of("lineIds", List.of(line)), COMPANY_A, staff1).andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/recount", Map.of("lineIds", List.of()), COMPANY_A, manager1).andExpect(status().isBadRequest());
        postJson(BASE + "/" + id + "/recount", Map.of("lineIds", List.of(line)), COMPANY_A, manager1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COUNTING")))
                .andExpect(jsonPath("$.data.uncountedLines", is(1)));
        getAs(BASE + "/" + id + "/count-lines?counted=false", COMPANY_A, staff1)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].recountRequested", is(true)));
        getAs(BASE + "/" + id + "/variances", COMPANY_A, manager1).andExpect(status().isConflict());
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, null, "7", "SET"), COMPANY_A, staff1)
                .andExpect(status().isOk());
        postJson(BASE + "/" + id + "/finish", null, COMPANY_A, manager1).andExpect(status().isOk());
        postJson(BASE + "/" + id + "/approve", null, COMPANY_A, manager1).andExpect(status().isOk());
        postJson(BASE + "/" + id + "/adjustment/approve", null, COMPANY_A, accountant).andExpect(status().isOk());

        TenantContext.runAs(COMPANY_A, () -> {
            StockItem after = stockItemRepository.findById(item.getId()).orElseThrow();
            assertThat(after.getQuantity()).isEqualByComparingTo("7.000");
            assertThat(after.getReservedQuantity()).isEqualByComparingTo("6.000");
        });
    }

    @Test
    @DisplayName("Từ chối ở cấp Quản lý kho và cấp Kế toán: bắt buộc lý do, mở đóng băng, tồn kho không đổi")
    void rejectUnfreezesWithoutChangingStock() throws Exception {
        long first = prepareCountingSession();
        finishConfirmed(first, COMPANY_A, manager1);
        postJson(BASE + "/" + first + "/reject", null, COMPANY_A, manager1).andExpect(status().isBadRequest());
        postJson(BASE + "/" + first + "/reject", Map.of("reason", "Sai"), COMPANY_A, staff1).andExpect(status().isForbidden());
        postJson(BASE + "/" + first + "/reject", Map.of("reason", "Sai"), COMPANY_A, accountant).andExpect(status().isForbidden());
        postJson(BASE + "/" + first + "/reject", Map.of("reason", "Đếm sai khu ZB"), COMPANY_A, manager1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")))
                .andExpect(jsonPath("$.data.closeReason", is("Đếm sai khu ZB")))
                .andExpect(jsonPath("$.data.locations[0].frozen", is(false)));
        postJson(BASE + "/" + first + "/reject", Map.of("reason", "Lần hai"), COMPANY_A, manager1).andExpect(status().isConflict());
        assertThat(onHand(COMPANY_A, itemA1.getId())).isEqualByComparingTo("10.000");
        postJson("/api/inventory/stock/deduct", deductOne(binA1.getId()), COMPANY_A, admin).andExpect(status().isOk());

        // Đợt thứ hai trên cùng kho (vị trí đã được mở), bị Kế toán từ chối phiếu điều chỉnh
        long second = createAndStart(warehouseScope(warehouse1.getId()), COMPANY_A, manager1);
        count(second, binA1.getId(), PRODUCT_1, "L1", "3");
        finishConfirmed(second, COMPANY_A, manager1);
        JsonNode pending = data(postJson(BASE + "/" + second + "/approve", null, COMPANY_A, manager1).andExpect(status().isOk()));
        long adjustmentId = pending.path("adjustmentId").asLong();
        postJson(BASE + "/" + second + "/reject", Map.of("reason", "Chênh lệch quá lớn, cần kiểm lại"), COMPANY_A, accountant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")))
                .andExpect(jsonPath("$.data.adjustmentStatus", is("REJECTED")));
        getAs("/api/inventory/stock-adjustments/" + adjustmentId, COMPANY_A, accountant)
                .andExpect(jsonPath("$.data.status", is("REJECTED")))
                .andExpect(jsonPath("$.data.rejectedBy", is("acc01")))
                .andExpect(jsonPath("$.data.rejectReason", is("Chênh lệch quá lớn, cần kiểm lại")))
                .andExpect(jsonPath("$.data.lines[0].quantityAfter", nullValue()));
        postJson(BASE + "/" + second + "/adjustment/approve", null, COMPANY_A, accountant).andExpect(status().isConflict());
        assertThat(onHand(COMPANY_A, itemA1.getId())).isEqualByComparingTo("9.999");
        TenantContext.runAs(COMPANY_A, () ->
                assertThat(locationRepository.findFrozenByBinLocationIds(List.of(binA1.getId(), binA2.getId(), binB1.getId()))).isEmpty());
    }

    @Test
    @DisplayName("Phân tách doanh nghiệp: B không xem chênh lệch, không duyệt, không từ chối đợt của A, không thấy phiếu của A; mã phiếu được trùng giữa hai doanh nghiệp")
    void companyIsolation() throws Exception {
        long idA = prepareCountingSession();
        finishConfirmed(idA, COMPANY_A, manager1);
        long adjA = data(postJson(BASE + "/" + idA + "/approve", null, COMPANY_A, manager1)).path("adjustmentId").asLong();
        String codeA = data(getAs("/api/inventory/stock-adjustments/" + adjA, COMPANY_A, admin)).path("code").asText();

        getAs(BASE + "/" + idA + "/variances", COMPANY_B, adminB).andExpect(status().isNotFound());
        postJson(BASE + "/" + idA + "/finish", null, COMPANY_B, adminB).andExpect(status().isNotFound());
        postJson(BASE + "/" + idA + "/recount", Map.of("lineIds", List.of(1L)), COMPANY_B, adminB).andExpect(status().isNotFound());
        postJson(BASE + "/" + idA + "/approve", null, COMPANY_B, adminB).andExpect(status().isNotFound());
        postJson(BASE + "/" + idA + "/adjustment/approve", null, COMPANY_B, adminB).andExpect(status().isNotFound());
        postJson(BASE + "/" + idA + "/reject", Map.of("reason", "x"), COMPANY_B, adminB).andExpect(status().isNotFound());
        getAs("/api/inventory/stock-adjustments/" + adjA, COMPANY_B, adminB).andExpect(status().isNotFound());
        getAs("/api/inventory/stock-adjustments", COMPANY_B, adminB).andExpect(jsonPath("$.data.content", hasSize(0)));
        assertThat(onHand(COMPANY_A, itemA1.getId())).isEqualByComparingTo("10.000");

        stock(COMPANY_B, binOfCompanyB, PRODUCT_1, null, "3.000", "0");
        long idB = createAndStart(warehouseScope(warehouseB.getId()), COMPANY_B, adminB);
        postJson(BASE + "/" + idB + "/counts", countBody(binOfCompanyB.getId(), PRODUCT_1, null, "2", "SET"), COMPANY_B, adminB)
                .andExpect(status().isOk());
        finishConfirmed(idB, COMPANY_B, adminB);
        long adjB = data(postJson(BASE + "/" + idB + "/approve", null, COMPANY_B, adminB)).path("adjustmentId").asLong();
        getAs("/api/inventory/stock-adjustments/" + adjB, COMPANY_B, adminB)
                .andExpect(jsonPath("$.data.code", is(codeA)));
        getAs("/api/inventory/stock-adjustments", COMPANY_A, admin).andExpect(jsonPath("$.data.content", hasSize(1)));

        postJson(BASE + "/" + idA + "/adjustment/approve", null, COMPANY_A, accountant).andExpect(status().isOk());
        assertStockAfterApproval();
        TenantContext.runAs(COMPANY_B, () -> {
            List<StocktakeLine> linesB = lineRepository.findBySessionIdOrdered(idB);
            assertThat(linesB).hasSize(1);
            assertThat(stockItemRepository.findByBinLocationId(binOfCompanyB.getId()).get(0).getQuantity()).isEqualByComparingTo("3.000");
        });
    }

    @Test
    @DisplayName("Không được kết thúc đếm, đếm lại hay duyệt sai trạng thái; quản lý kho khác không thao tác được")
    void stateAndScopeChecks() throws Exception {
        long draft = data(postJson(BASE, warehouseScope(warehouse1.getId()), COMPANY_A, manager1)).path("id").asLong();
        postJson(BASE + "/" + draft + "/finish", null, COMPANY_A, manager1).andExpect(status().isConflict());
        postJson(BASE + "/" + draft + "/approve", null, COMPANY_A, manager1).andExpect(status().isConflict());
        postJson(BASE + "/" + draft + "/reject", Map.of("reason", "x"), COMPANY_A, manager1).andExpect(status().isConflict());
        postJson(BASE + "/" + draft + "/recount", Map.of("lineIds", List.of(1L)), COMPANY_A, manager1).andExpect(status().isConflict());
        postJson(BASE + "/" + draft + "/cancel", null, COMPANY_A, manager1).andExpect(status().isOk());

        long id = prepareCountingSession();
        postJson(BASE + "/" + id + "/finish", Map.of("confirmUncountedAsZero", true), COMPANY_A, manager2).andExpect(status().isForbidden());
        finishConfirmed(id, COMPANY_A, manager1);
        getAs(BASE + "/" + id + "/variances", COMPANY_A, manager2).andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/approve", null, COMPANY_A, manager2).andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, "L1", "1", "ADD"), COMPANY_A, staff1)
                .andExpect(status().isConflict());
        postJson(BASE + "/" + id + "/cancel", Map.of("reason", "Hủy sau khi đếm"), COMPANY_A, manager1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));
        assertThat(onHand(COMPANY_A, itemA1.getId())).isEqualByComparingTo("10.000");
    }
}
