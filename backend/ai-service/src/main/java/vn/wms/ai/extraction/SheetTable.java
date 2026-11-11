package vn.wms.ai.extraction;

import java.util.List;

/**
 * Bảng đọc được từ file Excel hoặc CSV: dòng tiêu đề và tối đa {@link ExtractionLimits#MAX_ROWS} dòng dữ liệu.
 *
 * @param headers       tên cột theo thứ tự (ô tiêu đề trống được đặt tên "Cột A", "Cột B"...)
 * @param rows          các dòng dữ liệu không rỗng, mỗi dòng có đúng {@code headers.size()} ô
 * @param totalDataRows tổng số dòng dữ liệu không rỗng trong file (có thể lớn hơn {@code rows.size()})
 */
public record SheetTable(List<String> headers, List<SheetRow> rows, int totalDataRows) {

    /**
     * @param sourceRow số thứ tự dòng trong file (dòng Excel hoặc dòng CSV, từ 1)
     * @param cells     giá trị các ô đã cắt khoảng trắng hai đầu, ô trống là chuỗi rỗng
     */
    public record SheetRow(int sourceRow, List<String> cells) {
    }

    public SheetTable {
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }

    /** File có nhiều dòng hơn giới hạn và đã bị cắt. */
    public boolean truncated() {
        return totalDataRows > rows.size();
    }
}
