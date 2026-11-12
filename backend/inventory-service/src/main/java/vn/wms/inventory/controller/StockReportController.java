package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.report.InOutBalanceQuery;
import vn.wms.inventory.dto.report.InOutBalanceReportResponse;
import vn.wms.inventory.dto.report.LotTraceResponse;
import vn.wms.inventory.dto.report.StockCardResponse;
import vn.wms.inventory.service.report.LotTraceService;
import vn.wms.inventory.service.report.StockReportExcelWriter;
import vn.wms.inventory.service.report.StockReportExportService;
import vn.wms.inventory.service.report.StockReportExportService.ExportFile;
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
    private final LotTraceService lotTraceService;
    private final StockReportExportService exportService;

    public StockReportController(StockReportService stockReportService, LotTraceService lotTraceService,
                                 StockReportExportService exportService) {
        this.stockReportService = stockReportService;
        this.lotTraceService = lotTraceService;
        this.exportService = exportService;
    }

    @GetMapping("/in-out-balance")
    @Operation(summary = "Báo cáo Xuất - Nhập - Tồn theo kỳ: tồn đầu, nhập, xuất, tồn cuối từng sản phẩm (và kho), kèm dòng tổng cộng")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT')")
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

    @GetMapping("/in-out-balance/export")
    @Operation(summary = "Tải file Excel báo cáo Xuất - Nhập - Tồn (WMS-17.3): cùng bộ lọc với API xem, không phân trang, tối đa 20000 dòng")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> exportInOutBalance(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = StockReportService.GROUP_BY_PRODUCT_WAREHOUSE) String groupBy,
            @RequestParam(required = false, defaultValue = "productId") String sort,
            @RequestParam(required = false, defaultValue = "asc") String direction
    ) {
        InOutBalanceQuery query = new InOutBalanceQuery(warehouseId, fromDate, toDate, categoryId, productId, keyword,
                groupBy, sort, direction, 0, 1);
        return excelResponse(exportService.exportInOutBalance(query));
    }

    @GetMapping("/stock-card/export")
    @Operation(summary = "Tải file Excel thẻ kho (WMS-17.3): cùng bộ lọc với API xem, không phân trang, tối đa 20000 dòng")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> exportStockCard(
            @RequestParam Long productId,
            @RequestParam Long warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return excelResponse(exportService.exportStockCard(productId, warehouseId, fromDate, toDate));
    }

    /** Tên file chỉ gồm ký tự ASCII nên filename và filename* giống nhau; trình duyệt ưu tiên filename*. */
    private static ResponseEntity<byte[]> excelResponse(ExportFile file) {
        String name = file.fileName();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(StockReportExcelWriter.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + "\"; filename*=UTF-8''" + name)
                .cacheControl(CacheControl.noStore())
                .contentLength(file.content().length)
                .body(file.content());
    }

    @GetMapping("/lot-trace")
    @Operation(summary = "Truy vết nguồn gốc theo số lô: phiếu nhập, đơn mua, nhà cung cấp; vị trí hiện tại; điều chuyển và xuất bán (dạng cây và chuỗi thời gian)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT')")
    public ApiResponse<LotTraceResponse> traceLot(
            @RequestParam String lotNo,
            @RequestParam(required = false) Long productId
    ) {
        return ApiResponse.ok(lotTraceService.trace(lotNo, productId));
    }
}
