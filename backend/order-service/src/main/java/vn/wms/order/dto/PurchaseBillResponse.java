package vn.wms.order.dto;

import vn.wms.order.entity.PurchaseBill;
import vn.wms.order.entity.PurchaseBillStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Thông tin chi tiết Hóa đơn mua hàng (Purchase Bill - FR13 - WMS-10.3).
 */
public record PurchaseBillResponse(
        Long id,
        String billNumber,
        Long grnId,
        String grnNumber,
        Long poId,
        String poNumber,
        Long supplierId,
        String supplierName,
        LocalDate billDate,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        PurchaseBillStatus status,
        String note,
        Instant createdAt,
        Instant updatedAt,
        List<PurchaseBillItemResponse> items
) {
    public static PurchaseBillResponse from(PurchaseBill bill) {
        List<PurchaseBillItemResponse> itemResponses = bill.getItems() != null
                ? bill.getItems().stream().map(PurchaseBillItemResponse::from).toList()
                : List.of();

        return new PurchaseBillResponse(
                bill.getId(),
                bill.getBillNumber(),
                bill.getGoodsReceiptNote() != null ? bill.getGoodsReceiptNote().getId() : null,
                bill.getGrnNumber(),
                bill.getPurchaseOrder() != null ? bill.getPurchaseOrder().getId() : null,
                bill.getPoNumber(),
                bill.getSupplier() != null ? bill.getSupplier().getId() : null,
                bill.getSupplierName(),
                bill.getBillDate(),
                bill.getDueDate(),
                bill.getTotalAmount(),
                bill.getPaidAmount(),
                bill.getRemainingAmount(),
                bill.getStatus(),
                bill.getNote(),
                bill.getCreatedAt(),
                bill.getUpdatedAt(),
                itemResponses
        );
    }
}
