package vn.wms.inventory.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.common.messaging.event.ReceivedItemLine;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.client.ProductInfo;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.ReceiveStockTransferItemRequest;
import vn.wms.inventory.dto.ReceiveStockTransferRequest;
import vn.wms.inventory.dto.StockTransferResponse;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockMovementType;
import vn.wms.inventory.entity.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API kho của dashboard (WMS-16.4): tỷ trọng tồn kho theo nhóm hàng và giao dịch kho gần nhất, gồm cách tính, vai
 * trò, phạm vi kho và cách ly doanh nghiệp. Tồn kho và sổ kho được tạo qua đúng các service nghiệp vụ.
 */
class InventoryDashboardIntegrationTest extends ReportTestSupport {

    private static final String BY_CATEGORY = "/api/inventory/dashboard/stock-by-category";
    private static final String RECENT = "/api/inventory/dashboard/recent-transactions";

    private Warehouse wh1;
    private Warehouse wh2;
    private BinLocation bin1;
    private BinLocation bin2;

    private void setUpWarehouses() {
        wh1 = warehouse(COMPANY_A, "WH-D1", "Kho Hà Nội");
        wh2 = warehouse(COMPANY_A, "WH-D2", "Kho Đà Nẵng");
        bin1 = bin(COMPANY_A, wh1, "ZA");
        bin2 = bin(COMPANY_A, wh2, "ZB");
    }

    /** Danh mục hàng hóa giả lập theo doanh nghiệp đang gọi (Feign chạy trong ngữ cảnh doanh nghiệp của request). */
    private void stubCatalog(Map<Long, List<ProductInfo>> byCompany) {
        when(productClient.listCatalog(anyInt(), anyInt(), anyString())).thenAnswer(inv -> {
            List<ProductInfo> products = byCompany.getOrDefault(TenantContext.getCompanyId(), List.of());
            return ApiResponse.ok(PageResponse.of(products, 0, 100, products.size()));
        });
    }

    private static ProductInfo product(Long id, Long categoryId, String categoryName) {
        return new ProductInfo(id, "SKU-" + id, "Sản phẩm " + id, categoryId, categoryName, "BOX", "Hộp");
    }

    /** Nhập một phiếu GRN nhiều dòng qua service xử lý sự kiện order.received. */
    private void receiveLines(Long company, Long grnId, String grnNumber, Warehouse warehouse, BinLocation bin,
                              Map<Long, String> quantities) {
        List<ReceivedItemLine> lines = new ArrayList<>();
        quantities.forEach((productId, qty) -> lines.add(new ReceivedItemLine(productId, "SKU-" + productId,
                "Sản phẩm " + productId, new BigDecimal(qty), BigDecimal.ZERO, "Hộp", bin.getId(), bin.getCode(),
                null, null, null)));
        OrderReceivedEvent event = new OrderReceivedEvent(grnId, grnNumber, 800L, "PO-D-0001", warehouse.getId(),
                7L, "thukho01", LocalDate.now(), Instant.now(), lines);
        TenantContext.runAs(company, () -> stockReceiptService.processOrderReceived(event, "msg-" + grnNumber));
    }

