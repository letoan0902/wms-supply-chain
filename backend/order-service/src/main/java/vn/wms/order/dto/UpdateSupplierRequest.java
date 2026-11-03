package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload cập nhật hồ sơ nhà cung cấp (FR09 - WMS-08.1).
 */
public record UpdateSupplierRequest(
        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 150, message = "Tên nhà cung cấp tối đa 150 ký tự")
        @Schema(description = "Tên công ty / nhà cung cấp", example = "Công ty CP Sữa Việt Nam (Vinamilk)")
        String name,

        @Size(max = 30, message = "Mã số thuế tối đa 30 ký tự")
        @Schema(description = "Mã số thuế doanh nghiệp", example = "0300588569")
        String taxCode,

        @Size(max = 100, message = "Tên người liên hệ tối đa 100 ký tự")
        @Schema(description = "Người đại diện / liên hệ", example = "Nguyễn Văn B")
        String contactName,

        @Email(message = "Email không hợp lệ")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        @Schema(description = "Email liên hệ", example = "support@vinamilk.com.vn")
        String email,

        @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
        @Schema(description = "Số điện thoại liên hệ", example = "02854155555")
        String phone,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        @Schema(description = "Địa chỉ trụ sở", example = "10 Tân Trào, P. Tân Phú, Quận 7, TP.HCM")
        String address,

        @Size(max = 50, message = "Số tài khoản ngân hàng tối đa 50 ký tự")
        @Schema(description = "Số tài khoản ngân hàng", example = "19034567890012")
        String bankAccount,

        @Size(max = 100, message = "Tên ngân hàng tối đa 100 ký tự")
        @Schema(description = "Tên ngân hàng & chi nhánh", example = "Techcombank - CN Sài Gòn")
        String bankName,

        @Size(max = 50, message = "Điều khoản thanh toán tối đa 50 ký tự")
        @Schema(description = "Điều khoản thanh toán (Payment Terms)", example = "NET_45")
        String paymentTerms,

        @DecimalMin(value = "0.0", message = "Hạn mức công nợ không được âm")
        @Schema(description = "Hạn mức nợ tối đa (VNĐ)", example = "600000000.00")
        BigDecimal creditLimit,

        @Schema(description = "Trạng thái hợp tác", example = "true")
        Boolean active
) {
}
