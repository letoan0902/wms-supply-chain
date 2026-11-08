package vn.wms.order.dto;

import vn.wms.order.entity.PurchaseBillItem;

import java.math.BigDecimal;

/**
 * DTO chi tiết dòng hàng trong Hóa đơn mua hàng (Purchase Bill - WMS-10.3).
 */
public record PurchaseBillItemResponse(
        Long id,
        Long productId,
        String skuCode,
        String productName,
        String uom,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String batchNumber
) {
    public static PurchaseBillItemResponse from(PurchaseBillItem item) {
        return new PurchaseBillItemResponse(
                item.getId(),
                item.getProductId(),
                item.getSkuCode(),
                item.getProductName(),
                item.getUom(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal(),
                item.getBatchNumber()
        );
    }
}
