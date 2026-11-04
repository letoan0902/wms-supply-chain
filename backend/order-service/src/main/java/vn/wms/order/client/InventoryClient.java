package vn.wms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.wms.common.api.ApiResponse;

/**
 * Feign client gọi inventory-service qua Eureka (tên service: inventory-service), có cân bằng tải.
 * Dùng cho các tra cứu đồng bộ cần kết quả ngay khi lập đơn (kho có tồn tại và đang hoạt động không);
 * các thay đổi tồn kho thì đi bằng sự kiện RabbitMQ ({@code order.created}) chứ không gọi trực tiếp.
 * Nên gọi qua {@link InventoryGateway} để lỗi mạng và 404 được quy về ApiException thống nhất.
 */
@FeignClient(name = "inventory-service", contextId = "inventoryClient")
public interface InventoryClient {

    /** Thông tin một kho; inventory-service trả 404 (ApiResponse lỗi) nếu id không tồn tại. */
    @GetMapping("/api/inventory/warehouses/{id}")
    ApiResponse<WarehouseSummary> getWarehouse(@PathVariable("id") Long id);

    /** Thông tin tồn khả dụng ATP của một sản phẩm (ATP = OnHand - Reserved) (WMS-11.1). */
    @GetMapping("/api/inventory/stock/atp")
    ApiResponse<AtpResponse> getAtp(
            @org.springframework.web.bind.annotation.RequestParam("productId") Long productId,
            @org.springframework.web.bind.annotation.RequestParam(value = "warehouseId", required = false) Long warehouseId
    );
}
