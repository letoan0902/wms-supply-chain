package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.wms.order.entity.CustomerTransactionType;

import java.math.BigDecimal;

/**
 * Payload ghi nhận giao dịch công nợ với Khách hàng (FR14 - WMS-08.2).
 */
public record RecordCustomerTransactionRequest(
        @NotNull(message = "Loại giao dịch không được để trống")
        @Schema(description = "Loại giao dịch công nợ", example = "SALES_INVOICE")
        CustomerTransactionType type,

        @NotNull(message = "Số tiền không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền giao dịch phải lớn hơn 0")
        @Schema(description = "Số tiền giao dịch (VNĐ)", example = "25000000.00")
        BigDecimal amount,

        @Size(max = 50, message = "Mã chứng từ tối đa 50 ký tự")
        @Schema(description = "Số hóa đơn GTGT / Số SO / Số phiếu thu", example = "SO-20261103-01")
        String referenceCode,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        @Schema(description = "Diễn giải / lý do giao dịch", example = "Xuất bán hàng đợt 1 theo đơn SO-20261103-01")
        String note
) {
}
