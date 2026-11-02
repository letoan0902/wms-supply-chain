package vn.wms.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.BinBarcodeResponse;
import vn.wms.inventory.dto.BinLocationResponse;
import vn.wms.inventory.dto.BulkCreateBinLocationsRequest;
import vn.wms.inventory.dto.CreateBinLocationRequest;
import vn.wms.inventory.dto.UpdateBinLocationRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.util.BarcodeGenerator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Xử lý nghiệp vụ vị trí ô/kệ 4 cấp & sinh mã vạch tem vị trí (WMS-06).
 */
@Service
public class BinLocationService {

    private final BinLocationRepository binLocationRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;

    public BinLocationService(
            BinLocationRepository binLocationRepository,
            WarehouseRepository warehouseRepository,
            StockItemRepository stockItemRepository
    ) {
        this.binLocationRepository = binLocationRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockItemRepository = stockItemRepository;
    }

    /**
     * Tra cứu danh sách vị trí ô/kệ theo kho, khu vực (zone), kệ (rack), trạng thái (WMS-06.1).
     */
    @Transactional(readOnly = true)
    public List<BinLocationResponse> listLocations(Long warehouseId, String zone, String rack, String status, Boolean active) {
        List<BinLocation> locations;
        if (warehouseId != null) {
            locations = binLocationRepository.findByWarehouseIdOrderByZoneAscRackAscBinAsc(warehouseId);
        } else {
            locations = binLocationRepository.findAll();
        }

        // Lấy thống kê hàng hóa theo từng vị trí để map nhanh
        Map<Long, StockStat> statsMap = new HashMap<>();
        if (warehouseId != null) {
            List<Object[]> rawStats = stockItemRepository.findStockStatsByWarehouseId(warehouseId);
            for (Object[] row : rawStats) {
                Long locId = (Long) row[0];
                long count = ((Number) row[1]).longValue();
                BigDecimal totalQty = (BigDecimal) row[2];
                statsMap.put(locId, new StockStat(count, totalQty));
            }
        }

        return locations.stream()
                .filter(l -> zone == null || zone.trim().isEmpty() || l.getZone().equalsIgnoreCase(zone.trim()))
                .filter(l -> rack == null || rack.trim().isEmpty() || l.getRack().equalsIgnoreCase(rack.trim()))
                .filter(l -> status == null || status.trim().isEmpty() || l.getStatus().equalsIgnoreCase(status.trim()))
                .filter(l -> active == null || l.isActive() == active)
                .map(l -> {
                    StockStat st = statsMap.getOrDefault(l.getId(), new StockStat(0, BigDecimal.ZERO));
                    return BinLocationResponse.from(l, st.itemCount, st.totalQuantity);
                })
                .toList();
    }

    /**
     * Lấy chi tiết một vị trí ô/kệ theo ID.
     */
    @Transactional(readOnly = true)
    public BinLocationResponse getLocationById(Long id) {
        BinLocation loc = binLocationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí ô/kệ có ID: " + id));

        boolean hasStock = stockItemRepository.existsActiveStockByBinLocationId(id);
        long itemCount = hasStock ? 1 : 0;
        return BinLocationResponse.from(loc, itemCount, BigDecimal.ZERO);
    }

    /**
     * Lấy danh sách các khu vực (Zone) duy nhất trong kho.
     */
    @Transactional(readOnly = true)
    public List<String> listZonesByWarehouse(Long warehouseId) {
        return binLocationRepository.findDistinctZonesByWarehouseId(warehouseId);
    }

    /**
     * Tạo một vị trí ô/kệ đơn lẻ (WMS-06.1).
     */
    @Transactional
    public BinLocationResponse createLocation(CreateBinLocationRequest request) {
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho có ID: " + request.warehouseId()));

        String zone = request.zone().trim().toUpperCase();
        String rack = request.rack().trim().toUpperCase();
        String bin = request.bin().trim().toUpperCase();
        String code = BinLocation.buildCode(warehouse.getCode(), zone, rack, bin);

        if (binLocationRepository.existsByCode(code)) {
            throw ApiException.conflict("Vị trí ô/kệ có mã " + code + " đã tồn tại trong hệ thống.");
        }

        BinLocation location = new BinLocation();
        location.setWarehouse(warehouse);
        location.setZone(zone);
        location.setRack(rack);
        location.setBin(bin);
        location.setCode(code);
        location.setMaxWeight(request.maxWeight() != null ? request.maxWeight() : BigDecimal.valueOf(1000.00));
        location.setStatus("EMPTY");
        location.setActive(request.active() == null || request.active());

        BinLocation saved = binLocationRepository.save(location);
        return BinLocationResponse.from(saved);
    }

