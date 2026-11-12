package vn.wms.inventory.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.ReceiveStockTransferItemRequest;
import vn.wms.inventory.dto.ReceiveStockTransferRequest;
import vn.wms.inventory.dto.StockTransferResponse;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử API truy vết nguồn gốc theo số lô (WMS-17.2) qua các luồng nghiệp vụ thật: nhập GRN, điều chuyển, xuất bán.
 */
class LotTraceIntegrationTest extends ReportTestSupport {

    private static final String TRACE = "/api/inventory/reports/lot-trace";
    private static final Long PRODUCT = 7101L;

    private Warehouse wh1;
    private Warehouse wh2;
    private BinLocation bin1;
    private BinLocation bin2;

    private void setUpWarehouses() {
        wh1 = warehouse(COMPANY_A, "WH-T1", "Kho Hà Nội");
        wh2 = warehouse(COMPANY_A, "WH-T2", "Kho Đà Nẵng");
        bin1 = bin(COMPANY_A, wh1, "ZA");
        bin2 = bin(COMPANY_A, wh2, "ZB");
    }

    /** Nhập 100 lô LOT-T vào kho 1, điều chuyển 30 sang kho 2, xuất bán 50 từ kho 1. */
    private StockTransferResponse receiptTransferDispatch() {
        receive(COMPANY_A, 9101L, "GRN-T-0001", 811L, "PO-T-0001", 17L, wh1, bin1, PRODUCT, "SKU-7101", "LOT-T", "100.000");
        StockTransferResponse created = TenantContext.callAs(COMPANY_A, () -> stockTransferService.createTransfer(
                new CreateStockTransferRequest(wh1.getId(), wh2.getId(), Instant.now(), "Điều chuyển lô",
                        List.of(new CreateStockTransferItemRequest(PRODUCT, "SKU-7101", "Sản phẩm SKU-7101", "Thùng",
                                bin1.getId(), new BigDecimal("30.000"), null)))));
        TenantContext.runAs(COMPANY_A, () -> stockTransferService.dispatchTransfer(created.id()));
        Long itemId = created.items().get(0).id();
        StockTransferResponse received = TenantContext.callAs(COMPANY_A, () -> stockTransferService.receiveTransfer(
                created.id(), new ReceiveStockTransferRequest(List.of(new ReceiveStockTransferItemRequest(
                        itemId, new BigDecimal("30.000"), BigDecimal.ZERO, bin2.getId(), null)), null)));
        dispatch(COMPANY_A, 601L, "SO-T-0601", wh1, bin1, PRODUCT, "SKU-7101", "50.000");
        return received;
    }

