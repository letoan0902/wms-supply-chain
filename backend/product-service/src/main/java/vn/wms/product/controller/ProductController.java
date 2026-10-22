package vn.wms.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.product.dto.ProductResponse;
import vn.wms.product.service.ProductService;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "SKU, danh mục, đơn vị tính, barcode")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm có phân trang (tham số page, size, sort)")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return ApiResponse.ok(productService.list(pageable));
    }
}
