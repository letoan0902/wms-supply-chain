package vn.wms.order.dto;

import vn.wms.order.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Thông tin đơn mua hàng trả về cho client. */
public record PurchaseOrderResponse(Long id, String poNumber, Long supplierId, String supplierName, String status,
                                    LocalDate orderDate, LocalDate expectedDate, BigDecimal totalAmount) {

    /** Chuyển entity sang DTO. Gọi trong transaction vì supplier được nạp lazy. */
    public static PurchaseOrderResponse from(PurchaseOrder order) {
        return new PurchaseOrderResponse(
                order.getId(),
                order.getPoNumber(),
                order.getSupplier().getId(),
                order.getSupplier().getName(),
                order.getStatus().name(),
                order.getOrderDate(),
                order.getExpectedDate(),
                order.getTotalAmount());
    }
}
