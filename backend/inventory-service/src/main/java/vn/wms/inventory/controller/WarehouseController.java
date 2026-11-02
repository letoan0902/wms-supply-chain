package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.CreateWarehouseRequest;
import vn.wms.inventory.dto.UpdateWarehouseRequest;
import vn.wms.inventory.dto.WarehouseResponse;
import vn.wms.inventory.dto.WarehouseSummaryResponse;
import vn.wms.inventory.service.WarehouseService;

import java.util.List;
import java.util.Map;

/**
 * REST API Quản lý danh mục đa kho & giám sát sức chứa kho (WMS-05).
 */
@RestController
@RequestMapping("/api/inventory/warehouses")
@Tag(name = "Warehouse Management", description = "Quản lý danh mục đa kho, sức chứa và thẻ thống kê (WMS-05)")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    @Operation(summary = "Danh sách kho hàng kèm thống kê sức chứa và vị trí")
    public ApiResponse<List<WarehouseResponse>> listWarehouses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active
    ) {
        return ApiResponse.ok(warehouseService.listWarehouses(keyword, active));
    }

    @GetMapping("/summary")
    @Operation(summary = "Thẻ tóm tắt nhanh tình trạng toàn bộ hệ thống kho (WMS-05.2)")
    public ApiResponse<WarehouseSummaryResponse> getSummaryMetrics() {
        return ApiResponse.ok(warehouseService.getSummaryMetrics());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem thông tin chi tiết một kho hàng")
    public ApiResponse<WarehouseResponse> getWarehouseById(@PathVariable Long id) {
        return ApiResponse.ok(warehouseService.getWarehouseById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo mới một kho hàng (WMS-05.1)")
    public ApiResponse<WarehouseResponse> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return ApiResponse.ok(warehouseService.createWarehouse(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin kho hàng (WMS-05.1)")
    public ApiResponse<WarehouseResponse> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request
    ) {
        return ApiResponse.ok(warehouseService.updateWarehouse(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Khóa hoặc kích hoạt lại kho hàng (WMS-05.1)")
    public ApiResponse<WarehouseResponse> toggleWarehouseStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean active = body.getOrDefault("active", true);
        return ApiResponse.ok(warehouseService.toggleWarehouseStatus(id, active));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa kho hàng nếu chưa có hàng hóa hoặc vị trí (WMS-05.1)")
    public void deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
    }
}
