package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.BinBarcodeResponse;
import vn.wms.inventory.dto.BinLocationResponse;
import vn.wms.inventory.dto.BulkCreateBinLocationsRequest;
import vn.wms.inventory.dto.CreateBinLocationRequest;
import vn.wms.inventory.dto.UpdateBinLocationRequest;
import vn.wms.inventory.service.BinLocationService;

import java.util.List;

/**
 * REST API Quản lý vị trí ô/kệ 4 cấp & in mã vạch vị trí (WMS-06).
 */
@RestController
@RequestMapping("/api/inventory/locations")
@Tag(name = "Bin Location Management", description = "Quản lý vị trí lưu trữ Zone/Rack/Bin và tem mã vạch (WMS-06)")
public class BinLocationController {

    private final BinLocationService binLocationService;

    public BinLocationController(BinLocationService binLocationService) {
        this.binLocationService = binLocationService;
    }

    @GetMapping
    @Operation(summary = "Tra cứu danh sách vị trí ô/kệ theo bộ lọc (kho, khu, kệ, trạng thái)")
    public ApiResponse<List<BinLocationResponse>> listLocations(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String rack,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean active
    ) {
        return ApiResponse.ok(binLocationService.listLocations(warehouseId, zone, rack, status, active));
    }

    @GetMapping("/zones")
    @Operation(summary = "Lấy danh sách các khu vực (Zone) trong kho")
    public ApiResponse<List<String>> listZones(@RequestParam Long warehouseId) {
        return ApiResponse.ok(binLocationService.listZonesByWarehouse(warehouseId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem thông tin chi tiết một vị trí ô/kệ")
    public ApiResponse<BinLocationResponse> getLocationById(@PathVariable Long id) {
        return ApiResponse.ok(binLocationService.getLocationById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo mới một vị trí ô/kệ đơn lẻ (WMS-06.1)")
    public ApiResponse<BinLocationResponse> createLocation(@Valid @RequestBody CreateBinLocationRequest request) {
        return ApiResponse.ok(binLocationService.createLocation(request));
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sinh hàng loạt vị trí ô/kệ tự động theo dải kệ và ô (WMS-06.1)")
    public ApiResponse<List<BinLocationResponse>> bulkCreateLocations(
            @Valid @RequestBody BulkCreateBinLocationsRequest request
    ) {
        return ApiResponse.ok(binLocationService.bulkCreateLocations(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin vị trí ô/kệ (WMS-06.1)")
    public ApiResponse<BinLocationResponse> updateLocation(
            @PathVariable Long id,
            @RequestBody UpdateBinLocationRequest request
    ) {
        return ApiResponse.ok(binLocationService.updateLocation(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa vị trí ô/kệ nếu không có hàng hóa bên trong (WMS-06.1)")
    public void deleteLocation(@PathVariable Long id) {
        binLocationService.deleteLocation(id);
    }

    @GetMapping("/{id}/barcode/preview")
    @Operation(summary = "Xem trước thông tin tem vị trí kèm mã vạch Base64 (WMS-06.2)")
    public ApiResponse<BinBarcodeResponse> getBarcodePreview(@PathVariable Long id) {
        return ApiResponse.ok(binLocationService.generateBarcode(id));
    }

    @GetMapping(value = "/{id}/barcode", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Tải trực tiếp ảnh mã vạch Code 128 dạng PNG (WMS-06.2)")
    public ResponseEntity<byte[]> getBarcodePng(@PathVariable Long id) {
        byte[] imageBytes = binLocationService.generateBarcodePng(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"bin-" + id + "-barcode.png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }
}

