package vn.wms.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * Một dòng của bảng xem trước.
 *
 * @param row        số thứ tự dòng trong kết quả trích xuất, từ 1
 * @param sourceRow  số dòng trong file Excel, CSV (để người dùng đối chiếu); {@code null} với PDF, hình ảnh
 * @param values     giá trị theo từng trường (đủ mọi trường, ô trống là {@code null}); trường số là số JSON, riêng ô
 *                   không đọc được thành số thì giữ chuỗi gốc để người dùng sửa
 * @param confidence độ tin cậy của từng ô, 0 đến 1 (giao diện tô vàng ô dưới 0,8)
 * @param warnings   cảnh báo tiếng Việt của dòng (thiếu trường bắt buộc, không phải số, quá dài, trùng mã...)
 */
public record ExtractionRow(int row, Integer sourceRow, Map<String, Object> values, Map<String, Double> confidence,
                            List<String> warnings) {
}
