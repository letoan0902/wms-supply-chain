package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.BatchAtpRequest;
import vn.wms.inventory.dto.BatchAtpResponse;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.service.StockService;

/**
 * REST API Tra cứu tồn kho & Tính toán tồn khả dụng (ATP - Available To Promise) (FR15 - WMS-11.1).
 */
@RestController
@RequestMapping("/api/inventory/stock")
@Tag(name = "Stock & ATP Management", description = "Tra cứu tồn kho thực tế và tính toán tồn khả dụng ATP (FR15 - WMS-11.1)")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/atp")
    @Operation(summary = "Kiểm tra tồn khả dụng ATP của một sản phẩm (ATP = OnHand - Reserved)")
    public ApiResponse<StockAtpResponse> getAtp(
            @RequestParam Long productId,
            @RequestParam(required = false) Long warehouseId
    ) {
        return ApiResponse.ok(stockService.getAtp(productId, warehouseId));
    }

    @PostMapping("/atp/batch")
    @Operation(summary = "Kiểm tra tồn khả dụng ATP cho danh sách nhiều sản phẩm cùng lúc (Batch ATP)")
    public ApiResponse<BatchAtpResponse> getBatchAtp(@Valid @RequestBody BatchAtpRequest request) {
        return ApiResponse.ok(stockService.getBatchAtp(request.productIds(), request.warehouseId()));
    }
}
