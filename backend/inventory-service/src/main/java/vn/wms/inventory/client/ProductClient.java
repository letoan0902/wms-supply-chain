package vn.wms.inventory.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;

/**
 * Feign client gọi product-service qua Eureka (tên service: product-service), có cân bằng tải.
 * Header doanh nghiệp và người dùng được {@code TenantFeignRequestInterceptor} của module common chuyển tiếp.
 *
 * <p>TODO (Sprint 2): bổ sung tra cứu sản phẩm theo SKU/barcode khi product-service có endpoint tương ứng,
 * dùng để kiểm tra mã hàng hợp lệ trước khi nhập kho.
 */
@FeignClient(name = "product-service", contextId = "productClient")
public interface ProductClient {

    @GetMapping("/api/products")
    ApiResponse<PageResponse<ProductSummary>> listProducts(@RequestParam("page") int page,
                                                           @RequestParam("size") int size);

    /** Tìm sản phẩm theo từ khóa (SKU, tên) và danh mục, dùng cho bộ lọc báo cáo kho (WMS-17.1). */
    @GetMapping("/api/products")
    ApiResponse<PageResponse<ProductInfo>> searchProducts(@RequestParam(value = "keyword", required = false) String keyword,
                                                          @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                          @RequestParam("page") int page,
                                                          @RequestParam("size") int size);

    /** Chi tiết một sản phẩm, dùng để bổ sung mã và tên cho dòng báo cáo chưa có ảnh chụp trong sổ kho (WMS-17.1). */
    @GetMapping("/api/products/{id}")
    ApiResponse<ProductInfo> getProduct(@PathVariable("id") Long id);
}
