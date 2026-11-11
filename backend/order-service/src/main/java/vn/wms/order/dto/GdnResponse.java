package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.order.entity.GoodsDeliveryNote;
import vn.wms.order.entity.GoodsDeliveryStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Phản hồi thông tin Phiếu xuất kho (GDN Response - WMS-12.1).
 */
public record GdnResponse(
        @Schema(description = "ID phiếu xuất kho", example = "1")
        Long id,

        @Schema(description = "Mã phiếu xuất kho", example = "GDN-20261111-0001")
        String gdnNumber,

        @Schema(description = "ID đơn bán hàng", example = "10")
        Long soId,

        @Schema(description = "Mã đơn bán hàng", example = "SO-20261111-0001")
        String soNumber,

        @Schema(description = "ID khách hàng", example = "5")
        Long customerId,

        @Schema(description = "Tên khách hàng", example = "Công ty TNHH Bách Hóa")
        String customerName,

        @Schema(description = "ID kho xuất hàng", example = "1")
        Long warehouseId,

        @Schema(description = "Tên kho xuất hàng", example = "Kho Tổng Tân Bình")
        String warehouseName,

        @Schema(description = "Trạng thái phiếu xuất", example = "COMPLETED")
        GoodsDeliveryStatus status,

        @Schema(description = "Ngày xuất kho", example = "2026-11-11")
        LocalDate deliveryDate,

        @Schema(description = "Tổng số lượng xuất", example = "35.000")
        BigDecimal totalQuantity,

        @Schema(description = "Tổng giá trị xuất kho (VNĐ)", example = "875000.00")
        BigDecimal totalAmount,

        @Schema(description = "Người nhận hàng", example = "Nguyễn Văn A")
        String recipientName,

        @Schema(description = "Số điện thoại người nhận", example = "0987654321")
        String recipientPhone,

        @Schema(description = "Địa chỉ giao hàng", example = "123 Đường Cộng Hòa, Q. Tân Bình")
        String deliveryAddress,

        @Schema(description = "Ghi chú", example = "Giao hàng buổi sáng")
        String note,

        @Schema(description = "Người thực hiện xuất kho", example = "thukho1")
        String dispatchedBy,

        @Schema(description = "Thời điểm xuất xưởng")
        Instant dispatchedAt,

        @Schema(description = "Danh sách chi tiết dòng hàng xuất")
        List<GdnItemResponse> items,

        Instant createdAt,
        Instant updatedAt
) {
    public static GdnResponse from(GoodsDeliveryNote gdn) {
        if (gdn == null) return null;
        List<GdnItemResponse> itemResponses = gdn.getItems() != null
                ? gdn.getItems().stream().map(GdnItemResponse::from).toList()
                : List.of();

        return new GdnResponse(
                gdn.getId(),
                gdn.getGdnNumber(),
                gdn.getSalesOrder() != null ? gdn.getSalesOrder().getId() : null,
                gdn.getSoNumber(),
                gdn.getCustomer() != null ? gdn.getCustomer().getId() : null,
                gdn.getCustomerName(),
                gdn.getWarehouseId(),
                gdn.getWarehouseName(),
                gdn.getStatus(),
                gdn.getDeliveryDate(),
                gdn.getTotalQuantity(),
                gdn.getTotalAmount(),
                gdn.getRecipientName(),
                gdn.getRecipientPhone(),
                gdn.getDeliveryAddress(),
                gdn.getNote(),
                gdn.getDispatchedBy(),
                gdn.getDispatchedAt(),
                itemResponses,
                gdn.getCreatedAt(),
                gdn.getUpdatedAt()
        );
    }
}
