package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.order.entity.SalesOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Thông tin đơn bán hàng (Sales Order - SO) trả về cho client (FR15 - WMS-11.1).
 */
public record SalesOrderResponse(
        @Schema(description = "ID đơn bán hàng", example = "1")
        Long id,

        @Schema(description = "Mã đơn bán hàng", example = "SO-20261104-0001")
        String soNumber,

        @Schema(description = "ID khách hàng", example = "1")
        Long customerId,

        @Schema(description = "Mã khách hàng", example = "KH001")
        String customerCode,

        @Schema(description = "Tên khách hàng", example = "Công ty TNHH Bán lẻ Miền Bắc")
        String customerName,

        @Schema(description = "ID kho xuất hàng", example = "1")
        Long warehouseId,

        @Schema(description = "Tên kho xuất hàng", example = "Kho Tổng Tân Bình")
        String warehouseName,

        @Schema(description = "Địa chỉ nhận hàng", example = "123 Lê Lợi, Quận 1, TP.HCM")
        String deliveryAddress,

        @Schema(description = "Tên người nhận", example = "Nguyễn Văn B")
        String recipientName,

        @Schema(description = "Số điện thoại nhận hàng", example = "0901234567")
        String recipientPhone,

        @Schema(description = "Trạng thái đơn hàng: DRAFT, CONFIRMED, BACKORDER, COMPLETED, CANCELLED", example = "CONFIRMED")
        String status,

        @Schema(description = "Ngày lập đơn bán", example = "2026-11-04")
        LocalDate orderDate,

        @Schema(description = "Ngày dự kiến giao hàng", example = "2026-11-08")
        LocalDate expectedDate,

        @Schema(description = "Tiền hàng trước thuế và chiết khấu (VNĐ)", example = "4200000.00")
        BigDecimal subtotalAmount,

        @Schema(description = "Tổng tiền chiết khấu (VNĐ)", example = "210000.00")
        BigDecimal discountAmount,

        @Schema(description = "Tổng tiền thuế VAT (VNĐ)", example = "319200.00")
        BigDecimal taxAmount,

        @Schema(description = "Tổng tiền thanh toán sau thuế và chiết khấu (VNĐ)", example = "4309200.00")
        BigDecimal totalAmount,

        @Schema(description = "Ghi chú đơn hàng")
        String note,

        @Schema(description = "Người xác nhận đơn", example = "admin")
        String confirmedBy,

        @Schema(description = "Thời điểm xác nhận đơn")
        Instant confirmedAt,

        @Schema(description = "Lý do thiếu hàng nếu đơn chuyển sang BACKORDER")
        String backorderReason,

        @Schema(description = "Danh sách chi tiết mặt hàng trong đơn")
        List<SalesOrderItemResponse> items,

        @Schema(description = "Thời điểm tạo đơn")
        Instant createdAt,

        @Schema(description = "Thời điểm cập nhật đơn gần nhất")
        Instant updatedAt
) {
    public static SalesOrderResponse from(SalesOrder order) {
        List<SalesOrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(SalesOrderItemResponse::from).toList()
                : Collections.emptyList();

        return new SalesOrderResponse(
                order.getId(),
                order.getSoNumber(),
                order.getCustomer() != null ? order.getCustomer().getId() : null,
                order.getCustomer() != null ? order.getCustomer().getCode() : null,
                order.getCustomer() != null ? order.getCustomer().getName() : null,
                order.getWarehouseId(),
                order.getWarehouseName(),
                order.getDeliveryAddress(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getStatus().name(),
                order.getOrderDate(),
                order.getExpectedDate(),
                order.getSubtotalAmount() != null ? order.getSubtotalAmount() : BigDecimal.ZERO,
                order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO,
                order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO,
                order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO,
                order.getNote(),
                order.getConfirmedBy(),
                order.getConfirmedAt(),
                order.getBackorderReason(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
