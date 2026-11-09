package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.dto.DeductStockResponse;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.dto.StockTransferResponse;
import vn.wms.inventory.dto.UpdateStockTransferRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockTransfer;
import vn.wms.inventory.entity.StockTransferItem;
import vn.wms.inventory.entity.StockTransferStatus;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockTransferItemRepository;
import vn.wms.inventory.repository.StockTransferRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Xử lý nghiệp vụ điều chuyển hàng hóa nội bộ giữa các kho (FR08 - WMS-13.1).
 */
@Service
public class StockTransferService {

    private static final Logger log = LoggerFactory.getLogger(StockTransferService.class);

    private final StockTransferRepository stockTransferRepository;
    private final StockTransferItemRepository stockTransferItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final BinLocationRepository binLocationRepository;
    private final StockService stockService;
    private final StockDeductionService stockDeductionService;

    public StockTransferService(
            StockTransferRepository stockTransferRepository,
            StockTransferItemRepository stockTransferItemRepository,
            WarehouseRepository warehouseRepository,
            BinLocationRepository binLocationRepository,
            StockService stockService,
            StockDeductionService stockDeductionService
    ) {
        this.stockTransferRepository = stockTransferRepository;
        this.stockTransferItemRepository = stockTransferItemRepository;
        this.warehouseRepository = warehouseRepository;
        this.binLocationRepository = binLocationRepository;
        this.stockService = stockService;
        this.stockDeductionService = stockDeductionService;
    }

    /**
     * Tạo mới một lệnh điều chuyển hàng hóa ở trạng thái DRAFT (WMS-13.1).
     */
    @Transactional
    public StockTransferResponse createTransfer(CreateStockTransferRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Yêu cầu tạo lệnh điều chuyển không được để trống");
        }

        if (request.sourceWarehouseId().equals(request.destinationWarehouseId())) {
            throw ApiException.badRequest("Kho xuất nguồn và kho nhập đích không được trùng nhau");
        }

        Warehouse sourceWarehouse = warehouseRepository.findById(request.sourceWarehouseId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho xuất nguồn có ID: " + request.sourceWarehouseId()));

        Warehouse destWarehouse = warehouseRepository.findById(request.destinationWarehouseId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho nhập đích có ID: " + request.destinationWarehouseId()));

        if (!sourceWarehouse.isActive()) {
            throw ApiException.badRequest("Kho nguồn '" + sourceWarehouse.getName() + "' hiện đang bị khóa hoặc ngừng hoạt động");
        }
        if (!destWarehouse.isActive()) {
            throw ApiException.badRequest("Kho đích '" + destWarehouse.getName() + "' hiện đang bị khóa hoặc ngừng hoạt động");
        }

        checkWarehouseAccess(sourceWarehouse.getId());

        if (request.items() == null || request.items().isEmpty()) {
            throw ApiException.badRequest("Danh sách mặt hàng điều chuyển không được rỗng");
        }

        StockTransfer transfer = StockTransfer.builder()
                .transferNumber(generateTransferNumber())
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destWarehouse)
                .status(StockTransferStatus.DRAFT)
                .expectedDeliveryDate(request.expectedDeliveryDate())
                .notes(request.notes() != null ? request.notes().trim() : null)
                .items(new ArrayList<>())
                .build();

        for (CreateStockTransferItemRequest itemReq : request.items()) {
            validateItem(itemReq, sourceWarehouse);

            BinLocation sourceLoc = null;
            if (itemReq.sourceBinLocationId() != null) {
                sourceLoc = binLocationRepository.findById(itemReq.sourceBinLocationId())
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí ô/kệ có ID: " + itemReq.sourceBinLocationId()));
                if (!sourceLoc.getWarehouse().getId().equals(sourceWarehouse.getId())) {
                    throw ApiException.badRequest("Vị trí " + sourceLoc.getCode() + " không thuộc kho nguồn " + sourceWarehouse.getCode());
                }
            }

            StockTransferItem item = StockTransferItem.builder()
                    .transfer(transfer)
                    .productId(itemReq.productId())
                    .skuCode(itemReq.skuCode().trim())
                    .productName(itemReq.productName() != null ? itemReq.productName().trim() : null)
                    .uom(itemReq.uom() != null ? itemReq.uom().trim() : null)
                    .sourceBinLocation(sourceLoc)
                    .quantity(itemReq.quantity())
                    .transferredQuantity(BigDecimal.ZERO)
                    .receivedQuantity(BigDecimal.ZERO)
                    .damagedQuantity(BigDecimal.ZERO)
                    .notes(itemReq.notes() != null ? itemReq.notes().trim() : null)
                    .build();

            transfer.addItem(item);
        }

        StockTransfer saved = stockTransferRepository.save(transfer);
        log.info("Đã tạo lệnh điều chuyển mới: transferNumber='{}', từ kho '{}' tới kho '{}'",
                saved.getTransferNumber(), sourceWarehouse.getCode(), destWarehouse.getCode());
        return StockTransferResponse.from(saved);
    }

