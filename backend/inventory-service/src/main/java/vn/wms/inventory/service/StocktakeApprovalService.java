package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.InventoryUpdatedEvent;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.inventory.dto.StockAdjustmentResponse;
import vn.wms.inventory.dto.StocktakeReservedConflict;
import vn.wms.inventory.dto.StocktakeSessionResponse;
import vn.wms.inventory.dto.StocktakeVarianceLineResponse;
import vn.wms.inventory.dto.StocktakeVarianceReportResponse;
import vn.wms.inventory.entity.InventoryLot;
import vn.wms.inventory.entity.StockAdjustment;
import vn.wms.inventory.entity.StockAdjustmentLine;
import vn.wms.inventory.entity.StockAdjustmentStatus;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StocktakeLine;
import vn.wms.inventory.entity.StocktakeSession;
import vn.wms.inventory.entity.StocktakeStatus;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockAdjustmentRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StocktakeLineRepository;
import vn.wms.inventory.repository.StocktakeSessionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Tính chênh lệch kiểm kê và duyệt phiếu điều chỉnh tồn kho (WMS-14.2, FR20, UC-09 bước 3 và 4).
 * <p>
 * Quy trình duyệt hai cấp theo SRS: Quản lý kho duyệt kết quả kiểm kê (sinh phiếu điều chỉnh nếu có lệch), sau đó
 * Kế toán duyệt phiếu thì chênh lệch mới được ghi vào tồn kho; hai cấp phải là hai người khác nhau. Đợt không có
 * chênh lệch thì chỉ cần Quản lý kho duyệt. Mọi thao tác đổi trạng thái khóa bi quan dòng đợt kiểm kê, nên bấm duyệt
 * hai lần hoặc hai người duyệt cùng lúc chỉ áp dụng điều chỉnh một lần.
 */
@Service
public class StocktakeApprovalService {

    private static final Logger log = LoggerFactory.getLogger(StocktakeApprovalService.class);

    /** Lý do ghi trong sự kiện inventory.updated, cùng bộ giá trị với các luồng khác (GOODS_RECEIPT, TRANSFER...). */
    static final String EVENT_REASON = "ADJUSTMENT";

    private final StocktakeService stocktakeService;
    private final StocktakeSessionRepository sessionRepository;
    private final StocktakeLineRepository lineRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final StockItemRepository stockItemRepository;
    private final InventoryLotRepository inventoryLotRepository;

    @Autowired(required = false)
    private WmsEventPublisher wmsEventPublisher;

    public StocktakeApprovalService(StocktakeService stocktakeService,
                                    StocktakeSessionRepository sessionRepository,
                                    StocktakeLineRepository lineRepository,
                                    StockAdjustmentRepository adjustmentRepository,
                                    StockItemRepository stockItemRepository,
                                    InventoryLotRepository inventoryLotRepository) {
        this.stocktakeService = stocktakeService;
        this.sessionRepository = sessionRepository;
        this.lineRepository = lineRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.stockItemRepository = stockItemRepository;
        this.inventoryLotRepository = inventoryLotRepository;
    }

    // =========================================================================================
    // KẾT THÚC ĐẾM, CHÊNH LỆCH, ĐẾM LẠI
    // =========================================================================================

