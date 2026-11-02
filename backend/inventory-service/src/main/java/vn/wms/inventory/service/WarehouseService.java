package vn.wms.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.CreateWarehouseRequest;
import vn.wms.inventory.dto.UpdateWarehouseRequest;
import vn.wms.inventory.dto.WarehouseResponse;
import vn.wms.inventory.dto.WarehouseSummaryResponse;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Xử lý nghiệp vụ danh mục đa kho & giám sát sức chứa kho (WMS-05).
 */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final BinLocationRepository binLocationRepository;
    private final StockItemRepository stockItemRepository;

    public WarehouseService(
            WarehouseRepository warehouseRepository,
            BinLocationRepository binLocationRepository,
            StockItemRepository stockItemRepository
    ) {
        this.warehouseRepository = warehouseRepository;
        this.binLocationRepository = binLocationRepository;
        this.stockItemRepository = stockItemRepository;
    }

    /**
     * Danh sách kho hàng kèm thống kê vị trí lưu trữ và tỷ lệ lấp đầy.
     */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> listWarehouses(String keyword, Boolean active) {
        List<Warehouse> warehouses;
        if (keyword != null && !keyword.trim().isEmpty()) {
            warehouses = warehouseRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderByCodeAsc(
                    keyword.trim(), keyword.trim());
        } else if (Boolean.TRUE.equals(active)) {
            warehouses = warehouseRepository.findByActiveTrueOrderByCodeAsc();
        } else {
            warehouses = warehouseRepository.findAllByOrderByCodeAsc();
        }

        if (active != null && (keyword != null && !keyword.trim().isEmpty())) {
            warehouses = warehouses.stream().filter(w -> w.isActive() == active).toList();
        }

        return warehouses.stream().map(this::toResponseWithStats).toList();
    }

    /**
     * Lấy thông tin chi tiết một kho theo ID.
     */
    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho có ID: " + id));
        return toResponseWithStats(warehouse);
    }

    /**
     * Thống kê tổng quan năng lực toàn bộ hệ thống kho (WMS-05.2).
     */
    @Transactional(readOnly = true)
    public WarehouseSummaryResponse getSummaryMetrics() {
        List<Warehouse> allWarehouses = warehouseRepository.findAll();
        long totalWarehouses = allWarehouses.size();
        long activeWarehouses = allWarehouses.stream().filter(Warehouse::isActive).count();

        long totalCapacity = allWarehouses.stream()
                .mapToLong(w -> w.getCapacity() != null ? w.getCapacity() : 0)
                .sum();

        long totalLocations = binLocationRepository.count();
        long occupiedLocations = stockItemRepository.countTotalOccupiedLocations();
        long availableLocations = Math.max(0, totalLocations - occupiedLocations);

        double overallUtilizationRate = 0.0;
        if (totalCapacity > 0) {
            overallUtilizationRate = Math.round(((double) occupiedLocations / totalCapacity) * 1000.0) / 10.0;
        } else if (totalLocations > 0) {
            overallUtilizationRate = Math.round(((double) occupiedLocations / totalLocations) * 1000.0) / 10.0;
        }

        long totalProducts = stockItemRepository.countDistinctActiveProducts();
        BigDecimal totalStockQuantity = stockItemRepository.sumTotalQuantity();

        return new WarehouseSummaryResponse(
                totalWarehouses,
                activeWarehouses,
                totalCapacity,
                totalLocations,
                occupiedLocations,
                availableLocations,
                overallUtilizationRate,
                totalProducts,
                totalStockQuantity
        );
    }

    /**
     * Tạo mới kho hàng (WMS-05.1).
     */
    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        String cleanCode = request.code().trim().toUpperCase();
        if (warehouseRepository.existsByCode(cleanCode)) {
            throw ApiException.conflict("Mã kho đã tồn tại: " + cleanCode);
        }

        Warehouse warehouse = new Warehouse();
        warehouse.setCode(cleanCode);
        warehouse.setName(request.name().trim());
        warehouse.setAddress(request.address() != null ? request.address().trim() : null);
        warehouse.setManagerName(request.managerName() != null ? request.managerName().trim() : null);
        warehouse.setContactPhone(request.contactPhone() != null ? request.contactPhone().trim() : null);
        warehouse.setCapacity(request.capacity() != null ? request.capacity() : 0);
        warehouse.setActive(request.active() == null || request.active());

        Warehouse saved = warehouseRepository.save(warehouse);
        return toResponseWithStats(saved);
    }

    /**
     * Cập nhật thông tin kho hàng (WMS-05.1).
     */
    @Transactional
    public WarehouseResponse updateWarehouse(Long id, UpdateWarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho có ID: " + id));

        warehouse.setName(request.name().trim());
        warehouse.setAddress(request.address() != null ? request.address().trim() : null);
        warehouse.setManagerName(request.managerName() != null ? request.managerName().trim() : null);
        warehouse.setContactPhone(request.contactPhone() != null ? request.contactPhone().trim() : null);
        if (request.capacity() != null) {
            warehouse.setCapacity(request.capacity());
        }
        if (request.active() != null) {
            warehouse.setActive(request.active());
        }

        Warehouse updated = warehouseRepository.save(warehouse);
        return toResponseWithStats(updated);
    }

    /**
     * Khóa hoặc mở khóa kho hàng (WMS-05.1).
     */
    @Transactional
    public WarehouseResponse toggleWarehouseStatus(Long id, boolean active) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho có ID: " + id));

        if (!active && stockItemRepository.existsActiveStockByWarehouseId(id)) {
            throw ApiException.conflict("Không thể khóa kho '" + warehouse.getName() + "' vì hiện vẫn còn hàng tồn kho.");
        }

        warehouse.setActive(active);
        Warehouse updated = warehouseRepository.save(warehouse);
        return toResponseWithStats(updated);
    }

    /**
     * Xóa kho hàng: Nghiêm cấm xóa kho nếu còn hàng tồn hoặc có vị trí lưu trữ (WMS-05.1).
     */
    @Transactional
    public void deleteWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho có ID: " + id));

        if (stockItemRepository.existsActiveStockByWarehouseId(id)) {
            throw ApiException.conflict("Không thể xóa kho '" + warehouse.getName() + "' vì còn hàng hóa đang tồn tại.");
        }

        if (binLocationRepository.existsByWarehouseId(id)) {
            throw ApiException.conflict("Không thể xóa kho '" + warehouse.getName() + "' vì còn vị trí ô/kệ lưu trữ liên kết.");
        }

        warehouseRepository.delete(warehouse);
    }

    private WarehouseResponse toResponseWithStats(Warehouse warehouse) {
        long totalLocations = binLocationRepository.countByWarehouseId(warehouse.getId());
        long occupiedLocations = stockItemRepository.countOccupiedLocationsByWarehouseId(warehouse.getId());
        return WarehouseResponse.from(warehouse, totalLocations, occupiedLocations);
    }
}
