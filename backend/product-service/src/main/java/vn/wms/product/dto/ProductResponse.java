package vn.wms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.product.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

/** Thông tin sản phẩm trả về cho client. */
@Schema(description = "Thông tin chi tiết SKU sản phẩm")
public record ProductResponse(
        @Schema(description = "ID bản ghi", example = "1")
        Long id,

        @Schema(description = "Mã SKU", example = "SKU-GAO-ST25-5KG")
        String sku,

        @Schema(description = "Mã vạch EAN/UPC", example = "8938501234567")
        String barcode,

        @Schema(description = "Tên sản phẩm", example = "Gạo ST25 Túi 5kg")
        String name,

        @Schema(description = "Mô tả sản phẩm")
        String description,

        @Schema(description = "ID danh mục ngành hàng", example = "1")
        Long categoryId,

        @Schema(description = "Tên danh mục ngành hàng", example = "Lương thực")
        String categoryName,

        @Schema(description = "ID đơn vị tính", example = "1")
        Long uomId,

        @Schema(description = "Mã đơn vị tính", example = "BAG")
        String uomCode,

        @Schema(description = "Tên đơn vị tính", example = "Túi")
        String uomName,

        @Schema(description = "Giá nhập dự kiến (VNĐ)", example = "150000.00")
        BigDecimal purchasePrice,

        @Schema(description = "Giá bán niêm yết (VNĐ)", example = "180000.00")
        BigDecimal sellingPrice,

        @Schema(description = "Thuế suất VAT (%)", example = "5.00")
        BigDecimal vatRate,

        @Schema(description = "URL ảnh sản phẩm")
        String imageUrl,

        @Schema(description = "Cờ quản lý theo Lô", example = "true")
        boolean manageByLot,

        @Schema(description = "Cờ quản lý theo Hạn dùng", example = "true")
        boolean manageByExpiration,

        @Schema(description = "Mức tồn an toàn tối thiểu", example = "50.000")
        BigDecimal safetyStock,

        @Schema(description = "Điểm đặt hàng lại", example = "100.000")
        BigDecimal reorderPoint,

        @Schema(description = "Mức tồn kho tối đa", example = "1000.000")
        BigDecimal maxStock,

        @Schema(description = "Trạng thái hoạt động", example = "true")
        boolean active,

        @Schema(description = "Thời điểm tạo")
        Instant createdAt,

        @Schema(description = "Thời điểm cập nhật gần nhất")
        Instant updatedAt
) {

    /** Chuyển entity sang DTO. Gọi trong transaction vì category và uom được nạp lazy. */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                product.getDescription(),
                product.getCategory() == null ? null : product.getCategory().getId(),
                product.getCategory() == null ? null : product.getCategory().getName(),
                product.getUom() == null ? null : product.getUom().getId(),
                product.getUom() == null ? null : product.getUom().getCode(),
                product.getUom() == null ? null : product.getUom().getName(),
                product.getPurchasePrice(),
                product.getSellingPrice(),
                product.getVatRate(),
                product.getImageUrl(),
                product.isManageByLot(),
                product.isManageByExpiration(),
                product.getSafetyStock(),
                product.getReorderPoint(),
                product.getMaxStock(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
