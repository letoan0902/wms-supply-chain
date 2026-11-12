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

/**
 * Endpoint nội bộ chỉ cho notification-service gọi qua Eureka. Đường dẫn cố ý nằm ngoài /api/orders/** để
 * api-gateway không có route tới nó: endpoint không tự kiểm tra phạm vi kho của người gọi.
 *
 * <p>Số liệu chỉ của doanh nghiệp gọi (WMS-22.4b): notification-service gọi qua OpenFeign nên header
 * {@code X-Company-Id} được chuyển tiếp, và mọi truy vấn của {@link OrderKpiInternalService} là JPQL trên entity
 * kế thừa {@code TenantEntity} nên Hibernate tự thêm điều kiện {@code company_id}. Thiếu doanh nghiệp thì
 * {@code TenantFilter} trả 403 {@code COMPANY_REQUIRED}.
 */
@RestController
@RequestMapping("/internal/orders")
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
