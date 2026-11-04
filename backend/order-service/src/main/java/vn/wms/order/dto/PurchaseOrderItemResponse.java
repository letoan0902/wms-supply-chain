package vn.wms.order.dto;

import vn.wms.order.entity.PurchaseOrderItem;

import java.math.BigDecimal;

/**
 * DTO phản hồi chi tiết dòng mặt hàng trong đơn mua hàng (PO Item).
 */
public record PurchaseOrderItemResponse(
        Long id,
        Long productId,
        String skuCode,
        String productName,
        String uom,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal lineTotal,
        String note
) {
    public static PurchaseOrderItemResponse from(PurchaseOrderItem item) {
        return new PurchaseOrderItemResponse(
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
                item.getNote()
        );
    }
}
