package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.report.InOutBalanceQuery;
import vn.wms.inventory.dto.report.InOutBalanceReportResponse;
import vn.wms.inventory.dto.report.StockCardResponse;
import vn.wms.inventory.service.report.StockReportService;

import java.time.LocalDate;

/**
 * REST API báo cáo kho tính từ sổ kho (WMS-17.1): Xuất - Nhập - Tồn theo kỳ và thẻ kho chi tiết.
 * Vai trò được xem: ADMIN, ACCOUNTANT (mọi kho), WAREHOUSE_MANAGER, WAREHOUSE_STAFF (kho được gán).
 */
@RestController
@RequestMapping("/api/inventory/reports")
@Tag(name = "Inventory Reports", description = "Báo cáo Xuất - Nhập - Tồn, thẻ kho chi tiết và truy vết theo số lô")
public class StockReportController {

    private final StockReportService stockReportService;

    public StockReportController(StockReportService stockReportService) {
        this.stockReportService = stockReportService;
    }

    @GetMapping("/in-out-balance")
    @Operation(summary = "Báo cáo Xuất - Nhập - Tồn theo kỳ: tồn đầu, nhập, xuất, tồn cuối từng sản phẩm (và kho), kèm dòng tổng cộng")
    public ApiResponse<InOutBalanceReportResponse> getInOutBalance(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = StockReportService.GROUP_BY_PRODUCT_WAREHOUSE) String groupBy,
            @RequestParam(required = false, defaultValue = "productId") String sort,
            @RequestParam(required = false, defaultValue = "asc") String direction,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        InOutBalanceQuery query = new InOutBalanceQuery(warehouseId, fromDate, toDate, categoryId, productId, keyword,
                groupBy, sort, direction, page, size);
        return ApiResponse.ok(stockReportService.getInOutBalance(query));
    }

    @GetMapping("/stock-card")
    @Operation(summary = "Thẻ kho chi tiết một sản phẩm tại một kho: tồn đầu kỳ, từng biến động theo thời gian với số dư lũy kế, tồn cuối kỳ")
    public ApiResponse<StockCardResponse> getStockCard(
            @RequestParam Long productId,
            @RequestParam Long warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        return ApiResponse.ok(stockReportService.getStockCard(productId, warehouseId, fromDate, toDate, page, size));
    }
}
