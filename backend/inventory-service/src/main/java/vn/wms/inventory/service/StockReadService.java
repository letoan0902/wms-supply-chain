package vn.wms.inventory.service;

import org.springframework.stereotype.Service;
import vn.wms.inventory.cache.StockTotals;
import vn.wms.inventory.cache.StockTotalsCache;
import vn.wms.inventory.cache.WarehouseStockFigures;
import vn.wms.inventory.dto.BatchAtpResponse;
import vn.wms.inventory.dto.StockAtpResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Đọc tổng tồn và tồn khả dụng cho các API chỉ đọc, qua bộ nhớ đệm Redis {@link StockTotalsCache} (WMS-19.1):
 * {@code GET /api/inventory/stock/atp} (order-service gọi khi lập, xác nhận đơn bán; màn hình đơn bán và điều chuyển),
 * {@code POST /api/inventory/stock/atp/batch} và số tồn của {@code GET /internal/inventory/kpi-metrics}.
 *
 * <p>Kết quả trả về giống hệt {@link StockService}; trượt bộ nhớ đệm, tắt bộ nhớ đệm hay Redis lỗi đều đọc qua
 * {@link StockService}. Không có giao dịch ở lớp này: thế hệ của bộ nhớ đệm phải được đọc trước khi giao dịch đọc cơ
 * sở dữ liệu bắt đầu (xem {@link StockTotalsCache}). Nghiệp vụ thay đổi tồn KHÔNG được dùng lớp này.
 */
@Service
public class StockReadService {

    private final StockService stockService;
    private final StockTotalsCache cache;

    public StockReadService(StockService stockService, StockTotalsCache cache) {
        this.stockService = stockService;
        this.cache = cache;
    }

    public StockAtpResponse getAtp(Long productId, Long warehouseId) {
        if (productId == null) {
            return stockService.getAtp(null, warehouseId);
        }
        StockTotals totals = cache.productTotals(warehouseId, List.of(productId), ids -> {
            StockAtpResponse fromDb = stockService.getAtp(productId, warehouseId);
            return Map.of(productId, new StockTotals(fromDb.onHandQuantity(), fromDb.reservedQuantity()));
        }).get(productId);
        return StockAtpResponse.of(productId, warehouseId, totals.onHand(), totals.reserved());
    }

    public BatchAtpResponse getBatchAtp(List<Long> productIds, Long warehouseId) {
        List<Long> distinctIds = productIds == null ? List.of()
                : productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return stockService.getBatchAtp(productIds, warehouseId);
        }
        Map<Long, StockTotals> totals = cache.productTotals(warehouseId, distinctIds, ids -> {
            Map<Long, StockTotals> loaded = new HashMap<>();
            for (StockAtpResponse item : stockService.getBatchAtp(ids, warehouseId).items()) {
                loaded.put(item.productId(), new StockTotals(item.onHandQuantity(), item.reservedQuantity()));
            }
            return loaded;
        });
        List<StockAtpResponse> items = distinctIds.stream()
                .map(id -> StockAtpResponse.of(id, warehouseId, totals.get(id).onHand(), totals.get(id).reserved()))
                .toList();
        return new BatchAtpResponse(warehouseId, items);
    }

    public WarehouseStockFigures warehouseFigures(Long warehouseId) {
        return cache.warehouseFigures(warehouseId, () -> stockService.warehouseStockFigures(warehouseId));
    }
}
