package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.BatchAtpRequest;
import vn.wms.inventory.dto.BatchAtpResponse;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.dto.DeductStockResponse;
import vn.wms.inventory.dto.GeneratePickingListRequest;
import vn.wms.inventory.dto.PickingListResponse;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.service.PickingService;
import vn.wms.inventory.service.StockDeductionService;
import vn.wms.inventory.service.StockReservationService;
import vn.wms.inventory.service.StockReadService;

/**
 * REST API Quản lý Tồn kho, Tính toán ATP, Sinh Picking List & Xác nhận Trừ tồn kho với Khóa bi quan (WMS-11.1, WMS-11.2, WMS-12.1, WMS-12.2).
 */
@RestController
@RequestMapping("/api/inventory/stock")
@Tag(name = "Stock & Outbound Management", description = "Quản lý tồn kho, tính toán ATP, sinh Picking List FEFO/FIFO và trừ tồn kho xuất hàng với Khóa bi quan")
public class StockController {

    /** API chỉ đọc ATP đi qua bộ nhớ đệm Redis (WMS-19.1); trừ tồn và giữ chỗ vẫn đọc cơ sở dữ liệu dưới khóa. */
    private final StockReadService stockReadService;
    private final StockReservationService stockReservationService;
    private final StockDeductionService stockDeductionService;
    private final PickingService pickingService;

    public StockController(StockReadService stockReadService,
                           StockReservationService stockReservationService,
                           StockDeductionService stockDeductionService,
                           PickingService pickingService) {
        this.stockReadService = stockReadService;
        this.stockReservationService = stockReservationService;
        this.stockDeductionService = stockDeductionService;
        this.pickingService = pickingService;
    }

    @PostMapping("/picking-list")
    @Operation(summary = "Tự động sinh danh sách lấy hàng (Picking List) gợi ý vị trí ô kệ theo lộ trình ngắn nhất (Zone -> Rack -> Bin) và ưu tiên FEFO/FIFO (FR12, FR17 - WMS-12.1)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES')")
    public ApiResponse<PickingListResponse> generatePickingList(@Valid @RequestBody GeneratePickingListRequest request) {
        return ApiResponse.ok(pickingService.generatePickingList(request));
    }

    @GetMapping("/atp")
    @Operation(summary = "Kiểm tra tồn khả dụng ATP của một sản phẩm (ATP = OnHand - Reserved)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<StockAtpResponse> getAtp(
            @RequestParam Long productId,
            @RequestParam(required = false) Long warehouseId
    ) {
        return ApiResponse.ok(stockReadService.getAtp(productId, warehouseId));
    }

    @PostMapping("/atp/batch")
    @Operation(summary = "Kiểm tra tồn khả dụng ATP cho danh sách nhiều sản phẩm cùng lúc (Batch ATP)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<BatchAtpResponse> getBatchAtp(@Valid @RequestBody BatchAtpRequest request) {
        return ApiResponse.ok(stockReadService.getBatchAtp(request.productIds(), request.warehouseId()));
    }

    @PostMapping("/reservations/release")
    @Operation(summary = "Giải phóng số lượng giữ hàng ảo (soft reservation) khi đơn bán bị hủy (WMS-11.2)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'SALES')")
    public ApiResponse<Void> releaseReservations(@RequestParam Long orderId) {
        stockReservationService.releaseReservationsByOrderId(orderId);
        return ApiResponse.ok();
    }

    @PostMapping("/deduct")
    @Operation(summary = "Xác nhận trừ tồn kho với Khóa bi quan (PESSIMISTIC_WRITE) chống âm kho tuyệt đối (WMS-12.2 - FR17, NFR03)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    public ApiResponse<DeductStockResponse> deductStock(
            @Valid @RequestBody DeductStockRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        String operator = (username != null && !username.isBlank()) ? username : "system";
        return ApiResponse.ok(stockDeductionService.deductStock(request, operator));
    }

    @PostMapping("/dispatch")
    @Operation(summary = "Xác nhận xuất xưởng đơn hàng (Dispatch & Deduct Stock with Pessimistic Lock)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    public ApiResponse<DeductStockResponse> dispatchOrder(
            @Valid @RequestBody DeductStockRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        String operator = (username != null && !username.isBlank()) ? username : "system";
        return ApiResponse.ok(stockDeductionService.deductStock(request, operator));
    }
}
