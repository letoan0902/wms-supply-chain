package vn.wms.common.bulk;

/**
 * Kết quả của một dòng trong API nhập hàng loạt.
 *
 * @param row     thứ tự dòng trong mảng {@code items} của request, tính từ 1
 * @param status  {@link BulkRowStatus#CREATED}, {@link BulkRowStatus#SKIPPED} hoặc {@link BulkRowStatus#FAILED}
 * @param id      ID bản ghi vừa tạo ({@code CREATED}) hoặc bản ghi đang có ({@code SKIPPED}); {@code null} khi lỗi
 * @param code    mã của dòng (SKU, mã đối tác...) sau khi chuẩn hóa, {@code null} nếu dòng không có mã
 * @param message lý do bỏ qua hoặc lỗi, {@code null} khi tạo thành công
 */
public record BulkRowResult(int row, BulkRowStatus status, Long id, String code, String message) {

    public static BulkRowResult created(int row, Long id, String code) {
        return new BulkRowResult(row, BulkRowStatus.CREATED, id, code, null);
    }

    public static BulkRowResult skipped(int row, Long id, String code, String message) {
        return new BulkRowResult(row, BulkRowStatus.SKIPPED, id, code, message);
    }

    public static BulkRowResult failed(int row, String code, String message) {
        return new BulkRowResult(row, BulkRowStatus.FAILED, null, code, message);
    }
}
