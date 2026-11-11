package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload ghi nhận thanh toán tiền hàng cho Nhà cung cấp theo Hóa đơn mua (Purchase Bill - FR13).
 */
public record RecordBillPaymentRequest(
        @NotNull(message = "Số tiền thanh toán không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền thanh toán phải lớn hơn 0")
        @Schema(description = "Số tiền thanh toán (VNĐ)", example = "5000000.00")
        BigDecimal amount,

        @Schema(description = "Phương thức thanh toán: BANK_TRANSFER, CASH, CHEQUE...", example = "BANK_TRANSFER")
        String paymentMethod,

        @Size(max = 50, message = "Mã chứng từ / ủy nhiệm chi tối đa 50 ký tự")
        @Schema(description = "Mã chứng từ tham chiếu (UNC, số séc, mã giao dịch ngân hàng)", example = "UNC-20261108-01")
        String referenceCode,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        @Schema(description = "Ghi chú nội dung thanh toán", example = "Thanh toán đợt 1 tiền mua nguyên vật liệu")
        String note,

        @Schema(description = "Ngày thanh toán thực tế (mặc định hôm nay nếu để trống)", example = "2026-11-08")
        LocalDate paymentDate
) {
}
