package vn.wms.inventory.alert;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.wms.common.api.ApiResponse;

import java.util.List;

/**
 * Endpoint nội bộ của auth-service (không đi qua gateway): quản lý của kho và admin của doanh nghiệp, đang hoạt động,
 * có email gửi được. Doanh nghiệp đi theo header {@code X-Company-Id} do {@code TenantFeignRequestInterceptor} gắn từ
 * TenantContext, nên chỉ trả người của doanh nghiệp sở hữu tồn kho.
 */
@FeignClient(name = "auth-service", contextId = "alertRecipientClient")
public interface AlertRecipientClient {

    @GetMapping("/internal/auth/users/alert-recipients")
    ApiResponse<List<AlertRecipient>> alertRecipients(@RequestParam("warehouseId") Long warehouseId);
}
