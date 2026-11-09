package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.AutoLockResponse;
import vn.wms.inventory.dto.LockLotRequest;
import vn.wms.inventory.dto.LotResponse;
import vn.wms.inventory.dto.LotSummaryResponse;
import vn.wms.inventory.entity.InventoryLot;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockItemRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service Quản lý Lô hàng (Batch/Lot), Hạn sử dụng (Exp Date) & Khóa lô hết hạn (WMS-10.2 - FR12).
 * Hỗ trợ xuất theo nguyên tắc FEFO (hạn gần nhất xuất trước) và FIFO trên cả bảng inventory_lots và stock_items.
 */
@Service
public class LotService {

    private static final Logger log = LoggerFactory.getLogger(LotService.class);

    private final StockItemRepository stockItemRepository;
    private final InventoryLotRepository inventoryLotRepository;

    public LotService(StockItemRepository stockItemRepository, InventoryLotRepository inventoryLotRepository) {
        this.stockItemRepository = stockItemRepository;
        this.inventoryLotRepository = inventoryLotRepository;
    }

    /**
     * Tìm kiếm và phân trang danh sách các lô hàng trong kho (hỗ trợ cả inventory_lots và stock_items).
     */
    @Transactional(readOnly = true)
    public Page<LotResponse> searchLots(
            Long warehouseId,
            Long productId,
            String lotNo,
            Boolean locked,
            Boolean hasStock,
            String expiryStatus,
            Integer expiringDays,
            Pageable pageable
    ) {
        LocalDate today = LocalDate.now();
        int days = (expiringDays != null && expiringDays > 0) ? expiringDays : 30;
        LocalDate cutoffDate = today.plusDays(days);

        Boolean isExpired = null;
        Boolean isExpiringSoon = null;
        if ("EXPIRED".equalsIgnoreCase(expiryStatus)) {
            isExpired = true;
        } else if ("EXPIRING_SOON".equalsIgnoreCase(expiryStatus)) {
            isExpiringSoon = true;
        } else if ("GOOD".equalsIgnoreCase(expiryStatus)) {
            isExpired = false;
        }

        String normalizedLotNo = (lotNo != null && !lotNo.isBlank()) ? lotNo.trim() : null;

        if (inventoryLotRepository.count() > 0) {
            return inventoryLotRepository.searchLots(
                    warehouseId,
                    productId,
                    normalizedLotNo,
                    locked,
                    hasStock,
                    isExpired,
                    isExpiringSoon,
                    today,
                    cutoffDate,
                    pageable
            ).map(item -> LotResponse.from(item, today));
        }

        return stockItemRepository.searchLots(
                warehouseId,
                productId,
                normalizedLotNo,
                locked,
                hasStock,
                isExpired,
                isExpiringSoon,
                today,
                cutoffDate,
                pageable
        ).map(item -> LotResponse.from(item, today));
    }

    /**
     * Báo cáo thống kê tổng quan tình trạng các lô hàng theo kho hoặc toàn hệ thống.
     */
    @Transactional(readOnly = true)
    public LotSummaryResponse getSummary(Long warehouseId) {
        LocalDate today = LocalDate.now();
        LocalDate cutoffDate = today.plusDays(30);

        if (inventoryLotRepository.count() > 0) {
            long totalLots = inventoryLotRepository.countTotalLots(warehouseId);
            long activeLots = inventoryLotRepository.countActiveLots(warehouseId, today);
            long expiringSoonLots = inventoryLotRepository.countExpiringSoonLots(warehouseId, today, cutoffDate);
            long expiredLots = inventoryLotRepository.countExpiredLots(warehouseId, today);
            long lockedLots = inventoryLotRepository.countLockedLots(warehouseId);
            BigDecimal totalQty = inventoryLotRepository.sumQuantityInLots(warehouseId);

            return new LotSummaryResponse(
                    totalLots,
                    activeLots,
                    expiringSoonLots,
                    expiredLots,
                    lockedLots,
                    totalQty != null ? totalQty : BigDecimal.ZERO
            );
        }

        long totalLots = stockItemRepository.countTotalLots(warehouseId);
        long activeLots = stockItemRepository.countActiveLots(warehouseId, today);
        long expiringSoonLots = stockItemRepository.countExpiringSoonLots(warehouseId, today, cutoffDate);
        long expiredLots = stockItemRepository.countExpiredLots(warehouseId, today);
        long lockedLots = stockItemRepository.countLockedLots(warehouseId);
        BigDecimal totalQty = stockItemRepository.sumQuantityInLots(warehouseId);

        return new LotSummaryResponse(
                totalLots,
                activeLots,
                expiringSoonLots,
                expiredLots,
                lockedLots,
                totalQty != null ? totalQty : BigDecimal.ZERO
        );
    }

