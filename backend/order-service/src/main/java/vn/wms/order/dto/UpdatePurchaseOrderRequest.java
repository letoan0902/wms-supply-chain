package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload cập nhật Đơn mua hàng (PO - FR10, WMS-09.1).
 * Chỉ cho phép cập nhật khi đơn đang ở trạng thái DRAFT hoặc PENDING.
 */
public record UpdatePurchaseOrderRequest(
        @NotNull(message = "Nhà cung cấp không được để trống")
        @Schema(description = "ID nhà cung cấp", example = "1")
        Long supplierId,

        @Schema(description = "ID kho nhận hàng (nếu có)", example = "1")
        Long warehouseId,

        @Size(max = 100, message = "Tên kho nhận tối đa 100 ký tự")
        @Schema(description = "Tên kho nhận hàng", example = "Kho Tổng Tân Bình")
        String warehouseName,

        @NotNull(message = "Ngày đặt hàng không được để trống")
        @Schema(description = "Ngày lập đơn mua", example = "2026-11-03")
        LocalDate orderDate,

        @Schema(description = "Ngày dự kiến nhận hàng", example = "2026-11-10")
        LocalDate expectedDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        @Schema(description = "Ghi chú đơn hàng", example = "Cập nhật ngày giao hàng sớm hơn")
        String note,

        @NotEmpty(message = "Đơn mua hàng phải có ít nhất 1 dòng mặt hàng")
        @Valid
        @Schema(description = "Danh sách chi tiết mặt hàng đặt mua")
        List<PurchaseOrderItemRequest> items
) {
}
