package vn.wms.inventory.dto;

import vn.wms.inventory.entity.StockTransferItem;

import java.math.BigDecimal;

/**
 * Phản hồi chi tiết dòng sản phẩm trong lệnh điều chuyển kho (WMS-13.1).
 */
public record StockTransferItemResponse(
        Long id,
        Long productId,
        String skuCode,
        String productName,
        String uom,
        Long sourceBinLocationId,
        String sourceBinLocationCode,
        Long destinationBinLocationId,
        String destinationBinLocationCode,
        BigDecimal quantity,
        BigDecimal transferredQuantity,
        BigDecimal receivedQuantity,
        BigDecimal damagedQuantity,
        String notes
) {
    public static StockTransferItemResponse from(StockTransferItem item) {
        return new StockTransferItemResponse(
                item.getId(),
                item.getProductId(),
                item.getSkuCode(),
                item.getProductName(),
                item.getUom(),
                item.getSourceBinLocation() != null ? item.getSourceBinLocation().getId() : null,
                item.getSourceBinLocation() != null ? item.getSourceBinLocation().getCode() : null,
                item.getDestinationBinLocation() != null ? item.getDestinationBinLocation().getId() : null,
                item.getDestinationBinLocation() != null ? item.getDestinationBinLocation().getCode() : null,
                item.getQuantity(),
                item.getTransferredQuantity(),
                item.getReceivedQuantity(),
                item.getDamagedQuantity(),
                item.getNotes()
        );
    }
}

