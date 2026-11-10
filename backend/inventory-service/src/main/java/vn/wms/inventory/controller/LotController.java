package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.inventory.dto.AutoLockResponse;
import vn.wms.inventory.dto.LockLotRequest;
import vn.wms.inventory.dto.LotResponse;
import vn.wms.inventory.dto.LotSummaryResponse;
import vn.wms.inventory.service.LotService;

import java.util.List;

/**
 * REST API Quản lý Lô hàng (Batch/Lot), Hạn sử dụng (Exp Date) & Khóa lô hết hạn (WMS-10.2 - FR12).
 */
@RestController
@RequestMapping("/api/inventory/lots")
@Tag(name = "Lot & Expiry Date Management", description = "Quản lý số lô (Batch/Lot), ngày sản xuất (Mfg), hạn dùng (Exp) và khóa lô hết hạn (WMS-10.2 - FR12)")
public class LotController {

    private final LotService lotService;

    public LotController(LotService lotService) {
        this.lotService = lotService;
    }

    @GetMapping
    @Operation(summary = "Tìm kiếm và phân trang danh sách lô hàng theo vị trí ô/kệ lưu trữ")
    public ApiResponse<PageResponse<LotResponse>> searchLots(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) Boolean locked,
            @RequestParam(required = false) Boolean hasStock,
            @RequestParam(required = false) String expiryStatus,
            @RequestParam(required = false, defaultValue = "30") Integer expiringDays,
            @PageableDefault(size = 20, sort = "expDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                lotService.searchLots(warehouseId, productId, lotNo, locked, hasStock, expiryStatus, expiringDays, pageable)
        ));
    }

    @GetMapping("/summary")
    @Operation(summary = "Thống kê tổng quan tình trạng các lô hàng (tổng số, hoạt động, cận hạn, hết hạn, bị khóa)")
    public ApiResponse<LotSummaryResponse> getSummary(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false, defaultValue = "30") Integer expiringDays
    ) {
        return ApiResponse.ok(lotService.getSummary(warehouseId, expiringDays));
    }

    @GetMapping("/expiring-soon")
    @Operation(summary = "Danh sách các lô hàng cận hạn sử dụng trong N ngày tới (mặc định 30 ngày)")
    public ApiResponse<List<LotResponse>> getExpiringSoonLots(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false, defaultValue = "30") Integer expiringDays
    ) {
        return ApiResponse.ok(lotService.getExpiringSoonLots(warehouseId, expiringDays));
    }

    @PutMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Khóa lô hàng thủ công (phong tỏa kiểm định, lỗi chất lượng...)")
    public ApiResponse<LotResponse> lockLot(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LockLotRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.ok(lotService.lockLot(id, request, username));
    }

    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Mở khóa lô hàng (chỉ cho phép khi lô hàng chưa hết hạn sử dụng)")
    public ApiResponse<LotResponse> unlockLot(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.ok(lotService.unlockLot(id, username));
    }

    @PostMapping("/auto-lock-expired")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Kích hoạt quét và tự động khóa các lô hàng đã hết hạn sử dụng (Exp Date <= hôm nay)")
    public ApiResponse<AutoLockResponse> triggerAutoLockExpired() {
        return ApiResponse.ok(lotService.autoLockExpiredLots());
    }
}
