package vn.wms.order.dto;

import vn.wms.order.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Thông tin đơn mua hàng (Purchase Order - PO) trả về cho client.
 */
public record PurchaseOrderResponse(
        Long id,
        String poNumber,
        Long supplierId,
        String supplierName,
        Long warehouseId,
        String warehouseName,
        String status,
        LocalDate orderDate,
        LocalDate expectedDate,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String note,
        String approvedBy,
        Instant approvedAt,
        String rejectedReason,
        List<PurchaseOrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Chuyển entity sang DTO. Gọi trong transaction vì supplier và items được nạp lazy.
     */
    public static PurchaseOrderResponse from(PurchaseOrder order) {
        List<PurchaseOrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(PurchaseOrderItemResponse::from).toList()
                : Collections.emptyList();

        return new PurchaseOrderResponse(
                order.getId(),
                order.getPoNumber(),
                order.getSupplier() != null ? order.getSupplier().getId() : null,
                order.getSupplier() != null ? order.getSupplier().getName() : null,
                order.getWarehouseId(),
                order.getWarehouseName(),
                order.getStatus().name(),
                order.getOrderDate(),
                order.getExpectedDate(),
                order.getSubtotalAmount() != null ? order.getSubtotalAmount() : BigDecimal.ZERO,
                order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO,
                order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO,
                order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO,
                order.getNote(),
                order.getApprovedBy(),
                order.getApprovedAt(),
                order.getRejectedReason(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
