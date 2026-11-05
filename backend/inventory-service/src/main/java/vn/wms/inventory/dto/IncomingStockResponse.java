package vn.wms.inventory.dto;

import vn.wms.inventory.entity.IncomingStock;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thông tin chi tiết một bản ghi hàng đang về kho (Incoming Stock).
 */
public record IncomingStockResponse(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long productId,
        String skuCode,
        String productName,
        Long orderId,
        String orderNumber,
        BigDecimal quantity,
        String uom,
        String status,
        Instant createdAt
) {
    public static IncomingStockResponse from(IncomingStock entity) {
        return new IncomingStockResponse(
                entity.getId(),
                entity.getWarehouse() != null ? entity.getWarehouse().getId() : null,
                entity.getWarehouse() != null ? entity.getWarehouse().getCode() : null,
                entity.getWarehouse() != null ? entity.getWarehouse().getName() : null,
                entity.getProductId(),
                entity.getSkuCode(),
                entity.getProductName(),
                entity.getOrderId(),
                entity.getOrderNumber(),
                entity.getQuantity(),
                entity.getUom(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}