    /**
     * Lấy danh sách các lô hàng cận hạn sử dụng trong N ngày tới.
     */
    @Transactional(readOnly = true)
    public List<LotResponse> getExpiringSoonLots(Long warehouseId, Integer expiringDays) {
        LocalDate today = LocalDate.now();
        int days = (expiringDays != null && expiringDays > 0) ? expiringDays : 30;
        LocalDate cutoffDate = today.plusDays(days);

        if (inventoryLotRepository.count() > 0) {
            List<InventoryLot> items = inventoryLotRepository.findExpiringSoonLots(today, cutoffDate);
            if (warehouseId != null) {
                items = items.stream()
                        .filter(i -> i.getBinLocation() != null && i.getBinLocation().getWarehouse() != null
                                && warehouseId.equals(i.getBinLocation().getWarehouse().getId()))
                        .toList();
            }
            return items.stream().map(i -> LotResponse.from(i, today)).toList();
        }

        List<StockItem> items = stockItemRepository.findExpiringSoonLots(today, cutoffDate);
        if (warehouseId != null) {
            items = items.stream()
                    .filter(i -> i.getBinLocation() != null && i.getBinLocation().getWarehouse() != null
                            && warehouseId.equals(i.getBinLocation().getWarehouse().getId()))
                    .toList();
        }

        return items.stream().map(i -> LotResponse.from(i, today)).toList();
    }

