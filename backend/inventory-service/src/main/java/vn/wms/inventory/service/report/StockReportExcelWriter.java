package vn.wms.inventory.service.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import vn.wms.inventory.dto.report.InOutBalanceReportResponse;
import vn.wms.inventory.dto.report.InOutBalanceRow;
import vn.wms.inventory.dto.report.InOutBalanceTotals;
import vn.wms.inventory.dto.report.StockCardLine;
import vn.wms.inventory.dto.report.StockCardResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ghi file Excel (xlsx) của báo cáo Xuất - Nhập - Tồn và thẻ kho (WMS-17.3) bằng Apache POI, bố cục giống file mà
 * màn hình báo cáo tự dựng trên trình duyệt (frontend/src/features/reports/reportExcel.ts): dòng tiêu đề, dòng điều
 * kiện lọc, dòng tiêu đề cột (đậm, cố định khi cuộn), các dòng dữ liệu, dòng tổng, ghi chú.
 *
 * <p>Dùng {@link SXSSFWorkbook} (ghi theo luồng, chỉ giữ một cửa sổ nhỏ các dòng trong bộ nhớ) để 20000 dòng không
 * làm đầy heap; file tạm của POI luôn được xóa bằng {@code dispose()}. Số lượng là ô số (tối đa 3 chữ số thập phân),
 * ngày là ô ngày dd/MM/yyyy, số lượng {@code null} để ô trống (không ghi 0).
 */
@Component
public class StockReportExcelWriter {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final String IN_OUT_SHEET = "Xuat_Nhap_Ton";
    static final String STOCK_CARD_SHEET = "The_kho";

    private static final DateTimeFormatter DISPLAY_INSTANT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<String, String> DOCUMENT_TYPE_LABELS = Map.of(
            "GOODS_RECEIPT", "Phiếu nhập kho",
            "SALES_ORDER", "Đơn bán",
            "STOCK_TRANSFER", "Phiếu điều chuyển",
            "OPENING_BALANCE", "Số dư mở sổ",
            "PURCHASE_ORDER", "Đơn mua",
            "STOCKTAKE", "Phiếu kiểm kê",
            "OTHER", "Chứng từ khác");

