package vn.wms.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.product.dto.CreateUomRequest;
import vn.wms.product.dto.UomResponse;
import vn.wms.product.service.UomService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/uoms")
@Tag(name = "UOMs", description = "Quản lý danh mục Đơn vị tính cơ sở & đóng gói (Cái, Chai, Thùng, Lốc, Pallet...)")
public class UomController {

    private final UomService uomService;

    public UomController(UomService uomService) {
        this.uomService = uomService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các đơn vị tính trong hệ thống")
    public ApiResponse<List<UomResponse>> listAll() {
        return ApiResponse.ok(uomService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết đơn vị tính theo ID")
    public ApiResponse<UomResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(uomService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo mới đơn vị tính")
    public ResponseEntity<ApiResponse<UomResponse>> create(@Valid @RequestBody CreateUomRequest request) {
        UomResponse created = uomService.create(request);
        return ResponseEntity.created(URI.create("/api/uoms/" + created.id()))
                .body(ApiResponse.ok(created));
    }
}
