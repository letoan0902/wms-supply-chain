package vn.wms.inventory.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockMovement;
import vn.wms.inventory.entity.StockMovementType;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.service.report.StockReportExcelWriter;
import vn.wms.inventory.service.report.StockReportService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử xuất Excel báo cáo Xuất - Nhập - Tồn và thẻ kho (WMS-17.3): file mở được bằng POI, dòng dữ liệu và dòng
 * tổng khớp với API JSON cùng bộ lọc, ô null để trống, quyền, cách ly doanh nghiệp, lỗi vẫn là JSON.
 */
class StockReportExportIntegrationTest extends ReportTestSupport {

    private static final String REPORT = "/api/inventory/reports/in-out-balance";
    private static final String CARD = "/api/inventory/reports/stock-card";
    private static final Long PRODUCT = 7001L;
    private static final String RANGE = "fromDate=2026-11-01&toDate=2026-11-30";

    private Warehouse wh1;
    private Warehouse wh2;
    private BinLocation bin1;

    private void setUp() {
        wh1 = warehouse(COMPANY_A, "WH-X1", "Kho Hà Nội");
        wh2 = warehouse(COMPANY_A, "WH-X2", "Kho Đà Nẵng");
        bin1 = bin(COMPANY_A, wh1, "ZA");
        bin(COMPANY_A, wh2, "ZB");
    }

