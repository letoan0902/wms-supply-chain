package vn.wms.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.inventory.dto.internal.InventoryKpiMetricsResponse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InventoryKpiInternalService {

    private final StockItemRepository stockItemRepository;
    private final BinLocationRepository binLocationRepository;

    @Transactional(readOnly = true)
    public InventoryKpiMetricsResponse getMetrics(Long warehouseId) {
        LocalDate today = LocalDate.now();

        // 1. Tồn khả dụng (ATP)
        BigDecimal availableQuantity = stockItemRepository.sumAvailableQuantity(warehouseId, today);

        // 2. Tổng tồn thực tế
        BigDecimal totalQuantity = warehouseId != null
                ? stockItemRepository.sumQuantityByWarehouseId(warehouseId)
                : stockItemRepository.sumTotalQuantity();

        // 3. Mặt bằng & Tỷ lệ lấp đầy
        long totalLocations = binLocationRepository.countActiveLocations(warehouseId);
        long occupiedLocations = warehouseId != null
                ? stockItemRepository.countOccupiedLocationsByWarehouseId(warehouseId)
                : stockItemRepository.countTotalOccupiedLocations();

        double occupancyRate = 0.0;
        if (totalLocations > 0) {
            occupancyRate = Math.round(((double) occupiedLocations / totalLocations) * 1000.0) / 10.0;
        }

        return new InventoryKpiMetricsResponse(
                availableQuantity != null ? availableQuantity : BigDecimal.ZERO,
                totalQuantity != null ? totalQuantity : BigDecimal.ZERO,
                occupiedLocations,
                totalLocations,
                occupancyRate
        );
    }
}
