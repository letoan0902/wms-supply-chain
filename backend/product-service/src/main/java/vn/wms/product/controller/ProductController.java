package vn.wms.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import vn.wms.common.api.PageResponse;
import vn.wms.product.dto.CreateProductRequest;
import vn.wms.product.dto.ProductResponse;
import vn.wms.product.dto.UpdateProductRequest;
import vn.wms.product.service.ProductService;

import java.net.URI;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Quản lý danh mục hàng hóa, mã SKU, barcode, giá cả và định mức tồn")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm có phân trang, tìm kiếm theo từ khóa và lọc")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @Parameter(description = "Từ khóa tìm kiếm theo SKU, tên hoặc barcode")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo ID danh mục")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Lọc theo trạng thái hoạt động (true/false)")
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return ApiResponse.ok(productService.list(keyword, categoryId, active, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem thông tin chi tiết sản phẩm theo ID")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Xem thông tin chi tiết sản phẩm theo mã SKU")
    public ApiResponse<ProductResponse> getBySku(@PathVariable String sku) {
        return ApiResponse.ok(productService.getBySku(sku));
    }

    @PostMapping
    @Operation(summary = "Tạo mới sản phẩm (SKU)")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin sản phẩm theo ID")
    public ApiResponse<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa sản phẩm theo ID")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok();
    }
}
