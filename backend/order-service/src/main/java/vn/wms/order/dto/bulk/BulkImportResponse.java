package vn.wms.order.dto.bulk;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Kết quả API nhập hàng loạt (WMS-22.4b): luôn trả HTTP 200, từng dòng có trạng thái riêng.
 */
public record BulkImportResponse(
        @Schema(description = "Tổng số dòng gửi lên", example = "50")
        int total,

        @Schema(description = "Số dòng đã tạo mới", example = "47")
        int created,

        @Schema(description = "Số dòng bỏ qua vì mã đã tồn tại trong doanh nghiệp", example = "1")
        int skipped,

        @Schema(description = "Số dòng lỗi", example = "2")
        int failed,

        @Schema(description = "Kết quả từng dòng theo đúng thứ tự gửi lên")
        List<BulkRowResult> results
) {

    public static BulkImportResponse of(List<BulkRowResult> results) {
        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (BulkRowResult result : results) {
            switch (result.status()) {
                case CREATED -> created++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        return new BulkImportResponse(results.size(), created, skipped, failed, List.copyOf(results));
    }
}
