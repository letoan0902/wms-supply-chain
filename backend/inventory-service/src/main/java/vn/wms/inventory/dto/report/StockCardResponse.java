package vn.wms.inventory.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.common.api.PageResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Thẻ kho chi tiết của một sản phẩm tại một kho trong một kỳ (WMS-17.1). */
@Schema(description = "Thẻ kho chi tiết")
public record StockCardResponse(
        Long productId,
        @Schema(description = "Mã SKU theo dòng sổ kho gần nhất, null nếu chưa biết")
        String skuCode,
        @Schema(description = "Tên sản phẩm theo dòng sổ kho gần nhất, null nếu chưa biết")
        String productName,
        String uom,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        LocalDate fromDate,
        LocalDate toDate,
        @Schema(example = "Asia/Ho_Chi_Minh")
        String timezone,
        @Schema(description = "Tồn đầu kỳ; null nếu kỳ bắt đầu trước lúc có sổ kho", example = "100.000")
        BigDecimal openingQuantity,
        @Schema(description = "Tổng nhập trong kỳ; null nếu kỳ bắt đầu trước lúc có sổ kho", example = "50.000")
        BigDecimal totalIn,
        @Schema(description = "Tổng xuất trong kỳ; null nếu kỳ bắt đầu trước lúc có sổ kho", example = "30.000")
        BigDecimal totalOut,
        @Schema(description = "Tồn cuối kỳ; null nếu kỳ kết thúc trước lúc có sổ kho", example = "120.000")
        BigDecimal closingQuantity,
        @Schema(description = "Các dòng biến động trong kỳ theo thứ tự thời gian, có phân trang")
        PageResponse<StockCardLine> lines,
        @Schema(description = "Thời điểm bắt đầu ghi sổ kho của doanh nghiệp (UTC), null nếu sổ kho có từ đầu")
        Instant ledgerStartedAt,
        @Schema(description = "Giải thích khi có số liệu không xác định được, null nếu đầy đủ")
        String dataNote,
        Instant generatedAt
) {
}
