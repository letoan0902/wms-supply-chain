package vn.wms.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.notification.dto.dashboard.KpiSummaryResponse;
import vn.wms.notification.service.DashboardKpiService;

@RestController
@RequestMapping("/api/notifications/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard KPI", description = "API Tổng hợp Dữ liệu 8 Thẻ KPI điều hành thời gian thực (WMS-16.1 - UC-10)")
public class DashboardKpiController {

    private final DashboardKpiService dashboardKpiService;

    @GetMapping("/kpi-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Tổng hợp dữ liệu 8 thẻ KPI nhanh trên Executive Dashboard (WMS-16.1)")
    public ApiResponse<KpiSummaryResponse> getKpiSummary(
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "dateRange", defaultValue = "7_DAYS") String dateRange
    ) {
        return ApiResponse.ok(dashboardKpiService.getKpiSummary(warehouseId, dateRange));
    }
}
