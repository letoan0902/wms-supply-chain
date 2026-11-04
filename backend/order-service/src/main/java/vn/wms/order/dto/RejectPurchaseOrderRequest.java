package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload từ chối phê duyệt đơn mua hàng (PO).
 */
public record RejectPurchaseOrderRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 500, message = "Lý do từ chối tối đa 500 ký tự")
        @Schema(description = "Lý do từ chối phê duyệt đơn PO", example = "Giá nhập cao hơn thỏa thuận hợp đồng, đề nghị thương lượng lại với NCC")
        String reason
) {
}
