package vn.wms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import vn.wms.common.api.ApiResponse;
import vn.wms.order.dto.GeneratePickingListRequest;
import vn.wms.order.dto.PickingListResponse;

import java.util.List;

/**
 * Feign client gọi inventory-service qua Eureka (tên service: inventory-service), có cân bằng tải.
 * Dùng cho các tra cứu đồng bộ cần kết quả ngay khi lập đơn (kho có tồn tại và đang hoạt động không) và, từ
 * WMS-20.4, cho các bước tồn kho phải thành công thì chứng từ mới được lưu (kiểm tra vị trí nhập, giữ chỗ khi xác
 * nhận đơn bán, trừ tồn khi hoàn tất phiếu xuất) qua các endpoint nội bộ {@code /internal/inventory/**}.
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
            @RequestParam("productId") Long productId,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId
    );

    /** Giải phóng giữ hàng ảo khi đơn bán bị hủy (WMS-11.2). */
    @PostMapping("/api/inventory/stock/reservations/release")
    ApiResponse<Void> releaseReservations(@RequestParam("orderId") Long orderId);

    /** Tự động sinh Picking List gợi ý vị trí ô kệ theo lộ trình ngắn nhất và ưu tiên FEFO/FIFO (WMS-12.1). */
    @PostMapping("/api/inventory/stock/picking-list")
    ApiResponse<PickingListResponse> generatePickingList(@RequestBody GeneratePickingListRequest request);

    /**
     * Endpoint nội bộ (WMS-20.4): các dòng phiếu nhập sẽ cộng tồn vào vị trí đang đóng băng kiểm kê; rỗng là nhập
     * được. Gọi trước khi lưu phiếu nhập.
     */
    @PostMapping("/internal/inventory/receipts/location-check")
    ApiResponse<List<FrozenLocationConflict>> checkReceiptLocations(@RequestBody ReceiptLocationCheckRequest request);

    /**
     * Endpoint nội bộ (WMS-20.4): giữ chỗ toàn bộ đơn bán khi xác nhận, tất cả hoặc không, idempotent theo đơn;
     * thiếu hàng trả 409 kèm danh sách thiếu trong {@code data}.
     */
    @PostMapping("/internal/inventory/reservations")
    ApiResponse<ReserveOrderResponse> reserveOrder(@RequestBody ReserveOrderRequest request);
}
