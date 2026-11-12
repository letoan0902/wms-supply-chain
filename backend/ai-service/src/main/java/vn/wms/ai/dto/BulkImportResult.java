package vn.wms.ai.dto;

import java.util.List;

/**
 * Kết quả của API nhập hàng loạt (docs/SAAS_SPEC.md, mục WMS-22.4b), ai-service trả nguyên cho giao diện.
 *
 * @param total   số dòng đã gửi
 * @param created số dòng tạo mới
 * @param skipped số dòng bỏ qua vì mã đã tồn tại trong doanh nghiệp
 * @param failed  số dòng lỗi
 * @param results kết quả từng dòng
 */
public record BulkImportResult(int total, int created, int skipped, int failed, List<RowResult> results) {

    /**
     * @param row     thứ tự dòng trong danh sách đã gửi, từ 1 (dòng thứ k của {@code rows} trong request nhập)
     * @param status  {@code CREATED}, {@code SKIPPED} hoặc {@code FAILED}
     * @param id      ID bản ghi đã tạo hoặc đã có, {@code null} khi lỗi
     * @param code    mã SKU hoặc mã đối tác của dòng
     * @param message lý do bỏ qua hoặc lỗi
     */
    public record RowResult(int row, String status, Long id, String code, String message) {
    }
}
