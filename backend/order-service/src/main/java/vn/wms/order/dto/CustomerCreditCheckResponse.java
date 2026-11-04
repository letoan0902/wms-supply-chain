package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.order.entity.CustomerType;

import java.math.BigDecimal;

/**
 * Kết quả kiểm tra hạn mức tín dụng khách hàng để duyệt/chặn đơn bán hàng (FR14 - WMS-08.2).
 */
public record CustomerCreditCheckResponse(
        @Schema(description = "ID khách hàng")
        Long customerId,

        @Schema(description = "Mã khách hàng")
        String customerCode,

        @Schema(description = "Tên khách hàng")
        String customerName,

        @Schema(description = "Phân loại khách hàng")
        CustomerType customerType,

        @Schema(description = "Hạn mức nợ tối đa (Credit Limit, VNĐ)")
        BigDecimal creditLimit,

        @Schema(description = "Công nợ hiện tại (VNĐ)")
        BigDecimal currentDebt,

        @Schema(description = "Hạn mức còn lại có thể mua thêm (VNĐ)")
        BigDecimal availableCredit,

        @Schema(description = "Giá trị đơn hàng dự kiến đặt (VNĐ)")
        BigDecimal orderAmount,

        @Schema(description = "Công nợ dự kiến sau khi duyệt đơn này (VNĐ)")
        BigDecimal projectedDebt,

        @Schema(description = "Có bị chủ động khóa nợ hay không")
        boolean isDebtLocked,

        @Schema(description = "Đơn hàng này có làm vượt hạn mức nợ hay không")
        boolean isOverLimit,

        @Schema(description = "Cho phép tiếp tục tạo/duyệt đơn bán hay không")
        boolean canProceed,

        @Schema(description = "Mã trạng thái: ALLOWED, WARNING_NEAR_LIMIT, BLOCKED_OVER_LIMIT, BLOCKED_DEBT_LOCKED")
        String status,

        @Schema(description = "Thông báo chi tiết giải thích cho nhân viên bán hàng")
        String message
) {
}
