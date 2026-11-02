package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "Yêu cầu sinh tem mã vạch / QR Code kết hợp SKU, Lô hàng và Hạn sử dụng")
public record BatchBarcodeRequest(
        @Schema(description = "Mã số lô hàng (Batch / Lot Number)", example = "LOT-20261028-01")
        @NotBlank(message = "Mã lô không được để trống")
        String batchNumber,

        @Schema(description = "Hạn sử dụng (Exp Date)", example = "2027-10-28")
        @NotNull(message = "Hạn sử dụng không được để trống")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate expirationDate,

        @Schema(description = "Loại mã (mặc định QR_CODE)", example = "QR_CODE")
        BarcodeType type,

        @Schema(description = "Chiều rộng ảnh tùy chọn (mặc định 250)", example = "250")
        Integer width,

        @Schema(description = "Chiều cao ảnh tùy chọn (mặc định 250)", example = "250")
        Integer height
) {
}
