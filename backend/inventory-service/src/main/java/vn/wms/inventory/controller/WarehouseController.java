package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.WarehouseResponse;
import vn.wms.inventory.service.WarehouseService;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Kho, vị trí lưu trữ, tồn kho, điều chuyển, kiểm kê")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping("/warehouses")
    @Operation(summary = "Danh sách kho")
    public ApiResponse<List<WarehouseResponse>> listWarehouses() {
        return ApiResponse.ok(warehouseService.listWarehouses());
    }
}
