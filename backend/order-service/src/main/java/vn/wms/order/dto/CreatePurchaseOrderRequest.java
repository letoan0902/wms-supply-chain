package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.wms.order.entity.OrderStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload tạo mới Đơn mua hàng (PO - FR10, WMS-09.1).
 */
public record CreatePurchaseOrderRequest(
        @Size(max = 30, message = "Mã đơn PO tối đa 30 ký tự")
        @Schema(description = "Mã đơn mua hàng (bỏ trống hệ thống sẽ tự sinh dạng PO-YYYYMMDD-XXXX)", example = "PO-20261103-0001")
        String poNumber,

        @NotNull(message = "Nhà cung cấp không được để trống")
        @Schema(description = "ID nhà cung cấp", example = "1")
        Long supplierId,

        @Schema(description = "ID kho nhận hàng (nếu có)", example = "1")
        Long warehouseId,

        @Size(max = 100, message = "Tên kho nhận tối đa 100 ký tự")
        @Schema(description = "Tên kho nhận hàng", example = "Kho Tổng Tân Bình")
        String warehouseName,

        @Schema(description = "Ngày lập đơn mua (mặc định hôm nay nếu bỏ trống)", example = "2026-11-03")
        LocalDate orderDate,

        @Schema(description = "Ngày dự kiến nhận hàng", example = "2026-11-10")
        LocalDate expectedDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        @Schema(description = "Ghi chú đơn hàng", example = "Giao hàng giờ hành chính, liên hệ thủ kho trước 2 tiếng")
        String note,

        @Schema(description = "Trạng thái khởi tạo: PENDING (mặc định gửi duyệt) hoặc DRAFT (nháp)", example = "PENDING")
        OrderStatus status,

        @NotEmpty(message = "Đơn mua hàng phải có ít nhất 1 dòng mặt hàng")
        @Valid
        @Schema(description = "Danh sách chi tiết mặt hàng đặt mua")
        List<PurchaseOrderItemRequest> items
) {
}
