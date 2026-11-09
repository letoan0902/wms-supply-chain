package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload chi tiết từng mặt hàng khi lập phiếu nhập kho GRN (FR11, FR12 - WMS-10.1, WMS-10.2).
 */
public record CreateGrnItemRequest(
        @NotNull(message = "ID dòng mặt hàng PO không được để trống")
        @Schema(description = "ID dòng mặt hàng của đơn mua (po_items.id)", example = "1")
        Long poItemId,

        @NotNull(message = "Số lượng thực nhận đạt chuẩn không được để trống")
        @DecimalMin(value = "0.0", message = "Số lượng đạt chuẩn không được âm")
        @Schema(description = "Số lượng thực tế đạt chuẩn chất lượng nhập kho (Accepted)", example = "80.000")
        BigDecimal acceptedQuantity,

        @DecimalMin(value = "0.0", message = "Số lượng bị từ chối không được âm")
        @Schema(description = "Số lượng bị từ chối / phế phẩm / lỗi hỏng (Rejected)", example = "5.000")
        BigDecimal rejectedQuantity,

        @Size(max = 255, message = "Lý do từ chối tối đa 255 ký tự")
        @Schema(description = "Lý do hàng hỏng / phế phẩm", example = "Bao bì rách, móp méo khi vận chuyển")
        String rejectedReason,

        @Size(max = 50, message = "Số lô tối đa 50 ký tự")
        @Schema(description = "Số lô sản xuất (Batch / Lot Number - FR12)", example = "LOT-202611-A1")
        String batchNumber,

        @Schema(description = "Ngày sản xuất (Mfg Date - FR12)", example = "2026-10-01")
        LocalDate mfgDate,

        @Schema(description = "Hạn sử dụng (Exp Date - FR12)", example = "2027-10-01")
        LocalDate expDate,

        @Schema(description = "ID vị trí ô/kệ lưu kho", example = "10")
        Long locationId,

        @Size(max = 50, message = "Mã vị trí tối đa 50 ký tự")
        @Schema(description = "Mã vị trí ô/kệ lưu kho", example = "A-01-02")
        String locationCode,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        @Schema(description = "Ghi chú dòng hàng", example = "Đã kiểm đếm đạt chuẩn")
        String note
) {
}
