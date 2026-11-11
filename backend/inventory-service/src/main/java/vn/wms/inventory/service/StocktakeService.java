package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.inventory.dto.CreateStocktakeRequest;
import vn.wms.inventory.dto.FrozenLocationConflict;
import vn.wms.inventory.dto.StocktakeCountLineResponse;
import vn.wms.inventory.dto.StocktakeCountRequest;
import vn.wms.inventory.dto.StocktakeLocationResponse;
import vn.wms.inventory.dto.StocktakeSessionResponse;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StocktakeLine;
import vn.wms.inventory.entity.StocktakeLocation;
import vn.wms.inventory.entity.StocktakeScopeType;
import vn.wms.inventory.entity.StocktakeSession;
import vn.wms.inventory.entity.StocktakeStatus;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StocktakeLineRepository;
import vn.wms.inventory.repository.StocktakeLocationRepository;
import vn.wms.inventory.repository.StocktakeSessionRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Nghiệp vụ đợt kiểm kê (WMS-14.1): lập kế hoạch, bắt đầu (đóng băng vị trí và chụp tồn), đếm mù, hủy, tra cứu.
 * Phần tính chênh lệch, đếm lại và duyệt phiếu điều chỉnh thuộc WMS-14.2.
 */
@Service
public class StocktakeService {

    private static final Logger log = LoggerFactory.getLogger(StocktakeService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final StocktakeSessionRepository sessionRepository;
    private final StocktakeLocationRepository locationRepository;
    private final StocktakeLineRepository lineRepository;
    private final WarehouseRepository warehouseRepository;
    private final BinLocationRepository binLocationRepository;

    public StocktakeService(StocktakeSessionRepository sessionRepository,
                            StocktakeLocationRepository locationRepository,
                            StocktakeLineRepository lineRepository,
                            WarehouseRepository warehouseRepository,
                            BinLocationRepository binLocationRepository) {
        this.sessionRepository = sessionRepository;
        this.locationRepository = locationRepository;
        this.lineRepository = lineRepository;
        this.warehouseRepository = warehouseRepository;
        this.binLocationRepository = binLocationRepository;
    }

    // =========================================================================================
    // LẬP KẾ HOẠCH
    // =========================================================================================

    /**
     * Lập đợt kiểm kê ở trạng thái DRAFT. Phạm vi LOCATIONS lưu ngay danh sách vị trí (chưa đóng băng);
     * phạm vi WAREHOUSE và ZONES chốt danh sách vị trí khi bắt đầu.
     */
    @Transactional
    public StocktakeSessionResponse create(CreateStocktakeRequest request) {
        CurrentUser user = StocktakePermissions.requireUser();
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho hàng với ID: " + request.warehouseId()));
        StocktakePermissions.requireManage(user, warehouse.getId());
        if (!warehouse.isActive()) {
            throw ApiException.badRequest("Kho '" + warehouse.getName() + "' đang ngừng hoạt động, không thể lập đợt kiểm kê");
        }

        StocktakeSession session = new StocktakeSession();
        session.setWarehouse(warehouse);
        session.setScopeType(request.scopeType());
        session.setPlannedDate(request.plannedDate());
        session.setNotes(trimToNull(request.notes()));
        session.setProductIdSet(normalizeProductIds(request.productIds()));

        List<BinLocation> scopeBins = List.of();
        switch (request.scopeType()) {
            case WAREHOUSE -> {
                // Toàn kho: danh sách vị trí chốt khi bắt đầu
            }
            case ZONES -> session.setZoneList(validateZones(warehouse, request.zones()));
            case LOCATIONS -> scopeBins = validateLocations(warehouse, request.binLocationIds());
        }

        session.setCode(generateCode());
        try {
            session = sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Mã đợt kiểm kê vừa được sử dụng bởi yêu cầu khác, vui lòng thử lại");
        }
        for (BinLocation bin : scopeBins) {
            locationRepository.save(new StocktakeLocation(session, bin));
        }
        log.info("{} đã lập đợt kiểm kê {} cho kho {} (phạm vi {})",
                user.username(), session.getCode(), warehouse.getCode(), session.getScopeType());
        return toResponse(session, true);
    }

    // =========================================================================================
    // BẮT ĐẦU: ĐÓNG BĂNG VỊ TRÍ VÀ CHỤP TỒN
    // =========================================================================================

    /**
     * Bắt đầu kiểm kê (DRAFT -> COUNTING) trong một transaction:
     * 1. Chốt danh sách vị trí theo phạm vi; vị trí đang thuộc một đợt khác đang diễn ra thì từ chối (409).
     * 2. Đóng băng các vị trí (ghi frozen_bin_location_id, ràng buộc duy nhất chặn hai đợt cùng đóng băng một vị trí).
     * 3. Khóa bi quan các dòng tồn tại các vị trí đó rồi chụp tồn theo (vị trí, sản phẩm, số lô): tồn thực tế và giữ chỗ.
     * Thứ tự đóng băng trước, khóa dòng tồn sau trùng với thứ tự của luồng duyệt nên không gây khóa chéo.
     */
    @Transactional
    public StocktakeSessionResponse start(Long id) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = loadForUpdate(id);
        StocktakePermissions.requireManage(user, session.getWarehouse().getId());
        if (session.getStatus() != StocktakeStatus.DRAFT) {
            throw ApiException.conflict("Chỉ bắt đầu được đợt kiểm kê ở trạng thái DRAFT (hiện tại: " + session.getStatus() + ")");
        }
        if (!session.getWarehouse().isActive()) {
            throw ApiException.badRequest("Kho '" + session.getWarehouse().getName() + "' đang ngừng hoạt động, không thể bắt đầu kiểm kê");
        }

        List<StocktakeLocation> locations = resolveScopeLocations(session);
        if (locations.isEmpty()) {
            throw ApiException.badRequest("Phạm vi kiểm kê không có vị trí ô/kệ nào");
        }
        List<Long> binIds = locations.stream().map(l -> l.getBinLocation().getId()).toList();

        List<StocktakeLocation> conflicts = locationRepository.findFrozenByBinLocationIds(binIds);
        if (!conflicts.isEmpty()) {
            StocktakeLocation conflict = conflicts.get(0);
            throw ApiException.withData(ErrorCode.CONFLICT,
                    String.format("Vị trí %s đang thuộc đợt kiểm kê %s chưa kết thúc, mỗi vị trí chỉ được kiểm trong một đợt tại một thời điểm",
                            conflict.getBinLocation().getCode(), conflict.getSession().getCode()),
                    new FrozenLocationConflict(conflict.getSession().getId(), conflict.getSession().getCode(),
                            conflict.getBinLocation().getId(), conflict.getBinLocation().getCode(), null));
        }

        for (StocktakeLocation location : locations) {
            location.setFrozenBinLocationId(location.getBinLocation().getId());
        }
        try {
            locationRepository.saveAllAndFlush(locations);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Một số vị trí vừa được một đợt kiểm kê khác đóng băng, vui lòng tải lại và thử lại");
        }

        int lineCount = takeSnapshot(session, binIds);

        session.setStatus(StocktakeStatus.COUNTING);
        session.setStartedBy(user.username());
        session.setStartedAt(Instant.now());
        sessionRepository.save(session);
        log.info("{} đã bắt đầu đợt kiểm kê {}: đóng băng {} vị trí, chụp {} dòng tồn",
                user.username(), session.getCode(), locations.size(), lineCount);
        return toResponse(session, true);
    }

