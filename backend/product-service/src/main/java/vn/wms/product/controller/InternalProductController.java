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

/**
 * Endpoint nội bộ cho service khác đọc danh mục hàng hóa của một doanh nghiệp mà không cần người dùng, ví dụ tác vụ
 * quét cảnh báo tồn kho hằng đêm của inventory-service lấy mức tồn an toàn và điểm đặt hàng lại (WMS-15.1).
 *
 * <p>Đường dẫn cố ý nằm ngoài {@code /api/products/**} để api-gateway không có route tới. Doanh nghiệp lấy từ header
 * {@code X-Company-Id} do OpenFeign chuyển tiếp; thiếu doanh nghiệp thì {@code TenantFilter} từ chối. Trên máy chủ,
 * {@code GET /api/products} từ chối request không có người dùng (docs/SECURITY.md mục 4) nên tác vụ nền phải gọi
 * đường dẫn này.
 */
@RestController
@RequestMapping("/internal/products")
@Tag(name = "Products Internal", description = "Endpoint nội bộ đọc danh mục hàng hóa cho service khác")
public class InternalProductController {

    private final ProductService productService;

    public InternalProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Danh sách hàng hóa của doanh nghiệp trong header, có phân trang, cho lời gọi nội bộ")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return ApiResponse.ok(productService.list(null, null, null, pageable));
    }
}
