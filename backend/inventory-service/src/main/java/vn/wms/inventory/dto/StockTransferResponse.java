package vn.wms.inventory.dto;

import vn.wms.inventory.entity.StockTransfer;
import vn.wms.inventory.entity.StockTransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Phản hồi thông tin lệnh điều chuyển hàng hóa nội bộ (WMS-13.1).
 */
public record StockTransferResponse(
        Long id,
        String transferNumber,
        Long sourceWarehouseId,
        String sourceWarehouseCode,
        String sourceWarehouseName,
        Long destinationWarehouseId,
        String destinationWarehouseCode,
        String destinationWarehouseName,
        StockTransferStatus status,
        Instant transferDate,
        Instant expectedDeliveryDate,
        String notes,
        String approvedBy,
        Instant approvedAt,
        String receivedBy,
        Instant receivedAt,
        int totalItems,
        BigDecimal totalQuantity,
        BigDecimal totalTransferredQuantity,
        BigDecimal totalReceivedQuantity,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        List<StockTransferItemResponse> items
) {
    public static StockTransferResponse from(StockTransfer transfer) {
        List<StockTransferItemResponse> itemResponses = transfer.getItems() != null
                ? transfer.getItems().stream().map(StockTransferItemResponse::from).toList()
                : List.of();

        BigDecimal sumQuantity = itemResponses.stream()
                .map(StockTransferItemResponse::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumTransferred = itemResponses.stream()
                .map(StockTransferItemResponse::transferredQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumReceived = itemResponses.stream()
                .map(StockTransferItemResponse::receivedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new StockTransferResponse(
                transfer.getId(),
                transfer.getTransferNumber(),
                transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getId() : null,
                transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getCode() : null,
                transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getName() : null,
                transfer.getDestinationWarehouse() != null ? transfer.getDestinationWarehouse().getId() : null,
                transfer.getDestinationWarehouse() != null ? transfer.getDestinationWarehouse().getCode() : null,
                transfer.getDestinationWarehouse() != null ? transfer.getDestinationWarehouse().getName() : null,
                transfer.getStatus(),
                transfer.getTransferDate(),
                transfer.getExpectedDeliveryDate(),
                transfer.getNotes(),
                transfer.getApprovedBy(),
                transfer.getApprovedAt(),
                transfer.getReceivedBy(),
                transfer.getReceivedAt(),
                itemResponses.size(),
                sumQuantity,
                sumTransferred,
                sumReceived,
                transfer.getCreatedAt(),
                transfer.getUpdatedAt(),
                transfer.getCreatedBy(),
                itemResponses
        );
    }
}