    /** Các kiểu ô dùng chung của một workbook. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle text;
        final CellStyle header;
        final CellStyle quantity;
        final CellStyle quantityBold;
        final CellStyle bold;
        final CellStyle date;
        final CellStyle dateTime;
        final CellStyle note;

        Styles(SXSSFWorkbook wb) {
            CreationHelper helper = wb.getCreationHelper();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            title = wb.createCellStyle();
            title.setFont(titleFont);
            text = wb.createCellStyle();
            text.setWrapText(true);
            text.setVerticalAlignment(VerticalAlignment.TOP);
            header = wb.createCellStyle();
            header.setFont(boldFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);
            header.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            header.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            quantity = wb.createCellStyle();
            quantity.setDataFormat(helper.createDataFormat().getFormat("#,##0.000"));
            quantityBold = wb.createCellStyle();
            quantityBold.cloneStyleFrom(quantity);
            quantityBold.setFont(boldFont);
            bold = wb.createCellStyle();
            bold.setFont(boldFont);
            date = wb.createCellStyle();
            date.setDataFormat(helper.createDataFormat().getFormat("dd/MM/yyyy"));
            date.setAlignment(HorizontalAlignment.CENTER);
            dateTime = wb.createCellStyle();
            dateTime.setDataFormat(helper.createDataFormat().getFormat("dd/MM/yyyy HH:mm"));
            note = wb.createCellStyle();
            note.setWrapText(true);
            note.setVerticalAlignment(VerticalAlignment.TOP);
        }
    }

    // ===================== Xuất - Nhập - Tồn =====================

    /**
     * @param report      kết quả của {@code getInOutBalanceForExport}: mọi dòng trong {@code rows.content}
     * @param filterText  các điều kiện lọc đã dựng thành chữ (kỳ, kho, danh mục, sản phẩm, từ khóa)
     */
    public byte[] writeInOutBalance(InOutBalanceReportResponse report, List<String> filterText) {
        boolean byWarehouse = !"PRODUCT".equals(report.groupBy());
        List<InOutBalanceRow> rows = report.rows().content();

        List<String> header = new ArrayList<>(List.of("STT", "Mã SKU", "Tên sản phẩm", "ĐVT"));
        List<Integer> widths = new ArrayList<>(List.of(6, 18, 40, 10));
        if (byWarehouse) {
            header.add("Kho");
            widths.add(28);
        }
        int firstQty = header.size();
        header.addAll(List.of("Tồn đầu kỳ", "Nhập trong kỳ", "Xuất trong kỳ", "Tồn cuối kỳ"));
        widths.addAll(List.of(16, 16, 16, 16));
        int columnCount = header.size();

        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        try {
            Styles st = new Styles(wb);
            SXSSFSheet sheet = wb.createSheet(IN_OUT_SHEET);
            applyWidths(sheet, widths);
            int r = 0;
            writeMergedText(sheet, r++, "BÁO CÁO XUẤT - NHẬP - TỒN KHO", columnCount, st.title, widths);
            writeMergedText(sheet, r++, String.join("; ", filterText), columnCount, st.text, widths);
            writeHeader(sheet, r++, header, st);
            sheet.createFreezePane(0, r);

            int index = 1;
            for (InOutBalanceRow row : rows) {
                Row out = sheet.createRow(r++);
                int c = 0;
                number(out, c++, (long) index++, null);
                text(out, c++, nullToEmpty(row.skuCode()));
                text(out, c++, row.productName() != null ? row.productName()
                        : productLabel(row.skuCode(), row.productName(), row.productId()));
                text(out, c++, nullToEmpty(row.uom()));
                if (byWarehouse) {
                    text(out, c++, warehouseLabel(row.warehouseCode(), row.warehouseName(), row.warehouseId()));
                }
                number(out, c++, row.openingQuantity(), st.quantity);
                number(out, c++, row.quantityIn(), st.quantity);
                number(out, c++, row.quantityOut(), st.quantity);
                number(out, c, row.closingQuantity(), st.quantity);
            }

            InOutBalanceTotals totals = report.totals();
            Row total = sheet.createRow(r++);
            styledText(total, 0, "Tổng cộng", st.bold);
            number(total, firstQty, totals.openingQuantity(), st.quantityBold);
            number(total, firstQty + 1, totals.quantityIn(), st.quantityBold);
            number(total, firstQty + 2, totals.quantityOut(), st.quantityBold);
            number(total, firstQty + 3, totals.closingQuantity(), st.quantityBold);

            List<String> notes = new ArrayList<>();
            long matched = report.rows().totalElements();
            if (matched > rows.size()) {
                notes.add(truncationNote(matched, rows.size()));
            }
            if (report.dataNote() != null && !report.dataNote().isBlank()) {
                notes.add("Ghi chú số liệu: " + report.dataNote());
            }
            if (report.valuationNote() != null && !report.valuationNote().isBlank()) {
                notes.add("Giá trị tiền: " + report.valuationNote());
            }
            if (report.generatedAt() != null) {
                notes.add("Lập lúc: " + formatInstant(report.generatedAt()));
            }
            r = writeNotes(sheet, r, notes, columnCount, st, widths);
            return toBytes(wb);
        } finally {
            wb.dispose();
        }
    }

    // ===================== Thẻ kho =====================