    private List<StocktakeLocation> resolveScopeLocations(StocktakeSession session) {
        Long warehouseId = session.getWarehouse().getId();
        List<BinLocation> bins = switch (session.getScopeType()) {
            case WAREHOUSE -> binLocationRepository.findByWarehouseIdOrderByZoneAscRackAscBinAsc(warehouseId);
            case ZONES -> {
                List<BinLocation> result = new ArrayList<>();
                for (String zone : session.getZoneList()) {
                    result.addAll(binLocationRepository.findByWarehouseIdAndZoneOrderByRackAscBinAsc(warehouseId, zone));
                }
                yield result;
            }
            case LOCATIONS -> null;
        };
        if (bins == null) {
            return locationRepository.findBySessionId(session.getId());
        }
        List<StocktakeLocation> locations = new ArrayList<>();
        for (BinLocation bin : bins) {
            locations.add(new StocktakeLocation(session, bin));
        }
        return locations;
    }

    /** Chụp tồn hệ thống, gộp theo (vị trí, sản phẩm, số lô); bỏ qua bộ khóa có tồn và giữ chỗ đều bằng 0. */
    private int takeSnapshot(StocktakeSession session, List<Long> binIds) {
        Map<String, StocktakeLine> linesByKey = new LinkedHashMap<>();
        for (StockItem item : lineRepository.lockStockItemsInBins(binIds)) {
            if (!session.coversProduct(item.getProductId())) {
                continue;
            }
            String lotNo = normalizeLot(item.getLotNo());
            String key = lineKey(item.getBinLocation().getId(), item.getProductId(), lotNo);
            StocktakeLine line = linesByKey.computeIfAbsent(key, k -> {
                StocktakeLine created = new StocktakeLine();
                created.setSession(session);
                created.setBinLocation(item.getBinLocation());
                created.setProductId(item.getProductId());
                created.setLotNo(lotNo);
                return created;
            });
            line.setSystemQuantity(line.getSystemQuantity().add(nz(item.getQuantity())));
            line.setSystemReservedQuantity(line.getSystemReservedQuantity().add(nz(item.getReservedQuantity())));
        }
        List<StocktakeLine> lines = linesByKey.values().stream()
                .filter(l -> l.getSystemQuantity().signum() != 0 || l.getSystemReservedQuantity().signum() != 0)
                .toList();
        lineRepository.saveAll(lines);
        return lines.size();
    }

