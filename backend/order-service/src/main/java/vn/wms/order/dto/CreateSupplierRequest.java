package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload thêm mới nhà cung cấp (FR09 - WMS-08.1).
 */
public record CreateSupplierRequest(
        @NotBlank(message = "Mã nhà cung cấp không được để trống")
        @Size(max = 30, message = "Mã nhà cung cấp tối đa 30 ký tự")
        @Schema(description = "Mã nhà cung cấp duy nhất", example = "SUP-VINAMILK")
        String code,

        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 150, message = "Tên nhà cung cấp tối đa 150 ký tự")
        @Schema(description = "Tên công ty / nhà cung cấp", example = "Công ty CP Sữa Việt Nam (Vinamilk)")
        String name,

        @Size(max = 30, message = "Mã số thuế tối đa 30 ký tự")
        @Schema(description = "Mã số thuế doanh nghiệp", example = "0300588569")
        String taxCode,

        @Size(max = 100, message = "Tên người liên hệ tối đa 100 ký tự")
        @Schema(description = "Người đại diện / liên hệ", example = "Nguyễn Văn A")
        String contactName,

        @Email(message = "Email không hợp lệ")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        @Schema(description = "Email liên hệ", example = "contact@vinamilk.com.vn")
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
        @Schema(description = "Điều khoản thanh toán (Payment Terms)", example = "NET_30")
        String paymentTerms,

        @DecimalMin(value = "0.0", message = "Hạn mức công nợ không được âm")
        @Schema(description = "Hạn mức nợ tối đa (VNĐ)", example = "500000000.00")
        BigDecimal creditLimit,

        @DecimalMin(value = "0.0", message = "Công nợ ban đầu không được âm")
        @Schema(description = "Công nợ ban đầu (nếu có, VNĐ)", example = "0.00")
        BigDecimal initialDebt
) {
}
