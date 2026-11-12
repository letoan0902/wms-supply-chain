package vn.wms.ai.extraction;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dựng file Excel ngay trong test bằng POI (không commit file nhị phân). Giá trị {@link Number} thành ô số, giá trị
 * {@link Percent} thành ô số định dạng phần trăm, {@code null} bỏ trống ô, còn lại thành ô chữ.
 */
public final class TestSpreadsheets {

    /** Ô số định dạng phần trăm (giá trị 0,1 hiển thị 10%). */
    public record Percent(double value) {
    }

    private TestSpreadsheets() {
    }

    public static byte[] xlsx(List<List<Object>> rows) {
        return write(new XSSFWorkbook(), rows, false);
    }

    /** File xlsx có trang tính đầu trống, dữ liệu nằm ở trang thứ hai. */
    public static byte[] xlsxWithEmptyFirstSheet(List<List<Object>> rows) {
        return write(new XSSFWorkbook(), rows, true);
    }

    public static byte[] xls(List<List<Object>> rows) {
        return write(new HSSFWorkbook(), rows, false);
    }

    /** Bảng hàng hóa có tiêu đề rõ ràng và {@code count} dòng dữ liệu SP001, SP002... */
    public static List<List<Object>> productRows(int count) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Mã hàng", "Tên hàng hóa", "Đơn vị tính", "Giá nhập", "Giá bán", "Thuế VAT", "Nhóm hàng"));
        for (int i = 1; i <= count; i++) {
            rows.add(List.of(String.format("SP%03d", i), "Sản phẩm " + i, "Thùng", 150000 + i, 180000.5,
                    new Percent(0.1), "Đồ uống"));
        }
        return rows;
    }

    private static byte[] write(Workbook workbook, List<List<Object>> rows, boolean emptyFirstSheet) {
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (emptyFirstSheet) {
                workbook.createSheet("Trống");
            }
            Sheet sheet = workbook.createSheet("Dữ liệu");
            CellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0%"));
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    Object value = values.get(c);
                    if (value == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (value instanceof Percent percent) {
                        cell.setCellValue(percent.value());
                        cell.setCellStyle(percentStyle);
                    } else if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