    // =========================================================================================
    // ĐẾM MÙ
    // =========================================================================================

    /**
     * Ghi số đếm mù (BR-12: phản hồi không có tồn hệ thống). Khóa bi quan đợt kiểm kê để các lần quét đồng thời
     * cộng dồn đúng và không có số đếm nào lọt vào sau khi đã kết thúc đếm.
     */
    @Transactional
    public StocktakeCountLineResponse count(Long id, StocktakeCountRequest request) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = loadForUpdate(id);
        StocktakePermissions.requireCount(user, session.getWarehouse().getId());
        if (session.getStatus() != StocktakeStatus.COUNTING) {
            throw ApiException.conflict("Đợt kiểm kê " + session.getCode() + " không ở trạng thái đang đếm (hiện tại: "
                    + session.getStatus() + ")");
        }

        StocktakeCountRequest.CountMode mode = request.modeOrDefault();
        BigDecimal quantity = request.quantity();
        if (mode == StocktakeCountRequest.CountMode.ADD && quantity.signum() <= 0) {
            throw ApiException.badRequest("Số lượng cộng thêm khi quét phải lớn hơn 0");
        }

        StocktakeLine line = request.lineId() != null
                ? findLineOfSession(session, request.lineId())
                : findOrCreateLine(session, request);

