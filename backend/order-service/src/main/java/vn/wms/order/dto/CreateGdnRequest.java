package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import vn.wms.order.entity.GoodsDeliveryStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Yêu cầu lập phiếu xuất kho từ Đơn bán hàng SO (WMS-12.1 - FR17).
 */
public record CreateGdnRequest(
        @NotNull(message = "soId không được để trống")
        @Schema(description = "ID đơn bán hàng cần xuất", example = "1")
        Long soId,

        @Schema(description = "Ngày xuất kho thực tế (mặc định hôm nay nếu để trống)", example = "2026-11-11")
        LocalDate deliveryDate,

        @Schema(description = "Trạng thái phiếu xuất (COMPLETED để xuất ngay, PICKING nếu đang lấy hàng)", example = "COMPLETED")
        GoodsDeliveryStatus status,

        @Schema(description = "Người nhận hàng", example = "Nguyễn Văn A")
        String recipientName,

        @Schema(description = "Số điện thoại người nhận", example = "0987654321")
        String recipientPhone,

        @Schema(description = "Địa chỉ giao hàng thực tế", example = "123 Đường Cộng Hòa, Q. Tân Bình, TP. HCM")
        String deliveryAddress,

        @Schema(description = "Ghi chú xuất kho", example = "Giao buổi chiều trước 17h")
        String note,

        @NotEmpty(message = "Danh sách dòng hàng xuất không được để trống")
        @Valid
        @Schema(description = "Chi tiết các mặt hàng xuất kho")
        List<CreateGdnItemRequest> items
) {
}
