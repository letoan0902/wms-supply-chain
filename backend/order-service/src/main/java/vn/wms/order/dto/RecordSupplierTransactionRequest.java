package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.wms.order.entity.SupplierTransactionType;

import java.math.BigDecimal;

/**
 * Payload ghi nhận giao dịch công nợ với Nhà cung cấp (FR09 - WMS-08.1).
 */
public record RecordSupplierTransactionRequest(
        @NotNull(message = "Loại giao dịch không được để trống")
        @Schema(description = "Loại giao dịch công nợ", example = "PURCHASE_INVOICE")
        SupplierTransactionType type,

        @NotNull(message = "Số tiền không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền giao dịch phải lớn hơn 0")
        @Schema(description = "Số tiền giao dịch (VNĐ)", example = "15000000.00")
        BigDecimal amount,

        @Size(max = 50, message = "Mã chứng từ tối đa 50 ký tự")
        @Schema(description = "Số hóa đơn / Số PO / Số phiếu chi UNC", example = "PO-20261102-01")
        String referenceCode,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        @Schema(description = "Diễn giải / lý do giao dịch", example = "Nhập kho đợt 1 theo PO-20261102-01")
        String note
) {
}
