package vn.wms.inventory.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.client.ProductInfo;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.ReceiveStockTransferItemRequest;
import vn.wms.inventory.dto.ReceiveStockTransferRequest;
import vn.wms.inventory.dto.StockTransferResponse;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockMovement;
import vn.wms.inventory.entity.StockMovementType;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.dto.report.InOutBalanceQuery;
import vn.wms.inventory.dto.report.InOutBalanceReportResponse;
import vn.wms.inventory.dto.report.StockCardResponse;
import vn.wms.inventory.service.report.StockReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử sổ kho và API báo cáo Xuất - Nhập - Tồn, thẻ kho chi tiết (WMS-17.1).
 */
class StockReportIntegrationTest extends ReportTestSupport {

    private static final String REPORT = "/api/inventory/reports/in-out-balance";
    private static final String CARD = "/api/inventory/reports/stock-card";
    private static final Long PRODUCT = 7001L;

    @Autowired
    private StockReportService stockReportService;

    private Warehouse wh1;
    private Warehouse wh2;
    private BinLocation bin1;
    private BinLocation bin2;

    private void setUpTwoWarehouses() {
        wh1 = warehouse(COMPANY_A, "WH-R1", "Kho Hà Nội");
        wh2 = warehouse(COMPANY_A, "WH-R2", "Kho Đà Nẵng");
        bin1 = bin(COMPANY_A, wh1, "ZA");
        bin2 = bin(COMPANY_A, wh2, "ZB");
    }

    /** Nhập 100,250 -> xuất bán 30,125 -> điều chuyển 20 sang kho 2 qua đúng các service nghiệp vụ. */
    private StockTransferResponse runReceiptDispatchTransfer() {
        receive(COMPANY_A, 9001L, "GRN-R-0001", 801L, "PO-R-0001", 7L, wh1, bin1, PRODUCT, "SKU-7001", "LOT-A", "100.250");
        dispatch(COMPANY_A, 501L, "SO-R-0501", wh1, bin1, PRODUCT, "SKU-7001", "30.125");
        StockTransferResponse created = TenantContext.callAs(COMPANY_A, () -> stockTransferService.createTransfer(
                new CreateStockTransferRequest(wh1.getId(), wh2.getId(), Instant.now(), "Điều chuyển test",
                        List.of(new CreateStockTransferItemRequest(PRODUCT, "SKU-7001", "Sản phẩm SKU-7001", "Thùng",
                                bin1.getId(), new BigDecimal("20.000"), null)))));
        TenantContext.runAs(COMPANY_A, () -> stockTransferService.dispatchTransfer(created.id()));
        Long itemId = created.items().get(0).id();
        return TenantContext.callAs(COMPANY_A, () -> stockTransferService.receiveTransfer(created.id(),
                new ReceiveStockTransferRequest(List.of(new ReceiveStockTransferItemRequest(
                        itemId, new BigDecimal("20.000"), BigDecimal.ZERO, bin2.getId(), null)), null)));
    }

    private static String today() {
        return LocalDate.now(StockReportService.REPORT_ZONE).toString();
    }

