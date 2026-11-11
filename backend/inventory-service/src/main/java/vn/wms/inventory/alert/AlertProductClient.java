package vn.wms.inventory.alert;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;

/**
 * Đọc danh mục sản phẩm (kèm mức tồn an toàn, điểm đặt hàng lại) của doanh nghiệp hiện tại từ product-service.
 * {@code TenantFeignRequestInterceptor} của module common gắn {@code X-Company-Id} từ TenantContext, nên tác vụ nền
 * phải gọi bên trong {@code TenantContext.runAs}.
 */
@FeignClient(name = "product-service", contextId = "alertProductClient")
public interface AlertProductClient {

    @GetMapping("/api/products")
    ApiResponse<PageResponse<AlertProductInfo>> listProducts(@RequestParam("page") int page,
                                                             @RequestParam("size") int size,
                                                             @RequestParam("sort") String sort);
}
