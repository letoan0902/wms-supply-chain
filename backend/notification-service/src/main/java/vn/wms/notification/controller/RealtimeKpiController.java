package vn.wms.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;
import vn.wms.notification.realtime.KpiSnapshot;
import vn.wms.notification.realtime.RealtimeKpiService;

@RestController
@RequestMapping("/api/notifications/realtime")
@Tag(name = "Realtime", description = "Số liệu hiện tại của hub WebSocket /ws-wms")
public class RealtimeKpiController {

    private static final Logger log = LoggerFactory.getLogger(RealtimeKpiController.class);

    private final RealtimeKpiService kpiService;

    public RealtimeKpiController(RealtimeKpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping("/kpi")
    @Operation(summary = "Bộ đếm KPI trong ngày (giờ Việt Nam) của doanh nghiệp đang đăng nhập; các thay đổi sau đó "
            + "được đẩy qua /topic/company/{companyId}/kpi")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<KpiSnapshot> kpi() {
        // Doanh nghiệp lấy từ header X-Company-Id do gateway gắn theo JWT, không nhận từ tham số của client
        Long companyId = TenantContext.requireCompanyId();
        try {
            return ApiResponse.ok(kpiService.currentSnapshot(companyId));
        } catch (DataAccessException ex) {
            log.warn("Không đọc được bộ đếm KPI từ Redis: {}", ex.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Chưa đọc được chỉ số KPI, vui lòng thử lại sau", ex);
        }
    }
}
