package vn.wms.auth.subscription.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.auth.subscription.entity.BillingCycle;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenewMockRequest {
    @NotNull(message = "Gói dịch vụ không được để trống")
    private String plan; // 'PRO'

    @NotNull(message = "Chu kỳ thanh toán không được để trống")
    private BillingCycle cycle; // MONTHLY, YEARLY
}
