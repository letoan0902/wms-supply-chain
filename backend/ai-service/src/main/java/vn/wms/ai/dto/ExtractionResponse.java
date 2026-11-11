package vn.wms.ai.dto;

import vn.wms.ai.extraction.ExtractionDataType;

import java.time.Instant;
import java.util.List;

/**
 * Kết quả trích xuất ({@code POST /api/ai/extractions}, {@code GET /api/ai/extractions/{id}}).
 *
 * @param extractionId mã lần trích xuất, dùng để xem lại và bấm nhập trong vòng 1 giờ
 * @param dataType     {@code PRODUCT}, {@code SUPPLIER} hoặc {@code CUSTOMER}
 * @param fileName     tên file đã tải lên
 * @param fileType     {@code XLSX}, {@code XLS}, {@code CSV}, {@code PDF}, {@code PNG} hoặc {@code JPG}
 * @param method       {@code COLUMN_MAPPING} (bảng tính đọc tại chỗ, AI chỉ ghép cột), {@code HEADER_MATCHING}
 *                     (chế độ giả lập: ghép cột theo tên tiêu đề, không gọi AI) hoặc {@code AI_DOCUMENT} (AI đọc PDF,
 *                     hình ảnh)
 * @param totalRows    số dòng dữ liệu tìm thấy trong file (có thể lớn hơn số dòng trả về)
 * @param truncated    file có hơn 200 dòng và chỉ 200 dòng đầu được lấy
 * @param columns      các cột của bảng xem trước, theo thứ tự hiển thị
 * @param rows         các dòng, tối đa 200
 * @param warnings     cảnh báo chung của cả file
 * @param createdAt    thời điểm trích xuất
 * @param expiresAt    thời điểm kết quả bị xóa (sau đó không xem lại hay nhập được nữa)
 * @param usage        số lượt AI đã dùng sau lần trích xuất này; chỉ có trong kết quả của {@code POST}, {@code null}
 *                     khi xem lại
 */
public record ExtractionResponse(String extractionId, ExtractionDataType dataType, String fileName, String fileType,
                                 String method, int totalRows, boolean truncated, List<ExtractionColumn> columns,
                                 List<ExtractionRow> rows, List<String> warnings, Instant createdAt,
                                 Instant expiresAt, AskResponse.Usage usage) {

    public ExtractionResponse withUsage(AskResponse.Usage newUsage) {
        return new ExtractionResponse(extractionId, dataType, fileName, fileType, method, totalRows, truncated,
                columns, rows, warnings, createdAt, expiresAt, newUsage);
    }
}
