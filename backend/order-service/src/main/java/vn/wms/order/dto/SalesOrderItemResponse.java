package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.order.entity.SalesOrderItem;

import java.math.BigDecimal;

/**
 * Thông tin dòng hàng trong đơn bán hàng (SO Item) kèm trạng thái tồn khả dụng ATP (FR15 - WMS-11.1).
 */
public record SalesOrderItemResponse(
        @Schema(description = "ID dòng hàng", example = "1")
        Long id,

        @Schema(description = "ID sản phẩm trong product-service", example = "10")
        Long productId,

        @Schema(description = "Mã SKU", example = "MILK-VINA-100")
        String skuCode,

        @Schema(description = "Tên sản phẩm", example = "Sữa tươi tiệt trùng Vinamilk 100ml")
        String productName,

        @Schema(description = "Đơn vị tính", example = "Thùng")
        String uom,

        @Schema(description = "Số lượng đặt bán", example = "10.000")
        BigDecimal quantity,

        @Schema(description = "Đơn giá bán (VNĐ)", example = "420000.00")
        BigDecimal unitPrice,

        @Schema(description = "Tỷ lệ chiết khấu (%)", example = "5.00")
        BigDecimal discountPercent,

        @Schema(description = "Số tiền chiết khấu (VNĐ)", example = "210000.00")
        BigDecimal discountAmount,

        @Schema(description = "Thuế suất VAT (%)", example = "8.00")
        BigDecimal taxRate,

        @Schema(description = "Số tiền thuế VAT (VNĐ)", example = "319200.00")
        BigDecimal taxAmount,

        @Schema(description = "Thành tiền dòng hàng sau thuế và chiết khấu (VNĐ)", example = "4309200.00")
        BigDecimal lineTotal,

        @Schema(description = "Tồn kho thực tế tại thời điểm kiểm tra (OnHand)", example = "100.000")
        BigDecimal onHandQuantity,

        @Schema(description = "Số lượng đã giữ chỗ tại thời điểm kiểm tra (Reserved)", example = "20.000")
        BigDecimal reservedQuantity,

        @Schema(description = "Tồn khả dụng tại thời điểm kiểm tra (ATP = OnHand - Reserved)", example = "80.000")
        BigDecimal atpQuantity,

        @Schema(description = "Dòng hàng có bị thiếu hàng (Backorder) hay không", example = "false")
        boolean isBackorder,

        @Schema(description = "Ghi chú dòng hàng")
        String note
) {
    public static SalesOrderItemResponse from(SalesOrderItem item) {
        return new SalesOrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getSkuCode(),
                item.getProductName(),
                item.getUom(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountPercent(),
                item.getDiscountAmount(),
                item.getTaxRate(),
                item.getTaxAmount(),
                item.getLineTotal(),
                item.getOnHandQuantity(),
                item.getReservedQuantity(),
                item.getAtpQuantity(),
                item.isBackorder(),
                item.getNote()
        );
    }
}
