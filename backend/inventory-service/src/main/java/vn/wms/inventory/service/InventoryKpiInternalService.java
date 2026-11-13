package vn.wms.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.wms.inventory.cache.WarehouseStockFigures;
import vn.wms.inventory.dto.internal.InventoryKpiMetricsResponse;
import vn.wms.inventory.repository.BinLocationRepository;

/**
 * Chỉ số tồn kho và mặt bằng cho Dashboard KPI (WMS-16.1). Ba số theo tồn (tồn khả dụng, tổng tồn, số ô đang chứa
 * hàng) đi qua bộ nhớ đệm Redis (WMS-19.1, {@link StockReadService}); số ô của kho đọc thẳng cơ sở dữ liệu vì nó đổi
 * theo danh mục ô kệ. Không đặt {@code @Transactional} ở đây: bộ nhớ đệm phải đọc thế hệ trước khi giao dịch đọc tồn
 * bắt đầu, và hai phần số liệu không cần chung một ảnh chụp.
 */
@Service
@RequiredArgsConstructor
public class InventoryKpiInternalService {

    private final StockReadService stockReadService;
    private final BinLocationRepository binLocationRepository;

    public InventoryKpiMetricsResponse getMetrics(Long warehouseId) {
        // 1, 2, 3. Tồn khả dụng (ATP), tổng tồn thực tế, số ô đang chứa hàng
        WarehouseStockFigures figures = stockReadService.warehouseFigures(warehouseId);

        // 4. Mặt bằng & Tỷ lệ lấp đầy
        long totalLocations = binLocationRepository.countActiveLocations(warehouseId);
        long occupiedLocations = figures.occupiedLocations();

        double occupancyRate = 0.0;
        if (totalLocations > 0) {
            occupancyRate = Math.round(((double) occupiedLocations / totalLocations) * 1000.0) / 10.0;
        }

        return new InventoryKpiMetricsResponse(
                figures.availableQuantity(),
                figures.totalQuantity(),
                occupiedLocations,
                totalLocations,
                occupancyRate
        );
    }
}