    /**
     * Kết thúc đếm (COUNTING -> COUNTED) và tính chênh lệch từng dòng = số đếm - tồn hệ thống.
     * Còn dòng chưa đếm thì từ chối (409, {@code data.uncountedLines}) trừ khi người dùng xác nhận tính bằng 0.
     */
    @Transactional
    public StocktakeSessionResponse finish(Long id, boolean confirmUncountedAsZero) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = stocktakeService.loadForUpdate(id);
        StocktakePermissions.requireManage(user, session.getWarehouse().getId());
        if (session.getStatus() != StocktakeStatus.COUNTING) {
            throw ApiException.conflict("Chỉ kết thúc đếm được đợt kiểm kê đang đếm (hiện tại: " + session.getStatus() + ")");
        }
        List<StocktakeLine> lines = lineRepository.findBySessionIdOrdered(session.getId());
        long uncounted = lines.stream().filter(l -> !l.isCounted()).count();
        if (uncounted > 0 && !confirmUncountedAsZero) {
            throw ApiException.withData(ErrorCode.CONFLICT,
                    "Còn " + uncounted + " dòng chưa đếm. Xác nhận tính các dòng này bằng 0 hoặc đếm tiếp trước khi kết thúc",
                    Map.of("uncountedLines", uncounted));
        }
        Instant now = Instant.now();
        for (StocktakeLine line : lines) {
            if (!line.isCounted()) {
                line.setCountedQuantity(BigDecimal.ZERO);
                line.setLastCountedBy(user.username());
                line.setLastCountedAt(now);
            }
            line.setVarianceQuantity(line.getCountedQuantity().subtract(line.getSystemQuantity()));
        }
        lineRepository.saveAll(lines);
        session.setStatus(StocktakeStatus.COUNTED);
        session.setFinishedBy(user.username());
        session.setFinishedAt(now);
        sessionRepository.save(session);
        log.info("{} đã kết thúc đếm đợt kiểm kê {} ({} dòng, {} dòng chưa đếm tính bằng 0)",
                user.username(), session.getCode(), lines.size(), uncounted);
        return stocktakeService.toResponse(session, true);
    }

    /** Báo cáo chênh lệch: tổng hợp toàn đợt và trang dòng theo bộ lọc. Chỉ có sau khi kết thúc đếm. */
    @Transactional(readOnly = true)
    public StocktakeVarianceReportResponse variances(Long id, boolean onlyDifferences, String binCode, Long productId,
                                                     int page, int size) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = stocktakeService.loadOrThrow(id);
        StocktakePermissions.requireSeeSystemQuantities(user, session.getWarehouse().getId());
        if (session.getFinishedAt() == null) {
            throw ApiException.conflict("Đợt kiểm kê " + session.getCode() + " chưa kết thúc đếm nên chưa có chênh lệch");
        }

        List<StocktakeLine> all = lineRepository.findBySessionIdOrdered(session.getId());
        long counted = 0;
        long increaseLines = 0;
        long decreaseLines = 0;
        long conflicts = 0;
        BigDecimal increase = BigDecimal.ZERO;
        BigDecimal decrease = BigDecimal.ZERO;
        for (StocktakeLine line : all) {
            if (line.isCounted()) {
                counted++;
                if (line.getCountedQuantity().compareTo(line.getSystemReservedQuantity()) < 0) {
                    conflicts++;
                }
            }
            BigDecimal variance = line.getVarianceQuantity();
            if (variance == null) {
                continue;
            }
            if (variance.signum() > 0) {
                increaseLines++;
                increase = increase.add(variance);
            } else if (variance.signum() < 0) {
                decreaseLines++;
                decrease = decrease.add(variance.negate());
            }
        }
        StocktakeVarianceReportResponse.Summary summary = new StocktakeVarianceReportResponse.Summary(
                all.size(), counted, increaseLines + decreaseLines, increaseLines, decreaseLines,
                increase, decrease, increase.subtract(decrease), conflicts);

        Page<StocktakeLine> lines = lineRepository.search(session.getId(), productId, StocktakeService.trimToNull(binCode),
                null, onlyDifferences, PageRequest.of(Math.max(page, 0), StocktakeService.clampSize(size)));
        return new StocktakeVarianceReportResponse(summary, PageResponse.from(lines.map(StocktakeVarianceLineResponse::from)));
    }

    /**
     * Yêu cầu đếm lại một số dòng trước khi duyệt: xóa số đếm và chênh lệch của các dòng đó, đánh dấu đếm lại,
     * đợt quay về COUNTING (vị trí vẫn đóng băng). Sau khi đếm lại, gọi kết thúc đếm để tính lại chênh lệch.
     */
    @Transactional
    public StocktakeSessionResponse recount(Long id, List<Long> lineIds) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = stocktakeService.loadForUpdate(id);
        StocktakePermissions.requireManage(user, session.getWarehouse().getId());
        if (session.getStatus() != StocktakeStatus.COUNTED && session.getStatus() != StocktakeStatus.COUNTING) {
            throw ApiException.conflict("Chỉ yêu cầu đếm lại được khi đợt đang đếm hoặc chờ Quản lý kho duyệt (hiện tại: "
                    + session.getStatus() + ")");
        }
        Set<Long> ids = new LinkedHashSet<>(lineIds);
        ids.remove(null);
        List<StocktakeLine> lines = new ArrayList<>();
        for (Long lineId : ids) {
            StocktakeLine line = lineRepository.findById(lineId)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng kiểm kê với ID: " + lineId));
            if (!Objects.equals(line.getSession().getId(), session.getId())) {
                throw ApiException.notFound("Không tìm thấy dòng kiểm kê với ID: " + lineId + " trong đợt " + session.getCode());
            }
            line.setCountedQuantity(null);
            line.setVarianceQuantity(null);
            line.setRecountRequested(true);
            lines.add(line);
        }
        if (lines.isEmpty()) {
            throw ApiException.badRequest("Vui lòng chọn ít nhất một dòng cần đếm lại");
        }
        lineRepository.saveAll(lines);
        session.setStatus(StocktakeStatus.COUNTING);
        session.setFinishedBy(null);
        session.setFinishedAt(null);
        sessionRepository.save(session);
        log.info("{} yêu cầu đếm lại {} dòng của đợt kiểm kê {}", user.username(), lines.size(), session.getCode());
        return stocktakeService.toResponse(session, true);
    }

    /** Ghi lý do chênh lệch cho một dòng (ở trạng thái COUNTED). */
    @Transactional
    public StocktakeVarianceLineResponse setReason(Long id, Long lineId, String reason) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = stocktakeService.loadForUpdate(id);
        StocktakePermissions.requireManage(user, session.getWarehouse().getId());
        if (session.getStatus() != StocktakeStatus.COUNTED) {
            throw ApiException.conflict("Chỉ ghi lý do chênh lệch khi đợt đang chờ Quản lý kho duyệt (hiện tại: " + session.getStatus() + ")");
        }
        String trimmed = StocktakeService.trimToNull(reason);
        if (trimmed != null && trimmed.length() > 255) {
            throw ApiException.badRequest("Lý do chênh lệch tối đa 255 ký tự");
        }
        StocktakeLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng kiểm kê với ID: " + lineId));
        if (!Objects.equals(line.getSession().getId(), session.getId())) {
            throw ApiException.notFound("Không tìm thấy dòng kiểm kê với ID: " + lineId + " trong đợt " + session.getCode());
        }
        line.setReason(trimmed);
        return StocktakeVarianceLineResponse.from(lineRepository.save(line));
    }

    // =========================================================================================
    // DUYỆT HAI CẤP, TỪ CHỐI
    // =========================================================================================

    /**
     * Quản lý kho duyệt kết quả kiểm kê (COUNTED). Không có chênh lệch: đợt hoàn tất và mở đóng băng ngay.
     * Có chênh lệch: sinh phiếu điều chỉnh chờ Kế toán duyệt (PENDING_ACCOUNTING), tồn kho chưa đổi.
     * Dòng có tồn sau điều chỉnh nhỏ hơn số đang giữ chỗ thì từ chối (409, danh sách dòng trong {@code data}).
     */
    @Transactional
    public StocktakeSessionResponse approveByManager(Long id, String notes) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakeSession session = stocktakeService.loadForUpdate(id);
        StocktakePermissions.requireManage(user, session.getWarehouse().getId());
        if (session.getStatus() != StocktakeStatus.COUNTED) {
            throw ApiException.conflict("Đợt kiểm kê " + session.getCode()
                    + " không ở trạng thái chờ Quản lý kho duyệt (hiện tại: " + session.getStatus() + ")");
        }
        List<StocktakeLine> varianceLines = lineRepository.findBySessionIdOrdered(session.getId()).stream()
                .filter(l -> l.getVarianceQuantity() != null && l.getVarianceQuantity().signum() != 0)
                .toList();

        List<StocktakeReservedConflict> conflicts = new ArrayList<>();
        for (StocktakeLine line : varianceLines) {
            List<StockItem> rows = stockItemRepository.findByBinLocationIdAndProductIdAndLotNoForUpdate(
                    line.getBinLocation().getId(), line.getProductId(), line.getLotNo());
            checkLine(rows, line.getVarianceQuantity(), line.getId(), line.getBinLocation().getCode(),
                    line.getProductId(), line.getLotNo(), conflicts);
        }
        throwIfConflicts(conflicts);

        Instant now = Instant.now();
        session.setManagerApprovedBy(user.username());
        session.setManagerApprovedAt(now);

        if (varianceLines.isEmpty()) {
            stocktakeService.unfreeze(session);
            session.setStatus(StocktakeStatus.COMPLETED);
            session.setApprovedBy(user.username());
            session.setApprovedAt(now);
            sessionRepository.save(session);
            log.info("{} đã duyệt đợt kiểm kê {}: không có chênh lệch, hoàn tất và mở đóng băng",
                    user.username(), session.getCode());
            return stocktakeService.toResponse(session, true);
        }

        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setCode(generateAdjustmentCode());
        adjustment.setSession(session);
        adjustment.setWarehouse(session.getWarehouse());
        adjustment.setNotes(StocktakeService.trimToNull(notes));
        adjustment.setRequestedBy(user.username());
        adjustment.setRequestedAt(now);
        BigDecimal increase = BigDecimal.ZERO;
        BigDecimal decrease = BigDecimal.ZERO;
        for (StocktakeLine line : varianceLines) {
            StockAdjustmentLine adjLine = new StockAdjustmentLine();
            adjLine.setStocktakeLineId(line.getId());
            adjLine.setBinLocation(line.getBinLocation());
            adjLine.setProductId(line.getProductId());
            adjLine.setLotNo(line.getLotNo());
            adjLine.setSystemQuantity(line.getSystemQuantity());
            adjLine.setCountedQuantity(line.getCountedQuantity());
            adjLine.setDifferenceQuantity(line.getVarianceQuantity());
            adjLine.setReason(line.getReason());
            adjustment.addLine(adjLine);
            if (line.getVarianceQuantity().signum() > 0) {
                increase = increase.add(line.getVarianceQuantity());
            } else {
                decrease = decrease.add(line.getVarianceQuantity().negate());
            }
        }
        adjustment.setTotalIncreaseQuantity(increase);
        adjustment.setTotalDecreaseQuantity(decrease);
        try {
            adjustmentRepository.saveAndFlush(adjustment);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Đợt kiểm kê " + session.getCode() + " đã có phiếu điều chỉnh hoặc mã phiếu vừa bị trùng, vui lòng tải lại");
        }
        session.setStatus(StocktakeStatus.PENDING_ACCOUNTING);
        sessionRepository.save(session);
        log.info("{} đã duyệt đợt kiểm kê {} và lập phiếu điều chỉnh {} ({} dòng), chờ Kế toán duyệt",
                user.username(), session.getCode(), adjustment.getCode(), varianceLines.size());
        return stocktakeService.toResponse(session, true);
    }

    /**
     * Kế toán duyệt phiếu điều chỉnh (PENDING_ACCOUNTING -> COMPLETED) trong MỘT transaction:
     * mở đóng băng, khóa bi quan các dòng tồn của từng (vị trí, sản phẩm, lô), tồn mới = tồn hiện tại + chênh lệch
     * (không âm, không nhỏ hơn số đang giữ chỗ; vi phạm thì rollback toàn bộ và trả 409), đồng bộ bảng lô,
     * ghi tồn trước và sau vào dòng phiếu, rồi phát {@code inventory.updated} cho từng sản phẩm sau khi commit.
     */
    @Transactional
    public StocktakeSessionResponse approveAdjustment(Long id) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakePermissions.requireAccounting(user);
        StocktakeSession session = stocktakeService.loadForUpdate(id);
        if (session.getStatus() != StocktakeStatus.PENDING_ACCOUNTING) {
            throw ApiException.conflict("Đợt kiểm kê " + session.getCode()
                    + " không có phiếu điều chỉnh chờ Kế toán duyệt (hiện tại: " + session.getStatus() + ")");
        }
        StockAdjustment adjustment = adjustmentRepository.findBySessionId(session.getId())
                .orElseThrow(() -> ApiException.conflict("Đợt kiểm kê " + session.getCode() + " chưa có phiếu điều chỉnh"));
        if (adjustment.getStatus() != StockAdjustmentStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Phiếu điều chỉnh " + adjustment.getCode() + " đã được xử lý (" + adjustment.getStatus() + ")");
        }
        if (user.username().equals(session.getManagerApprovedBy())) {
            throw ApiException.forbidden("Người duyệt cấp Kế toán phải khác người đã duyệt cấp Quản lý kho ("
                    + session.getManagerApprovedBy() + ")");
        }

        // Mở đóng băng trước rồi mới khóa dòng tồn: cùng thứ tự với lúc bắt đầu kiểm kê, tránh khóa chéo
        stocktakeService.unfreeze(session);

        Long warehouseId = session.getWarehouse().getId();
        Map<Long, BigDecimal> beforeByProduct = new LinkedHashMap<>();
        for (StockAdjustmentLine line : adjustment.getLines()) {
            beforeByProduct.computeIfAbsent(line.getProductId(),
                    p -> adjustmentRepository.sumOnHandByProductAndWarehouse(p, warehouseId));
        }

        List<StocktakeReservedConflict> conflicts = new ArrayList<>();
        for (StockAdjustmentLine line : adjustment.getLines()) {
            applyLine(line, conflicts);
        }
        throwIfConflicts(conflicts);

        Instant now = Instant.now();
        adjustment.setStatus(StockAdjustmentStatus.APPROVED);
        adjustment.setApprovedBy(user.username());
        adjustment.setApprovedAt(now);
        adjustmentRepository.save(adjustment);
        session.setStatus(StocktakeStatus.COMPLETED);
        session.setApprovedBy(user.username());
        session.setApprovedAt(now);
        sessionRepository.saveAndFlush(session);

        for (Map.Entry<Long, BigDecimal> entry : beforeByProduct.entrySet()) {
            BigDecimal after = adjustmentRepository.sumOnHandByProductAndWarehouse(entry.getKey(), warehouseId);
            publishInventoryUpdated(warehouseId, entry.getKey(), entry.getValue(), after, adjustment.getCode(), now);
        }
        log.info("{} đã duyệt phiếu điều chỉnh {} của đợt kiểm kê {}: cập nhật {} dòng tồn, mở đóng băng",
                user.username(), adjustment.getCode(), session.getCode(), adjustment.getLines().size());
        return stocktakeService.toResponse(session, true);
    }

    /**
     * Từ chối kết quả kiểm kê hoặc phiếu điều chỉnh (bắt buộc lý do): mở đóng băng, tồn kho giữ nguyên.
     * Ở COUNTED: Quản trị viên hoặc Quản lý kho; ở PENDING_ACCOUNTING: thêm Kế toán.
     */
    @Transactional
    public StocktakeSessionResponse reject(Long id, String reason) {
        CurrentUser user = StocktakePermissions.requireUser();
        String trimmed = StocktakeService.trimToNull(reason);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập lý do từ chối");
        }
        StocktakeSession session = stocktakeService.loadForUpdate(id);
        Long warehouseId = session.getWarehouse().getId();
        if (session.getStatus() == StocktakeStatus.COUNTED) {
            StocktakePermissions.requireManage(user, warehouseId);
        } else if (session.getStatus() == StocktakeStatus.PENDING_ACCOUNTING) {
            if (!StocktakePermissions.canManage(user, warehouseId)) {
                StocktakePermissions.requireAccounting(user);
            }
        } else {
            throw ApiException.conflict("Chỉ từ chối được đợt kiểm kê đang chờ duyệt (hiện tại: " + session.getStatus() + ")");
        }

        Instant now = Instant.now();
        stocktakeService.unfreeze(session);
        Optional<StockAdjustment> adjustment = adjustmentRepository.findBySessionId(session.getId());
        adjustment.filter(a -> a.getStatus() == StockAdjustmentStatus.PENDING_APPROVAL).ifPresent(a -> {
            a.setStatus(StockAdjustmentStatus.REJECTED);
            a.setRejectedBy(user.username());
            a.setRejectedAt(now);
            a.setRejectReason(trimmed);
            adjustmentRepository.save(a);
        });
        session.setStatus(StocktakeStatus.REJECTED);
        session.setClosedBy(user.username());
        session.setClosedAt(now);
        session.setCloseReason(trimmed);
        sessionRepository.save(session);
        log.info("{} đã từ chối đợt kiểm kê {}: {}", user.username(), session.getCode(), trimmed);
        return stocktakeService.toResponse(session, true);
    }

    // =========================================================================================
    // PHIẾU ĐIỀU CHỈNH: TRA CỨU
    // =========================================================================================

    @Transactional(readOnly = true)
    public PageResponse<StockAdjustmentResponse> listAdjustments(Long warehouseId, StockAdjustmentStatus status,
                                                                LocalDate fromDate, LocalDate toDate, String keyword,
                                                                int page, int size) {
        CurrentUser user = StocktakePermissions.requireUser();
        StocktakePermissions.requireAdjustmentListRole(user);
        Set<Long> allowed = UserContext.resolveAllowedWarehouses(user, warehouseId);

        Specification<StockAdjustment> spec = (root, query, cb) -> cb.conjunction();
        if (!allowed.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("warehouse").get("id").in(allowed));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (fromDate != null) {
            Instant from = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (toDate != null) {
            Instant to = toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
        }
        String kw = StocktakeService.trimToNull(keyword);
        if (kw != null) {
            String pattern = "%" + kw.toUpperCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.upper(root.get("code")), pattern));
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), StocktakeService.clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(adjustmentRepository.findAll(spec, pageable).map(a -> StockAdjustmentResponse.from(a, false)));
    }

    @Transactional(readOnly = true)
    public StockAdjustmentResponse getAdjustment(Long id) {
        CurrentUser user = StocktakePermissions.requireUser();
        StockAdjustment adjustment = adjustmentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu điều chỉnh với ID: " + id));
        StocktakePermissions.requireSeeSystemQuantities(user, adjustment.getWarehouse().getId());
        return StockAdjustmentResponse.from(adjustment, true);
    }

    // =========================================================================================
    // HÀM NỘI BỘ
    // =========================================================================================

    /** Kiểm tra một bộ (vị trí, sản phẩm, lô): tồn mới không âm và không nhỏ hơn giữ chỗ. Trả về true nếu hợp lệ. */
    private boolean checkLine(List<StockItem> rows, BigDecimal difference, Long lineId, String binCode, Long productId,
                              String lotNo, List<StocktakeReservedConflict> conflicts) {
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal reserved = BigDecimal.ZERO;
        for (StockItem row : rows) {
            current = current.add(StocktakeService.nz(row.getQuantity()));
            reserved = reserved.add(StocktakeService.nz(row.getReservedQuantity()));
        }
        BigDecimal newQuantity = current.add(difference);
        if (newQuantity.signum() < 0 || newQuantity.compareTo(reserved) < 0) {
            conflicts.add(new StocktakeReservedConflict(lineId, binCode, productId, lotNo, newQuantity, reserved));
            return false;
        }
        return true;
    }

    private void throwIfConflicts(List<StocktakeReservedConflict> conflicts) {
        if (!conflicts.isEmpty()) {
            StocktakeReservedConflict first = conflicts.get(0);
            throw ApiException.withData(ErrorCode.CONFLICT, String.format(
                    "Có %d dòng mà tồn sau điều chỉnh sẽ nhỏ hơn số đang giữ chỗ cho đơn hoặc bị âm (ví dụ vị trí %s, sản phẩm %d: tồn mới %s, đang giữ %s). "
                            + "Hãy hủy hoặc chuyển giữ chỗ của đơn, hoặc yêu cầu đếm lại trước khi duyệt",
                    conflicts.size(), first.binLocationCode(), first.productId(),
                    first.newQuantity().toPlainString(), first.reservedQuantity().toPlainString()), conflicts);
        }
    }

    /** Áp dụng một dòng phiếu vào tồn kho dưới khóa bi quan; vi phạm thì ghi vào danh sách xung đột, không đổi tồn. */
    private void applyLine(StockAdjustmentLine line, List<StocktakeReservedConflict> conflicts) {
        Long binId = line.getBinLocation().getId();
        List<StockItem> rows = stockItemRepository.findByBinLocationIdAndProductIdAndLotNoForUpdate(
                binId, line.getProductId(), line.getLotNo());
        BigDecimal difference = line.getDifferenceQuantity();
        if (!checkLine(rows, difference, line.getStocktakeLineId(), line.getBinLocation().getCode(),
                line.getProductId(), line.getLotNo(), conflicts)) {
            return;
        }
        BigDecimal before = rows.stream().map(r -> StocktakeService.nz(r.getQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (difference.signum() > 0) {
            if (rows.isEmpty()) {
                StockItem created = new StockItem();
                created.setProductId(line.getProductId());
                created.setBinLocation(line.getBinLocation());
                created.setLotNo(line.getLotNo());
                created.setQuantity(difference);
                created.setReservedQuantity(BigDecimal.ZERO);
                created.setLocked(false);
                stockItemRepository.save(created);
            } else {
                StockItem first = rows.get(0);
                first.setQuantity(StocktakeService.nz(first.getQuantity()).add(difference));
                stockItemRepository.save(first);
            }
        } else {
            // Giảm phần tồn chưa giữ chỗ trước; tổng tồn mới đã được kiểm tra không nhỏ hơn tổng giữ chỗ
            BigDecimal remaining = difference.negate();
            for (StockItem row : rows) {
                BigDecimal free = StocktakeService.nz(row.getQuantity()).subtract(StocktakeService.nz(row.getReservedQuantity())).max(BigDecimal.ZERO);
                BigDecimal take = free.min(remaining);
                row.setQuantity(StocktakeService.nz(row.getQuantity()).subtract(take));
                remaining = remaining.subtract(take);
            }
            for (StockItem row : rows) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal take = StocktakeService.nz(row.getQuantity()).min(remaining);
                row.setQuantity(StocktakeService.nz(row.getQuantity()).subtract(take));
                remaining = remaining.subtract(take);
            }
            stockItemRepository.saveAll(rows);
        }
        line.setQuantityBefore(before);
        line.setQuantityAfter(before.add(difference));
        syncLot(line, difference);
    }

    /** Đồng bộ bảng inventory_lots cho hàng theo lô (cùng cách các luồng nhập, xuất đang làm). */
    private void syncLot(StockAdjustmentLine line, BigDecimal difference) {
        if (line.getLotNo() == null) {
            return;
        }
        Optional<InventoryLot> lot = inventoryLotRepository.findByLotNumberAndBinLocationIdAndProductIdForUpdate(
                line.getLotNo(), line.getBinLocation().getId(), line.getProductId());
        if (lot.isPresent()) {
            InventoryLot existing = lot.get();
            existing.setQuantity(StocktakeService.nz(existing.getQuantity()).add(difference).max(BigDecimal.ZERO));
            inventoryLotRepository.save(existing);
        } else if (difference.signum() > 0) {
            InventoryLot created = new InventoryLot();
            created.setLotNumber(line.getLotNo());
            created.setProductId(line.getProductId());
            created.setBinLocation(line.getBinLocation());
            created.setQuantity(difference);
            created.setReservedQuantity(BigDecimal.ZERO);
            created.setLocked(false);
            inventoryLotRepository.save(created);
        }
    }

    private void publishInventoryUpdated(Long warehouseId, Long productId, BigDecimal before, BigDecimal after,
                                         String adjustmentCode, Instant now) {
        if (wmsEventPublisher == null) {
            return;
        }
        try {
            // skuCode để null: inventory-service không lưu SKU; bên nhận hiển thị theo productId (xem docs/MESSAGING.md)
            wmsEventPublisher.publishAfterCommit(WmsEvents.RoutingKeys.INVENTORY_UPDATED, new InventoryUpdatedEvent(
                    warehouseId, productId, null, before, after, EVENT_REASON, adjustmentCode, now));
        } catch (Exception ex) {
            log.warn("Không thể phát sự kiện inventory.updated cho phiếu điều chỉnh {}: {}", adjustmentCode, ex.getMessage());
        }
    }

    private synchronized String generateAdjustmentCode() {
        String prefix = "ADJ-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        long next = adjustmentRepository.countByCodePrefix(prefix) + 1;
        String candidate;
        do {
            candidate = String.format("%s%02d", prefix, next++);
        } while (adjustmentRepository.existsByCode(candidate));
        return candidate;
    }
}
