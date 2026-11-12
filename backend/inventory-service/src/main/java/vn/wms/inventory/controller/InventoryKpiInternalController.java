package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.internal.InventoryKpiMetricsResponse;
import vn.wms.inventory.service.InventoryKpiInternalService;

/**
 * Endpoint nội bộ chỉ cho notification-service gọi qua Eureka. Đường dẫn cố ý nằm ngoài /api/inventory/** để
 * api-gateway không có route tới nó: endpoint không tự kiểm tra phạm vi kho của người gọi (việc đó do
 * DashboardKpiService làm), nên không được để người dùng gọi thẳng từ Internet.
 */
@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory Internal KPI", description = "Endpoint nội bộ tổng hợp dữ liệu kho bãi và tồn kho cho Dashboard KPI")
public class InventoryKpiInternalController {

    private final InventoryKpiInternalService inventoryKpiInternalService;

    @GetMapping("/kpi-metrics")
    @Operation(summary = "Lấy các chỉ số tồn kho & mặt bằng ô/kệ phục vụ tổng hợp Dashboard KPI (WMS-16.1)")
    public ApiResponse<InventoryKpiMetricsResponse> getKpiMetrics(
            @RequestParam(value = "warehouseId", required = false) Long warehouseId
    ) {
        return ApiResponse.ok(inventoryKpiInternalService.getMetrics(warehouseId));
    }
}