    public byte[] writeStockCard(StockCardResponse card) {
        List<String> header = List.of("STT", "Thời điểm", "Loại biến động", "Số chứng từ", "Chứng từ tham chiếu",
                "Số lô", "Hạn dùng", "Vị trí", "Nhập", "Xuất", "Tồn", "Người thực hiện", "Diễn giải");
        List<Integer> widths = List.of(6, 18, 26, 30, 26, 16, 12, 20, 14, 14, 14, 16, 30);
        final int in = 8;
        final int out = 9;
        final int bal = 10;
        int columnCount = header.size();
        List<StockCardLine> lines = card.lines().content();

        String product = productLabel(card.skuCode(), card.productName(), card.productId());
        String filters = String.join("; ",
                "Sản phẩm: " + product + (card.uom() != null && !card.uom().isBlank() ? " (ĐVT: " + card.uom() + ")" : ""),
                "Kho: " + warehouseLabel(card.warehouseCode(), card.warehouseName(), card.warehouseId()),
                "Kỳ: " + formatDate(card.fromDate()) + " - " + formatDate(card.toDate()));

        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        try {
            Styles st = new Styles(wb);
            SXSSFSheet sheet = wb.createSheet(STOCK_CARD_SHEET);
            applyWidths(sheet, widths);
            int r = 0;
            writeMergedText(sheet, r++, "THẺ KHO", columnCount, st.title, widths);
            writeMergedText(sheet, r++, filters, columnCount, st.text, widths);
            writeHeader(sheet, r++, header, st);
            sheet.createFreezePane(0, r);

            Row opening = sheet.createRow(r++);
            styledText(opening, 2, "Tồn đầu kỳ", st.bold);
            number(opening, bal, card.openingQuantity(), st.quantityBold);

            int index = 1;
            for (StockCardLine l : lines) {
                Row row = sheet.createRow(r++);
                number(row, 0, (long) index++, null);
                dateTime(row, 1, l.occurredAt(), st.dateTime);
                text(row, 2, movementLabel(l));
                text(row, 3, documentCell(l.documentType(), l.documentNumber(), l.documentId()));
                boolean hasRef = (l.referenceType() != null && !l.referenceType().isBlank())
                        || (l.referenceNumber() != null && !l.referenceNumber().isBlank());
                text(row, 4, hasRef ? documentCell(l.referenceType(), l.referenceNumber(), l.referenceId()) : "");
                text(row, 5, nullToEmpty(l.lotNo()));
                if (l.expDate() != null) {
                    Cell cell = row.createCell(6);
                    cell.setCellValue(l.expDate());
                    cell.setCellStyle(st.date);
                }
                text(row, 7, nullToEmpty(l.binLocationCode()));
                number(row, in, l.quantityIn(), st.quantity);
                number(row, out, l.quantityOut(), st.quantity);
                number(row, bal, l.balanceAfter(), st.quantity);
                text(row, 11, nullToEmpty(l.performedBy()));
                text(row, 12, nullToEmpty(l.reason()));
            }

            Row total = sheet.createRow(r++);
            styledText(total, 2, "Cộng phát sinh trong kỳ", st.bold);
            number(total, in, card.totalIn(), st.quantityBold);
            number(total, out, card.totalOut(), st.quantityBold);
            Row closing = sheet.createRow(r++);
            styledText(closing, 2, "Tồn cuối kỳ", st.bold);
            number(closing, bal, card.closingQuantity(), st.quantityBold);

            List<String> notes = new ArrayList<>();
            long matched = card.lines().totalElements();
            if (matched > lines.size()) {
                notes.add(truncationNote(matched, lines.size()));
            }
            if (card.dataNote() != null && !card.dataNote().isBlank()) {
                notes.add("Ghi chú số liệu: " + card.dataNote());
            }
            if (card.generatedAt() != null) {
                notes.add("Lập lúc: " + formatInstant(card.generatedAt()));
            }
            writeNotes(sheet, r, notes, columnCount, st, widths);
            return toBytes(wb);
        } finally {
            wb.dispose();
        }
    }

    // ===================== Tiện ích =====================

    static String truncationNote(long matched, int written) {
        return "Báo cáo có " + matched + " dòng, vượt giới hạn " + written + " dòng của một file Excel nên file chỉ "
                + "gồm " + written + " dòng đầu; tổng cộng ở trên vẫn tính trên toàn bộ " + matched + " dòng. Hãy thu "
                + "hẹp kỳ báo cáo hoặc lọc theo kho, danh mục, sản phẩm để có đủ dòng.";
    }

