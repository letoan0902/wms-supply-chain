package vn.wms.order.controller;

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
import vn.wms.order.dto.PurchaseOrderResponse;
import vn.wms.order.service.PurchaseOrderService;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Nhà cung cấp, khách hàng, đơn mua (PO/GRN), đơn bán (SO/GDN)")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping("/purchase")
    @Operation(summary = "Danh sách đơn mua hàng (PO) có phân trang (tham số page, size, sort)")
    public ApiResponse<PageResponse<PurchaseOrderResponse>> listPurchaseOrders(
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return ApiResponse.ok(purchaseOrderService.list(pageable));
    }
}
