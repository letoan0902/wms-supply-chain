package vn.wms.auth.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.wms.auth.subscription.entity.BillingCycle;

/**
 * Body của {@code POST /api/auth/payments}. Không có trường số tiền: số tiền luôn lấy từ bảng {@code plans},
 * trường {@code amount} client tự gửi kèm bị bỏ qua.
 *
 * @param plan  gói cần thanh toán, hiện chỉ nhận {@code PRO}
 * @param cycle {@code MONTHLY} (30 ngày) hoặc {@code YEARLY} (365 ngày)
 */
public record CreatePaymentRequest(
        @NotBlank(message = "Gói dịch vụ không được để trống") String plan,
        @NotNull(message = "Chu kỳ thanh toán không được để trống") BillingCycle cycle) {
}
