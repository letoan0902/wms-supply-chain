package vn.wms.inventory.stocktake;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.common.messaging.event.ReceivedItemLine;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.dto.ReceiveStockTransferItemRequest;
import vn.wms.inventory.dto.ReceiveStockTransferRequest;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.service.StockReceiptService;
import vn.wms.inventory.service.StockReservationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp WMS-14.1: lập đợt kiểm kê, đóng băng vị trí và chụp tồn, đếm mù, hủy, phân quyền và
 * phân tách doanh nghiệp; đóng băng chặn nhập, xuất, giữ chỗ và điều chuyển tại vị trí đang kiểm.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StocktakeSessionIntegrationTest extends StocktakeTestSupport {

    private static final String BASE = "/api/inventory/stocktakes";

    @Autowired
    private StockReservationService stockReservationService;

    @Autowired
    private StockReceiptService stockReceiptService;

    private String codePrefix() {
        return "ST-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
    }

    @Test
    @DisplayName("Luồng chính: lập đợt toàn kho, bắt đầu thì đóng băng và chụp tồn gộp theo vị trí, sản phẩm, lô; đếm mù không lộ tồn hệ thống")
    void createStartAndBlindCount() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, "L1", "10.000", "2.000");
        stock(COMPANY_A, binA2, PRODUCT_2, null, "3.000", "0");
        stock(COMPANY_A, binA2, PRODUCT_2, null, "1.250", "0");
        stock(COMPANY_A, binB1, PRODUCT_2, null, "0", "0");
        stock(COMPANY_A, binW2, PRODUCT_1, null, "7.000", "0");

        JsonNode created = data(postJson(BASE, warehouseScope(warehouse1.getId()), COMPANY_A, manager1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.code", startsWith(codePrefix())))
                .andExpect(jsonPath("$.data.warehouseCode", is("WH-ST-01")))
                .andExpect(jsonPath("$.data.locations", hasSize(0))));
        long id = created.path("id").asLong();

        postJson(BASE + "/" + id + "/start", null, COMPANY_A, manager1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COUNTING")))
                .andExpect(jsonPath("$.data.startedBy", is("manager01")))
                .andExpect(jsonPath("$.data.totalLocations", is(3)))
                .andExpect(jsonPath("$.data.totalLines", is(2)))
                .andExpect(jsonPath("$.data.uncountedLines", is(2)))
                .andExpect(jsonPath("$.data.locations[0].frozen", is(true)));

        // Ảnh chụp lưu đúng tồn và giữ chỗ, gộp hai dòng không lô của sản phẩm 2
        TenantContext.runAs(COMPANY_A, () -> {
            var lines = lineRepository.findBySessionIdOrdered(id);
            assertThat(lines).hasSize(2);
            var lineA1 = lines.stream().filter(l -> l.getBinLocation().getId().equals(binA1.getId())).findFirst().orElseThrow();
            assertThat(lineA1.getSystemQuantity()).isEqualByComparingTo("10.000");
            assertThat(lineA1.getSystemReservedQuantity()).isEqualByComparingTo("2.000");
            assertThat(lineA1.getLotNo()).isEqualTo("L1");
            var lineA2 = lines.stream().filter(l -> l.getBinLocation().getId().equals(binA2.getId())).findFirst().orElseThrow();
            assertThat(lineA2.getSystemQuantity()).isEqualByComparingTo("4.250");
        });

        // Danh sách đếm của nhân viên kho không có tồn hệ thống, giữ chỗ hay chênh lệch
        String sheet = getAs(BASE + "/" + id + "/count-lines", COMPANY_A, staff1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].counted", is(false)))
                .andReturn().getResponse().getContentAsString();
        assertThat(sheet).doesNotContain("systemQuantity").doesNotContain("eserved").doesNotContain("variance");

        // Quét cộng dồn rồi nhập tay ghi đè, số thập phân 3 chữ số
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, "L1", "1", "ADD"), COMPANY_A, staff1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countedQuantity", is(1)));
        String counted = postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, "L1", "4.5", "ADD"), COMPANY_A, staff1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countedQuantity", is(5.5)))
                .andExpect(jsonPath("$.data.countTimes", is(2)))
                .andExpect(jsonPath("$.data.lastCountedBy", is("staff01")))
                .andExpect(jsonPath("$.data.systemQuantity").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(counted).doesNotContain("systemQuantity").doesNotContain("eserved").doesNotContain("variance");
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, "L1", "9.995", "SET"), COMPANY_A, staff1)
                .andExpect(jsonPath("$.data.countedQuantity", is(9.995)))
                .andExpect(jsonPath("$.data.countTimes", is(3)));

        // Quét theo mã vị trí (mã vạch ô kệ)
        Map<String, Object> byCode = countBody(null, PRODUCT_2, null, "0.001", "ADD");
        byCode.put("binLocationCode", binA2.getCode().toLowerCase());
        long lineA2Id = data(postJson(BASE + "/" + id + "/counts", byCode, COMPANY_A, staff1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unexpected", is(false)))
                .andExpect(jsonPath("$.data.countedQuantity", is(0.001)))).path("lineId").asLong();

        // Ghi theo lineId
        Map<String, Object> byLine = countBody(null, null, null, "4.25", "SET");
        byLine.put("lineId", lineA2Id);
        postJson(BASE + "/" + id + "/counts", byLine, COMPANY_A, manager1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countedQuantity", is(4.25)));

        // Hàng phát sinh không có trong ảnh chụp
        postJson(BASE + "/" + id + "/counts", countBody(binB1.getId(), PRODUCT_1, "NEW-LOT", "2", "SET"), COMPANY_A, staff1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unexpected", is(true)));

        getAs(BASE + "/" + id, COMPANY_A, staff1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLines", is(3)))
                .andExpect(jsonPath("$.data.countedLines", is(3)))
                .andExpect(jsonPath("$.data.uncountedLines", is(0)));

        getAs(BASE + "/" + id + "/count-lines?counted=false", COMPANY_A, staff1)
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @DisplayName("Kiểm tra dữ liệu đếm: ADD bằng 0, vị trí ngoài phạm vi, đợt chưa bắt đầu, dòng của đợt khác")
    void countValidation() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "5", "0");
        long draftId = data(postJson(BASE, locationScope(warehouse1.getId(), List.of(binA2.getId())), COMPANY_A, manager1)).path("id").asLong();
        postJson(BASE + "/" + draftId + "/counts", countBody(binA2.getId(), PRODUCT_1, null, "1", "ADD"), COMPANY_A, staff1)
                .andExpect(status().isConflict());

        long id = createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, null, "0", "ADD"), COMPANY_A, staff1)
                .andExpect(status().isBadRequest());
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, null, "-1", "SET"), COMPANY_A, staff1)
                .andExpect(status().isBadRequest());
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, null, "1.0005", "SET"), COMPANY_A, staff1)
                .andExpect(status().isBadRequest());
        postJson(BASE + "/" + id + "/counts", countBody(binA2.getId(), PRODUCT_1, null, "1", "ADD"), COMPANY_A, staff1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("không thuộc phạm vi")));
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), null, null, "1", "ADD"), COMPANY_A, staff1)
                .andExpect(status().isBadRequest());

        long otherId = createAndStart(locationScope(warehouse1.getId(), List.of(binA2.getId())), COMPANY_A, manager1);
        stock(COMPANY_A, binA2, PRODUCT_2, null, "1", "0");
        long lineOfFirst = TenantContext.callAs(COMPANY_A, () -> lineRepository.findBySessionIdOrdered(id).get(0).getId());
        Map<String, Object> wrongLine = countBody(null, null, null, "1", "SET");
        wrongLine.put("lineId", lineOfFirst);
        postJson(BASE + "/" + otherId + "/counts", wrongLine, COMPANY_A, staff1)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Đóng băng chặn xuất kho chỉ định vị trí đang kiểm (409 nêu mã đợt), xuất tự động bỏ qua vị trí đó, vị trí khác không bị ảnh hưởng")
    void freezeBlocksDeductionOnFrozenLocationOnly() throws Exception {
        StockItem frozenItem = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        StockItem freeItem = stock(COMPANY_A, binA2, PRODUCT_1, null, "10.000", "0");
        long id = createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);
        String code = data(getAs(BASE + "/" + id, COMPANY_A, manager1)).path("code").asText();

        DeductStockRequest fromFrozen = new DeductStockRequest(null, "MANUAL-1", warehouse1.getId(), "MANUAL", null,
                List.of(new DeductStockItemRequest(PRODUCT_1, "SKU-1", new BigDecimal("1.000"), binA1.getId(), null)));
        postJson("/api/inventory/stock/deduct", fromFrozen, COMPANY_A, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.message", containsString(code)))
                .andExpect(jsonPath("$.message", containsString(binA1.getCode())))
                .andExpect(jsonPath("$.data.sessionCode", is(code)))
                .andExpect(jsonPath("$.data.binLocationId", is(binA1.getId().intValue())));

        DeductStockRequest fromFree = new DeductStockRequest(null, "MANUAL-2", warehouse1.getId(), "MANUAL", null,
                List.of(new DeductStockItemRequest(PRODUCT_1, "SKU-1", new BigDecimal("3.000"), binA2.getId(), null)));
        postJson("/api/inventory/stock/deduct", fromFree, COMPANY_A, admin).andExpect(status().isOk());

        DeductStockRequest auto = new DeductStockRequest(null, "MANUAL-3", warehouse1.getId(), "MANUAL", null,
                List.of(new DeductStockItemRequest(PRODUCT_1, "SKU-1", new BigDecimal("4.000"), null, null)));
        postJson("/api/inventory/stock/deduct", auto, COMPANY_A, admin).andExpect(status().isOk());

        assertThat(onHand(COMPANY_A, frozenItem.getId())).isEqualByComparingTo("10.000");
        assertThat(onHand(COMPANY_A, freeItem.getId())).isEqualByComparingTo("3.000");

        DeductStockRequest tooMuch = new DeductStockRequest(null, "MANUAL-4", warehouse1.getId(), "MANUAL", null,
                List.of(new DeductStockItemRequest(PRODUCT_1, "SKU-1", new BigDecimal("5.000"), null, null)));
        postJson("/api/inventory/stock/deduct", tooMuch, COMPANY_A, admin).andExpect(status().isBadRequest());
        assertThat(onHand(COMPANY_A, frozenItem.getId())).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("Đóng băng: giữ chỗ cho đơn mới bỏ qua vị trí đang kiểm và lấy ở vị trí khác")
    void freezeSkipsReservationOnFrozenLocation() throws Exception {
        StockItem frozenItem = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        StockItem freeItem = stock(COMPANY_A, binA2, PRODUCT_1, null, "10.000", "0");
        createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);

        OrderCreatedEvent order = new OrderCreatedEvent(7001L, "SO-ST-1", OrderType.SALES, "CONFIRMED", null, 1L,
                warehouse1.getId(), "Kho kiểm kê 1", BigDecimal.TEN, "sales", Instant.now(),
                List.of(new OrderLine(PRODUCT_1, "SKU-1", "SP 1", new BigDecimal("6.000"), "cái", BigDecimal.ONE)));
        TenantContext.runAs(COMPANY_A, () -> stockReservationService.reserveStock(order, "msg-st-1"));

        TenantContext.runAs(COMPANY_A, () -> {
            assertThat(stockItemRepository.findById(frozenItem.getId()).orElseThrow().getReservedQuantity()).isEqualByComparingTo("0");
            assertThat(stockItemRepository.findById(freeItem.getId()).orElseThrow().getReservedQuantity()).isEqualByComparingTo("6.000");
            List<StockReservation> reservations = stockReservationRepository.findByOrderIdAndStatus(7001L, "ACTIVE");
            assertThat(reservations).hasSize(1);
            assertThat(reservations.get(0).getStockItem().getId()).isEqualTo(freeItem.getId());
        });
    }

    @Test
    @DisplayName("Đóng băng: nhập kho (sự kiện order.received) vào vị trí đang kiểm bị từ chối, vị trí khác nhập bình thường")
    void freezeBlocksReceiptIntoFrozenLocation() throws Exception {
        long id = createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);
        String code = data(getAs(BASE + "/" + id, COMPANY_A, manager1)).path("code").asText();

        OrderReceivedEvent intoFrozen = receipt(801L, binA1.getId());
        assertThatThrownBy(() -> TenantContext.runAs(COMPANY_A, () -> stockReceiptService.processOrderReceived(intoFrozen, "m-801")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(code)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        TenantContext.runAs(COMPANY_A, () -> {
            assertThat(stockItemRepository.findByBinLocationId(binA1.getId())).isEmpty();
            assertThat(stockReceiptRepository.existsByGrnId(801L)).isFalse();
        });

        TenantContext.runAs(COMPANY_A, () -> stockReceiptService.processOrderReceived(receipt(802L, binA2.getId()), "m-802"));
        TenantContext.runAs(COMPANY_A, () ->
                assertThat(stockItemRepository.findByBinLocationId(binA2.getId())).hasSize(1));
    }

    private OrderReceivedEvent receipt(Long grnId, Long binId) {
        ReceivedItemLine line = new ReceivedItemLine(PRODUCT_1, "SKU-1", "SP 1", new BigDecimal("5.000"), BigDecimal.ZERO,
                "cái", binId, null, "LOT-R" + grnId, null, null);
        return new OrderReceivedEvent(grnId, "GRN-ST-" + grnId, null, null, warehouse1.getId(), null, "staff01",
                LocalDate.now(), Instant.now(), List.of(line));
    }

    @Test
    @DisplayName("Đóng băng: duyệt xuất điều chuyển từ vị trí đang kiểm và nhận hàng điều chuyển vào vị trí đang kiểm đều bị từ chối 409")
    void freezeBlocksTransferOnFrozenLocations() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        StockItem w2Item = stock(COMPANY_A, binW2, PRODUCT_1, null, "10.000", "0");

        long outTransfer = createTransfer(warehouse1.getId(), warehouse2.getId(), binA1.getId());
        long inTransfer = createTransfer(warehouse2.getId(), warehouse1.getId(), binW2.getId());
        postJson("/api/inventory/transfers/" + inTransfer + "/dispatch", null, COMPANY_A, admin).andExpect(status().isOk());

        long id = createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);
        String code = data(getAs(BASE + "/" + id, COMPANY_A, manager1)).path("code").asText();

        postJson("/api/inventory/transfers/" + outTransfer + "/dispatch", null, COMPANY_A, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(code)));

        long itemId = data(getAs("/api/inventory/transfers/" + inTransfer, COMPANY_A, admin))
                .path("items").get(0).path("id").asLong();
        ReceiveStockTransferRequest intoFrozen = new ReceiveStockTransferRequest(
                List.of(new ReceiveStockTransferItemRequest(itemId, new BigDecimal("2.000"), BigDecimal.ZERO, binA1.getId(), null)), null);
        postJson("/api/inventory/transfers/" + inTransfer + "/receive", intoFrozen, COMPANY_A, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.sessionCode", is(code)));

        ReceiveStockTransferRequest intoFree = new ReceiveStockTransferRequest(
                List.of(new ReceiveStockTransferItemRequest(itemId, new BigDecimal("2.000"), BigDecimal.ZERO, binA2.getId(), null)), null);
        postJson("/api/inventory/transfers/" + inTransfer + "/receive", intoFree, COMPANY_A, admin)
                .andExpect(status().isOk());
        assertThat(onHand(COMPANY_A, w2Item.getId())).isEqualByComparingTo("8.000");
    }

    private long createTransfer(Long sourceWarehouseId, Long destWarehouseId, Long sourceBinId) throws Exception {
        CreateStockTransferRequest request = new CreateStockTransferRequest(sourceWarehouseId, destWarehouseId, Instant.now(),
                "Điều chuyển thử", List.of(new CreateStockTransferItemRequest(PRODUCT_1, "SKU-1", "SP 1", "cái",
                sourceBinId, new BigDecimal("2.000"), null)));
        return data(postJson("/api/inventory/transfers", request, COMPANY_A, admin)
                .andExpect(status().isCreated())).path("id").asLong();
    }

    @Test
    @DisplayName("Đợt có giới hạn sản phẩm chỉ đóng băng các sản phẩm đó tại vị trí")
    void productScopedFreeze() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "10", "0");
        stock(COMPANY_A, binA1, PRODUCT_2, null, "10", "0");
        Map<String, Object> body = locationScope(warehouse1.getId(), List.of(binA1.getId()));
        body.put("productIds", List.of(PRODUCT_1));
        long id = createAndStart(body, COMPANY_A, manager1);
        getAs(BASE + "/" + id, COMPANY_A, manager1)
                .andExpect(jsonPath("$.data.totalLines", is(1)))
                .andExpect(jsonPath("$.data.productIds[0]", is(PRODUCT_1.intValue())));

        DeductStockRequest otherProduct = new DeductStockRequest(null, "M-P2", warehouse1.getId(), "MANUAL", null,
                List.of(new DeductStockItemRequest(PRODUCT_2, "SKU-2", BigDecimal.ONE, binA1.getId(), null)));
        postJson("/api/inventory/stock/deduct", otherProduct, COMPANY_A, admin).andExpect(status().isOk());
        DeductStockRequest scopedProduct = new DeductStockRequest(null, "M-P1", warehouse1.getId(), "MANUAL", null,
                List.of(new DeductStockItemRequest(PRODUCT_1, "SKU-1", BigDecimal.ONE, binA1.getId(), null)));
        postJson("/api/inventory/stock/deduct", scopedProduct, COMPANY_A, admin).andExpect(status().isConflict());

        // Đếm sản phẩm ngoài phạm vi bị từ chối
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_2, null, "1", "ADD"), COMPANY_A, staff1)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Mỗi vị trí chỉ thuộc một đợt đang kiểm; hủy đợt thì mở đóng băng, tồn kho không đổi, vị trí dùng lại được")
    void oneActiveSessionPerLocationAndCancelUnfreezes() throws Exception {
        StockItem item = stock(COMPANY_A, binA2, PRODUCT_1, null, "10.000", "0");
        long first = createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId(), binA2.getId())), COMPANY_A, manager1);
        String firstCode = data(getAs(BASE + "/" + first, COMPANY_A, manager1)).path("code").asText();

        long second = data(postJson(BASE, locationScope(warehouse1.getId(), List.of(binA2.getId(), binB1.getId())), COMPANY_A, manager1)
                .andExpect(status().isCreated())).path("id").asLong();
        postJson(BASE + "/" + second + "/start", null, COMPANY_A, manager1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.sessionCode", is(firstCode)))
                .andExpect(jsonPath("$.data.binLocationId", is(binA2.getId().intValue())));
        getAs(BASE + "/" + second, COMPANY_A, manager1).andExpect(jsonPath("$.data.status", is("DRAFT")));

        postJson(BASE + "/" + first + "/counts", countBody(binA2.getId(), PRODUCT_1, null, "3", "SET"), COMPANY_A, staff1)
                .andExpect(status().isOk());
        postJson(BASE + "/" + first + "/cancel", Map.of("reason", "Lập nhầm phạm vi"), COMPANY_A, manager1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")))
                .andExpect(jsonPath("$.data.closeReason", is("Lập nhầm phạm vi")))
                .andExpect(jsonPath("$.data.closedBy", is("manager01")))
                .andExpect(jsonPath("$.data.locations[0].frozen", is(false)))
                .andExpect(jsonPath("$.data.locations[1].frozen", is(false)));
        assertThat(onHand(COMPANY_A, item.getId())).isEqualByComparingTo("10.000");
        postJson(BASE + "/" + first + "/cancel", null, COMPANY_A, manager1).andExpect(status().isConflict());
        postJson(BASE + "/" + first + "/counts", countBody(binA2.getId(), PRODUCT_1, null, "1", "ADD"), COMPANY_A, staff1)
                .andExpect(status().isConflict());

        DeductStockRequest deduct = new DeductStockRequest(null, "M-1", warehouse1.getId(), "MANUAL", null,
                List.of(new DeductStockItemRequest(PRODUCT_1, "SKU-1", BigDecimal.ONE, binA2.getId(), null)));
        postJson("/api/inventory/stock/deduct", deduct, COMPANY_A, admin).andExpect(status().isOk());

        postJson(BASE + "/" + second + "/start", null, COMPANY_A, manager1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COUNTING")));
        postJson(BASE + "/" + second + "/start", null, COMPANY_A, manager1).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Phân quyền: nhân viên kho không lập hay bắt đầu đợt, quản lý kho khác không thao tác, kế toán chỉ xem, thiếu người dùng thì 401")
    void rolesAndWarehouseScope() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "1", "0");
        postJson(BASE, warehouseScope(warehouse1.getId()), COMPANY_A, staff1).andExpect(status().isForbidden());
        postJson(BASE, warehouseScope(warehouse1.getId()), COMPANY_A, manager2).andExpect(status().isForbidden());
        postJson(BASE, warehouseScope(warehouse1.getId()), COMPANY_A, accountant).andExpect(status().isForbidden());
        postJson(BASE, warehouseScope(warehouse1.getId()), COMPANY_A, null).andExpect(status().isUnauthorized());

        long id = data(postJson(BASE, warehouseScope(warehouse1.getId()), COMPANY_A, manager1)
                .andExpect(status().isCreated())).path("id").asLong();
        postJson(BASE + "/" + id + "/start", null, COMPANY_A, manager2).andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/start", null, COMPANY_A, staff1).andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/start", null, COMPANY_A, admin).andExpect(status().isOk());

        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, null, "1", "ADD"), COMPANY_A, accountant)
                .andExpect(status().isForbidden());
        TestUser staffOfOther = new TestUser("staff02", "WAREHOUSE_STAFF", String.valueOf(warehouse2.getId()));
        postJson(BASE + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, null, "1", "ADD"), COMPANY_A, staffOfOther)
                .andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/cancel", null, COMPANY_A, staff1).andExpect(status().isForbidden());

        getAs(BASE + "/" + id, COMPANY_A, accountant).andExpect(status().isOk());
        getAs(BASE + "/" + id, COMPANY_A, staff1).andExpect(status().isOk());
        getAs(BASE + "/" + id, COMPANY_A, manager2).andExpect(status().isForbidden());
        getAs(BASE + "/" + id + "/count-lines", COMPANY_A, manager2).andExpect(status().isForbidden());
        TestUser sales = new TestUser("sales01", "SALES", String.valueOf(warehouse1.getId()));
        getAs(BASE, COMPANY_A, sales).andExpect(status().isForbidden());

        getAs(BASE, COMPANY_A, manager1).andExpect(jsonPath("$.data.content", hasSize(1)));
        getAs(BASE, COMPANY_A, manager2).andExpect(jsonPath("$.data.content", hasSize(0)));
        getAs(BASE + "?warehouseId=" + warehouse1.getId(), COMPANY_A, manager2).andExpect(status().isForbidden());
        getAs(BASE, COMPANY_A, accountant).andExpect(jsonPath("$.data.totalElements", is(1)));
    }

    @Test
    @DisplayName("Danh sách lọc theo kho, trạng thái, khoảng ngày dự kiến và mã đợt, có phân trang")
    void listFilters() throws Exception {
        Map<String, Object> early = warehouseScope(warehouse1.getId());
        early.put("plannedDate", "2026-11-01");
        long draftId = data(postJson(BASE, early, COMPANY_A, manager1)).path("id").asLong();
        String draftCode = data(getAs(BASE + "/" + draftId, COMPANY_A, manager1)).path("code").asText();
        Map<String, Object> late = warehouseScope(warehouse2.getId());
        late.put("plannedDate", "2026-11-20");
        createAndStart(late, COMPANY_A, admin);

        getAs(BASE + "?status=COUNTING", COMPANY_A, admin)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].warehouseCode", is("WH-ST-02")))
                .andExpect(jsonPath("$.data.content[0].locations").doesNotExist());
        getAs(BASE + "?warehouseId=" + warehouse1.getId(), COMPANY_A, admin)
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        getAs(BASE + "?fromDate=2026-11-10&toDate=2026-11-30", COMPANY_A, admin)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].plannedDate", is("2026-11-20")));
        getAs(BASE + "?keyword=" + draftCode.toLowerCase(), COMPANY_A, admin)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is((int) draftId)));
        getAs(BASE + "?page=0&size=1", COMPANY_A, admin)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.totalPages", is(2)));
    }

    @Test
    @DisplayName("Kiểm tra phạm vi khi lập đợt: khu không tồn tại, vị trí của kho khác, thiếu ngày dự kiến")
    void createValidation() throws Exception {
        Map<String, Object> zones = warehouseScope(warehouse1.getId());
        zones.put("scopeType", "ZONES");
        zones.put("zones", List.of("ZX"));
        postJson(BASE, zones, COMPANY_A, manager1).andExpect(status().isBadRequest());
        zones.put("zones", List.of("za"));
        long zoneId = data(postJson(BASE, zones, COMPANY_A, manager1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.zones[0]", is("ZA")))).path("id").asLong();
        postJson(BASE + "/" + zoneId + "/start", null, COMPANY_A, manager1)
                .andExpect(jsonPath("$.data.totalLocations", is(2)));

        postJson(BASE, locationScope(warehouse1.getId(), List.of(binW2.getId())), COMPANY_A, manager1)
                .andExpect(status().isBadRequest());
        postJson(BASE, locationScope(warehouse1.getId(), List.of(binOfCompanyB.getId())), COMPANY_A, manager1)
                .andExpect(status().isBadRequest());
        Map<String, Object> noDate = warehouseScope(warehouse1.getId());
        noDate.remove("plannedDate");
        postJson(BASE, noDate, COMPANY_A, manager1).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Phân tách doanh nghiệp: B không xem, không bắt đầu, không đếm, không hủy đợt của A; mã đợt được trùng giữa hai doanh nghiệp")
    void companyIsolation() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "5", "0");
        long idA = data(postJson(BASE, warehouseScope(warehouse1.getId()), COMPANY_A, manager1)).path("id").asLong();
        long idB = data(postJson(BASE, warehouseScope(warehouseB.getId()), COMPANY_B, adminB)
                .andExpect(status().isCreated())).path("id").asLong();
        String codeA = data(getAs(BASE + "/" + idA, COMPANY_A, admin)).path("code").asText();
        String codeB = data(getAs(BASE + "/" + idB, COMPANY_B, adminB)).path("code").asText();
        assertThat(codeA).isEqualTo(codeB).startsWith(codePrefix());

        getAs(BASE + "/" + idA, COMPANY_B, adminB).andExpect(status().isNotFound());
        postJson(BASE + "/" + idA + "/start", null, COMPANY_B, adminB).andExpect(status().isNotFound());
        postJson(BASE + "/" + idA + "/start", null, COMPANY_A, admin).andExpect(status().isOk());
        postJson(BASE + "/" + idA + "/counts", countBody(binA1.getId(), PRODUCT_1, null, "1", "ADD"), COMPANY_B, adminB)
                .andExpect(status().isNotFound());
        getAs(BASE + "/" + idA + "/count-lines", COMPANY_B, adminB).andExpect(status().isNotFound());
        postJson(BASE + "/" + idA + "/cancel", null, COMPANY_B, adminB).andExpect(status().isNotFound());
        postJson(BASE, warehouseScope(warehouse1.getId()), COMPANY_B, adminB).andExpect(status().isNotFound());

        getAs(BASE, COMPANY_B, adminB)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is((int) idB)));
        getAs(BASE + "/" + idA, COMPANY_A, admin).andExpect(jsonPath("$.data.status", is("COUNTING")));

        // Vị trí của A bị đóng băng không ảnh hưởng tới B (vị trí của B không bị chặn)
        stock(COMPANY_B, binOfCompanyB, PRODUCT_1, null, "5", "0");
        DeductStockRequest deductB = new DeductStockRequest(null, "M-B", warehouseB.getId(), "MANUAL", null,
                List.of(new DeductStockItemRequest(PRODUCT_1, "SKU-1", BigDecimal.ONE, binOfCompanyB.getId(), null)));
        postJson("/api/inventory/stock/deduct", deductB, COMPANY_B, adminB).andExpect(status().isOk());
    }
}
