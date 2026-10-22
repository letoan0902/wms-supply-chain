package vn.wms.inventory.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;

/**
 * Feign client gọi product-service qua Eureka (tên service: product-service), có cân bằng tải.
 *
 * <p>TODO (Sprint 2): bổ sung tra cứu sản phẩm theo SKU/barcode khi product-service có endpoint tương ứng,
 * dùng để kiểm tra mã hàng hợp lệ trước khi nhập kho. Hiện chưa có nơi nào gọi client này.
 */
@FeignClient(name = "product-service", contextId = "productClient")
public interface ProductClient {

    @GetMapping("/api/products")
    ApiResponse<PageResponse<ProductSummary>> listProducts(@RequestParam("page") int page,
                                                           @RequestParam("size") int size);
}
