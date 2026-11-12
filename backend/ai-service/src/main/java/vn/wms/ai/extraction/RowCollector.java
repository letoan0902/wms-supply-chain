package vn.wms.ai.extraction;

import vn.wms.common.exception.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gom các dòng đọc được từ một trang tính (theo thứ tự) thành {@link SheetTable}: bỏ dòng trống, chọn dòng tiêu đề
 * trong {@link ExtractionLimits#HEADER_SCAN_ROWS} dòng đầu (dòng đầu tiên có từ hai ô trở lên, để bỏ qua dòng tên bảng
 * kiểu "DANH SÁCH HÀNG HÓA"), giữ tối đa {@link ExtractionLimits#MAX_ROWS} dòng dữ liệu và đếm tổng số dòng.
 */
final class RowCollector {

    private final List<SheetTable.SheetRow> kept = new ArrayList<>();
    private int nonEmptyRows;

    /** Nhận một dòng: khóa là chỉ số cột (từ 0), giá trị là nội dung ô. */
    void accept(int sourceRow, Map<Integer, String> cells) {
        TreeMap<Integer, String> trimmed = new TreeMap<>();
        cells.forEach((column, value) -> {
            if (column != null && column >= 0 && column < ExtractionLimits.MAX_COLUMNS && value != null) {
                String text = value.strip();
                if (!text.isEmpty()) {
                    trimmed.put(column, text);
                }
            }
        });
        if (trimmed.isEmpty()) {
            return;
        }
        nonEmptyRows++;
        if (kept.size() < ExtractionLimits.HEADER_SCAN_ROWS + ExtractionLimits.MAX_ROWS) {
            List<String> values = new ArrayList<>();
            for (int column = 0; column <= trimmed.lastKey(); column++) {
                values.add(trimmed.getOrDefault(column, ""));
            }
            kept.add(new SheetTable.SheetRow(sourceRow, values));
        }
    }

    /** Trang tính có ít nhất một ô có dữ liệu. */
    boolean hasData() {
        return nonEmptyRows > 0;
    }

    SheetTable toTable() {
        if (kept.isEmpty()) {
            throw ApiException.badRequest("File không có dữ liệu: trang tính trống. Lượt này không bị tính.");
        }
        int headerIndex = 0;
        for (int i = 0; i < Math.min(kept.size(), ExtractionLimits.HEADER_SCAN_ROWS); i++) {
            if (kept.get(i).cells().stream().filter(cell -> !cell.isEmpty()).count() >= 2) {
                headerIndex = i;
                break;
            }
        }
        List<SheetTable.SheetRow> data = kept.subList(headerIndex + 1, kept.size());
        if (data.isEmpty()) {
            throw ApiException.badRequest("File chỉ có dòng tiêu đề, không có dòng dữ liệu nào. "
                    + "Lượt này không bị tính.");
        }
        int columns = kept.get(headerIndex).cells().size();
        for (SheetTable.SheetRow row : data) {
            columns = Math.max(columns, row.cells().size());
        }
        List<String> headers = new ArrayList<>();
        List<String> headerCells = kept.get(headerIndex).cells();
        for (int column = 0; column < columns; column++) {
            String header = column < headerCells.size() ? headerCells.get(column) : "";
            headers.add(header.isEmpty() ? "Cột " + columnLetter(column) : header);
        }
        List<SheetTable.SheetRow> rows = new ArrayList<>();
        for (SheetTable.SheetRow row : data.subList(0, Math.min(data.size(), ExtractionLimits.MAX_ROWS))) {
            List<String> cells = new ArrayList<>(row.cells());
            while (cells.size() < columns) {
                cells.add("");
            }
            rows.add(new SheetTable.SheetRow(row.sourceRow(), cells));
        }
        int totalDataRows = nonEmptyRows - headerIndex - 1;
        return new SheetTable(headers, rows, totalDataRows);
    }

    /** Tên cột kiểu Excel: 0 thành A, 25 thành Z, 26 thành AA. */
    static String columnLetter(int column) {
        StringBuilder letters = new StringBuilder();
        int value = column + 1;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            letters.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return letters.toString();
    }
}
