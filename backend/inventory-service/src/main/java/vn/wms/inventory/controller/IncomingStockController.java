package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.IncomingStockResponse;
import vn.wms.inventory.dto.ProductIncomingSummaryResponse;
import vn.wms.inventory.service.IncomingStockService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/incoming-stocks")
@Tag(name = "Incoming Stock Management", description = "Quản lý chỉ tiêu hàng đang trên đường về kho (Incoming Stock - WMS-09.2)")
public class IncomingStockController {

    private final IncomingStockService incomingStockService;

    public IncomingStockController(IncomingStockService incomingStockService) {
        this.incomingStockService = incomingStockService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách hàng đang trên đường về kho theo kho và trạng thái")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT', 'INTEGRATION')")
    public ResponseEntity<ApiResponse<List<IncomingStockResponse>>> getIncomingStocks(
            @RequestParam Long warehouseId,
            @RequestParam(required = false, defaultValue = "INCOMING") String status) {
        List<IncomingStockResponse> data = incomingStockService.getIncomingStocksByWarehouse(warehouseId, status);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/by-product/{productId}")
    @Operation(summary = "Tra cứu tổng số lượng hàng đang về của một SKU tại kho cụ thể")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT', 'INTEGRATION')")
    public ResponseEntity<ApiResponse<ProductIncomingSummaryResponse>> getProductIncomingSummary(
            @PathVariable Long productId,
            @RequestParam Long warehouseId) {
        ProductIncomingSummaryResponse data = incomingStockService.getProductIncomingSummary(warehouseId, productId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/warehouses/{warehouseId}/summary")
    @Operation(summary = "Tổng hợp tổng lượng hàng đang trên đường về của toàn kho")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT', 'INTEGRATION')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWarehouseIncomingSummary(
            @PathVariable Long warehouseId) {
        BigDecimal totalIncoming = incomingStockService.getTotalIncomingQuantityByWarehouse(warehouseId);
        List<IncomingStockResponse> list = incomingStockService.getIncomingStocksByWarehouse(warehouseId, "INCOMING");
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "warehouseId", warehouseId,
                "totalIncomingQuantity", totalIncoming,
                "incomingOrdersCount", list.stream().map(IncomingStockResponse::orderId).distinct().count(),
                "incomingLinesCount", list.size()
        )));
    }
}

