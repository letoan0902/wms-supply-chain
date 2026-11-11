package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.FrozenLocationConflict;
import vn.wms.inventory.dto.internal.ReceiptLocationCheckRequest;
import vn.wms.inventory.service.StockReceiptService;

import java.util.List;

/**
 * Endpoint nội bộ cho order-service gọi đồng bộ khi lập chứng từ làm thay đổi tồn kho (WMS-20.4), để chứng từ chỉ
 * được lưu khi inventory-service chấp nhận. Đường dẫn nằm dưới {@code /internal/} nên api-gateway không định tuyến
 * tới và không cần {@code @PreAuthorize} (docs/SECURITY.md mục 4); vai trò và phạm vi kho của người dùng đã được
 * order-service kiểm tra trước khi gọi. Mọi truy vấn vẫn lọc theo doanh nghiệp trong header {@code X-Company-Id}.
 */
@RestController
@RequestMapping("/internal/inventory")
@Tag(name = "Inventory Internal Stock", description = "Endpoint nội bộ kiểm tra và thay đổi tồn kho đồng bộ cho order-service")
public class InventoryStockInternalController {

    private final StockReceiptService stockReceiptService;

    public InventoryStockInternalController(StockReceiptService stockReceiptService) {
        this.stockReceiptService = stockReceiptService;
    }

    @PostMapping("/receipts/location-check")
    @Operation(summary = "Các dòng phiếu nhập sẽ cộng tồn vào vị trí đang đóng băng kiểm kê (rỗng là nhập được)")
    public ApiResponse<List<FrozenLocationConflict>> checkReceiptLocations(
            @Valid @RequestBody ReceiptLocationCheckRequest request) {
        return ApiResponse.ok(stockReceiptService.findFrozenReceiptLocations(request));
    }
}
