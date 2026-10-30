package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** DTO tạo mới sản phẩm (SKU). */
@Schema(description = "Thông tin tạo mới SKU sản phẩm")
public record CreateProductRequest(
        @Schema(description = "Mã SKU duy nhất", example = "SKU-GAO-ST25-5KG")
        @NotBlank(message = "Mã SKU không được để trống")
        @Size(max = 64, message = "Mã SKU tối đa 64 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "Mã SKU chỉ gồm chữ cái, số, dấu gạch ngang và gạch dưới")
        String sku,

        @Schema(description = "Mã vạch EAN/UPC", example = "8938501234567")
        @Size(max = 64, message = "Mã vạch tối đa 64 ký tự")
        String barcode,

        @Schema(description = "Tên sản phẩm", example = "Gạo ST25 Túi 5kg")
        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
        String name,

        @Schema(description = "Mô tả chi tiết", example = "Gạo thơm thượng hạng Sóc Trăng đóng túi 5kg")
        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
        String description,

        @Schema(description = "ID danh mục ngành hàng", example = "1")
        Long categoryId,

        @Schema(description = "ID đơn vị tính cơ sở", example = "1")
        @NotNull(message = "Đơn vị tính không được để trống")
        Long uomId,

        @Schema(description = "Giá nhập dự kiến (VNĐ)", example = "150000.00")
        @DecimalMin(value = "0.0", message = "Giá nhập không được âm")
        @Digits(integer = 16, fraction = 2, message = "Giá nhập không hợp lệ")
        BigDecimal purchasePrice,

        @Schema(description = "Giá bán niêm yết (VNĐ)", example = "180000.00")
        @DecimalMin(value = "0.0", message = "Giá bán không được âm")
        @Digits(integer = 16, fraction = 2, message = "Giá bán không hợp lệ")
        BigDecimal sellingPrice,

        @Schema(description = "Thuế suất VAT (%)", example = "5.00")
        @DecimalMin(value = "0.0", message = "Thuế suất VAT không được âm")
        @Digits(integer = 3, fraction = 2, message = "Thuế suất VAT không hợp lệ")
        BigDecimal vatRate,

        @Schema(description = "Đường dẫn ảnh sản phẩm", example = "https://example.com/images/gao-st25.jpg")
        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự")
        String imageUrl,

        @Schema(description = "Cờ quản lý theo Lô (Batch/Lot)", example = "true")
        Boolean manageByLot,

        @Schema(description = "Cờ quản lý theo Hạn dùng (Exp Date)", example = "true")
        Boolean manageByExpiration,

        @Schema(description = "Mức tồn an toàn tối thiểu (Safety Stock)", example = "50.000")
        @DecimalMin(value = "0.0", message = "Mức tồn an toàn không được âm")
        @Digits(integer = 15, fraction = 3, message = "Mức tồn an toàn không hợp lệ")
        BigDecimal safetyStock,

        @Schema(description = "Điểm đặt hàng lại (Reorder Point)", example = "100.000")
        @DecimalMin(value = "0.0", message = "Điểm đặt hàng lại không được âm")
        @Digits(integer = 15, fraction = 3, message = "Điểm đặt hàng lại không hợp lệ")
        BigDecimal reorderPoint,

        @Schema(description = "Mức tồn kho tối đa (Max Stock)", example = "1000.000")
        @DecimalMin(value = "0.0", message = "Mức tồn tối đa không được âm")
        @Digits(integer = 15, fraction = 3, message = "Mức tồn tối đa không hợp lệ")
        BigDecimal maxStock
) {
}