    @Test
    @DisplayName("Tỷ trọng theo nhóm hàng tính theo số lượng tồn thực tế; sản phẩm không có nhóm vào nhóm có tên riêng")
    void stockByCategoryGroupsOnHandQuantity() throws Exception {
        setUpWarehouses();
        Map<Long, String> lines = new HashMap<>();
        lines.put(7101L, "10");
        lines.put(7102L, "30");
        lines.put(7103L, "5");
        lines.put(7104L, "5");
        receiveLines(COMPANY_A, 9101L, "GRN-D-0001", wh1, bin1, lines);
        // Giữ chỗ không làm giảm tồn thực tế: xuất bán 4 của sản phẩm 7102 thì tồn còn 26
        dispatch(COMPANY_A, 501L, "SO-D-0501", wh1, bin1, 7102L, "SKU-7102", "4");
        stubCatalog(Map.of(COMPANY_A, List.of(
                product(7101L, 1L, "Đồ uống"),
                product(7102L, 2L, "Bánh kẹo"),
                product(7103L, null, null))));
        // 7104 không còn trong danh mục: cũng vào nhóm chưa phân nhóm

        JsonNode report = data(asAdmin(BY_CATEGORY + "?dateRange=THIS_MONTH", COMPANY_A));
        assertThat(report.path("dateRange").asText()).isEqualTo("THIS_MONTH");
        assertThat(report.path("basis").asText()).isEqualTo("QUANTITY");
        assertThat(report.path("valuationMethod").asText()).isEqualTo("NOT_AVAILABLE");
        assertThat(report.path("valueVisible").asBoolean()).isTrue();
        assertThat(report.path("totalValue").isNull()).isTrue();
        assertThat(report.path("basisNote").asText()).contains("chưa có giá vốn");
        assertThat(dec(report.path("totalQuantity"))).isEqualByComparingTo("46");
        assertThat(report.path("productCount").asInt()).isEqualTo(4);
        assertThat(report.path("categoryCount").asInt()).isEqualTo(3);

        JsonNode slices = report.path("slices");
        assertThat(slices).hasSize(3);
        assertThat(slices.get(0).path("name").asText()).isEqualTo("Bánh kẹo");
        assertThat(slices.get(0).path("kind").asText()).isEqualTo("CATEGORY");
        assertThat(slices.get(0).path("categoryId").asLong()).isEqualTo(2L);
        assertThat(dec(slices.get(0).path("quantity"))).isEqualByComparingTo("26");
        assertThat(slices.get(0).path("percentage").asDouble()).isEqualTo(56.5);
        assertThat(slices.get(0).path("value").isNull()).isTrue();
        JsonNode drinks = slices.get(1).path("name").asText().equals("Đồ uống") ? slices.get(1) : slices.get(2);
        JsonNode uncategorized = slices.get(1).path("kind").asText().equals("UNCATEGORIZED") ? slices.get(1) : slices.get(2);
        assertThat(dec(drinks.path("quantity"))).isEqualByComparingTo("10");
        assertThat(uncategorized.path("name").asText()).isEqualTo("Chưa phân nhóm hàng");
        assertThat(uncategorized.path("categoryId").isNull()).isTrue();
        assertThat(dec(uncategorized.path("quantity"))).isEqualByComparingTo("10");
        assertThat(uncategorized.path("productCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Tỷ trọng theo nhóm hàng: B không thấy tồn của A; kho lọc theo phạm vi; vai trò không xem tài chính")
    void stockByCategoryIsolationScopeAndRoles() throws Exception {
        setUpWarehouses();
        receiveLines(COMPANY_A, 9201L, "GRN-D-0002", wh1, bin1, Map.of(7101L, "10"));
        receiveLines(COMPANY_A, 9202L, "GRN-D-0003", wh2, bin2, Map.of(7102L, "20"));
        Warehouse whB = warehouse(COMPANY_B, "WH-D1", "Kho của B");
        BinLocation binB = bin(COMPANY_B, whB, "ZA");
        receiveLines(COMPANY_B, 9301L, "GRN-D-0002", whB, binB, Map.of(7101L, "999"));
        stubCatalog(Map.of(
                COMPANY_A, List.of(product(7101L, 1L, "Đồ uống"), product(7102L, 2L, "Bánh kẹo")),
                COMPANY_B, List.of(product(7101L, 9L, "Nhóm của B"))));

        JsonNode reportA = data(asAdmin(BY_CATEGORY, COMPANY_A));
        assertThat(dec(reportA.path("totalQuantity"))).isEqualByComparingTo("30");
        assertThat(reportA.path("warehouseIds").isNull()).isTrue();
        JsonNode reportB = data(asAdmin(BY_CATEGORY, COMPANY_B));
        assertThat(dec(reportB.path("totalQuantity"))).isEqualByComparingTo("999");
        assertThat(reportB.path("slices")).hasSize(1);
        assertThat(reportB.path("slices").get(0).path("name").asText()).isEqualTo("Nhóm của B");
        // B không mở được kho của A
        mockMvc.perform(asAdmin(BY_CATEGORY + "?warehouseId=" + wh1.getId(), COMPANY_B))
                .andExpect(status().isNotFound());

        // Nhân viên kho 2 chỉ thấy tồn kho 2, không được xem giá trị
        JsonNode staff = data(asRole(BY_CATEGORY, COMPANY_A, "WAREHOUSE_STAFF", wh2.getId()));
        assertThat(dec(staff.path("totalQuantity"))).isEqualByComparingTo("20");
        assertThat(staff.path("valueVisible").asBoolean()).isFalse();
        assertThat(staff.path("slices").get(0).path("name").asText()).isEqualTo("Bánh kẹo");
        // Bán hàng được gán kho 1 vẫn xem được tỷ trọng (dashboard mở cho sáu vai trò)
        JsonNode sales = data(asRole(BY_CATEGORY, COMPANY_A, "SALES", wh1.getId()));
        assertThat(dec(sales.path("totalQuantity"))).isEqualByComparingTo("10");
        // Xin kho ngoài phạm vi: 403; chưa được gán kho: 403
        mockMvc.perform(asRole(BY_CATEGORY + "?warehouseId=" + wh1.getId(), COMPANY_A, "WAREHOUSE_STAFF", wh2.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(asRole(BY_CATEGORY, COMPANY_A, "PURCHASING"))
                .andExpect(status().isForbidden());
        // Khoảng thời gian sai: 400
        mockMvc.perform(asAdmin(BY_CATEGORY + "?dateRange=LAST_YEAR", COMPANY_A))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tỷ trọng theo nhóm hàng: product-service lỗi thì 503; không có tồn thì không gọi product-service")
    void stockByCategoryProductServiceDownAndEmpty() throws Exception {
        setUpWarehouses();
        JsonNode empty = data(asAdmin(BY_CATEGORY, COMPANY_A));
        assertThat(empty.path("slices")).isEmpty();
        assertThat(dec(empty.path("totalQuantity"))).isEqualByComparingTo("0");
        verify(productClient, never()).listCatalog(anyInt(), anyInt(), anyString());

        receiveLines(COMPANY_A, 9401L, "GRN-D-0004", wh1, bin1, Map.of(7101L, "10"));
        when(productClient.listCatalog(anyInt(), anyInt(), anyString())).thenThrow(new RuntimeException("down"));
        mockMvc.perform(asAdmin(BY_CATEGORY, COMPANY_A))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Giao dịch gần nhất: gom theo chứng từ và kho, đủ loại nhập, xuất bán, điều chuyển; bỏ số dư mở sổ")
    void recentTransactionsGroupDocuments() throws Exception {
        setUpWarehouses();
        Map<Long, String> lines = new HashMap<>();
        lines.put(7101L, "10");
        lines.put(7102L, "30");
        receiveLines(COMPANY_A, 9501L, "GRN-D-0005", wh1, bin1, lines);
        dispatch(COMPANY_A, 601L, "SO-D-0601", wh1, bin1, 7102L, "SKU-7102", "4");
        StockTransferResponse created = TenantContext.callAs(COMPANY_A, () -> stockTransferService.createTransfer(
                new CreateStockTransferRequest(wh1.getId(), wh2.getId(), Instant.now(), "Điều chuyển test",
                        List.of(new CreateStockTransferItemRequest(7101L, "SKU-7101", "Sản phẩm 7101", "Hộp",
                                bin1.getId(), new BigDecimal("3"), null)))));
        TenantContext.runAs(COMPANY_A, () -> stockTransferService.dispatchTransfer(created.id()));
        Long itemId = created.items().get(0).id();
        TenantContext.runAs(COMPANY_A, () -> stockTransferService.receiveTransfer(created.id(),
                new ReceiveStockTransferRequest(List.of(new ReceiveStockTransferItemRequest(
                        itemId, new BigDecimal("3"), BigDecimal.ZERO, bin2.getId(), null)), null)));
        movement(COMPANY_A, wh1, 7199L, StockMovementType.OPENING_BALANCE, "100", Instant.now());
        // Một chứng từ cũ 40 ngày: không thuộc "7 ngày qua" nhưng có trong "toàn bộ thời gian"
        movement(COMPANY_A, wh1, 7198L, StockMovementType.ISSUE_OTHER, "1", Instant.now().minus(40, ChronoUnit.DAYS));

        JsonNode report = data(asAdmin(RECENT + "?dateRange=7_DAYS", COMPANY_A));
        assertThat(report.path("limit").asInt()).isEqualTo(10);
        JsonNode rows = report.path("rows");
        assertThat(rows).hasSize(4);
        Map<String, JsonNode> byType = new HashMap<>();
        rows.forEach(r -> byType.put(r.path("movementType").asText(), r));
        assertThat(byType).containsOnlyKeys("RECEIPT", "SALES_DISPATCH", "TRANSFER_OUT", "TRANSFER_IN");

        JsonNode receipt = byType.get("RECEIPT");
        assertThat(receipt.path("documentType").asText()).isEqualTo("GOODS_RECEIPT");
        assertThat(receipt.path("documentId").asLong()).isEqualTo(9501L);
        assertThat(receipt.path("documentNumber").asText()).isEqualTo("GRN-D-0005");
        assertThat(receipt.path("movementTypeLabel").asText()).isEqualTo("Nhập kho theo phiếu nhập");
        assertThat(receipt.path("direction").asText()).isEqualTo("IN");
        assertThat(dec(receipt.path("quantity"))).isEqualByComparingTo("40");
        assertThat(receipt.path("productCount").asLong()).isEqualTo(2);
        assertThat(receipt.path("lineCount").asLong()).isEqualTo(2);
        assertThat(receipt.path("warehouseName").asText()).isEqualTo("Kho Hà Nội");
        assertThat(receipt.path("warehouseCode").asText()).isEqualTo("WH-D1");
        assertThat(receipt.path("performedBy").asText()).isEqualTo("thukho01");
        assertThat(receipt.path("occurredAt").isTextual()).isTrue();
        assertThat(receipt.path("stocktakeId").isNull()).isTrue();

        JsonNode sale = byType.get("SALES_DISPATCH");
        assertThat(sale.path("documentType").asText()).isEqualTo("SALES_ORDER");
        assertThat(sale.path("documentNumber").asText()).isEqualTo("SO-D-0601");
        assertThat(sale.path("direction").asText()).isEqualTo("OUT");
        assertThat(dec(sale.path("quantity"))).isEqualByComparingTo("4");
        assertThat(sale.path("performedBy").asText()).isEqualTo("thukho02");

        JsonNode out = byType.get("TRANSFER_OUT");
        assertThat(out.path("documentType").asText()).isEqualTo("STOCK_TRANSFER");
        assertThat(out.path("documentId").asLong()).isEqualTo(created.id());
        assertThat(out.path("warehouseId").asLong()).isEqualTo(wh1.getId());
        assertThat(out.path("counterpartWarehouseName").asText()).isEqualTo("Kho Đà Nẵng");
        assertThat(dec(out.path("quantity"))).isEqualByComparingTo("3");
        JsonNode in = byType.get("TRANSFER_IN");
        assertThat(in.path("warehouseId").asLong()).isEqualTo(wh2.getId());
        assertThat(in.path("counterpartWarehouseName").asText()).isEqualTo("Kho Hà Nội");

        // Giới hạn số dòng và toàn bộ thời gian (có cả chứng từ cũ, vẫn không có số dư mở sổ)
        assertThat(data(asAdmin(RECENT + "?dateRange=7_DAYS&limit=2", COMPANY_A)).path("rows")).hasSize(2);
        JsonNode allTime = data(asAdmin(RECENT + "?dateRange=ALL_TIME", COMPANY_A));
        assertThat(allTime.path("rows")).hasSize(5);
        assertThat(allTime.path("fromTime").isNull()).isTrue();
        allTime.path("rows").forEach(r -> assertThat(r.path("movementType").asText()).isNotEqualTo("OPENING_BALANCE"));
        mockMvc.perform(asAdmin(RECENT + "?limit=51", COMPANY_A)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Giao dịch gần nhất: mới nhất trước theo thời điểm phát sinh")
    void recentTransactionsNewestFirst() throws Exception {
        setUpWarehouses();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        movement(COMPANY_A, wh1, 7301L, StockMovementType.ISSUE_OTHER, "1", now.minus(3, ChronoUnit.HOURS));
        movement(COMPANY_A, wh1, 7302L, StockMovementType.ISSUE_OTHER, "2", now.minus(1, ChronoUnit.HOURS));
        movement(COMPANY_A, wh1, 7303L, StockMovementType.ISSUE_OTHER, "3", now.minus(2, ChronoUnit.HOURS));

        JsonNode rows = data(asAdmin(RECENT + "?dateRange=ALL_TIME", COMPANY_A)).path("rows");
        assertThat(rows).hasSize(3);
        assertThat(dec(rows.get(0).path("quantity"))).isEqualByComparingTo("2");
        assertThat(dec(rows.get(1).path("quantity"))).isEqualByComparingTo("3");
        assertThat(dec(rows.get(2).path("quantity"))).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("Giao dịch gần nhất: B không thấy chứng từ của A; kho lọc theo phạm vi; SALES, PURCHASING bị từ chối")
    void recentTransactionsIsolationScopeAndRoles() throws Exception {
        setUpWarehouses();
        receiveLines(COMPANY_A, 9601L, "GRN-D-0006", wh1, bin1, Map.of(7101L, "10"));
        receiveLines(COMPANY_A, 9602L, "GRN-D-0007", wh2, bin2, Map.of(7101L, "20"));
        Warehouse whB = warehouse(COMPANY_B, "WH-D1", "Kho của B");
        BinLocation binB = bin(COMPANY_B, whB, "ZA");
        receiveLines(COMPANY_B, 9701L, "GRN-D-0006", whB, binB, Map.of(7101L, "999"));

        JsonNode rowsB = data(asAdmin(RECENT, COMPANY_B)).path("rows");
        assertThat(rowsB).hasSize(1);
        assertThat(rowsB.get(0).path("documentId").asLong()).isEqualTo(9701L);
        assertThat(dec(rowsB.get(0).path("quantity"))).isEqualByComparingTo("999");
        assertThat(rowsB.get(0).path("warehouseName").asText()).isEqualTo("Kho của B");
        assertThat(data(asAdmin(RECENT, COMPANY_A)).path("rows")).hasSize(2);
        mockMvc.perform(asAdmin(RECENT + "?warehouseId=" + wh1.getId(), COMPANY_B))
                .andExpect(status().isNotFound());

        // Quản lý kho 2 chỉ thấy phiếu của kho 2; kế toán thấy mọi kho
        JsonNode manager = data(asRole(RECENT, COMPANY_A, "WAREHOUSE_MANAGER", wh2.getId()));
        assertThat(manager.path("rows")).hasSize(1);
        assertThat(manager.path("rows").get(0).path("documentNumber").asText()).isEqualTo("GRN-D-0007");
        assertThat(data(asRole(RECENT, COMPANY_A, "ACCOUNTANT")).path("rows")).hasSize(2);
        mockMvc.perform(asRole(RECENT + "?warehouseId=" + wh1.getId(), COMPANY_A, "WAREHOUSE_STAFF", wh2.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(asRole(RECENT, COMPANY_A, "SALES", wh1.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(asRole(RECENT, COMPANY_A, "PURCHASING", wh1.getId()))
                .andExpect(status().isForbidden());
    }
}