    @Test
    @DisplayName("Truy vết lô qua nhập kho -> điều chuyển -> xuất bán: nguồn gốc, vị trí hiện tại, nơi đã đi, chuỗi thời gian, cây")
    void traceLotThroughReceiptTransferDispatch() throws Exception {
        setUpWarehouses();
        StockTransferResponse transfer = receiptTransferDispatch();

        JsonNode t = data(asAdmin(TRACE + "?lotNo=LOT-T", COMPANY_A));
        assertThat(t.path("productId").asLong()).isEqualTo(PRODUCT);
        assertThat(t.path("skuCode").asText()).isEqualTo("SKU-7101");
        assertThat(t.path("expDate").asText()).isEqualTo("2027-10-01");

        JsonNode origins = t.path("origins");
        assertThat(origins).hasSize(1);
        assertThat(origins.get(0).path("movementType").asText()).isEqualTo("RECEIPT");
        assertThat(origins.get(0).path("documentNumber").asText()).isEqualTo("GRN-T-0001");
        assertThat(origins.get(0).path("referenceNumber").asText()).isEqualTo("PO-T-0001");
        assertThat(origins.get(0).path("supplierId").asLong()).isEqualTo(17L);
        assertThat(origins.get(0).path("warehouseName").asText()).isEqualTo("Kho Hà Nội");
        assertThat(origins.get(0).path("performedBy").asText()).isEqualTo("thukho01");
        assertThat(dec(origins.get(0).path("quantity"))).isEqualByComparingTo("100");

        JsonNode locations = t.path("currentLocations");
        assertThat(locations).hasSize(1);
        assertThat(locations.get(0).path("warehouseId").asLong()).isEqualTo(wh1.getId());
        assertThat(dec(locations.get(0).path("quantity"))).isEqualByComparingTo("20");
        assertThat(locations.get(0).path("locked").asBoolean()).isFalse();
        assertThat(locations.get(0).path("expired").asBoolean()).isFalse();

        JsonNode outflows = t.path("outflows");
        assertThat(outflows).hasSize(2);
        JsonNode trf = outflows.get(0);
        assertThat(trf.path("movementType").asText()).isEqualTo("TRANSFER_OUT");
        assertThat(trf.path("documentNumber").asText()).isEqualTo(transfer.transferNumber());
        assertThat(dec(trf.path("quantity"))).isEqualByComparingTo("30");
        assertThat(trf.path("transfer").path("destinationWarehouseId").asLong()).isEqualTo(wh2.getId());
        assertThat(trf.path("transfer").path("destinationWarehouseName").asText()).isEqualTo("Kho Đà Nẵng");
        assertThat(trf.path("transfer").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(dec(trf.path("transfer").path("itemReceivedQuantity"))).isEqualByComparingTo("30");
        assertThat(trf.path("transfer").path("lotTrackedAtDestination").asBoolean()).isFalse();
        JsonNode so = outflows.get(1);
        assertThat(so.path("movementType").asText()).isEqualTo("SALES_DISPATCH");
        assertThat(so.path("documentNumber").asText()).isEqualTo("SO-T-0601");
        assertThat(so.path("documentId").asLong()).isEqualTo(601L);
        assertThat(dec(so.path("quantity"))).isEqualByComparingTo("50");

        JsonNode timeline = t.path("timeline");
        assertThat(timeline).hasSize(3);
        assertThat(dec(timeline.get(0).path("lotBalanceAfter"))).isEqualByComparingTo("100");
        assertThat(dec(timeline.get(1).path("lotBalanceAfter"))).isEqualByComparingTo("70");
        assertThat(dec(timeline.get(2).path("lotBalanceAfter"))).isEqualByComparingTo("20");

        JsonNode summary = t.path("summary");
        assertThat(dec(summary.path("totalReceived"))).isEqualByComparingTo("100");
        assertThat(dec(summary.path("totalTransferredOut"))).isEqualByComparingTo("30");
        assertThat(dec(summary.path("totalSalesDispatched"))).isEqualByComparingTo("50");
        assertThat(dec(summary.path("ledgerBalance"))).isEqualByComparingTo(dec(summary.path("currentOnHand")));

        JsonNode tree = t.path("tree");
        assertThat(tree.path("nodeType").asText()).isEqualTo("LOT");
        List<String> childTypes = new ArrayList<>();
        tree.path("children").forEach(c -> childTypes.add(c.path("nodeType").asText()));
        assertThat(childTypes).containsExactly("GOODS_RECEIPT", "LOCATION", "TRANSFER", "SALES_ORDER");
        JsonNode grnNode = tree.path("children").get(0);
        assertThat(grnNode.path("children").get(0).path("nodeType").asText()).isEqualTo("PURCHASE_ORDER");
        assertThat(grnNode.path("children").get(0).path("children").get(0).path("nodeType").asText()).isEqualTo("SUPPLIER");
        assertThat(tree.path("children").get(2).path("children").get(0).path("nodeType").asText())
                .isEqualTo("TRANSFER_DESTINATION");
        assertThat(t.path("notes").toString()).contains("chưa lưu số lô").contains("Tên nhà cung cấp");
    }

    @Test
    @DisplayName("Số lô trùng giữa hai sản phẩm: không chọn sản phẩm thì 409 kèm danh sách, chọn sản phẩm thì truy vết được")
    void ambiguousLotRequiresProduct() throws Exception {
        setUpWarehouses();
        receive(COMPANY_A, 9201L, "GRN-T-0201", null, null, null, wh1, bin1, 7201L, "SKU-7201", "LOT-DUP", "5.000");
        receive(COMPANY_A, 9202L, "GRN-T-0202", null, null, null, wh1, bin1, 7202L, "SKU-7202", "LOT-DUP", "6.000");

        mockMvc.perform(asAdmin(TRACE + "?lotNo=LOT-DUP", COMPANY_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.data.candidates.length()").value(2));
        JsonNode t = data(asAdmin(TRACE + "?lotNo=LOT-DUP&productId=7202", COMPANY_A));
        assertThat(dec(t.path("summary").path("currentOnHand"))).isEqualByComparingTo("6");
        mockMvc.perform(asAdmin(TRACE + "?lotNo=KHONG-CO", COMPANY_A)).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(TRACE, COMPANY_A).param("lotNo", "   ")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Phạm vi kho: quản lý kho khác không thấy lô; vai trò không được xem bị 403")
    void warehouseScope() throws Exception {
        setUpWarehouses();
        receive(COMPANY_A, 9301L, "GRN-T-0301", null, null, null, wh1, bin1, PRODUCT, "SKU-7101", "LOT-S", "5.000");

        mockMvc.perform(asRole(TRACE + "?lotNo=LOT-S", COMPANY_A, "WAREHOUSE_MANAGER", wh2.getId()))
                .andExpect(status().isNotFound());
        JsonNode own = data(asRole(TRACE + "?lotNo=LOT-S", COMPANY_A, "WAREHOUSE_STAFF", wh1.getId()));
        assertThat(own.path("warehouseIds").get(0).asLong()).isEqualTo(wh1.getId());
        assertThat(own.path("origins")).hasSize(1);
        mockMvc.perform(asRole(TRACE + "?lotNo=LOT-S", COMPANY_A, "PURCHASING", wh1.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cách ly doanh nghiệp: cùng số lô ở hai doanh nghiệp, mỗi bên chỉ thấy lô của mình")
    void companyIsolation() throws Exception {
        setUpWarehouses();
        Warehouse whB = warehouse(COMPANY_B, "WH-T1", "Kho của B");
        BinLocation binB = bin(COMPANY_B, whB, "ZA");
        receive(COMPANY_A, 9401L, "GRN-T-0401", null, null, null, wh1, bin1, PRODUCT, "SKU-7101", "LOT-ISO", "5.000");
        receive(COMPANY_B, 9401L, "GRN-T-0401", null, null, null, whB, binB, PRODUCT, "SKU-7101", "LOT-ISO", "77.000");
        receive(COMPANY_B, 9402L, "GRN-T-0402", null, null, null, whB, binB, PRODUCT, "SKU-7101", "LOT-ONLY-B", "1.000");

        JsonNode a = data(asAdmin(TRACE + "?lotNo=LOT-ISO", COMPANY_A));
        assertThat(dec(a.path("summary").path("currentOnHand"))).isEqualByComparingTo("5");
        assertThat(a.path("currentLocations")).hasSize(1);
        JsonNode b = data(asAdmin(TRACE + "?lotNo=LOT-ISO", COMPANY_B));
        assertThat(dec(b.path("summary").path("currentOnHand"))).isEqualByComparingTo("77");
        mockMvc.perform(asAdmin(TRACE + "?lotNo=LOT-ONLY-B", COMPANY_A)).andExpect(status().isNotFound());
    }
}
