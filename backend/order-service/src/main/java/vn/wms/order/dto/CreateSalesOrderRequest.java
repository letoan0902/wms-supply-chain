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
 * Payload tạo mới Đơn bán hàng (SO - FR15, WMS-11.1).
 */
public record CreateSalesOrderRequest(
        @Size(max = 30, message = "Mã đơn SO tối đa 30 ký tự")
        @Schema(description = "Mã đơn bán hàng (bỏ trống hệ thống sẽ tự sinh dạng SO-YYYYMMDD-XXXX)", example = "SO-20261104-0001")
        String soNumber,

        @NotNull(message = "Khách hàng không được để trống")
        @Schema(description = "ID khách hàng", example = "1")
        Long customerId,

        @Schema(description = "ID kho xuất hàng (nếu có)", example = "1")
        Long warehouseId,

        @Size(max = 100, message = "Tên kho xuất tối đa 100 ký tự")
        @Schema(description = "Tên kho xuất hàng", example = "Kho Tổng Tân Bình")
        String warehouseName,

        @Size(max = 255, message = "Địa chỉ nhận hàng tối đa 255 ký tự")
        @Schema(description = "Địa chỉ giao hàng", example = "123 Lê Lợi, Quận 1, TP.HCM")
        String deliveryAddress,

        @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự")
        @Schema(description = "Họ tên người nhận hàng", example = "Nguyễn Văn B")
        String recipientName,

        @Size(max = 30, message = "Số điện thoại người nhận tối đa 30 ký tự")
        @Schema(description = "Số điện thoại liên hệ nhận hàng", example = "0901234567")
        String recipientPhone,

        @Schema(description = "Ngày lập đơn bán (mặc định hôm nay nếu bỏ trống)", example = "2026-11-04")
        LocalDate orderDate,

        @Schema(description = "Ngày dự kiến giao hàng", example = "2026-11-08")
        LocalDate expectedDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        @Schema(description = "Ghi chú đơn hàng", example = "Giao giờ hành chính, gọi trước khi đến")
        String note,

        @Schema(description = "Trạng thái mong muốn (CONFIRMED hoặc DRAFT). Mặc định CONFIRMED nếu tồn khả dụng ATP đủ.", example = "CONFIRMED")
        OrderStatus status,

        @Schema(description = "Cho phép chuyển đơn sang BACKORDER khi thiếu tồn kho ATP (mặc định true)", example = "true")
        Boolean allowBackorder,

        @NotEmpty(message = "Đơn bán hàng phải có ít nhất 1 dòng mặt hàng")
        @Valid
        @Schema(description = "Danh sách chi tiết mặt hàng đặt bán")
        List<SalesOrderItemRequest> items
) {
    public boolean isAllowBackorder() {
        return allowBackorder == null || allowBackorder;
    }
}
