package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Chi tiết kết quả một dòng hàng được trừ tồn kho tại một vị trí ô kệ (WMS-12.2).
 */
@Schema(description = "Kết quả trừ tồn kho tại một vị trí lưu trữ")
public record DeductedItemDetail(
        @Schema(description = "ID bản ghi tồn kho StockItem được cập nhật", example = "205")
        Long stockItemId,

        @Schema(description = "ID sản phẩm", example = "101")
        Long productId,

        @Schema(description = "Mã SKU", example = "SKU-MILK-100")
        String skuCode,

        @Schema(description = "ID vị trí ô/kệ", example = "15")
        Long binLocationId,

        @Schema(description = "Mã vị trí ô/kệ", example = "WH01-A-01-01")
        String binLocationCode,

        @Schema(description = "Mã số lô hàng", example = "LOT-202611-01")
        String lotNo,

        @Schema(description = "Số lượng đã trừ trong lượt này", example = "10.000")
        BigDecimal deductedQuantity,

        @Schema(description = "Số lượng tồn thực tế (OnHand) còn lại sau khi trừ", example = "90.000")
        BigDecimal remainingQuantity,

        @Schema(description = "Số lượng giữ chỗ (Reserved) còn lại sau khi xuất", example = "0.000")
        BigDecimal remainingReservedQuantity
) {
}
