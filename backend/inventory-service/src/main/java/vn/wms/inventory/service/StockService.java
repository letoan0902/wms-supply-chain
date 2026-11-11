package vn.wms.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.inventory.dto.BatchAtpResponse;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.repository.StockItemRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service quản lý tồn kho và tính toán Tồn khả dụng (ATP - Available To Promise) (FR15 - WMS-11.1).
 * Công thức: ATP = OnHand - Reserved
 * Lô hàng bị khóa hoặc đã hết hạn sử dụng (expDate <= hôm nay) tự động bị loại khỏi phép tính ATP.
 */
@Service
public class StockService {

    private final StockItemRepository stockItemRepository;

    public StockService(StockItemRepository stockItemRepository) {
        this.stockItemRepository = stockItemRepository;
    }

    /**
     * Lấy thông tin tồn khả dụng ATP của một sản phẩm (toàn hệ thống hoặc theo kho).
     */
    @Transactional(readOnly = true)
    public StockAtpResponse getAtp(Long productId, Long warehouseId) {
        if (productId == null) {
            return StockAtpResponse.of(null, warehouseId, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        LocalDate today = LocalDate.now();
        List<Object[]> results;
        if (warehouseId != null) {
            results = stockItemRepository.getStockTotalsByProductIdAndWarehouseId(productId, warehouseId, today);
        } else {
            results = stockItemRepository.getStockTotalsByProductId(productId, today);
        }

        if (results == null || results.isEmpty() || results.get(0) == null) {
            return StockAtpResponse.of(productId, warehouseId, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Object[] row = results.get(0);
        BigDecimal onHand = row[0] != null ? (BigDecimal) row[0] : BigDecimal.ZERO;
        BigDecimal reserved = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;

        return StockAtpResponse.of(productId, warehouseId, onHand, reserved);
    }

    /**
     * Kiểm tra tồn khả dụng ATP cho danh sách nhiều sản phẩm cùng lúc (Batch ATP).
     */
    @Transactional(readOnly = true)
    public BatchAtpResponse getBatchAtp(List<Long> productIds, Long warehouseId) {
        if (productIds == null || productIds.isEmpty()) {
            return new BatchAtpResponse(warehouseId, List.of());
        }

        List<Long> distinctIds = productIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return new BatchAtpResponse(warehouseId, List.of());
        }

        LocalDate today = LocalDate.now();
        List<Object[]> rawStats;
        if (warehouseId != null) {
            rawStats = stockItemRepository.getBatchStockTotalsByProductIdsAndWarehouseId(distinctIds, warehouseId, today);
        } else {
            rawStats = stockItemRepository.getBatchStockTotalsByProductIds(distinctIds, today);
        }

        Map<Long, Object[]> statsMap = new HashMap<>();
        for (Object[] row : rawStats) {
            if (row != null && row.length >= 3 && row[0] != null) {
                statsMap.put((Long) row[0], row);
            }
        }

        List<StockAtpResponse> items = new ArrayList<>();
        for (Long pid : distinctIds) {
            Object[] row = statsMap.get(pid);
            BigDecimal onHand = (row != null && row[1] != null) ? (BigDecimal) row[1] : BigDecimal.ZERO;
            BigDecimal reserved = (row != null && row[2] != null) ? (BigDecimal) row[2] : BigDecimal.ZERO;
            items.add(StockAtpResponse.of(pid, warehouseId, onHand, reserved));
        }

        return new BatchAtpResponse(warehouseId, items);
    }
}
