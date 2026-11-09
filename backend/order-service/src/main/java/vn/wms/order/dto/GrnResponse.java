package vn.wms.order.dto;

import vn.wms.order.entity.GoodsReceiptNote;
import vn.wms.order.entity.GoodsReceiptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * DTO thông tin phiếu nhập kho hoàn chỉnh (GRN Response - FR11).
 */
public record GrnResponse(
        Long id,
        String grnNumber,
        Long poId,
        String poNumber,
        Long supplierId,
        String supplierName,
        Long warehouseId,
        String warehouseName,
        GoodsReceiptStatus status,
        LocalDate receiptDate,
        BigDecimal totalAcceptedQuantity,
        BigDecimal totalRejectedQuantity,
        BigDecimal totalReceivedQuantity,
        BigDecimal totalAmount,
        String note,
        String receivedBy,
        List<GrnItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static GrnResponse from(GoodsReceiptNote grn) {
        BigDecimal accepted = grn.getTotalAcceptedQuantity() != null ? grn.getTotalAcceptedQuantity() : BigDecimal.ZERO;
        BigDecimal rejected = grn.getTotalRejectedQuantity() != null ? grn.getTotalRejectedQuantity() : BigDecimal.ZERO;

        List<GrnItemResponse> itemResponses = grn.getItems() != null
                ? grn.getItems().stream().map(GrnItemResponse::from).toList()
                : Collections.emptyList();

        return new GrnResponse(
                grn.getId(),
                grn.getGrnNumber(),
                grn.getPurchaseOrder() != null ? grn.getPurchaseOrder().getId() : null,
                grn.getPoNumber(),
                grn.getSupplier() != null ? grn.getSupplier().getId() : null,
                grn.getSupplierName(),
                grn.getWarehouseId(),
                grn.getWarehouseName(),
                grn.getStatus(),
                grn.getReceiptDate(),
                accepted,
                rejected,
                accepted.add(rejected),
                grn.getTotalAmount() != null ? grn.getTotalAmount() : BigDecimal.ZERO,
                grn.getNote(),
                grn.getReceivedBy(),
                itemResponses,
                grn.getCreatedAt(),
                grn.getUpdatedAt()
        );
    }
}
