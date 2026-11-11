package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Dòng mặt hàng trong yêu cầu lập phiếu xuất kho (WMS-12.1).
 */
public record CreateGdnItemRequest(
        @NotNull(message = "soItemId không được để trống")
        @Schema(description = "ID dòng hàng trong đơn bán (so_items.id)", example = "10")
        Long soItemId,

        @NotNull(message = "productId không được để trống")
        @Schema(description = "ID sản phẩm", example = "10")
        Long productId,

        @Schema(description = "Mã SKU", example = "SKU-MILK-001")
        String skuCode,

        @Schema(description = "Tên sản phẩm", example = "Sữa tươi tiệt trùng 1L")
        String productName,

        @Schema(description = "Đơn vị tính", example = "Hộp")
        String uom,

        @NotNull(message = "Số lượng xuất không được để trống")
        @DecimalMin(value = "0.001", message = "Số lượng xuất phải lớn hơn 0")
        @Schema(description = "Số lượng thực xuất", example = "10.000")
        BigDecimal deliveredQuantity,

        @Schema(description = "Đơn giá xuất", example = "25000.00")
        BigDecimal unitPrice,

        @Schema(description = "Mã số lô hàng xuất", example = "LOT-202611-01")
        String batchNumber,

        @Schema(description = "ID vị trí ô/kệ lấy hàng", example = "5")
        Long locationId,

        @Schema(description = "Mã vị trí ô/kệ lấy hàng", example = "A-01-01")
        String locationCode,

        @Schema(description = "Ghi chú dòng xuất", example = "Hàng đóng thùng nguyên seal")
        String note
) {
}
