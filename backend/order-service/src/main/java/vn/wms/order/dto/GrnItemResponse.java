package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.order.entity.GoodsReceiptNoteItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO chi tiết dòng mặt hàng trong phiếu nhập kho (GRN Item Response - FR11).
 */
public record GrnItemResponse(
        Long id,
        Long poItemId,
        Long productId,
        String skuCode,
        String productName,
        String uom,
        BigDecimal orderedQuantity,
        BigDecimal acceptedQuantity,
        BigDecimal rejectedQuantity,
        BigDecimal totalReceivedQuantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String rejectedReason,
        String batchNumber,
        LocalDate mfgDate,
        LocalDate expDate,
        Long locationId,
        String locationCode,
        String note,
        Instant createdAt
) {
    public static GrnItemResponse from(GoodsReceiptNoteItem item) {
        BigDecimal acc = item.getAcceptedQuantity() != null ? item.getAcceptedQuantity() : BigDecimal.ZERO;
        BigDecimal rej = item.getRejectedQuantity() != null ? item.getRejectedQuantity() : BigDecimal.ZERO;
        return new GrnItemResponse(
                item.getId(),
                item.getPurchaseOrderItem() != null ? item.getPurchaseOrderItem().getId() : null,
                item.getProductId(),
                item.getSkuCode(),
                item.getProductName(),
                item.getUom(),
                item.getOrderedQuantity() != null ? item.getOrderedQuantity() : BigDecimal.ZERO,
                acc,
                rej,
                acc.add(rej),
                item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO,
                item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO,
                item.getRejectedReason(),
                item.getBatchNumber(),
                item.getMfgDate(),
                item.getExpDate(),
                item.getLocationId(),
                item.getLocationCode(),
                item.getNote(),
                item.getCreatedAt()
        );
    }
}
