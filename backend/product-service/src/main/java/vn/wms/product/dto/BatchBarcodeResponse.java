package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Kết quả sinh tem mã vạch / QR Code kết hợp SKU, Lô hàng và Hạn dùng")
public record BatchBarcodeResponse(
        @Schema(description = "ID sản phẩm", example = "1")
        Long productId,

        @Schema(description = "Mã SKU", example = "SKU-GAO-ST25-5KG")
        String sku,

        @Schema(description = "Tên sản phẩm", example = "Gạo ST25 Túi 5kg")
        String productName,

        @Schema(description = "Số lô hàng", example = "LOT-20261028-01")
        String batchNumber,

        @Schema(description = "Hạn sử dụng", example = "2027-10-28")
        LocalDate expirationDate,

        @Schema(description = "Chuỗi dữ liệu được mã hóa trong mã vạch/QR", example = "SKU:SKU-GAO-ST25-5KG|LOT:LOT-20261028-01|EXP:2027-10-28")
        String encodedContent,

        @Schema(description = "Loại mã", example = "QR_CODE")
        BarcodeType type,

        @Schema(description = "Chuỗi Base64 Data URI của tem nhãn")
        String base64Image
) {
}
