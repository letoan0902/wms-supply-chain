package vn.wms.order.dto;

import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO theo dõi tiến độ nhập kho của Đơn mua hàng (PO Receipt Progress - Partial Receipt - FR11).
 */
public record PoReceiptProgressResponse(
        Long poId,
        String poNumber,
        Long supplierId,
        String supplierName,
        Long warehouseId,
        String warehouseName,
        OrderStatus status,
        LocalDate orderDate,
        LocalDate expectedDate,
        BigDecimal totalOrderedQuantity,
        BigDecimal totalReceivedQuantity,
        BigDecimal totalRemainingQuantity,
        boolean isFullyReceived,
        List<ItemProgress> items
) {
    public record ItemProgress(
            Long poItemId,
            Long productId,
            String skuCode,
            String productName,
            String uom,
            BigDecimal orderedQuantity,
            BigDecimal receivedQuantity,
            BigDecimal remainingQuantity,
            BigDecimal unitPrice,
            boolean isCompleted
    ) {
    }

    public static PoReceiptProgressResponse from(PurchaseOrder po) {
        BigDecimal totalOrdered = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;

        List<ItemProgress> itemProgressList = null;
        if (po.getItems() != null) {
            itemProgressList = po.getItems().stream().map(i -> {
                BigDecimal ordered = i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO;
                BigDecimal received = i.getReceivedQuantity() != null ? i.getReceivedQuantity() : BigDecimal.ZERO;
                BigDecimal remaining = i.getRemainingQuantity();
                boolean completed = remaining.compareTo(BigDecimal.ZERO) <= 0;

                return new ItemProgress(
                        i.getId(),
                        i.getProductId(),
                        i.getSkuCode(),
                        i.getProductName(),
                        i.getUom(),
                        ordered,
                        received,
                        remaining,
                        i.getUnitPrice() != null ? i.getUnitPrice() : BigDecimal.ZERO,
                        completed
                );
            }).toList();

            for (ItemProgress item : itemProgressList) {
                totalOrdered = totalOrdered.add(item.orderedQuantity());
                totalReceived = totalReceived.add(item.receivedQuantity());
            }
        } else {
            itemProgressList = List.of();
        }

        BigDecimal totalRemaining = totalOrdered.subtract(totalReceived).max(BigDecimal.ZERO);
        boolean fullyReceived = totalRemaining.compareTo(BigDecimal.ZERO) <= 0;

        return new PoReceiptProgressResponse(
                po.getId(),
                po.getPoNumber(),
                po.getSupplier() != null ? po.getSupplier().getId() : null,
                po.getSupplier() != null ? po.getSupplier().getName() : null,
                po.getWarehouseId(),
                po.getWarehouseName(),
                po.getStatus(),
                po.getOrderDate(),
                po.getExpectedDate(),
                totalOrdered,
                totalReceived,
                totalRemaining,
                fullyReceived,
                itemProgressList
        );
    }
}