    /**
     * Khóa lô hàng thủ công (ví dụ nghi ngờ lỗi chất lượng, phong tỏa kiểm định, hoặc hư hỏng).
     */
    @Transactional
    public LotResponse lockLot(Long id, LockLotRequest request, String username) {
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim() : "Khóa thủ công bởi " + (username != null ? username : "người dùng");

        Optional<InventoryLot> lotOpt = inventoryLotRepository.findByIdWithLock(id);
        if (lotOpt.isPresent()) {
            InventoryLot lot = lotOpt.get();
            lot.setLocked(true);
            lot.setLockedReason(reason);
            InventoryLot saved = inventoryLotRepository.save(lot);
            log.warn("Đã khóa lô hàng (inventory_lots) ID: {}, Lô số: {} bởi {}. Lý do: {}",
                    saved.getId(), saved.getLotNumber(), username, saved.getLockedReason());
            return LotResponse.from(saved, LocalDate.now());
        }

        StockItem stockItem = stockItemRepository.findByIdWithPessimisticLock(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bản ghi lô hàng tồn kho với ID: " + id));

        stockItem.setLocked(true);
        stockItem.setLockedReason(reason);

        StockItem saved = stockItemRepository.save(stockItem);
        log.warn("Đã khóa lô hàng (stock_items) ID: {}, Lô số: {}, Vị trí: {} bởi {}. Lý do: {}",
                saved.getId(), saved.getLotNo(),
                saved.getBinLocation() != null ? saved.getBinLocation().getCode() : "N/A",
                username, saved.getLockedReason());

        return LotResponse.from(saved, LocalDate.now());
    }

    /**
     * Mở khóa lô hàng.
     * Chặn mở khóa nếu lô hàng đã hết hạn sử dụng.
     */
    @Transactional
    public LotResponse unlockLot(Long id, String username) {
        Optional<InventoryLot> lotOpt = inventoryLotRepository.findByIdWithLock(id);
        if (lotOpt.isPresent()) {
            InventoryLot lot = lotOpt.get();
            if (lot.isExpired(LocalDate.now())) {
                throw ApiException.badRequest(String.format(
                        "Không thể mở khóa lô hàng '%s' vì đã hết hạn sử dụng (Exp Date: %s)!",
                        lot.getLotNumber() != null ? lot.getLotNumber() : lot.getId(),
                        lot.getExpDate()));
            }
            lot.setLocked(false);
            lot.setLockedReason(null);
            InventoryLot saved = inventoryLotRepository.save(lot);
            log.info("Đã mở khóa lô hàng (inventory_lots) ID: {}, Lô số: {} bởi {}",
                    saved.getId(), saved.getLotNumber(), username);
            return LotResponse.from(saved, LocalDate.now());
        }

        StockItem stockItem = stockItemRepository.findByIdWithPessimisticLock(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bản ghi lô hàng tồn kho với ID: " + id));

        if (stockItem.isExpired(LocalDate.now())) {
            throw ApiException.badRequest(String.format(
                    "Không thể mở khóa lô hàng '%s' vì đã hết hạn sử dụng (Exp Date: %s)!",
                    stockItem.getLotNo() != null ? stockItem.getLotNo() : stockItem.getId(),
                    stockItem.getExpDate()));
        }

        stockItem.setLocked(false);
        stockItem.setLockedReason(null);

        StockItem saved = stockItemRepository.save(stockItem);
        log.info("Đã mở khóa lô hàng (stock_items) ID: {}, Lô số: {} bởi {}",
                saved.getId(), saved.getLotNo(), username);

        return LotResponse.from(saved, LocalDate.now());
    }

    /**
     * Tự động quét và khóa tất cả các lô hàng đã hết hạn sử dụng (Exp Date <= hôm nay) của doanh nghiệp hiện tại.
     * Được gọi qua REST API (doanh nghiệp của người gọi) hoặc bởi {@link ExpiredLotScheduler} lúc 01:00 AM mỗi ngày,
     * khi đó scheduler đặt doanh nghiệp bằng TenantContext.runAs trước khi transaction này mở.
     */
    @Transactional
    public AutoLockResponse autoLockExpiredLots() {
        LocalDate today = LocalDate.now();
        int totalLocked = 0;

        // Khóa trên bảng inventory_lots
        List<InventoryLot> expiredInvLots = inventoryLotRepository.findExpiredUnlockedLots(today);
        if (!expiredInvLots.isEmpty()) {
            for (InventoryLot lot : expiredInvLots) {
                lot.setLocked(true);
                lot.setLockedReason("Lô hàng đã hết hạn sử dụng (Hạn dùng: " + lot.getExpDate() + ")");
            }
            inventoryLotRepository.saveAll(expiredInvLots);
            totalLocked += expiredInvLots.size();
        }

        // Khóa trên bảng stock_items
        List<StockItem> expiredStockLots = stockItemRepository.findExpiredUnlockedLots(today);
        if (!expiredStockLots.isEmpty()) {
            for (StockItem lot : expiredStockLots) {
                lot.setLocked(true);
                lot.setLockedReason("Lô hàng đã hết hạn sử dụng (Hạn dùng: " + lot.getExpDate() + ")");
            }
            stockItemRepository.saveAll(expiredStockLots);
            totalLocked += expiredStockLots.size();
        }

        if (totalLocked == 0) {
            log.info("Quét lô hết hạn ngày {}: Không có lô nào cần khóa", today);
            return new AutoLockResponse(0, "Không có lô hàng nào hết hạn cần khóa", Instant.now());
        }

        log.warn("Tự động khóa thành công {} lô hàng đã hết hạn sử dụng (Exp Date <= {})", totalLocked, today);

        return new AutoLockResponse(
                totalLocked,
                "Đã tự động khóa thành công " + totalLocked + " lô hàng hết hạn sử dụng",
                Instant.now()
        );
    }
}