    private static void applyWidths(SXSSFSheet sheet, List<Integer> widths) {
        for (int i = 0; i < widths.size(); i++) {
            sheet.setColumnWidth(i, Math.min(widths.get(i) + 2, 255) * 256);
        }
    }

    private static void writeHeader(SXSSFSheet sheet, int rowIndex, List<String> header, Styles st) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(30);
        for (int i = 0; i < header.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(header.get(i));
            cell.setCellStyle(st.header);
        }
    }

    /** Dòng có ô đầu trải hết chiều ngang; chiều cao ước lượng theo độ dài chữ vì Excel không tự giãn ô gộp. */
    private static void writeMergedText(SXSSFSheet sheet, int rowIndex, String value, int columnCount, CellStyle style,
                                        List<Integer> widths) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        if (columnCount > 1) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, columnCount - 1));
        }
        int totalChars = widths.stream().mapToInt(Integer::intValue).sum();
        int lines = Math.max(1, (int) Math.ceil(value.length() * 1.1 / Math.max(totalChars, 1)));
        if (lines > 1) {
            row.setHeightInPoints(Math.min(lines * 15f, 400f));
        }
    }

    private static int writeNotes(SXSSFSheet sheet, int rowIndex, List<String> notes, int columnCount, Styles st,
                                  List<Integer> widths) {
        int r = rowIndex;
        if (!notes.isEmpty()) {
            sheet.createRow(r++);
        }
        for (String note : notes) {
            writeMergedText(sheet, r++, note, columnCount, st.note, widths);
        }
        return r;
    }

    private static void text(Row row, int col, String value) {
        row.createCell(col).setCellValue(value);
    }

    private static void styledText(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** Ô số; giá trị null để ô trống (không tạo ô, không ghi 0). */
    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        if (value == null) {
            return;
        }
        Cell cell = row.createCell(col);
        cell.setCellValue(value.doubleValue());
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void number(Row row, int col, long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void dateTime(Row row, int col, Instant value, CellStyle style) {
        if (value == null) {
            return;
        }
        Cell cell = row.createCell(col);
        cell.setCellValue(value.atZone(StockReportService.REPORT_ZONE).toLocalDateTime());
        cell.setCellStyle(style);
    }

    private static byte[] toBytes(SXSSFWorkbook wb) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024)) {
            wb.write(buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được file Excel báo cáo", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    static String formatInstant(Instant instant) {
        ZonedDateTime z = instant.atZone(StockReportService.REPORT_ZONE);
        return DISPLAY_INSTANT.format(z);
    }

    static String productLabel(String sku, String name, Long productId) {
        List<String> parts = new ArrayList<>();
        if (sku != null && !sku.isBlank()) {
            parts.add(sku);
        }
        if (name != null && !name.isBlank()) {
            parts.add(name);
        }
        return parts.isEmpty() ? "Sản phẩm ID " + productId : String.join(" - ", parts);
    }

    static String warehouseLabel(String code, String name, Long id) {
        List<String> parts = new ArrayList<>();
        if (code != null && !code.isBlank()) {
            parts.add(code);
        }
        if (name != null && !name.isBlank()) {
            parts.add(name);
        }
        if (!parts.isEmpty()) {
            return String.join(" - ", parts);
        }
        return id == null ? "" : "Kho ID " + id;
    }

    private static String movementLabel(StockCardLine l) {
        if (l.movementTypeLabel() != null && !l.movementTypeLabel().isBlank()) {
            return l.movementTypeLabel();
        }
        return nullToEmpty(l.movementType());
    }

    private static String documentCell(String type, String number, Long id) {
        String label = type == null || type.isBlank() ? "" : DOCUMENT_TYPE_LABELS.getOrDefault(type, type);
        String ref = number != null && !number.isBlank() ? number : (id != null ? "ID " + id : "");
        return (label + " " + ref).trim();
    }
}