        BigDecimal newCounted = mode == StocktakeCountRequest.CountMode.SET
                ? quantity
                : nz(line.getCountedQuantity()).add(quantity);
        line.setCountedQuantity(newCounted);
        line.setCountTimes(line.getCountTimes() + 1);
        line.setLastCountedBy(user.username());
        line.setLastCountedAt(Instant.now());
        line = lineRepository.save(line);
        return StocktakeCountLineResponse.from(line);
    }

    private StocktakeLine findLineOfSession(StocktakeSession session, Long lineId) {
        StocktakeLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng kiểm kê với ID: " + lineId));
        if (!Objects.equals(line.getSession().getId(), session.getId())) {
            throw ApiException.notFound("Không tìm thấy dòng kiểm kê với ID: " + lineId + " trong đợt " + session.getCode());
        }
        return line;
    }

    private StocktakeLine findOrCreateLine(StocktakeSession session, StocktakeCountRequest request) {
        BinLocation bin = resolveBin(request.binLocationId(), request.binLocationCode());
        if (!locationRepository.existsBySessionIdAndBinLocationId(session.getId(), bin.getId())) {
            throw ApiException.badRequest("Vị trí " + bin.getCode() + " không thuộc phạm vi đợt kiểm kê " + session.getCode());
        }
        if (request.productId() == null || request.productId() <= 0) {
            throw ApiException.badRequest("Vui lòng chọn hoặc quét mã sản phẩm cần đếm");
        }
        if (!session.coversProduct(request.productId())) {
            throw ApiException.badRequest("Sản phẩm ID " + request.productId() + " không thuộc phạm vi sản phẩm của đợt kiểm kê " + session.getCode());
        }
        String lotNo = normalizeLot(request.lotNo());
        List<StocktakeLine> existing = lineRepository.findByKey(session.getId(), bin.getId(), request.productId(), lotNo);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        // Hàng có thực tế nhưng không có trong ảnh chụp tồn: tạo dòng phát sinh với tồn hệ thống bằng 0
        StocktakeLine line = new StocktakeLine();
        line.setSession(session);
        line.setBinLocation(bin);
        line.setProductId(request.productId());
        line.setLotNo(lotNo);
        line.setUnexpected(true);
        log.info("Đợt kiểm kê {}: ghi nhận hàng phát sinh sản phẩm {} lô {} tại vị trí {}",
                session.getCode(), request.productId(), lotNo, bin.getCode());
        return line;
    }

    private BinLocation resolveBin(Long binLocationId, String binLocationCode) {
        if (binLocationId != null) {
            return binLocationRepository.findById(binLocationId)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí ô/kệ với ID: " + binLocationId));
        }
        if (binLocationCode != null && !binLocationCode.isBlank()) {
            String code = binLocationCode.trim();
            return binLocationRepository.findByCode(code)
                    .or(() -> binLocationRepository.findByCode(code.toUpperCase(Locale.ROOT)))
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí ô/kệ có mã: " + code));
        }
        throw ApiException.badRequest("Vui lòng chọn dòng kiểm kê hoặc quét mã vị trí ô/kệ");
    }

    /** Danh sách dòng cho màn hình đếm mù (không có tồn hệ thống), có phân trang và bộ lọc. */
    @Transactional(readOnly = true)
    public PageResponse<StocktakeCountLineResponse> listCountLines(Long id, Long productId, String binCode,
                                                                  Boolean counted, int page, int size) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = loadOrThrow(id);
        StocktakePermissions.requireView(user, session.getWarehouse().getId());
        Page<StocktakeLine> lines = lineRepository.search(session.getId(), productId, trimToNull(binCode), counted,
                false, PageRequest.of(Math.max(page, 0), clampSize(size)));
        return PageResponse.from(lines.map(StocktakeCountLineResponse::from));
    }

    // =========================================================================================
    // HỦY, TRA CỨU
    // =========================================================================================

    /** Hủy đợt kiểm kê trước khi Quản lý kho duyệt: mở đóng băng, tồn kho không đổi. */
    @Transactional
    public StocktakeSessionResponse cancel(Long id, String reason) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = loadForUpdate(id);
        StocktakePermissions.requireManage(user, session.getWarehouse().getId());
        StocktakeStatus status = session.getStatus();
        if (status != StocktakeStatus.DRAFT && status != StocktakeStatus.COUNTING && status != StocktakeStatus.COUNTED) {
            throw ApiException.conflict("Chỉ hủy được đợt kiểm kê ở trạng thái DRAFT, COUNTING hoặc COUNTED (hiện tại: "
                    + status + "); đợt đang chờ kế toán duyệt thì dùng thao tác từ chối");
        }
        unfreeze(session);
        session.setStatus(StocktakeStatus.CANCELLED);
        session.setClosedBy(user.username());
        session.setClosedAt(Instant.now());
        session.setCloseReason(trimToNull(reason));
        sessionRepository.save(session);
        log.info("{} đã hủy đợt kiểm kê {}", user.username(), session.getCode());
        return toResponse(session, true);
    }

    @Transactional(readOnly = true)
    public StocktakeSessionResponse getById(Long id) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = loadOrThrow(id);
        StocktakePermissions.requireView(user, session.getWarehouse().getId());
        return toResponse(session, true);
    }

    /**
     * Danh sách đợt kiểm kê có phân trang, lọc theo kho, trạng thái, khoảng ngày dự kiến và mã đợt;
     * Quản lý và Nhân viên kho chỉ thấy đợt của kho mình phụ trách.
     */
    @Transactional(readOnly = true)
    public PageResponse<StocktakeSessionResponse> list(Long warehouseId, StocktakeStatus status, LocalDate fromDate,
                                                      LocalDate toDate, String keyword, int page, int size) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakePermissions.requireListRole(user);
        Set<Long> allowed = UserContext.resolveAllowedWarehouses(user, warehouseId);

        Specification<StocktakeSession> spec = (root, query, cb) -> cb.conjunction();
        if (!allowed.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("warehouse").get("id").in(allowed));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (fromDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("plannedDate"), fromDate));
        }
        if (toDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("plannedDate"), toDate));
        }
        String kw = trimToNull(keyword);
        if (kw != null) {
            String pattern = "%" + kw.toUpperCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.upper(root.get("code")), pattern));
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<StocktakeSession> result = sessionRepository.findAll(spec, pageable);
        return PageResponse.from(result.map(s -> toResponse(s, false)));
    }

    // =========================================================================================
    // HÀM DÙNG CHUNG (cũng được StocktakeApprovalService sử dụng)
    // =========================================================================================

    StocktakeSession loadOrThrow(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đợt kiểm kê với ID: " + id));
    }

    StocktakeSession loadForUpdate(Long id) {
        return sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đợt kiểm kê với ID: " + id));
    }

    /** Mở đóng băng mọi vị trí của đợt (giữ lại danh sách vị trí để tra cứu). */
    void unfreeze(StocktakeSession session) {
        List<StocktakeLocation> locations = locationRepository.findBySessionId(session.getId());
        boolean changed = false;
        for (StocktakeLocation location : locations) {
            if (location.isFrozen()) {
                location.setFrozenBinLocationId(null);
                changed = true;
            }
        }
        if (changed) {
            locationRepository.saveAllAndFlush(locations);
        }
    }

    StocktakeSessionResponse toResponse(StocktakeSession session, boolean withLocations) {
        long totalLines = lineRepository.countBySessionId(session.getId());
        long uncounted = lineRepository.countBySessionIdAndCountedQuantityIsNull(session.getId());
        List<StocktakeLocationResponse> locations = null;
        long totalLocations;
        if (withLocations) {
            locations = locationRepository.findBySessionId(session.getId()).stream()
                    .map(StocktakeLocationResponse::from)
                    .toList();
            totalLocations = locations.size();
        } else {
            totalLocations = locationRepository.countBySessionId(session.getId());
        }
        return StocktakeSessionResponse.of(session, totalLocations, totalLines, uncounted, locations);
    }

    static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    static String normalizeLot(String lotNo) {
        return (lotNo == null || lotNo.isBlank()) ? null : lotNo.trim();
    }

    static String lineKey(Long binId, Long productId, String lotNo) {
        return binId + "|" + productId + "|" + (lotNo != null ? lotNo : "");
    }

    static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private List<String> validateZones(Warehouse warehouse, List<String> zones) {
        if (zones == null || zones.isEmpty()) {
            throw ApiException.badRequest("Vui lòng chọn ít nhất một khu (zone) khi kiểm kê theo khu");
        }
        Set<String> existing = new LinkedHashSet<>(binLocationRepository.findDistinctZonesByWarehouseId(warehouse.getId()));
        Set<String> result = new LinkedHashSet<>();
        for (String zone : zones) {
            String normalized = zone != null ? zone.trim().toUpperCase(Locale.ROOT) : "";
            if (normalized.isEmpty()) {
                continue;
            }
            if (!existing.contains(normalized)) {
                throw ApiException.badRequest("Khu '" + normalized + "' không tồn tại trong kho " + warehouse.getCode());
            }
            result.add(normalized);
        }
        if (result.isEmpty()) {
            throw ApiException.badRequest("Vui lòng chọn ít nhất một khu (zone) khi kiểm kê theo khu");
        }
        if (String.join(",", result).length() > 500) {
            throw ApiException.badRequest("Danh sách khu quá dài (tối đa 500 ký tự)");
        }
        return new ArrayList<>(result);
    }

    private List<BinLocation> validateLocations(Warehouse warehouse, List<Long> binLocationIds) {
        if (binLocationIds == null || binLocationIds.isEmpty()) {
            throw ApiException.badRequest("Vui lòng chọn ít nhất một vị trí ô/kệ khi kiểm kê theo vị trí");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long binId : binLocationIds) {
            if (binId != null) {
                ids.add(binId);
            }
        }
        List<BinLocation> bins = binLocationRepository.findAllById(ids);
        if (bins.size() != ids.size()) {
            throw ApiException.badRequest("Có vị trí ô/kệ không tồn tại trong danh sách đã chọn");
        }
        for (BinLocation bin : bins) {
            if (!Objects.equals(bin.getWarehouse().getId(), warehouse.getId())) {
                throw ApiException.badRequest("Vị trí " + bin.getCode() + " không thuộc kho " + warehouse.getCode());
            }
        }
        return bins;
    }

    private Collection<Long> normalizeProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (productId == null || productId <= 0) {
                throw ApiException.badRequest("ID sản phẩm trong phạm vi kiểm kê không hợp lệ");
            }
            result.add(productId);
        }
        return result;
    }

    private synchronized String generateCode() {
        String prefix = "ST-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        long next = sessionRepository.countByCodePrefix(prefix) + 1;
        String candidate;
        do {
            candidate = String.format("%s%02d", prefix, next++);
        } while (sessionRepository.existsByCode(candidate));
        return candidate;
    }
}
