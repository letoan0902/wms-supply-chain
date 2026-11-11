package vn.wms.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.order.dto.internal.OrderKpiMetricsResponse;
import vn.wms.order.service.OrderKpiInternalService;

@RestController
@RequestMapping("/api/orders/internal")
@RequiredArgsConstructor
@Tag(name = "Order Internal KPI", description = "Endpoint nội bộ tổng hợp dữ liệu đơn hàng và tài chính cho Dashboard KPI")
public class OrderKpiInternalController {

    private final OrderKpiInternalService orderKpiInternalService;

    @GetMapping("/kpi-metrics")
    @Operation(summary = "Lấy các chỉ số đơn hàng & công nợ phục vụ tổng hợp Dashboard KPI (WMS-16.1)")
    public ApiResponse<OrderKpiMetricsResponse> getKpiMetrics(
            @RequestParam(value = "warehouseId", required = false) Long warehouseId
    ) {
        return ApiResponse.ok(orderKpiInternalService.getMetrics(warehouseId));
    }
}