    /**
     * Sinh hàng loạt vị trí ô/kệ tự động (Bulk Generate) (WMS-06.1).
     * Ví dụ: Zone A, Rack 1..5, Bin 1..10 -> Tự động sinh 50 ô kệ chuẩn hóa.
     */
    @Transactional
    public List<BinLocationResponse> bulkCreateLocations(BulkCreateBinLocationsRequest request) {
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho có ID: " + request.warehouseId()));

        if (request.rackFrom() > request.rackTo()) {
            throw ApiException.badRequest("Chỉ số kệ bắt đầu không được lớn hơn kệ kết thúc.");
        }
        if (request.binFrom() > request.binTo()) {
            throw ApiException.badRequest("Chỉ số ô bắt đầu không được lớn hơn ô kết thúc.");
        }

        int totalCount = (request.rackTo() - request.rackFrom() + 1) * (request.binTo() - request.binFrom() + 1);
        if (totalCount > 1000) {
            throw ApiException.badRequest("Số lượng vị trí sinh tự động trong một lần không được vượt quá 1000.");
        }

        String zone = request.zone().trim().toUpperCase();
        List<BinLocation> toSave = new ArrayList<>();

        for (int r = request.rackFrom(); r <= request.rackTo(); r++) {
            String rack = String.format("%s%02d", request.rackPrefix().trim().toUpperCase(), r);
            for (int b = request.binFrom(); b <= request.binTo(); b++) {
                String bin = String.format("%s%02d", request.binPrefix().trim().toUpperCase(), b);
                String code = BinLocation.buildCode(warehouse.getCode(), zone, rack, bin);

                if (!binLocationRepository.existsByCode(code)) {
                    BinLocation loc = new BinLocation();
                    loc.setWarehouse(warehouse);
                    loc.setZone(zone);
                    loc.setRack(rack);
                    loc.setBin(bin);
                    loc.setCode(code);
                    loc.setMaxWeight(request.maxWeight() != null ? request.maxWeight() : BigDecimal.valueOf(1000.00));
                    loc.setStatus("EMPTY");
                    loc.setActive(true);
                    toSave.add(loc);
                }
            }
        }

        List<BinLocation> saved = binLocationRepository.saveAll(toSave);
        return saved.stream().map(BinLocationResponse::from).toList();
    }

    /**
     * Cập nhật thông tin vị trí ô/kệ (tải trọng, trạng thái, kích hoạt) (WMS-06.1).
     */
    @Transactional
    public BinLocationResponse updateLocation(Long id, UpdateBinLocationRequest request) {
        BinLocation loc = binLocationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí ô/kệ có ID: " + id));

        if (request.maxWeight() != null) {
            loc.setMaxWeight(request.maxWeight());
        }
        if (request.status() != null && !request.status().trim().isEmpty()) {
            loc.setStatus(request.status().trim().toUpperCase());
        }
        if (request.active() != null) {
            loc.setActive(request.active());
        }

        BinLocation updated = binLocationRepository.save(loc);
        return BinLocationResponse.from(updated);
    }

    /**
     * Xóa vị trí ô/kệ: Không cho xóa nếu vị trí đang chứa hàng (WMS-06.1).
     */
    @Transactional
    public void deleteLocation(Long id) {
        BinLocation loc = binLocationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí ô/kệ có ID: " + id));

        if (stockItemRepository.existsActiveStockByBinLocationId(id)) {
            throw ApiException.conflict("Không thể xóa vị trí " + loc.getCode() + " vì đang có hàng hóa lưu trữ bên trong.");
        }

        binLocationRepository.delete(loc);
    }

    /**
     * Sinh dữ liệu mã vạch tem vị trí (Code 128 base64) để xem trước và in ấn (WMS-06.2).
     */
    @Transactional(readOnly = true)
    public BinBarcodeResponse generateBarcode(Long id) {
        BinLocation loc = binLocationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí ô/kệ có ID: " + id));

        String base64Image = BarcodeGenerator.generateCode128Base64(loc.getCode());

        return new BinBarcodeResponse(
                loc.getId(),
                loc.getCode(),
                loc.getWarehouse().getCode(),
                loc.getWarehouse().getName(),
                loc.getZone(),
                loc.getRack(),
                loc.getBin(),
                base64Image
        );
    }

    /**
     * Sinh ảnh binary PNG mã vạch phục vụ tải file trực tiếp (WMS-06.2).
     */
    @Transactional(readOnly = true)
    public byte[] generateBarcodePng(Long id) {
        BinLocation loc = binLocationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí ô/kệ có ID: " + id));

        return BarcodeGenerator.generateCode128Png(loc.getCode(), 2, 60);
    }

    private record StockStat(long itemCount, BigDecimal totalQuantity) {}
}