    private MockHttpServletResponse download(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).isEqualTo(StockReportExcelWriter.CONTENT_TYPE);
        return response;
    }

    private static XSSFWorkbook open(MockHttpServletResponse response) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
    }

    private static String text(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null || cell.getCellType() == CellType.BLANK ? null : cell.getStringCellValue();
    }

    private static BigDecimal number(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null || cell.getCellType() == CellType.BLANK ? null : BigDecimal.valueOf(cell.getNumericCellValue());
    }

    private static boolean blank(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null || cell.getCellType() == CellType.BLANK;
    }

    private void seedMovements() {
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.RECEIPT, "100.250", Instant.parse("2026-11-10T03:00:00Z"));
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.SALES_DISPATCH, "30.125", Instant.parse("2026-11-11T03:00:00Z"));
        movement(COMPANY_A, wh2, 7002L, StockMovementType.RECEIPT, "7.500", Instant.parse("2026-11-10T04:00:00Z"));
    }

    @Test
    @DisplayName("Xuất Excel Xuất - Nhập - Tồn: header đúng, dòng dữ liệu và dòng tổng bằng API JSON, số thập phân chính xác")
    void inOutBalanceExportMatchesJson() throws Exception {
        setUp();
        seedMovements();

        MockHttpServletResponse response = download(asAdmin(REPORT + "/export?" + RANGE, COMPANY_A));
        assertThat(response.getHeader("Content-Disposition")).isEqualTo(
                "attachment; filename=\"bao-cao-xuat-nhap-ton_2026-11-01_2026-11-30.xlsx\"; "
                        + "filename*=UTF-8''bao-cao-xuat-nhap-ton_2026-11-01_2026-11-30.xlsx");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");

        JsonNode json = data(asAdmin(REPORT + "?" + RANGE, COMPANY_A));
        JsonNode jsonRows = json.path("rows").path("content");
        assertThat(jsonRows).hasSize(2);

        try (XSSFWorkbook wb = open(response)) {
            Sheet sheet = wb.getSheet("Xuat_Nhap_Ton");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("BÁO CÁO XUẤT - NHẬP - TỒN KHO");
            assertThat(text(sheet.getRow(1), 0)).contains("Kỳ: 01/11/2026 - 30/11/2026").contains("Kho: Tất cả kho của doanh nghiệp");
            Row header = sheet.getRow(2);
            assertThat(text(header, 0)).isEqualTo("STT");
            assertThat(text(header, 4)).isEqualTo("Kho");
            assertThat(text(header, 8)).isEqualTo("Tồn cuối kỳ");
            assertThat(wb.getFontAt(header.getCell(0).getCellStyle().getFontIndex()).getBold()).isTrue();
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 3);

            for (int i = 0; i < jsonRows.size(); i++) {
                JsonNode j = jsonRows.get(i);
                Row r = sheet.getRow(3 + i);
                assertThat(r.getCell(0).getNumericCellValue()).isEqualTo(i + 1);
                assertThat(text(r, 1)).isEqualTo(j.path("skuCode").asText());
                assertThat(text(r, 4)).isEqualTo(j.path("warehouseCode").asText() + " - " + j.path("warehouseName").asText());
                assertThat(number(r, 5)).isEqualByComparingTo(dec(j.path("openingQuantity")));
                assertThat(number(r, 6)).isEqualByComparingTo(dec(j.path("quantityIn")));
                assertThat(number(r, 7)).isEqualByComparingTo(dec(j.path("quantityOut")));
                assertThat(number(r, 8)).isEqualByComparingTo(dec(j.path("closingQuantity")));
            }
            Row first = sheet.getRow(3);
            assertThat(number(first, 6)).isEqualByComparingTo("100.250");
            assertThat(number(first, 7)).isEqualByComparingTo("30.125");
            assertThat(number(first, 8)).isEqualByComparingTo("70.125");
            assertThat(first.getCell(6).getCellStyle().getDataFormatString()).isEqualTo("#,##0.000");

            Row total = sheet.getRow(3 + jsonRows.size());
            assertThat(text(total, 0)).isEqualTo("Tổng cộng");
            JsonNode t = json.path("totals");
            assertThat(number(total, 5)).isEqualByComparingTo(dec(t.path("openingQuantity")));
            assertThat(number(total, 6)).isEqualByComparingTo("107.750");
            assertThat(number(total, 7)).isEqualByComparingTo("30.125");
            assertThat(number(total, 8)).isEqualByComparingTo("77.625");

            List<String> notes = new ArrayList<>();
            for (int i = total.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                String note = text(sheet.getRow(i), 0);
                if (note != null) {
                    notes.add(note);
                }
            }
            assertThat(notes).anyMatch(n -> n.startsWith("Giá trị tiền: "));
            assertThat(notes).anyMatch(n -> n.startsWith("Lập lúc: "));
            assertThat(notes).noneMatch(n -> n.startsWith("Ghi chú số liệu"));
        }
    }

    @Test
    @DisplayName("Gom theo sản phẩm bỏ cột Kho; bộ lọc kho và sản phẩm ghi vào dòng điều kiện")
    void inOutBalanceGroupByProductAndFilters() throws Exception {
        setUp();
        seedMovements();
        movement(COMPANY_A, wh2, PRODUCT, StockMovementType.RECEIPT, "1.000", Instant.parse("2026-11-12T03:00:00Z"));

        try (XSSFWorkbook wb = open(download(asAdmin(REPORT + "/export?groupBy=PRODUCT&productId=" + PRODUCT + "&" + RANGE,
                COMPANY_A)))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(text(sheet.getRow(1), 0)).contains("Sản phẩm: ").contains("theo sản phẩm (gộp các kho)");
            assertThat(text(sheet.getRow(2), 4)).isEqualTo("Tồn đầu kỳ");
            assertThat(number(sheet.getRow(3), 7)).isEqualByComparingTo("71.125");
            assertThat(text(sheet.getRow(4), 0)).isEqualTo("Tổng cộng");
        }
        try (XSSFWorkbook wb = open(download(asAdmin(REPORT + "/export?warehouseId=" + wh2.getId() + "&" + RANGE, COMPANY_A)))) {
            assertThat(text(wb.getSheetAt(0).getRow(1), 0)).contains("Kho: WH-X2 - Kho Đà Nẵng");
        }
    }

    @Test
    @DisplayName("Kỳ bắt đầu trước lúc có sổ kho: ô số lượng null để trống (không ghi 0), có ghi chú số liệu")
    void nullQuantitiesAreBlankCells() throws Exception {
        setUp();
        Instant start = Instant.parse("2026-11-10T05:00:00Z");
        ledgerStart(COMPANY_A, start);
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.OPENING_BALANCE, "40.000", start);
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.SALES_DISPATCH, "5.000", start.plusSeconds(86400 * 2));

        try (XSSFWorkbook wb = open(download(asAdmin(REPORT + "/export?" + RANGE, COMPANY_A)))) {
            Sheet sheet = wb.getSheetAt(0);
            Row row = sheet.getRow(3);
            assertThat(blank(row, 5)).isTrue();
            assertThat(blank(row, 6)).isTrue();
            assertThat(blank(row, 7)).isTrue();
            assertThat(number(row, 8)).isEqualByComparingTo("35.000");
            Row total = sheet.getRow(4);
            assertThat(blank(total, 5)).isTrue();
            assertThat(blank(total, 6)).isTrue();
            assertThat(number(total, 8)).isEqualByComparingTo("35.000");
            boolean hasDataNote = false;
            for (int i = 5; i <= sheet.getLastRowNum(); i++) {
                String note = text(sheet.getRow(i), 0);
                hasDataNote |= note != null && note.startsWith("Ghi chú số liệu: ") && note.contains("bắt đầu ghi");
            }
            assertThat(hasDataNote).isTrue();
        }

        try (XSSFWorkbook wb = open(download(asAdmin(CARD + "/export?productId=" + PRODUCT + "&warehouseId=" + wh1.getId()
                + "&" + RANGE, COMPANY_A)))) {
            Sheet sheet = wb.getSheetAt(0);
            Row opening = sheet.getRow(3);
            assertThat(text(opening, 2)).isEqualTo("Tồn đầu kỳ");
            assertThat(blank(opening, 10)).isTrue();
            Row total = sheet.getRow(3 + 1 + 2);
            assertThat(text(total, 2)).isEqualTo("Cộng phát sinh trong kỳ");
            assertThat(blank(total, 8)).isTrue();
            assertThat(blank(total, 9)).isTrue();
        }
    }

    @Test
    @DisplayName("Xuất Excel thẻ kho: dòng khớp API JSON, ngày là ô ngày dd/MM/yyyy, tên file ASCII")
    void stockCardExportMatchesJson() throws Exception {
        setUp();
        receive(COMPANY_A, 9101L, "GRN-X-0001", 801L, "PO-X-0001", 7L, wh1, bin1, PRODUCT, "SKU-7001", "LOT-A", "100.250");
        dispatch(COMPANY_A, 501L, "SO-X-0501", wh1, bin1, PRODUCT, "SKU-7001", "30.125");
        String today = LocalDate.now(StockReportService.REPORT_ZONE).toString();
        String query = "productId=" + PRODUCT + "&warehouseId=" + wh1.getId() + "&fromDate=" + today + "&toDate=" + today;

        MockHttpServletResponse response = download(asAdmin(CARD + "/export?" + query, COMPANY_A));
        String expectedName = "the-kho_SKU-7001_WH-X1_" + today + "_" + today + ".xlsx";
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"" + expectedName + "\"; filename*=UTF-8''" + expectedName);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");

        JsonNode json = data(asAdmin(CARD + "?" + query, COMPANY_A));
        JsonNode lines = json.path("lines").path("content");
        assertThat(lines).hasSize(2);
        try (XSSFWorkbook wb = open(response)) {
            Sheet sheet = wb.getSheet("The_kho");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("THẺ KHO");
            assertThat(text(sheet.getRow(1), 0)).contains("Sản phẩm: SKU-7001").contains("Kho: WH-X1 - Kho Hà Nội");
            assertThat(text(sheet.getRow(2), 12)).isEqualTo("Diễn giải");
            assertThat(number(sheet.getRow(3), 10)).isEqualByComparingTo(dec(json.path("openingQuantity")));
            for (int i = 0; i < lines.size(); i++) {
                JsonNode j = lines.get(i);
                Row r = sheet.getRow(4 + i);
                assertThat(DateUtil.isCellDateFormatted(r.getCell(1))).isTrue();
                assertThat(r.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("dd/MM/yyyy HH:mm");
                assertThat(text(r, 2)).isEqualTo(j.path("movementTypeLabel").asText());
                assertThat(text(r, 3)).contains(j.path("documentNumber").asText());
                if (!j.path("quantityIn").isNull() && dec(j.path("quantityIn")).signum() != 0) {
                    assertThat(number(r, 8)).isEqualByComparingTo(dec(j.path("quantityIn")));
                }
                assertThat(number(r, 10)).isEqualByComparingTo(dec(j.path("balanceAfter")));
            }
            Row receipt = sheet.getRow(4);
            assertThat(text(receipt, 5)).isEqualTo("LOT-A");
            assertThat(DateUtil.isCellDateFormatted(receipt.getCell(6))).isTrue();
            assertThat(receipt.getCell(6).getCellStyle().getDataFormatString()).isEqualTo("dd/MM/yyyy");
            assertThat(receipt.getCell(6).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2027, 10, 1));
            assertThat(number(receipt, 10)).isEqualByComparingTo("100.250");
            Row total = sheet.getRow(4 + lines.size());
            assertThat(number(total, 8)).isEqualByComparingTo("100.250");
            assertThat(number(total, 9)).isEqualByComparingTo("30.125");
            Row closing = sheet.getRow(5 + lines.size());
            assertThat(text(closing, 2)).isEqualTo("Tồn cuối kỳ");
            assertThat(number(closing, 10)).isEqualByComparingTo("70.125");
        }
    }

    @Test
    @DisplayName("Vai trò và phạm vi kho như API xem: 403 với SALES, kho không được gán; quản lý chỉ thấy kho của mình")
    void rolesAndWarehouseScope() throws Exception {
        setUp();
        seedMovements();

        for (String url : List.of(REPORT + "/export?" + RANGE,
                CARD + "/export?productId=" + PRODUCT + "&warehouseId=" + wh1.getId() + "&" + RANGE)) {
            mockMvc.perform(asRole(url, COMPANY_A, "SALES", wh1.getId()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(asRole(url, COMPANY_A, "PURCHASING", wh1.getId())).andExpect(status().isForbidden());
        }
        mockMvc.perform(asRole(REPORT + "/export?warehouseId=" + wh2.getId() + "&" + RANGE, COMPANY_A,
                "WAREHOUSE_STAFF", wh1.getId())).andExpect(status().isForbidden());
        mockMvc.perform(asRole(CARD + "/export?productId=" + PRODUCT + "&warehouseId=" + wh2.getId() + "&" + RANGE,
                COMPANY_A, "WAREHOUSE_MANAGER", wh1.getId())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(asRole(REPORT + "/export?" + RANGE, COMPANY_A, "WAREHOUSE_STAFF"))
                .andExpect(status().isForbidden());

        try (XSSFWorkbook wb = open(download(asRole(REPORT + "/export?" + RANGE, COMPANY_A, "WAREHOUSE_MANAGER",
                wh1.getId())))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(text(sheet.getRow(1), 0)).contains("Kho: Tất cả kho được phân công");
            assertThat(text(sheet.getRow(3), 4)).isEqualTo("WH-X1 - Kho Hà Nội");
            assertThat(text(sheet.getRow(4), 0)).isEqualTo("Tổng cộng");
        }
        try (XSSFWorkbook wb = open(download(asRole(REPORT + "/export?" + RANGE, COMPANY_A, "ACCOUNTANT")))) {
            assertThat(text(wb.getSheetAt(0).getRow(5), 0)).isEqualTo("Tổng cộng");
        }
    }

    @Test
    @DisplayName("Cách ly doanh nghiệp: file không chứa dữ liệu của doanh nghiệp khác; kho của doanh nghiệp khác là 404 JSON")
    void companyIsolation() throws Exception {
        setUp();
        Warehouse whB = warehouse(COMPANY_B, "WH-XB", "Kho của B");
        movement(COMPANY_A, wh1, PRODUCT, StockMovementType.RECEIPT, "1.000", Instant.parse("2026-11-10T03:00:00Z"));
        movement(COMPANY_B, whB, 7777L, StockMovementType.RECEIPT, "999.000", Instant.parse("2026-11-10T03:00:00Z"));

        try (XSSFWorkbook wb = open(download(asAdmin(REPORT + "/export?" + RANGE, COMPANY_A)))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(text(sheet.getRow(3), 1)).isEqualTo("SKU-7001");
            Row total = sheet.getRow(4);
            assertThat(text(total, 0)).isEqualTo("Tổng cộng");
            assertThat(number(total, 8)).isEqualByComparingTo("1.000");
        }
        try (XSSFWorkbook wb = open(download(asAdmin(REPORT + "/export?" + RANGE, COMPANY_B)))) {
            assertThat(number(wb.getSheetAt(0).getRow(4), 8)).isEqualByComparingTo("999.000");
        }
        mockMvc.perform(asAdmin(REPORT + "/export?warehouseId=" + whB.getId() + "&" + RANGE, COMPANY_A))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(asAdmin(CARD + "/export?productId=" + PRODUCT + "&warehouseId=" + whB.getId() + "&" + RANGE, COMPANY_A))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Lỗi tham số vẫn trả JSON ApiResponse (400), không phải file")
    void validationErrorsAreJson() throws Exception {
        setUp();
        String[] badUrls = {
                REPORT + "/export?fromDate=2025-01-01&toDate=2026-11-30",
                REPORT + "/export?fromDate=2026-11-30&toDate=2026-11-01",
                REPORT + "/export?fromDate=2026-13-01&toDate=2026-11-30",
                REPORT + "/export?toDate=2026-11-30",
                REPORT + "/export?" + RANGE + "&groupBy=ABC",
                REPORT + "/export?" + RANGE + "&sort=price",
                CARD + "/export?productId=1&" + RANGE,
                CARD + "/export?warehouseId=" + wh1.getId() + "&" + RANGE,
                CARD + "/export?productId=1&warehouseId=" + wh1.getId() + "&fromDate=2026-11-30&toDate=2026-11-01",
        };
        for (String url : badUrls) {
            MockHttpServletResponse response = mockMvc.perform(asAdmin(url, COMPANY_A))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                    .andReturn().getResponse();
            assertThat(response.getContentType()).startsWith("application/json");
        }
        mockMvc.perform(get401(REPORT + "/export?" + RANGE)).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder get401(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).header("X-Company-Id", COMPANY_A);
    }

    @Test
    @DisplayName("Quá 20000 dòng: file chỉ có 20000 dòng đầu kèm dòng ghi chú; tồn cuối vẫn tính trên toàn bộ")
    void exportOverCapIsTruncatedWithNote() throws Exception {
        setUp();
        int cap = StockReportService.EXPORT_MAX_ROWS;
        Instant base = Instant.parse("2026-11-10T00:00:00Z");
        List<StockMovement> batch = new ArrayList<>(cap + 1);
        for (int i = 0; i < cap + 1; i++) {
            StockMovement m = new StockMovement();
            m.setOccurredAt(base.plusSeconds(i));
            m.setMovementType(StockMovementType.RECEIPT);
            m.setWarehouseId(wh1.getId());
            m.setProductId(PRODUCT);
            m.setSkuCode("SKU-7001");
            m.setProductName("Sản phẩm 7001");
            m.setQuantityIn(BigDecimal.ONE);
            m.setQuantityOut(BigDecimal.ZERO);
            m.setDocumentType(StockMovement.DOC_OTHER);
            m.setDocumentNumber("DOC-" + i);
            batch.add(m);
        }
        TenantContext.runAs(COMPANY_A, () -> stockMovementRepository.saveAll(batch));

        MockHttpServletResponse response = download(asAdmin(CARD + "/export?productId=" + PRODUCT + "&warehouseId="
                + wh1.getId() + "&" + RANGE, COMPANY_A));
        try (XSSFWorkbook wb = open(response)) {
            Sheet sheet = wb.getSheetAt(0);
            // dòng 0-2 đầu trang, dòng 3 tồn đầu kỳ, rồi 20000 dòng dữ liệu
            assertThat(number(sheet.getRow(3 + cap), 10)).isEqualByComparingTo(String.valueOf(cap));
            assertThat(text(sheet.getRow(4 + cap), 2)).isEqualTo("Cộng phát sinh trong kỳ");
            assertThat(number(sheet.getRow(4 + cap), 8)).isEqualByComparingTo(String.valueOf(cap + 1));
            assertThat(number(sheet.getRow(5 + cap), 10)).isEqualByComparingTo(String.valueOf(cap + 1));
            boolean truncated = false;
            for (int i = 6 + cap; i <= sheet.getLastRowNum(); i++) {
                String note = text(sheet.getRow(i), 0);
                truncated |= note != null && note.contains("20001 dòng") && note.contains("20000 dòng đầu");
            }
            assertThat(truncated).isTrue();
        }
    }
}
