package vn.wms.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
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
import java.util.Optional;
import java.util.Set;

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
        Optional<CurrentUser> userOpt = UserContext.getCurrentUser();

        if (userOpt.isPresent() && !UserContext.isGlobalScope(userOpt.get())) {
            Set<Long> userWarehouseIds = userOpt.get().warehouseIds();
            if (userWarehouseIds.isEmpty()) {
                throw ApiException.forbidden("Tài khoản chưa được phân quyền phụ trách bất kỳ kho nào");
            }
            warehouses = warehouseRepository.findByIdInOrderByCodeAsc(userWarehouseIds);
        } else if (Boolean.TRUE.equals(active)) {
            warehouses = warehouseRepository.findByActiveTrueOrderByCodeAsc();
        } else {
            warehouses = warehouseRepository.findAllByOrderByCodeAsc();
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            warehouses = warehouses.stream()
                    .filter(w -> (w.getName() != null && w.getName().toLowerCase().contains(kw))
                            || (w.getCode() != null && w.getCode().toLowerCase().contains(kw)))
                    .toList();
        }

        if (active != null) {
            warehouses = warehouses.stream().filter(w -> w.isActive() == active).toList();
        }

        return warehouses.stream().map(this::toResponseWithStats).toList();
    }

    /**
     * Lấy thông tin chi tiết một kho theo ID.
     */
    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(Long id) {
        checkWarehouseAccess(id);
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho có ID: " + id));
        return toResponseWithStats(warehouse);
    }

    /**
     * Thống kê tổng quan năng lực toàn bộ hệ thống kho (WMS-05.2).
     */
    @Transactional(readOnly = true)
    public WarehouseSummaryResponse getSummaryMetrics() {
        Optional<CurrentUser> userOpt = UserContext.getCurrentUser();
        List<Warehouse> warehouses;
        Set<Long> scopedWarehouseIds = null;

        if (userOpt.isPresent() && !UserContext.isGlobalScope(userOpt.get())) {
            scopedWarehouseIds = userOpt.get().warehouseIds();
            if (scopedWarehouseIds.isEmpty()) {
                throw ApiException.forbidden("Tài khoản chưa được phân quyền phụ trách bất kỳ kho nào");
            }
            warehouses = warehouseRepository.findByIdInOrderByCodeAsc(scopedWarehouseIds);
        } else {
            warehouses = warehouseRepository.findAll();
        }

        long totalWarehouses = warehouses.size();
        long activeWarehouses = warehouses.stream().filter(Warehouse::isActive).count();

        long totalCapacity = warehouses.stream()
                .mapToLong(w -> w.getCapacity() != null ? w.getCapacity() : 0)
                .sum();

        long totalLocations;
        long occupiedLocations;
        long totalProducts;
        BigDecimal totalStockQuantity;

        if (scopedWarehouseIds != null) {
            totalLocations = binLocationRepository.countByWarehouseIdIn(scopedWarehouseIds);
            occupiedLocations = stockItemRepository.countOccupiedLocationsByWarehouseIds(scopedWarehouseIds);
            totalProducts = stockItemRepository.countDistinctActiveProductsByWarehouseIds(scopedWarehouseIds);
            totalStockQuantity = stockItemRepository.sumQuantityByWarehouseIds(scopedWarehouseIds);
        } else {
            totalLocations = binLocationRepository.count();
            occupiedLocations = stockItemRepository.countTotalOccupiedLocations();
            totalProducts = stockItemRepository.countDistinctActiveProducts();
            totalStockQuantity = stockItemRepository.sumTotalQuantity();
        }

        long availableLocations = Math.max(0, totalLocations - occupiedLocations);

        double overallUtilizationRate = 0.0;
        if (totalCapacity > 0) {
            overallUtilizationRate = Math.round(((double) occupiedLocations / totalCapacity) * 1000.0) / 10.0;
        } else if (totalLocations > 0) {
            overallUtilizationRate = Math.round(((double) occupiedLocations / totalLocations) * 1000.0) / 10.0;
        }

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
        UserContext.getCurrentUser().ifPresent(user -> {
            if (!user.hasRole(Roles.ADMIN)) {
                throw ApiException.forbidden("Chỉ Quản trị viên (ADMIN) mới có quyền tạo kho hàng mới");
            }
        });

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
        checkWarehouseWriteAccess(id);
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
        checkWarehouseWriteAccess(id);
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
        UserContext.getCurrentUser().ifPresent(user -> {
            if (!user.hasRole(Roles.ADMIN)) {
                throw ApiException.forbidden("Chỉ Quản trị viên (ADMIN) mới có quyền xóa kho hàng");
            }
        });
        checkWarehouseWriteAccess(id);
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

    /**
     * Kiểm tra quyền đọc dữ liệu kho:
     * - ADMIN và ACCOUNTANT được xem toàn bộ kho (Global Read Scope).
     * - WAREHOUSE_MANAGER và WAREHOUSE_STAFF chỉ được xem kho được phân công.
     * - Khi không có header X-User-Name (gọi nội bộ dev/mock test), hệ thống cho phép bỏ qua (fail-open dev).
     */
    private void checkWarehouseAccess(Long warehouseId) {
        UserContext.getCurrentUser().ifPresent(user -> {
            if (!UserContext.isGlobalScope(user) && !user.warehouseIds().contains(warehouseId)) {
                throw ApiException.forbidden("Bạn không có quyền thao tác trên kho ID: " + warehouseId);
            }
        });
    }

    /**
     * Kiểm tra quyền ghi dữ liệu kho (sửa kho, đổi trạng thái kho, xóa kho):
     * - Chỉ ADMIN hoặc WAREHOUSE_MANAGER phụ trách kho mới có quyền ghi.
     * - ACCOUNTANT chỉ có quyền đọc báo cáo, bị từ chối thao tác ghi.
     * - Khi không có header X-User-Name (gọi nội bộ dev/mock test), cho phép bỏ qua (fail-open dev).
     */
    private void checkWarehouseWriteAccess(Long warehouseId) {
        UserContext.getCurrentUser().ifPresent(user -> {
            UserContext.requireWarehouseWriteAccess(user, warehouseId);
        });
    }

    private WarehouseResponse toResponseWithStats(Warehouse warehouse) {
        long totalLocations = binLocationRepository.countByWarehouseId(warehouse.getId());
        long occupiedLocations = stockItemRepository.countOccupiedLocationsByWarehouseId(warehouse.getId());
        return WarehouseResponse.from(warehouse, totalLocations, occupiedLocations);
    }
}