    @Test
    @DisplayName("Nhập, xuất bán, điều chuyển ghi sổ kho; báo cáo khớp tồn thực tế và tồn đầu + nhập - xuất = tồn cuối")
    void ledgerMatchesStockAfterReceiptDispatchTransfer() throws Exception {
        setUpTwoWarehouses();
        StockTransferResponse transfer = runReceiptDispatchTransfer();

        JsonNode report = data(asAdmin(REPORT + "?fromDate=" + today() + "&toDate=" + today(), COMPANY_A));
        JsonNode rows = report.path("rows").path("content");
        assertThat(rows).hasSize(2);
        JsonNode r1 = rows.get(0).path("warehouseId").asLong() == wh1.getId() ? rows.get(0) : rows.get(1);
        JsonNode r2 = rows.get(0).path("warehouseId").asLong() == wh2.getId() ? rows.get(0) : rows.get(1);

        assertThat(r1.path("warehouseCode").asText()).isEqualTo("WH-R1");
        assertThat(r1.path("skuCode").asText()).isEqualTo("SKU-7001");
        assertThat(dec(r1.path("openingQuantity"))).isEqualByComparingTo("0");
        assertThat(dec(r1.path("quantityIn"))).isEqualByComparingTo("100.250");
        assertThat(dec(r1.path("quantityOut"))).isEqualByComparingTo("50.125");
        assertThat(dec(r1.path("closingQuantity"))).isEqualByComparingTo("50.125");
        assertThat(r1.path("closingValue").isNull()).isTrue();

        assertThat(dec(r2.path("quantityIn"))).isEqualByComparingTo("20.000");
        assertThat(dec(r2.path("quantityOut"))).isEqualByComparingTo("0");
        assertThat(dec(r2.path("closingQuantity"))).isEqualByComparingTo("20.000");

        for (JsonNode r : rows) {
            assertThat(dec(r.path("openingQuantity")).add(dec(r.path("quantityIn"))).subtract(dec(r.path("quantityOut"))))
                    .isEqualByComparingTo(dec(r.path("closingQuantity")));
        }
        JsonNode totals = report.path("totals");
        assertThat(dec(totals.path("quantityIn"))).isEqualByComparingTo("120.250");
        assertThat(dec(totals.path("quantityOut"))).isEqualByComparingTo("50.125");
        assertThat(dec(totals.path("closingQuantity"))).isEqualByComparingTo("70.125");
        assertThat(report.path("quantitiesComplete").asBoolean()).isTrue();
        assertThat(report.path("dataNote").isNull()).isTrue();
        assertThat(report.path("valuationNote").asText()).contains("chưa có giá vốn");

        // Tồn cuối kỳ của sổ kho bằng đúng tồn thực tế trong stock_items
        BigDecimal stockWh1 = TenantContext.callAs(COMPANY_A, () -> stockItemRepository.findByBinLocationId(bin1.getId())
                .stream().map(StockItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal stockWh2 = TenantContext.callAs(COMPANY_A, () -> stockItemRepository.findByBinLocationId(bin2.getId())
                .stream().map(StockItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(stockWh1).isEqualByComparingTo(dec(r1.path("closingQuantity")));
        assertThat(stockWh2).isEqualByComparingTo(dec(r2.path("closingQuantity")));

        // Gộp mọi kho: một dòng cho sản phẩm, không có kho
        JsonNode grouped = data(asAdmin(REPORT + "?groupBy=PRODUCT&fromDate=" + today() + "&toDate=" + today(), COMPANY_A));
        assertThat(grouped.path("rows").path("content")).hasSize(1);
        assertThat(grouped.path("rows").path("content").get(0).path("warehouseId").isNull()).isTrue();
        assertThat(dec(grouped.path("rows").path("content").get(0).path("closingQuantity"))).isEqualByComparingTo("70.125");

        // Thẻ kho kho 1: nhập GRN, xuất bán SO, xuất điều chuyển; số dư lũy kế từng dòng
        JsonNode card = data(asAdmin(CARD + "?productId=" + PRODUCT + "&warehouseId=" + wh1.getId()
                + "&fromDate=" + today() + "&toDate=" + today(), COMPANY_A));
        JsonNode lines = card.path("lines").path("content");
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).path("movementType").asText()).isEqualTo("RECEIPT");
        assertThat(lines.get(0).path("documentNumber").asText()).isEqualTo("GRN-R-0001");
        assertThat(lines.get(0).path("referenceNumber").asText()).isEqualTo("PO-R-0001");
        assertThat(lines.get(0).path("partnerId").asLong()).isEqualTo(7L);
        assertThat(lines.get(0).path("lotNo").asText()).isEqualTo("LOT-A");
        assertThat(lines.get(0).path("performedBy").asText()).isEqualTo("thukho01");
        assertThat(dec(lines.get(0).path("balanceAfter"))).isEqualByComparingTo("100.250");
        assertThat(lines.get(1).path("movementType").asText()).isEqualTo("SALES_DISPATCH");
        assertThat(lines.get(1).path("documentNumber").asText()).isEqualTo("SO-R-0501");
        assertThat(lines.get(1).path("documentId").asLong()).isEqualTo(501L);
        assertThat(dec(lines.get(1).path("balanceAfter"))).isEqualByComparingTo("70.125");
        assertThat(lines.get(2).path("movementType").asText()).isEqualTo("TRANSFER_OUT");
        assertThat(lines.get(2).path("documentNumber").asText()).isEqualTo(transfer.transferNumber());
        assertThat(lines.get(2).path("documentId").asLong()).isEqualTo(transfer.id());
        assertThat(lines.get(2).path("counterpartWarehouseId").asLong()).isEqualTo(wh2.getId());
        assertThat(dec(lines.get(2).path("balanceAfter"))).isEqualByComparingTo("50.125");
        assertThat(dec(card.path("openingQuantity"))).isEqualByComparingTo("0");
        assertThat(dec(card.path("totalIn"))).isEqualByComparingTo("100.250");
        assertThat(dec(card.path("totalOut"))).isEqualByComparingTo("50.125");
        assertThat(dec(card.path("closingQuantity"))).isEqualByComparingTo("50.125");
        assertThat(card.path("warehouseName").asText()).isEqualTo("Kho Hà Nội");

        // Thẻ kho kho 2: dòng nhận điều chuyển
        JsonNode card2 = data(asAdmin(CARD + "?productId=" + PRODUCT + "&warehouseId=" + wh2.getId()
                + "&fromDate=" + today() + "&toDate=" + today(), COMPANY_A));
        assertThat(card2.path("lines").path("content")).hasSize(1);
        assertThat(card2.path("lines").path("content").get(0).path("movementType").asText()).isEqualTo("TRANSFER_IN");
        assertThat(card2.path("lines").path("content").get(0).path("counterpartWarehouseId").asLong()).isEqualTo(wh1.getId());
    }

    @Test
    @DisplayName("Biên ngày theo giờ Việt Nam: 23:59:59 ngày cuối tính vào kỳ, 00:00 ngày đầu tháng sau thì không")
    void dateRangeEdgesUseVietnamTime() throws Exception {
        setUpTwoWarehouses();
        // 31/10 23:59:59.999 giờ VN -> trước kỳ
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.RECEIPT, "10.000", Instant.parse("2026-10-31T16:59:59.999Z"));
        // 01/11 00:00 giờ VN -> trong kỳ
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.RECEIPT, "5.500", Instant.parse("2026-10-31T17:00:00Z"));
        // 30/11 23:59:59 giờ VN -> trong kỳ
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.SALES_DISPATCH, "3.250", Instant.parse("2026-11-30T16:59:59Z"));
        // 01/12 00:00 giờ VN -> sau kỳ
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.SALES_DISPATCH, "1.000", Instant.parse("2026-11-30T17:00:00Z"));
        // Sản phẩm nhập rồi xuất hết từ trước kỳ: không có dòng trong báo cáo
        movement(COMPANY_A, wh1, 7002L, StockMovementType.RECEIPT, "4.000", Instant.parse("2026-10-05T03:00:00Z"));
        movement(COMPANY_A, wh1, 7002L, StockMovementType.SALES_DISPATCH, "4.000", Instant.parse("2026-10-06T03:00:00Z"));

        JsonNode report = data(asAdmin(REPORT + "?fromDate=2026-11-01&toDate=2026-11-30", COMPANY_A));
        JsonNode rows = report.path("rows").path("content");
        assertThat(rows).hasSize(1);
        assertThat(dec(rows.get(0).path("openingQuantity"))).isEqualByComparingTo("10.000");
        assertThat(dec(rows.get(0).path("quantityIn"))).isEqualByComparingTo("5.500");
        assertThat(dec(rows.get(0).path("quantityOut"))).isEqualByComparingTo("3.250");
        assertThat(dec(rows.get(0).path("closingQuantity"))).isEqualByComparingTo("12.250");

        JsonNode card = data(asAdmin(CARD + "?productId=" + PRODUCT + "&warehouseId=" + wh1.getId()
                + "&fromDate=2026-11-01&toDate=2026-11-30", COMPANY_A));
        assertThat(card.path("lines").path("content")).hasSize(2);
        assertThat(dec(card.path("openingQuantity"))).isEqualByComparingTo("10.000");
        assertThat(dec(card.path("lines").path("content").get(0).path("balanceAfter"))).isEqualByComparingTo("15.500");
        assertThat(dec(card.path("lines").path("content").get(1).path("balanceAfter"))).isEqualByComparingTo("12.250");
        assertThat(dec(card.path("closingQuantity"))).isEqualByComparingTo("12.250");
    }

    @Test
    @DisplayName("Phân trang, sắp xếp theo tồn cuối giảm dần; tổng cộng tính trên mọi dòng; thẻ kho trang 2 giữ đúng số dư lũy kế")
    void pagingSortingAndTotals() throws Exception {
        setUpTwoWarehouses();
        Instant t = Instant.parse("2026-11-10T03:00:00Z");
        for (int i = 1; i <= 5; i++) {
            movement(COMPANY_A, wh1, 8000L + i, StockMovementType.RECEIPT, i + ".111", t.plusSeconds(i));
        }
        JsonNode page1 = data(asAdmin(REPORT + "?fromDate=2026-11-01&toDate=2026-11-30&sort=closingQuantity"
                + "&direction=desc&page=1&size=2", COMPANY_A));
        assertThat(page1.path("rows").path("totalElements").asLong()).isEqualTo(5);
        assertThat(page1.path("rows").path("totalPages").asInt()).isEqualTo(3);
        assertThat(page1.path("rows").path("content")).hasSize(2);
        assertThat(page1.path("rows").path("content").get(0).path("productId").asLong()).isEqualTo(8003L);
        assertThat(page1.path("rows").path("content").get(1).path("productId").asLong()).isEqualTo(8002L);
        assertThat(dec(page1.path("totals").path("closingQuantity"))).isEqualByComparingTo("15.555");

        // Thẻ kho 5 dòng, trang 2 cỡ 2: số dư bắt đầu từ tổng 2 dòng đầu
        for (int i = 1; i <= 5; i++) {
            movement(COMPANY_A, wh2, 9100L, i % 2 == 0 ? StockMovementType.SALES_DISPATCH : StockMovementType.RECEIPT,
                    "1.001", t.plusSeconds(100 + i));
        }
        JsonNode card = data(asAdmin(CARD + "?productId=9100&warehouseId=" + wh2.getId()
                + "&fromDate=2026-11-01&toDate=2026-11-30&page=1&size=2", COMPANY_A));
        assertThat(card.path("lines").path("totalElements").asLong()).isEqualTo(5);
        // Dòng 1 +1.001, dòng 2 -1.001, dòng 3 +1.001 (=1.001), dòng 4 -1.001 (=0)
        assertThat(dec(card.path("lines").path("content").get(0).path("balanceAfter"))).isEqualByComparingTo("1.001");
        assertThat(dec(card.path("lines").path("content").get(1).path("balanceAfter"))).isEqualByComparingTo("0");
        assertThat(dec(card.path("closingQuantity"))).isEqualByComparingTo("1.001");

        mockMvc.perform(asAdmin(REPORT + "?fromDate=2026-11-01&toDate=2026-11-30&size=500", COMPANY_A))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(REPORT + "?fromDate=2026-11-01&toDate=2026-11-30&sort=price", COMPANY_A))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Kỳ bắt đầu trước lúc có sổ kho: tồn đầu, nhập, xuất trả null kèm giải thích; tồn cuối vẫn đúng")
    void periodBeforeLedgerStartReturnsNullWithExplanation() throws Exception {
        setUpTwoWarehouses();
        Instant start = Instant.parse("2026-11-10T05:00:00Z");
        ledgerStart(COMPANY_A, start);
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.OPENING_BALANCE, "40.000", start);
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.SALES_DISPATCH, "5.000", start.plusSeconds(86400 * 2));

        JsonNode straddle = data(asAdmin(REPORT + "?fromDate=2026-11-01&toDate=2026-11-30", COMPANY_A));
        JsonNode row = straddle.path("rows").path("content").get(0);
        assertThat(row.path("openingQuantity").isNull()).isTrue();
        assertThat(row.path("quantityIn").isNull()).isTrue();
        assertThat(row.path("quantityOut").isNull()).isTrue();
        assertThat(dec(row.path("closingQuantity"))).isEqualByComparingTo("35.000");
        assertThat(straddle.path("quantitiesComplete").asBoolean()).isFalse();
        assertThat(straddle.path("dataNote").asText()).contains("bắt đầu ghi");
        assertThat(straddle.path("totals").path("openingQuantity").isNull()).isTrue();
        assertThat(straddle.path("ledgerStartedAt").asText()).isEqualTo("2026-11-10T05:00:00Z");

        JsonNode after = data(asAdmin(REPORT + "?fromDate=2026-11-11&toDate=2026-11-30", COMPANY_A));
        JsonNode afterRow = after.path("rows").path("content").get(0);
        assertThat(dec(afterRow.path("openingQuantity"))).isEqualByComparingTo("40.000");
        assertThat(dec(afterRow.path("quantityOut"))).isEqualByComparingTo("5.000");
        assertThat(after.path("dataNote").isNull()).isTrue();

        JsonNode before = data(asAdmin(REPORT + "?fromDate=2026-10-01&toDate=2026-10-31", COMPANY_A));
        assertThat(before.path("totals").path("closingQuantity").isNull()).isTrue();

        // Thẻ kho của kỳ chứa mốc mở sổ: dòng số dư mở sổ có số dư lũy kế đúng
        JsonNode card = data(asAdmin(CARD + "?productId=" + PRODUCT + "&warehouseId=" + wh1.getId()
                + "&fromDate=2026-11-01&toDate=2026-11-30", COMPANY_A));
        assertThat(card.path("openingQuantity").isNull()).isTrue();
        assertThat(card.path("totalIn").isNull()).isTrue();
        assertThat(card.path("lines").path("content").get(0).path("movementType").asText()).isEqualTo("OPENING_BALANCE");
        assertThat(dec(card.path("lines").path("content").get(1).path("balanceAfter"))).isEqualByComparingTo("35.000");
        assertThat(dec(card.path("closingQuantity"))).isEqualByComparingTo("35.000");
    }

    @Test
    @DisplayName("Phạm vi kho: quản lý chỉ thấy kho được gán, vai trò không được xem bị 403, thiếu người dùng bị 401")
    void warehouseScopeAndRoles() throws Exception {
        setUpTwoWarehouses();
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.RECEIPT, "1.000", Instant.parse("2026-11-10T03:00:00Z"));
        movement(COMPANY_A, wh2, PRODUCT, StockMovementType.RECEIPT, "2.000", Instant.parse("2026-11-10T03:00:00Z"));
        String range = "fromDate=2026-11-01&toDate=2026-11-30";

        JsonNode manager = data(asRole(REPORT + "?" + range, COMPANY_A, "WAREHOUSE_MANAGER", wh1.getId()));
        assertThat(manager.path("rows").path("content")).hasSize(1);
        assertThat(manager.path("rows").path("content").get(0).path("warehouseId").asLong()).isEqualTo(wh1.getId());
        assertThat(manager.path("warehouseIds").get(0).asLong()).isEqualTo(wh1.getId());
        assertThat(dec(manager.path("totals").path("closingQuantity"))).isEqualByComparingTo("1.000");

        mockMvc.perform(asRole(REPORT + "?" + range + "&warehouseId=" + wh2.getId(), COMPANY_A, "WAREHOUSE_STAFF", wh1.getId()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(asRole(CARD + "?" + range + "&productId=" + PRODUCT + "&warehouseId=" + wh2.getId(),
                        COMPANY_A, "WAREHOUSE_MANAGER", wh1.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(asRole(REPORT + "?" + range, COMPANY_A, "SALES", wh1.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(asRole(REPORT + "?" + range, COMPANY_A, "WAREHOUSE_STAFF"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(REPORT + "?" + range).header("X-Company-Id", COMPANY_A))
                .andExpect(status().isUnauthorized());

        JsonNode accountant = data(asRole(REPORT + "?" + range, COMPANY_A, "ACCOUNTANT"));
        assertThat(accountant.path("rows").path("content")).hasSize(2);
        assertThat(accountant.path("warehouseIds").isNull()).isTrue();
    }

    @Test
    @DisplayName("Cách ly doanh nghiệp: SQL tổng hợp và thẻ kho không lẫn số liệu của doanh nghiệp khác")
    void companyIsolation() throws Exception {
        setUpTwoWarehouses();
        Warehouse whB = warehouse(COMPANY_B, "WH-R1", "Kho của B");
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.RECEIPT, "1.000", Instant.parse("2026-11-10T03:00:00Z"));
        movement(COMPANY_B, whB, PRODUCT, StockMovementType.RECEIPT, "999.000", Instant.parse("2026-11-10T03:00:00Z"));
        String range = "fromDate=2026-11-01&toDate=2026-11-30";

        JsonNode a = data(asAdmin(REPORT + "?" + range, COMPANY_A));
        assertThat(a.path("rows").path("totalElements").asLong()).isEqualTo(1);
        assertThat(dec(a.path("totals").path("closingQuantity"))).isEqualByComparingTo("1.000");
        JsonNode aGrouped = data(asAdmin(REPORT + "?groupBy=PRODUCT&" + range, COMPANY_A));
        assertThat(dec(aGrouped.path("rows").path("content").get(0).path("closingQuantity"))).isEqualByComparingTo("1.000");

        JsonNode b = data(asAdmin(REPORT + "?" + range, COMPANY_B));
        assertThat(b.path("rows").path("totalElements").asLong()).isEqualTo(1);
        assertThat(dec(b.path("totals").path("closingQuantity"))).isEqualByComparingTo("999.000");

        mockMvc.perform(asAdmin(REPORT + "?" + range + "&warehouseId=" + whB.getId(), COMPANY_A))
                .andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(CARD + "?" + range + "&productId=" + PRODUCT + "&warehouseId=" + whB.getId(), COMPANY_A))
                .andExpect(status().isNotFound());
        // Ghi sổ không đổi được dữ liệu của B
        assertThat(TenantContext.callAs(COMPANY_B, () -> stockMovementRepository.count())).isEqualTo(1);
    }

    @Test
    @DisplayName("Lọc theo danh mục qua product-service; dịch vụ sản phẩm lỗi thì trả 503 thay vì bỏ bộ lọc")
    void categoryFilterUsesProductService() throws Exception {
        setUpTwoWarehouses();
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.RECEIPT, "1.000", Instant.parse("2026-11-10T03:00:00Z"));
        movement(COMPANY_A, wh1, 7999L, StockMovementType.RECEIPT, "2.000", Instant.parse("2026-11-10T03:00:00Z"));
        String range = "fromDate=2026-11-01&toDate=2026-11-30";

        when(productClient.searchProducts(isNull(), eq(5L), anyInt(), anyInt())).thenReturn(ApiResponse.ok(
                PageResponse.of(List.of(new ProductInfo(PRODUCT, "SKU-7001", "Gạo", 5L, "Lương thực", "BAG", "Túi")),
                        0, 100, 1)));
        JsonNode filtered = data(asAdmin(REPORT + "?" + range + "&categoryId=5", COMPANY_A));
        assertThat(filtered.path("rows").path("content")).hasSize(1);
        assertThat(filtered.path("rows").path("content").get(0).path("productId").asLong()).isEqualTo(PRODUCT);

        when(productClient.searchProducts(eq("khong-co"), isNull(), anyInt(), anyInt())).thenReturn(ApiResponse.ok(
                PageResponse.of(List.of(), 0, 100, 0)));
        JsonNode empty = data(asAdmin(REPORT + "?" + range + "&keyword=khong-co", COMPANY_A));
        assertThat(empty.path("rows").path("totalElements").asLong()).isZero();

        when(productClient.searchProducts(any(), eq(6L), anyInt(), anyInt())).thenThrow(new RuntimeException("down"));
        mockMvc.perform(asAdmin(REPORT + "?" + range + "&categoryId=6", COMPANY_A))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Kiểm tra tham số: thiếu ngày, ngày cuối trước ngày đầu, khoảng quá 366 ngày đều trả 400")
    void validatesDateRange() throws Exception {
        mockMvc.perform(asAdmin(REPORT + "?toDate=2026-11-30", COMPANY_A)).andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(REPORT + "?fromDate=2026-11-30&toDate=2026-11-01", COMPANY_A))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(asAdmin(REPORT + "?fromDate=2025-01-01&toDate=2026-11-30", COMPANY_A))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(REPORT + "?fromDate=2026-13-01&toDate=2026-11-30", COMPANY_A))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(CARD + "?fromDate=2026-11-01&toDate=2026-11-30&productId=1", COMPANY_A))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Phương thức cho bộ xuất Excel trả toàn bộ dòng trong một trang, cùng số liệu với API")
    void exportMethodsReturnAllRows() {
        setUpTwoWarehouses();
        Instant t = Instant.parse("2026-11-10T03:00:00Z");
        for (int i = 1; i <= 3; i++) {
            movement(COMPANY_A, wh1, 8100L + i, StockMovementType.RECEIPT, "2.000", t.plusSeconds(i));
            movement(COMPANY_A, wh1, 8100L + i, StockMovementType.SALES_DISPATCH, "0.500", t.plusSeconds(10 + i));
        }
        // Bộ xuất Excel chạy trong request của người dùng: dựng request có header như gateway chuyển tới
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Name", "ketoan");
        request.addHeader("X-User-Roles", "ACCOUNTANT");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            InOutBalanceReportResponse all = TenantContext.callAs(COMPANY_A, () ->
                    stockReportService.getInOutBalanceForExport(new InOutBalanceQuery(null, LocalDate.of(2026, 11, 1),
                            LocalDate.of(2026, 11, 30), null, null, null, null, null, null, 0, 1)));
            assertThat(all.rows().content()).hasSize(3);
            assertThat(all.rows().totalElements()).isEqualTo(3);
            assertThat(all.rows().content()).allSatisfy(r -> {
                assertThat(r.closingQuantity()).isEqualByComparingTo("1.500");
                assertThat(r.closingValue()).isNull();
            });

            StockCardResponse card = TenantContext.callAs(COMPANY_A, () -> stockReportService.getStockCardForExport(
                    8101L, wh1.getId(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
            assertThat(card.lines().content()).hasSize(2);
            assertThat(card.lines().content().get(1).balanceAfter()).isEqualByComparingTo("1.500");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