    /**
     * Tra cứu danh sách lệnh điều chuyển có phân trang và bộ lọc (WMS-13.1).
     */
    @Transactional(readOnly = true)
    public Page<StockTransferResponse> listTransfers(
            StockTransferStatus status,
            Long sourceWarehouseId,
            Long destinationWarehouseId,
            String keyword,
            Pageable pageable
    ) {
        Specification<StockTransfer> spec = Specification.where(null);

        // Phân quyền theo kho phân công của tài khoản
        Optional<CurrentUser> userOpt = UserContext.getCurrentUser();
        if (userOpt.isPresent() && !UserContext.isGlobalScope(userOpt.get())) {
            Set<Long> userWarehouseIds = userOpt.get().warehouseIds();
            if (userWarehouseIds.isEmpty()) {
                throw ApiException.forbidden("Tài khoản chưa được phân quyền phụ trách bất kỳ kho nào");
            }
            spec = spec.and((root, query, cb) -> cb.or(
                    root.get("sourceWarehouse").get("id").in(userWarehouseIds),
                    root.get("destinationWarehouse").get("id").in(userWarehouseIds)
            ));
        }

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (sourceWarehouseId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sourceWarehouse").get("id"), sourceWarehouseId));
        }

        if (destinationWarehouseId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("destinationWarehouse").get("id"), destinationWarehouseId));
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("transferNumber")), kw),
                    cb.like(cb.lower(root.get("notes")), kw)
            ));
        }

        return stockTransferRepository.findAll(spec, pageable).map(StockTransferResponse::from);
    }

    /**
     * Xem chi tiết một lệnh điều chuyển theo ID.
     */
    @Transactional(readOnly = true)
    public StockTransferResponse getTransferById(Long id) {
        StockTransfer transfer = findTransferOrThrow(id);
        checkWarehouseAccess(transfer.getSourceWarehouse().getId());
        return StockTransferResponse.from(transfer);
    }

    /**
     * Cập nhật thông tin lệnh điều chuyển (chỉ áp dụng khi còn DRAFT).
     */
    @Transactional
    public StockTransferResponse updateTransfer(Long id, UpdateStockTransferRequest request) {
        StockTransfer transfer = findTransferOrThrow(id);

        if (transfer.getStatus() != StockTransferStatus.DRAFT) {
            throw ApiException.badRequest("Chỉ có thể chỉnh sửa lệnh điều chuyển khi còn ở trạng thái DRAFT");
        }

        checkWarehouseWriteAccess(transfer.getSourceWarehouse().getId());

        if (request.items() == null || request.items().isEmpty()) {
            throw ApiException.badRequest("Danh sách mặt hàng điều chuyển không được rỗng");
        }

        transfer.setExpectedDeliveryDate(request.expectedDeliveryDate());
        transfer.setNotes(request.notes() != null ? request.notes().trim() : null);

        // Làm mới danh sách mặt hàng
        transfer.getItems().clear();
        for (CreateStockTransferItemRequest itemReq : request.items()) {
            validateItem(itemReq, transfer.getSourceWarehouse());

            BinLocation sourceLoc = null;
            if (itemReq.sourceBinLocationId() != null) {
                sourceLoc = binLocationRepository.findById(itemReq.sourceBinLocationId())
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí có ID: " + itemReq.sourceBinLocationId()));
            }

            StockTransferItem item = StockTransferItem.builder()
                    .transfer(transfer)
                    .productId(itemReq.productId())
                    .skuCode(itemReq.skuCode().trim())
                    .productName(itemReq.productName() != null ? itemReq.productName().trim() : null)
                    .uom(itemReq.uom() != null ? itemReq.uom().trim() : null)
                    .sourceBinLocation(sourceLoc)
                    .quantity(itemReq.quantity())
                    .transferredQuantity(BigDecimal.ZERO)
                    .receivedQuantity(BigDecimal.ZERO)
                    .damagedQuantity(BigDecimal.ZERO)
                    .notes(itemReq.notes() != null ? itemReq.notes().trim() : null)
                    .build();

            transfer.addItem(item);
        }

        StockTransfer updated = stockTransferRepository.save(transfer);
        return StockTransferResponse.from(updated);
    }

    /**
     * Hủy lệnh điều chuyển khi còn ở trạng thái DRAFT (WMS-13.1).
     */
    @Transactional
    public StockTransferResponse cancelTransfer(Long id, String reason) {
        StockTransfer transfer = findTransferOrThrow(id);

        if (transfer.getStatus() != StockTransferStatus.DRAFT) {
            throw ApiException.badRequest("Chỉ có thể hủy lệnh điều chuyển khi còn ở trạng thái DRAFT");
        }

        checkWarehouseWriteAccess(transfer.getSourceWarehouse().getId());

        transfer.setStatus(StockTransferStatus.CANCELLED);
        if (reason != null && !reason.trim().isEmpty()) {
            String existingNotes = transfer.getNotes() != null ? transfer.getNotes() + " | " : "";
            transfer.setNotes(existingNotes + "Lý do hủy: " + reason.trim());
        }

        StockTransfer cancelled = stockTransferRepository.save(transfer);
        log.info("Đã hủy lệnh điều chuyển: transferNumber='{}'", cancelled.getTransferNumber());
        return StockTransferResponse.from(cancelled);
    }

    /**
     * Duyệt xuất kho và chuyển hàng đi đường (DRAFT -> IN_TRANSIT) (WMS-13.1 - FR08).
     * <p>
     * Thao tác này kích hoạt:
     * 1. Khóa bi quan và trừ tồn kho thực tế tại kho xuất nguồn (ngăn ngừa âm kho).
     * 2. Ghi nhận số lượng thực xuất đi đường (transferredQuantity = quantity).
     * 3. Chuyển trạng thái lệnh sang IN_TRANSIT ("Hàng đang đi đường").
     * 4. Phát sự kiện {@code inventory.updated} cho từng SKU tại kho nguồn.
     */
    @Transactional(rollbackFor = Exception.class)
    public StockTransferResponse dispatchTransfer(Long id) {
        StockTransfer transfer = findTransferOrThrow(id);

        if (transfer.getStatus() != StockTransferStatus.DRAFT) {
            throw ApiException.badRequest("Chỉ có thể duyệt xuất chuyển đối với lệnh ở trạng thái DRAFT (hiện tại: " + transfer.getStatus() + ")");
        }

        checkWarehouseWriteAccess(transfer.getSourceWarehouse().getId());

        String currentUser = UserContext.getCurrentUser()
                .map(CurrentUser::username)
                .orElse("SYSTEM");

        // Chuẩn bị danh sách trừ tồn kho với Khóa bi quan
        List<DeductStockItemRequest> deductItems = new ArrayList<>();
        for (StockTransferItem item : transfer.getItems()) {
            deductItems.add(new DeductStockItemRequest(
                    item.getProductId(),
                    item.getSkuCode(),
                    item.getQuantity(),
                    item.getSourceBinLocation() != null ? item.getSourceBinLocation().getId() : null,
                    null
            ));
        }

        DeductStockRequest deductRequest = new DeductStockRequest(
                transfer.getId(),
                transfer.getTransferNumber(),
                transfer.getSourceWarehouse().getId(),
                "TRANSFER_OUT",
                "Xuất kho điều chuyển sang kho " + transfer.getDestinationWarehouse().getName() + " (" + transfer.getDestinationWarehouse().getCode() + ")",
                deductItems
        );

        // Trừ tồn kho nguồn an toàn tuyệt đối với Khóa bi quan PESSIMISTIC_WRITE
        DeductStockResponse deductResponse = stockDeductionService.deductStock(deductRequest, currentUser);
        log.info("Đã trừ tồn kho nguồn thành công cho lệnh điều chuyển {}: {} mặt hàng đã trừ",
                transfer.getTransferNumber(), deductResponse.deductedItems().size());

        // Cập nhật trạng thái lệnh sang IN_TRANSIT (Hàng đang đi đường)
        transfer.setStatus(StockTransferStatus.IN_TRANSIT);
        transfer.setTransferDate(Instant.now());
        transfer.setApprovedBy(currentUser);
        transfer.setApprovedAt(Instant.now());

        for (StockTransferItem item : transfer.getItems()) {
            item.setTransferredQuantity(item.getQuantity());
        }

        StockTransfer dispatched = stockTransferRepository.save(transfer);
        log.info("Lệnh điều chuyển {} đã chuyển sang trạng thái IN_TRANSIT", dispatched.getTransferNumber());
        return StockTransferResponse.from(dispatched);
    }

    private void validateItem(CreateStockTransferItemRequest itemReq, Warehouse sourceWarehouse) {
        if (itemReq.quantity() == null || itemReq.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Số lượng điều chuyển của sản phẩm " + itemReq.skuCode() + " phải lớn hơn 0");
        }

        StockAtpResponse atpResponse = stockService.getAtp(itemReq.productId(), sourceWarehouse.getId());
        BigDecimal availableAtp = atpResponse.atpQuantity() != null ? atpResponse.atpQuantity() : BigDecimal.ZERO;
        if (itemReq.quantity().compareTo(availableAtp) > 0) {
            throw ApiException.badRequest(String.format(
                    "Sản phẩm '%s' tại kho '%s' không đủ tồn khả dụng để lập lệnh điều chuyển (Khả dụng: %s, Yêu cầu: %s)",
                    itemReq.skuCode(), sourceWarehouse.getName(), availableAtp, itemReq.quantity()
            ));
        }
    }

    private StockTransfer findTransferOrThrow(Long id) {
        return stockTransferRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lệnh điều chuyển có ID: " + id));
    }

    private synchronized String generateTransferNumber() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "TRF-" + dateStr + "-";
        long count = stockTransferRepository.countByTransferNumberPrefix(prefix) + 1;
        String candidate;
        do {
            candidate = String.format("%s%04d", prefix, count++);
        } while (stockTransferRepository.existsByTransferNumber(candidate));
        return candidate;
    }

    private void checkWarehouseAccess(Long warehouseId) {
        UserContext.getCurrentUser().ifPresent(user -> {
            if (!UserContext.isGlobalScope(user) && !user.warehouseIds().contains(warehouseId)) {
                throw ApiException.forbidden("Bạn không có quyền thao tác trên kho ID: " + warehouseId);
            }
        });
    }

    private void checkWarehouseWriteAccess(Long warehouseId) {
        UserContext.getCurrentUser().ifPresent(user -> {
            UserContext.requireWarehouseWriteAccess(user, warehouseId);
        });
    }
}

