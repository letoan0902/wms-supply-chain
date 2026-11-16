package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.service.StockReadService;

/**
 * Endpoint nội bộ cho order-service kiểm tra tồn khả dụng khi lập, xác nhận đơn bán (WMS-20.4). Khác
 * {@code GET /api/inventory/stock/atp}, endpoint này không lọc theo phạm vi kho của người được chuyển tiếp: tài khoản
 * SALES thường không được gán kho mà vẫn phải xác nhận được đơn, và vai trò đã được order-service kiểm tra trước khi
 * gọi. Đường dẫn nằm dưới {@code /internal/} nên api-gateway không định tuyến tới và không cần {@code @PreAuthorize}
 * (docs/SECURITY.md mục 4); truy vấn vẫn lọc theo doanh nghiệp trong header {@code X-Company-Id}.
 */
@RestController
@RequestMapping("/internal/inventory/stock")
@Tag(name = "Inventory Internal Stock", description = "Endpoint nội bộ kiểm tra và thay đổi tồn kho đồng bộ cho order-service")
public class StockAtpInternalController {

    private final StockReadService stockReadService;

    public StockAtpInternalController(StockReadService stockReadService) {
        this.stockReadService = stockReadService;
    }

    @GetMapping("/atp")
    @Operation(summary = "Tồn khả dụng ATP của một sản phẩm theo kho (bỏ trống là mọi kho của doanh nghiệp), không lọc theo phạm vi kho của người gọi")
    public ApiResponse<StockAtpResponse> getAtp(
            @RequestParam Long productId,
            @RequestParam(required = false) Long warehouseId
    ) {
        return ApiResponse.ok(stockReadService.getAtp(productId, warehouseId));
    }
}
