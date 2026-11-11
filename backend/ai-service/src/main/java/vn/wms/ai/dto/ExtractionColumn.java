package vn.wms.ai.dto;

import java.util.List;

/**
 * Một cột của bảng xem trước, tương ứng một trường của API nhập hàng loạt.
 *
 * @param field             tên trường (khóa trong {@code values} của mỗi dòng), ví dụ {@code purchasePrice}
 * @param label             nhãn tiếng Việt, ví dụ "Giá nhập"
 * @param type              {@code TEXT}, {@code SKU}, {@code EMAIL}, {@code NUMBER}, {@code PERCENT} hoặc {@code ENUM}
 * @param required          bắt buộc có giá trị khi nhập
 * @param maxLength         độ dài tối đa (trường chuỗi), {@code null} nếu không giới hạn
 * @param options           giá trị hợp lệ của trường {@code ENUM} (ví dụ {@code WHOLESALE}, {@code RETAIL}), rỗng với
 *                          kiểu khác
 * @param sourceColumn      số thứ tự cột trong file (từ 1) đã ghép vào trường này; {@code null} khi không tìm thấy cột
 *                          hoặc khi dữ liệu do AI đọc từ PDF, hình ảnh
 * @param sourceHeader      tiêu đề cột trong file, {@code null} như trên
 * @param mappingConfidence độ tin cậy của việc ghép cột (0 đến 1), {@code null} như trên
 */
public record ExtractionColumn(String field, String label, String type, boolean required, Integer maxLength,
                               List<String> options, Integer sourceColumn, String sourceHeader,
                               Double mappingConfidence) {
}
