package vn.wms.auth.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.tenant.TenantContext;

import java.util.List;

/**
 * Endpoint nội bộ cho service khác gọi qua Eureka (WMS-15.2). Đường dẫn cố ý nằm ngoài {@code /api/auth/**} và
 * {@code /api/users/**} nên api-gateway không có route tới nó. Không cần JWT; doanh nghiệp bắt buộc lấy từ header
 * {@code X-Company-Id} do OpenFeign của service gọi chuyển tiếp (thiếu thì 403 {@code COMPANY_REQUIRED}), và kết quả
 * chỉ gồm tài khoản của đúng doanh nghiệp đó.
 */
@RestController
@RequestMapping("/internal/auth/users")
@Tag(name = "Internal Users", description = "Endpoint nội bộ tra người nhận thông báo theo doanh nghiệp")
public class InternalUserController {

    private final AlertRecipientService alertRecipientService;

    public InternalUserController(AlertRecipientService alertRecipientService) {
        this.alertRecipientService = alertRecipientService;
    }

    @GetMapping("/alert-recipients")
    @Operation(summary = "Người nhận email cảnh báo tồn kho: quản lý của kho và admin của doanh nghiệp, đang hoạt động, có email gửi được")
    public ApiResponse<List<AlertRecipientResponse>> alertRecipients(
            @RequestParam(required = false) Long warehouseId) {
        Long companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(alertRecipientService.findRecipients(companyId, warehouseId));
    }
}
