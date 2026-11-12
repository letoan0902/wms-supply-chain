package vn.wms.inventory.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Một dòng báo cáo Xuất - Nhập - Tồn (WMS-17.1): một sản phẩm tại một kho, hoặc một sản phẩm gộp mọi kho trong
 * phạm vi khi {@code groupBy = PRODUCT}. Là dòng dữ liệu phẳng để màn hình và bộ xuất Excel dùng chung.
 *
 * <p>Khi số liệu đủ: {@code openingQuantity + quantityIn - quantityOut = closingQuantity} chính xác tới 3 chữ số
 * thập phân. Khi kỳ bắt đầu trước lúc có sổ kho, ba trường đầu là {@code null} (xem {@code dataNote} của báo cáo).
 * Các trường giá trị tiền luôn {@code null} vì tồn kho chưa có giá vốn.
 */
@Schema(description = "Dòng báo cáo Xuất - Nhập - Tồn")
public record InOutBalanceRow(
        @Schema(description = "ID sản phẩm bên product-service", example = "101")
        Long productId,
        @Schema(description = "Mã SKU theo dòng sổ kho gần nhất, null nếu chưa biết", example = "SKU-GAO-ST25")
        String skuCode,
        @Schema(description = "Tên sản phẩm theo dòng sổ kho gần nhất, null nếu chưa biết", example = "Gạo ST25 túi 5kg")
        String productName,
        @Schema(description = "Đơn vị tính, null nếu chưa biết", example = "Túi")
        String uom,
        @Schema(description = "ID kho; null khi gộp mọi kho (groupBy = PRODUCT)", example = "1")
        Long warehouseId,
        @Schema(description = "Mã kho; null khi gộp mọi kho", example = "WH01")
        String warehouseCode,
        @Schema(description = "Tên kho; null khi gộp mọi kho", example = "Kho Hà Nội")
        String warehouseName,
        @Schema(description = "Tồn đầu kỳ; null nếu kỳ bắt đầu trước lúc có sổ kho", example = "100.000")
        BigDecimal openingQuantity,
        @Schema(description = "Tổng nhập trong kỳ; null nếu kỳ bắt đầu trước lúc có sổ kho", example = "50.000")
        BigDecimal quantityIn,
        @Schema(description = "Tổng xuất trong kỳ; null nếu kỳ bắt đầu trước lúc có sổ kho", example = "30.000")
        BigDecimal quantityOut,
        @Schema(description = "Tồn cuối kỳ; null nếu kỳ kết thúc trước lúc có sổ kho", example = "120.000")
        BigDecimal closingQuantity,
        @Schema(description = "Giá trị tồn đầu kỳ, luôn null vì chưa có giá vốn")
        BigDecimal openingValue,
        @Schema(description = "Giá trị nhập trong kỳ, luôn null vì chưa có giá vốn")
        BigDecimal valueIn,
        @Schema(description = "Giá trị xuất trong kỳ, luôn null vì chưa có giá vốn")
        BigDecimal valueOut,
        @Schema(description = "Giá trị tồn cuối kỳ, luôn null vì chưa có giá vốn")
        BigDecimal closingValue
) {
}
