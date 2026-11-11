package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một điểm dừng trong Lộ trình lấy hàng (Picking Route Stop) (WMS-12.1).
 */
public record PickingItemResponse(
        @Schema(description = "Số thứ tự chặng lấy hàng (Stop #1, #2...)", example = "1")
        int step,

        @Schema(description = "ID sản phẩm", example = "10")
        Long productId,

        @Schema(description = "Mã SKU", example = "SKU-MILK-001")
        String skuCode,

        @Schema(description = "Tên sản phẩm", example = "Sữa tươi tiệt trùng 1L")
        String productName,

        @Schema(description = "Đơn vị tính", example = "Hộp")
        String uom,

        @Schema(description = "ID vị trí ô/kệ lưu cất", example = "5")
        Long binLocationId,

        @Schema(description = "Mã vị trí ô/kệ (Zone-Rack-Bin)", example = "A-01-02")
        String locationCode,

        @Schema(description = "Khu vực kho (Zone)", example = "A")
        String zone,

        @Schema(description = "Dãy kệ (Rack)", example = "01")
        String rack,

        @Schema(description = "Ô chứa hàng (Bin)", example = "02")
        String bin,

        @Schema(description = "Số lô hàng (Batch/Lot)", example = "LOT-202611-01")
        String lotNo,

        @Schema(description = "Hạn sử dụng (Exp Date)", example = "2027-05-15")
        LocalDate expDate,

        @Schema(description = "Ngày sản xuất (Mfg Date)", example = "2026-05-15")
        LocalDate mfgDate,

        @Schema(description = "Số lượng cần lấy tại vị trí này", example = "10.000")
        BigDecimal quantityToPick,

        @Schema(description = "Số lượng tồn hiện có tại vị trí này", example = "50.000")
        BigDecimal availableQuantity
) {
}
