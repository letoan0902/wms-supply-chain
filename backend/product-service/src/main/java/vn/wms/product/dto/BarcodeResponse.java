package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Thông tin ảnh mã vạch / QR Code đã sinh")
public record BarcodeResponse(
        @Schema(description = "Nội dung văn bản được mã hóa", example = "SKU-GAO-ST25-5KG")
        String content,

        @Schema(description = "Loại mã vạch", example = "CODE_128")
        BarcodeType type,

        @Schema(description = "Chiều rộng ảnh (pixel)", example = "300")
        int width,

        @Schema(description = "Chiều cao ảnh (pixel)", example = "100")
        int height,

        @Schema(description = "Chuỗi Base64 Data URI để nhúng trực tiếp vào thẻ <img src=... />",
                example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
        String base64Image
) {
}
