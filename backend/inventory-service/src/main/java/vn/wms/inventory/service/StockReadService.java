package vn.wms.inventory.service;

import org.springframework.stereotype.Service;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.inventory.cache.StockTotals;
import vn.wms.inventory.cache.StockTotalsCache;
import vn.wms.inventory.cache.WarehouseStockFigures;
import vn.wms.inventory.dto.BatchAtpResponse;
import vn.wms.inventory.dto.StockAtpResponse;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Đọc tổng tồn và tồn khả dụng cho các API chỉ đọc, qua bộ nhớ đệm Redis {@link StockTotalsCache} (WMS-19.1):
 * {@code GET /api/inventory/stock/atp} (order-service gọi khi lập, xác nhận đơn bán; màn hình đơn bán và điều chuyển),
 * {@code POST /api/inventory/stock/atp/batch} và số tồn của {@code GET /internal/inventory/kpi-metrics}.
 *
 * <p><b>Phạm vi kho (WMS-20.4).</b> API người dùng gọi {@link #getAtpInUserScope} và {@link #getBatchAtpInUserScope}:
 * kho ngoài phạm vi của người gọi trả 403 ({@link UserContext#resolveAllowedWarehouses}); không truyền kho thì ADMIN,
 * ACCOUNTANT, INTEGRATION nhận tổng mọi kho như trước, vai trò giới hạn theo kho nhận tổng các kho được gán, cộng từ số
 * đệm của TỪNG kho nên không bao giờ đọc hay ghi khóa đệm "mọi kho" thay cho người dùng bị giới hạn. Lời gọi nội bộ của
 * order-service ({@code GET /internal/inventory/stock/atp}) dùng {@link #getAtp} không lọc theo kho.
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

    /**
     * Tồn khả dụng của một sản phẩm trong phạm vi kho của người đang gọi (API {@code GET /api/inventory/stock/atp}).
     * Kho ngoài phạm vi, hoặc tài khoản giới hạn theo kho chưa được gán kho nào, nhận 403.
     */
    public StockAtpResponse getAtpInUserScope(Long productId, Long warehouseId) {
        Set<Long> scope = restrictedScope(warehouseId);
        if (scope == null) {
            return getAtp(productId, warehouseId);
        }
        BigDecimal onHand = BigDecimal.ZERO;
        BigDecimal reserved = BigDecimal.ZERO;
        for (Long scopedWarehouseId : scope) {
            StockAtpResponse part = getAtp(productId, scopedWarehouseId);
            onHand = onHand.add(part.onHandQuantity());
            reserved = reserved.add(part.reservedQuantity());
        }
        return StockAtpResponse.of(productId, null, onHand, reserved);
    }

    /**
     * Tồn khả dụng của nhiều sản phẩm trong phạm vi kho của người đang gọi (API
     * {@code POST /api/inventory/stock/atp/batch}), cùng quy tắc với {@link #getAtpInUserScope}.
     */
    public BatchAtpResponse getBatchAtpInUserScope(List<Long> productIds, Long warehouseId) {
        Set<Long> scope = restrictedScope(warehouseId);
        if (scope == null) {
            return getBatchAtp(productIds, warehouseId);
        }
        Map<Long, BigDecimal[]> sums = new LinkedHashMap<>();
        for (Long scopedWarehouseId : scope) {
            for (StockAtpResponse item : getBatchAtp(productIds, scopedWarehouseId).items()) {
                BigDecimal[] sum = sums.computeIfAbsent(item.productId(),
                        id -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                sum[0] = sum[0].add(item.onHandQuantity());
                sum[1] = sum[1].add(item.reservedQuantity());
            }
        }
        List<StockAtpResponse> items = sums.entrySet().stream()
                .map(e -> StockAtpResponse.of(e.getKey(), null, e.getValue()[0], e.getValue()[1]))
                .toList();
        return new BatchAtpResponse(null, items);
    }

    /**
     * Tập kho cần cộng khi người gọi bị giới hạn theo kho và không chỉ định kho; {@code null} nghĩa là đọc như cũ theo
     * đúng {@code warehouseId} được truyền (đã kiểm tra quyền), hoặc mọi kho với ADMIN, ACCOUNTANT, INTEGRATION và lời
     * gọi không có người dùng (chế độ phát triển). Ném 403 nếu kho ngoài phạm vi.
     */
    private static Set<Long> restrictedScope(Long warehouseId) {
        Optional<CurrentUser> user = UserContext.getCurrentUser();
        if (user.isEmpty()) {
            return null;
        }
        Set<Long> allowed = UserContext.resolveAllowedWarehouses(user.get(), warehouseId);
        if (allowed.isEmpty() || warehouseId != null) {
            return null;
        }
        return new TreeSet<>(allowed);
    }

    public WarehouseStockFigures warehouseFigures(Long warehouseId) {
        return cache.warehouseFigures(warehouseId, () -> stockService.warehouseStockFigures(warehouseId));
    }
}
