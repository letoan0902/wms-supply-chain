package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.order.entity.GoodsDeliveryNoteItem;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Phản hồi chi tiết dòng hàng trong Phiếu xuất kho (WMS-12.1).
 */
public record GdnItemResponse(
        @Schema(description = "ID dòng xuất kho", example = "1")
        Long id,

        @Schema(description = "ID dòng đơn bán hàng", example = "10")
        Long soItemId,

        @Schema(description = "ID sản phẩm", example = "10")
        Long productId,

        @Schema(description = "Mã SKU", example = "SKU-MILK-001")
        String skuCode,

        @Schema(description = "Tên sản phẩm", example = "Sữa tươi tiệt trùng 1L")
        String productName,

        @Schema(description = "Đơn vị tính", example = "Hộp")
        String uom,

        @Schema(description = "Số lượng đặt ban đầu", example = "20.000")
        BigDecimal orderedQuantity,

        @Schema(description = "Số lượng đã xuất theo phiếu", example = "10.000")
        BigDecimal deliveredQuantity,

        @Schema(description = "Đơn giá xuất", example = "25000.00")
        BigDecimal unitPrice,

        @Schema(description = "Thành tiền dòng", example = "250000.00")
        BigDecimal lineTotal,

        @Schema(description = "Số lô hàng xuất", example = "LOT-202611-01")
        String batchNumber,

        @Schema(description = "ID vị trí ô kệ", example = "5")
        Long locationId,

        @Schema(description = "Mã vị trí ô kệ", example = "A-01-01")
        String locationCode,

        @Schema(description = "Ghi chú", example = "Giao đủ")
        String note,

        Instant createdAt
) {
    public static GdnItemResponse from(GoodsDeliveryNoteItem item) {
        if (item == null) return null;
        return new GdnItemResponse(
                item.getId(),
                item.getSalesOrderItem() != null ? item.getSalesOrderItem().getId() : null,
                item.getProductId(),
                item.getSkuCode(),
                item.getProductName(),
                item.getUom(),
                item.getOrderedQuantity(),
                item.getDeliveredQuantity(),
                item.getUnitPrice(),
                item.getLineTotal(),
                item.getBatchNumber(),
                item.getLocationId(),
                item.getLocationCode(),
                item.getNote(),
                item.getCreatedAt()
        );
    }
}
