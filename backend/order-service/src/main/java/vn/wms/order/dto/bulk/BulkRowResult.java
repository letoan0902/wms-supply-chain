package vn.wms.order.dto.bulk;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kết quả của một dòng trong API nhập hàng loạt (WMS-22.4b).
 */
public record BulkRowResult(
        @Schema(description = "Thứ tự dòng trong danh sách gửi lên, bắt đầu từ 1", example = "1")
        int row,

        @Schema(description = "CREATED, SKIPPED hoặc FAILED", example = "CREATED")
        BulkRowStatus status,

        @Schema(description = "ID bản ghi vừa tạo (CREATED) hoặc đã có sẵn (SKIPPED); null khi FAILED", example = "15")
        Long id,

        @Schema(description = "Mã của dòng sau khi chuẩn hóa (viết hoa, bỏ khoảng trắng hai đầu)", example = "SUP-VINAMILK")
        String code,

        @Schema(description = "Giải thích kết quả, luôn có với SKIPPED và FAILED", example = "Đã tạo mới")
        String message
) {

    public static BulkRowResult created(int row, Long id, String code) {
        return new BulkRowResult(row, BulkRowStatus.CREATED, id, code, "Đã tạo mới");
    }

    public static BulkRowResult skipped(int row, Long id, String code, String message) {
        return new BulkRowResult(row, BulkRowStatus.SKIPPED, id, code, message);
    }

    public static BulkRowResult failed(int row, String code, String message) {
        return new BulkRowResult(row, BulkRowStatus.FAILED, null, code, message);
    }
}
