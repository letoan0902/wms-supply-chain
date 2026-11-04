package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO yêu cầu cho từng dòng sản phẩm trong đơn mua hàng (PO Item).
 */
public record PurchaseOrderItemRequest(
        @Schema(description = "ID sản phẩm (nếu đã có trong danh mục)", example = "1")
        Long productId,

        @NotBlank(message = "Mã SKU không được để trống")
        @Size(max = 50, message = "Mã SKU tối đa 50 ký tự")
        @Schema(description = "Mã SKU sản phẩm", example = "MILK-VINA-100")
        String skuCode,

        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
        @Schema(description = "Tên mặt hàng", example = "Sữa tươi tiệt trùng Vinamilk 100ml")
        String productName,

        @Size(max = 30, message = "Đơn vị tính tối đa 30 ký tự")
        @Schema(description = "Đơn vị tính", example = "Thùng")
        String uom,

        @NotNull(message = "Số lượng đặt mua không được để trống")
        @DecimalMin(value = "0.001", message = "Số lượng phải lớn hơn 0")
        @Schema(description = "Số lượng đặt mua", example = "100.000")
        BigDecimal quantity,

        @NotNull(message = "Đơn giá mua không được để trống")
        @DecimalMin(value = "0.0", message = "Đơn giá không được âm")
        @Schema(description = "Đơn giá mua trước thuế và chiết khấu (VNĐ)", example = "350000.00")
        BigDecimal unitPrice,

        @DecimalMin(value = "0.0", message = "Tỷ lệ chiết khấu không được âm")
        @jakarta.validation.constraints.DecimalMax(value = "100.00", message = "Tỷ lệ chiết khấu tối đa 100%")
        @Schema(description = "Tỷ lệ chiết khấu (%)", example = "5.00")
        BigDecimal discountPercent,

        @DecimalMin(value = "0.0", message = "Tiền chiết khấu không được âm")
        @Schema(description = "Số tiền chiết khấu chỉ định trực tiếp (VNĐ)", example = "0.00")
        BigDecimal discountAmount,

        @DecimalMin(value = "0.0", message = "Thuế suất VAT không được âm")
        @jakarta.validation.constraints.DecimalMax(value = "100.00", message = "Thuế suất VAT tối đa 100%")
        @Schema(description = "Thuế suất VAT (%)", example = "8.00")
        BigDecimal taxRate,

        @DecimalMin(value = "0.0", message = "Tiền thuế VAT không được âm")
        @Schema(description = "Số tiền thuế VAT chỉ định trực tiếp (VNĐ)", example = "0.00")
        BigDecimal taxAmount,

        @Size(max = 255, message = "Ghi chú dòng tối đa 255 ký tự")
        @Schema(description = "Ghi chú dòng mặt hàng", example = "Hàng đợt 1 đóng bao bì mới")
        String note
) {
}
