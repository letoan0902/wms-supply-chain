package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.wms.order.entity.CustomerType;

import java.math.BigDecimal;

/**
 * Payload cập nhật hồ sơ khách hàng / đại lý (FR14 - WMS-08.2).
 */
public record UpdateCustomerRequest(
        @NotBlank(message = "Tên khách hàng không được để trống")
        @Size(max = 150, message = "Tên khách hàng tối đa 150 ký tự")
        @Schema(description = "Tên khách hàng hoặc tên công ty", example = "Công ty CP Bách Hóa Xanh")
        String name,

        @NotNull(message = "Loại khách hàng không được để trống")
        @Schema(description = "Phân loại khách hàng: WHOLESALE (sỉ) hoặc RETAIL (lẻ)", example = "WHOLESALE")
        CustomerType customerType,

        @Size(max = 30, message = "Mã số thuế tối đa 30 ký tự")
        @Schema(description = "Mã số thuế doanh nghiệp", example = "0310471746")
        String taxCode,

        @Size(max = 100, message = "Tên người liên hệ tối đa 100 ký tự")
        @Schema(description = "Người đại diện / liên hệ", example = "Trần Thị Mai")
        String contactName,

        @Email(message = "Email không hợp lệ")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        @Schema(description = "Email liên hệ", example = "procurement@bachhoaxanh.com")
        String email,

        @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
        @Schema(description = "Số điện thoại liên hệ", example = "02838123456")
        String phone,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        @Schema(description = "Địa chỉ giao hàng / trụ sở", example = "128 Trần Quang Khải, P. Tân Định, Quận 1, TP.HCM")
        String address,

        @Size(max = 50, message = "Điều khoản thanh toán tối đa 50 ký tự")
        @Schema(description = "Điều khoản thanh toán (Payment Terms)", example = "NET_45")
        String paymentTerms,

        @DecimalMin(value = "0.0", message = "Hạn mức nợ không được âm")
        @Schema(description = "Hạn mức nợ tối đa (Credit Limit, VNĐ)", example = "1500000000.00")
        BigDecimal creditLimit,

        @Schema(description = "Khóa nợ chặn bán hàng", example = "false")
        Boolean debtLocked,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        @Schema(description = "Ghi chú về khách hàng", example = "Nâng hạn mức từ tháng 11")
        String notes,

        @Schema(description = "Trạng thái hợp tác", example = "true")
        Boolean active
) {
}
