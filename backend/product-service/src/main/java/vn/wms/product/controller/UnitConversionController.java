package vn.wms.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.product.dto.BreakdownResponse;
import vn.wms.product.dto.CalculateConversionRequest;
import vn.wms.product.dto.CalculateConversionResponse;
import vn.wms.product.dto.ConvertToBaseRequest;
import vn.wms.product.dto.ConvertToBaseResponse;
import vn.wms.product.dto.CreateUnitConversionRequest;
import vn.wms.product.dto.UnitConversionResponse;
import vn.wms.product.dto.UpdateUnitConversionRequest;
import vn.wms.product.service.UnitConversionService;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/conversions")
@Tag(name = "Unit Conversions", description = "Quy đổi đơn vị tính đa cấp (UOM Conversion) theo sản phẩm (FR06)")
public class UnitConversionController {

    private final UnitConversionService unitConversionService;

    public UnitConversionController(UnitConversionService unitConversionService) {
        this.unitConversionService = unitConversionService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách các tỷ lệ quy đổi của một sản phẩm (kèm tỷ lệ quy đổi tích lũy về đơn vị cơ sở)")
    public ApiResponse<List<UnitConversionResponse>> getConversions(
            @Parameter(description = "ID sản phẩm") @PathVariable Long productId) {
        return ApiResponse.ok(unitConversionService.getConversions(productId));
    }

    @PostMapping
    @Operation(summary = "Thêm mới cấu hình quy đổi đơn vị tính cho sản phẩm")
    public ResponseEntity<ApiResponse<UnitConversionResponse>> createConversion(
            @Parameter(description = "ID sản phẩm") @PathVariable Long productId,
            @Valid @RequestBody CreateUnitConversionRequest request) {
        UnitConversionResponse created = unitConversionService.createConversion(productId, request);
        return ResponseEntity.created(URI.create("/api/products/" + productId + "/conversions/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật cấu hình quy đổi đơn vị tính")
    public ApiResponse<UnitConversionResponse> updateConversion(
            @Parameter(description = "ID sản phẩm") @PathVariable Long productId,
            @Parameter(description = "ID cấu hình quy đổi") @PathVariable Long id,
            @Valid @RequestBody UpdateUnitConversionRequest request) {
        return ApiResponse.ok(unitConversionService.updateConversion(productId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa cấu hình quy đổi đơn vị tính")
    public ApiResponse<Void> deleteConversion(
            @Parameter(description = "ID sản phẩm") @PathVariable Long productId,
            @Parameter(description = "ID cấu hình quy đổi") @PathVariable Long id) {
        unitConversionService.deleteConversion(productId, id);
        return ApiResponse.ok();
    }

    @PostMapping("/convert-to-base")
    @Operation(summary = "Quy đổi số lượng từ đơn vị đóng gói về đơn vị cơ sở khi tính toán tồn kho (FR06)")
    public ApiResponse<ConvertToBaseResponse> convertToBase(
            @Parameter(description = "ID sản phẩm") @PathVariable Long productId,
            @Valid @RequestBody ConvertToBaseRequest request) {
        return ApiResponse.ok(unitConversionService.convertToBase(productId, request));
    }

    @PostMapping("/calculate")
    @Operation(summary = "Quy đổi số lượng giữa 2 đơn vị tính bất kỳ trên cây quy đổi đa cấp")
    public ApiResponse<CalculateConversionResponse> calculate(
            @Parameter(description = "ID sản phẩm") @PathVariable Long productId,
            @Valid @RequestBody CalculateConversionRequest request) {
        return ApiResponse.ok(unitConversionService.calculate(productId, request));
    }

    @GetMapping("/breakdown")
    @Operation(summary = "Phân rã số lượng tồn cơ sở sang các cấp đóng gói trực quan (VD: 50 Chai -> 2 Thùng, 2 Chai)")
    public ApiResponse<BreakdownResponse> breakdown(
            @Parameter(description = "ID sản phẩm") @PathVariable Long productId,
            @Parameter(description = "Tổng số lượng theo đơn vị cơ sở") @RequestParam BigDecimal baseQuantity) {
        return ApiResponse.ok(unitConversionService.breakdown(productId, baseQuantity));
    }
}
