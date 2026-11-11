package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Một dòng của API nhập hàng hóa hàng loạt {@code POST /api/products/bulk} (WMS-22.4b).
 *
 * <p>Danh mục và đơn vị tính ghi bằng tên thay cho ID; tên chưa có trong doanh nghiệp thì được tạo mới. Các trường
 * còn lại được kiểm tra bằng đúng các ràng buộc của {@link CreateProductRequest} (xem
 * {@link #toCreateRequest(Long, Long)}), ở đây chỉ khai báo ràng buộc của hai trường riêng là {@code uomName} và
 * {@code categoryName}.
 */
@Schema(description = "Một dòng nhập hàng hóa hàng loạt")
public record BulkProductItem(
        @Schema(description = "Mã SKU (bắt buộc, chữ cái, số, gạch ngang, gạch dưới)", example = "SKU-GAO-ST25-5KG")
        String sku,

        @Schema(description = "Tên sản phẩm (bắt buộc)", example = "Gạo ST25 Túi 5kg")
        String name,

        @Schema(description = "Mã vạch EAN/UPC", example = "8938501234567")
        String barcode,

        @Schema(description = "Mô tả chi tiết", example = "Gạo thơm Sóc Trăng đóng túi 5kg")
        String description,

        @Schema(description = "Tên danh mục; chưa có trong doanh nghiệp thì tự tạo", example = "Lương thực")
        @Size(max = 150, message = "Tên danh mục tối đa 150 ký tự")
        String categoryName,

        @Schema(description = "Tên đơn vị tính (bắt buộc); chưa có trong doanh nghiệp thì tự tạo", example = "Túi")
        @NotBlank(message = "Đơn vị tính không được để trống")
        @Size(max = 100, message = "Tên đơn vị tính tối đa 100 ký tự")
        String uomName,

        @Schema(description = "Giá nhập dự kiến (VNĐ)", example = "150000.00")
        BigDecimal purchasePrice,

        @Schema(description = "Giá bán niêm yết (VNĐ)", example = "180000.00")
        BigDecimal sellingPrice,

        @Schema(description = "Thuế suất VAT (%)", example = "5.00")
        BigDecimal vatRate,

        @Schema(description = "Mức tồn an toàn tối thiểu", example = "50.000")
        BigDecimal safetyStock,

        @Schema(description = "Điểm đặt hàng lại", example = "100.000")
        BigDecimal reorderPoint,

        @Schema(description = "Mức tồn kho tối đa", example = "1000.000")
        BigDecimal maxStock
) {

    /**
     * Chuyển dòng thành request tạo một sản phẩm, để kiểm tra và tạo đúng như {@code POST /api/products}. Chuỗi được
     * cắt khoảng trắng hai đầu (dữ liệu từ file Excel hay dính khoảng trắng); trường tùy chọn để trống thành
     * {@code null}.
     */
    public CreateProductRequest toCreateRequest(Long categoryId, Long uomId) {
        return new CreateProductRequest(trim(sku), blankToNull(barcode), trim(name), blankToNull(description),
                categoryId, uomId, purchasePrice, sellingPrice, vatRate, null, null, null,
                safetyStock, reorderPoint, maxStock);
    }

    private static String trim(String value) {
        return value != null ? value.trim() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
