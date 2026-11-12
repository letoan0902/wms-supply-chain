package vn.wms.inventory.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.common.api.PageResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Kết quả báo cáo Xuất - Nhập - Tồn theo kỳ (WMS-17.1). */
@Schema(description = "Báo cáo Xuất - Nhập - Tồn")
public record InOutBalanceReportResponse(
        @Schema(description = "Ngày đầu kỳ (bao gồm), theo múi giờ timezone", example = "2026-11-01")
        LocalDate fromDate,
        @Schema(description = "Ngày cuối kỳ (bao gồm), theo múi giờ timezone", example = "2026-11-30")
        LocalDate toDate,
        @Schema(description = "Múi giờ dùng để cắt ngày", example = "Asia/Ho_Chi_Minh")
        String timezone,
        @Schema(description = "Kho được lọc theo yêu cầu, null nếu xem mọi kho trong phạm vi", example = "1")
        Long warehouseId,
        @Schema(description = "Các kho thực sự được tổng hợp; null nghĩa là mọi kho của doanh nghiệp (Admin, Kế toán)")
        List<Long> warehouseIds,
        @Schema(description = "Cách gom dòng: PRODUCT_WAREHOUSE hoặc PRODUCT", example = "PRODUCT_WAREHOUSE")
        String groupBy,
        @Schema(description = "Các dòng của trang hiện tại")
        PageResponse<InOutBalanceRow> rows,
        @Schema(description = "Tổng cộng trên mọi dòng khớp bộ lọc")
        InOutBalanceTotals totals,
        @Schema(description = "Thời điểm bắt đầu ghi sổ kho của doanh nghiệp (UTC), null nếu sổ kho có từ đầu")
        Instant ledgerStartedAt,
        @Schema(description = "true nếu tồn đầu kỳ, nhập, xuất của kỳ này đều xác định được từ sổ kho")
        boolean quantitiesComplete,
        @Schema(description = "Giải thích khi có số liệu không xác định được, null nếu số liệu đầy đủ")
        String dataNote,
        @Schema(description = "Phương pháp tính giá trị tồn, null vì chưa có giá vốn")
        String valuationMethod,
        @Schema(description = "Giải thích vì sao các trường giá trị tiền là null")
        String valuationNote,
        @Schema(description = "Thời điểm lập báo cáo (UTC)")
        Instant generatedAt
) {
}
