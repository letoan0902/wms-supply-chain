package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.InventoryUpdatedEvent;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.dto.DeductStockResponse;
import vn.wms.inventory.dto.ReceiveStockTransferItemRequest;
import vn.wms.inventory.dto.ReceiveStockTransferRequest;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.dto.StockTransferResponse;
import vn.wms.inventory.dto.UpdateStockTransferRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockTransfer;
import vn.wms.inventory.entity.StockTransferItem;
import vn.wms.inventory.entity.StockTransferStatus;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockTransferItemRepository;
import vn.wms.inventory.repository.StockTransferRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xử lý nghiệp vụ điều chuyển hàng hóa nội bộ giữa các kho (FR08 - WMS-13.1, WMS-13.2).
 */
@Service
public class StockTransferService {

    private static final Logger log = LoggerFactory.getLogger(StockTransferService.class);

    private final StockTransferRepository stockTransferRepository;
    private final StockTransferItemRepository stockTransferItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final BinLocationRepository binLocationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockService stockService;
    private final StockDeductionService stockDeductionService;

    @Autowired(required = false)
    private WmsEventPublisher wmsEventPublisher;

    public StockTransferService(
            StockTransferRepository stockTransferRepository,
            StockTransferItemRepository stockTransferItemRepository,
            WarehouseRepository warehouseRepository,
            BinLocationRepository binLocationRepository,
            StockItemRepository stockItemRepository,
            StockService stockService,
            StockDeductionService stockDeductionService
    ) {
        this.stockTransferRepository = stockTransferRepository;
        this.stockTransferItemRepository = stockTransferItemRepository;
        this.warehouseRepository = warehouseRepository;
        this.binLocationRepository = binLocationRepository;
        this.stockItemRepository = stockItemRepository;
        this.stockService = stockService;
        this.stockDeductionService = stockDeductionService;
    }

    public void setWmsEventPublisher(WmsEventPublisher wmsEventPublisher) {
        this.wmsEventPublisher = wmsEventPublisher;
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
        // Người phụ trách kho đích cũng phải xem được phiếu để kiểm đếm nhận hàng (WMS-13.2), giống phạm vi danh sách
        checkWarehouseAccessAny(transfer.getSourceWarehouse().getId(), transfer.getDestinationWarehouse().getId());
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
                if (!sourceLoc.getWarehouse().getId().equals(transfer.getSourceWarehouse().getId())) {
                    throw ApiException.badRequest("Vị trí " + sourceLoc.getCode() + " không thuộc kho nguồn " + transfer.getSourceWarehouse().getCode());
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
        StockTransfer transfer = findTransferForUpdateOrThrow(id);

        if (transfer.getStatus() != StockTransferStatus.DRAFT) {
            throw ApiException.badRequest("Chỉ có thể duyệt xuất chuyển đối với lệnh ở trạng thái DRAFT (hiện tại: " + transfer.getStatus() + ")");
        }

        checkWarehouseWriteAccess(transfer.getSourceWarehouse().getId());

        String currentUser = UserContext.getCurrentUser()
                .map(CurrentUser::username)
                .orElse("SYSTEM");

        // Kiểm tra lại ATP lúc duyệt xuất: từ lúc lập phiếu tới giờ tồn có thể đã được giữ chỗ cho đơn bán.
        // StockDeductionService chỉ chặn âm OnHand, không chặn lấy vào phần Reserved của đơn khác.
        Map<Long, BigDecimal> requiredByProduct = new LinkedHashMap<>();
        Map<Long, String> skuByProduct = new LinkedHashMap<>();
        for (StockTransferItem item : transfer.getItems()) {
            requiredByProduct.merge(item.getProductId(), item.getQuantity(), BigDecimal::add);
            skuByProduct.putIfAbsent(item.getProductId(), item.getSkuCode());
        }
        for (Map.Entry<Long, BigDecimal> entry : requiredByProduct.entrySet()) {
            StockAtpResponse atp = stockService.getAtp(entry.getKey(), transfer.getSourceWarehouse().getId());
            BigDecimal availableAtp = atp != null && atp.atpQuantity() != null ? atp.atpQuantity() : BigDecimal.ZERO;
            if (entry.getValue().compareTo(availableAtp) > 0) {
                throw ApiException.badRequest(String.format(
                        "Sản phẩm '%s' tại kho '%s' không đủ tồn khả dụng để duyệt xuất điều chuyển (Khả dụng: %s, Yêu cầu: %s)",
                        skuByProduct.get(entry.getKey()), transfer.getSourceWarehouse().getName(), availableAtp, entry.getValue()));
            }
        }

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

        // orderId để null: StockDeductionService coi orderId là ID đơn bán (SO) và sẽ hoàn tất/trả giữ chỗ ACTIVE
        // của đơn có ID đó. ID phiếu điều chuyển và ID đơn bán là hai dãy số độc lập, truyền ID phiếu sẽ xóa nhầm
        // giữ chỗ của đơn bán trùng số. Mã phiếu điều chuyển vẫn được ghi làm số chứng từ tham chiếu.
        DeductStockRequest deductRequest = new DeductStockRequest(
                null,
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

    /**
     * Xác nhận nhận hàng tại kho đích và xử lý biên bản tổn thất (WMS-13.2 - FR08).
     * <p>
     * Thao tác này kích hoạt:
     * 1. Xác thực trạng thái lệnh điều chuyển phải là IN_TRANSIT.
     * 2. Phân quyền truy cập ghi tại kho nhận đích.
     * 3. Kiểm đếm toàn bộ mặt hàng; ghi nhận số lượng thực nhận (receivedQuantity), số lượng thiếu/hỏng (damagedQuantity).
     * 4. Khóa bi quan và tăng tồn kho OnHand tại kho đích cho từng sản phẩm tại vị trí ô/kệ tương ứng.
     * 5. Xác định trạng thái cuối cùng:
     *    - COMPLETED nếu nhận đủ toàn bộ và không có hàng thiếu/hỏng.
     *    - PARTIAL_RECEIVED nếu có hàng hư hỏng hoặc nhận thiếu so với số xuất, kèm biên bản sự cố.
     * 6. Phát sự kiện {@code inventory.updated} ("TRANSFER_IN") cho từng SKU nhận tại kho đích.
     */
    @Transactional(rollbackFor = Exception.class)
    public StockTransferResponse receiveTransfer(Long id, ReceiveStockTransferRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw ApiException.badRequest("Danh sách mặt hàng nhận không được để trống");
        }

        StockTransfer transfer = findTransferForUpdateOrThrow(id);

        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT) {
            throw ApiException.badRequest("Chỉ có thể nhận hàng đối với lệnh điều chuyển ở trạng thái IN_TRANSIT (hiện tại: " + transfer.getStatus() + ")");
        }

        checkWarehouseWriteAccess(transfer.getDestinationWarehouse().getId());

        String currentUser = UserContext.getCurrentUser()
                .map(CurrentUser::username)
                .orElse("SYSTEM");

        Map<Long, StockTransferItem> transferItemMap = transfer.getItems().stream()
                .collect(Collectors.toMap(StockTransferItem::getId, item -> item));

        // 1. Kiểm tra tính đầy đủ và không trùng lặp của toàn bộ danh sách mặt hàng trước khi ghi dữ liệu
        Set<Long> requestItemIds = new HashSet<>();
        for (ReceiveStockTransferItemRequest itemReq : request.items()) {
            if (!transferItemMap.containsKey(itemReq.itemId())) {
                throw ApiException.badRequest("Mặt hàng có ID " + itemReq.itemId() + " không thuộc lệnh điều chuyển này");
            }
            if (!requestItemIds.add(itemReq.itemId())) {
                throw ApiException.badRequest("Mặt hàng ID " + itemReq.itemId() + " bị trùng lặp trong yêu cầu nhận hàng");
            }
        }

        if (requestItemIds.size() < transfer.getItems().size()) {
            throw ApiException.badRequest("Cần kiểm đếm và xác nhận toàn bộ danh sách mặt hàng trong lệnh điều chuyển");
        }

        boolean hasDiscrepancy = false;

        for (ReceiveStockTransferItemRequest itemReq : request.items()) {
            StockTransferItem item = transferItemMap.get(itemReq.itemId());

            BigDecimal receivedQty = itemReq.receivedQuantity();
            if (receivedQty == null || receivedQty.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest("Số lượng thực nhận của sản phẩm " + item.getSkuCode() + " không được âm");
            }

            BigDecimal damagedQty = itemReq.damagedQuantityOrDefault();
            if (damagedQty.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest("Số lượng thiếu/hỏng của sản phẩm " + item.getSkuCode() + " không được âm");
            }

            BigDecimal transferredQty = item.getTransferredQuantity();
            BigDecimal totalAccounted = receivedQty.add(damagedQty);

            if (totalAccounted.compareTo(transferredQty) > 0) {
                throw ApiException.badRequest(String.format(
                        "Tổng số lượng thực nhận (%s) và thiếu/hỏng (%s) vượt quá số lượng đã xuất chuyển (%s) cho sản phẩm '%s'",
                        receivedQty, damagedQty, transferredQty, item.getSkuCode()));
            }

            // Có sự cố nếu phát hiện hư hỏng hoặc số thực nhận nhỏ hơn số đã xuất
            if (damagedQty.compareTo(BigDecimal.ZERO) > 0 || receivedQty.compareTo(transferredQty) < 0) {
                hasDiscrepancy = true;
            }

            BinLocation destBinLocation = null;
            if (receivedQty.compareTo(BigDecimal.ZERO) > 0) {
                if (itemReq.destinationBinLocationId() == null) {
                    throw ApiException.badRequest("Vui lòng chọn vị trí ô/kệ tại kho đích cho sản phẩm " + item.getSkuCode());
                }
                destBinLocation = binLocationRepository.findById(itemReq.destinationBinLocationId())
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí ô/kệ có ID: " + itemReq.destinationBinLocationId()));

                if (!destBinLocation.getWarehouse().getId().equals(transfer.getDestinationWarehouse().getId())) {
                    throw ApiException.badRequest(String.format(
                            "Vị trí '%s' không thuộc kho đích '%s'",
                            destBinLocation.getCode(), transfer.getDestinationWarehouse().getCode()));
                }
                if (!destBinLocation.isActive()) {
                    throw ApiException.badRequest("Vị trí '" + destBinLocation.getCode() + "' hiện đang bị khóa hoặc ngừng hoạt động");
                }

                // Cập nhật tăng tồn kho OnHand tại kho đích với Khóa bi quan
                List<StockItem> existingItems = stockItemRepository
                        .findAllByBinLocationIdAndProductIdWithPessimisticLock(destBinLocation.getId(), item.getProductId());

                // Phiếu điều chuyển chưa lưu lô xuất đi nên hàng nhận về là hàng không theo lô: chỉ cộng dồn vào dòng
                // không có số lô, không có hạn dùng và không bị khóa. Cộng vào dòng của một lô khác sẽ gán nhầm
                // số lô, hạn dùng (sai FEFO, bị khóa khi lô đó hết hạn) hoặc nhập thẳng vào dòng đang bị khóa.
                Optional<StockItem> mergeTarget = existingItems.stream()
                        .filter(s -> (s.getLotNo() == null || s.getLotNo().isBlank())
                                && s.getExpDate() == null
                                && !s.isLocked())
                        .findFirst();

                StockItem destStockItem;
                BigDecimal beforeQty;
                if (mergeTarget.isPresent()) {
                    destStockItem = mergeTarget.get();
                    beforeQty = destStockItem.getQuantity() != null ? destStockItem.getQuantity() : BigDecimal.ZERO;
                    destStockItem.setQuantity(beforeQty.add(receivedQty));
                } else {
                    destStockItem = new StockItem();
                    destStockItem.setBinLocation(destBinLocation);
                    destStockItem.setProductId(item.getProductId());
                    destStockItem.setQuantity(receivedQty);
                    destStockItem.setReservedQuantity(BigDecimal.ZERO);
                    destStockItem.setLocked(false);
                    beforeQty = BigDecimal.ZERO;
                }
                StockItem savedItem = stockItemRepository.save(destStockItem);
                BigDecimal afterQty = (savedItem != null && savedItem.getQuantity() != null)
                        ? savedItem.getQuantity()
                        : destStockItem.getQuantity();

                // Phát sự kiện inventory.updated cho SKU tại kho đích
                publishInventoryUpdated(
                        transfer.getDestinationWarehouse().getId(),
                        item.getProductId(),
                        item.getSkuCode(),
                        beforeQty,
                        afterQty,
                        "TRANSFER_IN",
                        transfer.getTransferNumber(),
                        Instant.now()
                );
            }

            item.setReceivedQuantity(receivedQty);
            item.setDamagedQuantity(damagedQty);
            item.setDestinationBinLocation(destBinLocation);
            if (itemReq.notes() != null && !itemReq.notes().trim().isEmpty()) {
                item.setNotes(itemReq.notes().trim());
            }
        }

        // Cập nhật thông tin nhận hàng trên lệnh điều chuyển
        transfer.setReceivedBy(currentUser);
        transfer.setReceivedAt(Instant.now());
        if (request.notes() != null && !request.notes().trim().isEmpty()) {
            String existingNotes = transfer.getNotes() != null ? transfer.getNotes() + " | " : "";
            transfer.setNotes(existingNotes + "Biên bản nhận hàng: " + request.notes().trim());
        }

        transfer.setStatus(hasDiscrepancy ? StockTransferStatus.PARTIAL_RECEIVED : StockTransferStatus.COMPLETED);

        StockTransfer saved = stockTransferRepository.save(transfer);
        log.info("Lệnh điều chuyển {} đã nhận hàng thành công với trạng thái: {}",
                saved.getTransferNumber(), saved.getStatus());

        return StockTransferResponse.from(saved);
    }

    private void publishInventoryUpdated(
            Long warehouseId,
            Long productId,
            String skuCode,
            BigDecimal beforeQty,
            BigDecimal afterQty,
            String reason,
            String referenceNumber,
            Instant now
    ) {
        if (wmsEventPublisher != null) {
            try {
                InventoryUpdatedEvent event = new InventoryUpdatedEvent(
                        warehouseId, productId, skuCode, beforeQty, afterQty, reason, referenceNumber, now
                );
                wmsEventPublisher.publishAfterCommit(WmsEvents.RoutingKeys.INVENTORY_UPDATED, event);
                log.debug("Đã phát sự kiện inventory.updated cho sản phẩm {} tại kho {}", productId, warehouseId);
            } catch (Exception ex) {
                log.warn("Không thể phát sự kiện inventory.updated cho SKU {}: {}", skuCode, ex.getMessage());
            }
        }
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

    private StockTransfer findTransferForUpdateOrThrow(Long id) {
        return stockTransferRepository.findByIdForUpdate(id)
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

    private void checkWarehouseAccessAny(Long sourceWarehouseId, Long destinationWarehouseId) {
        UserContext.getCurrentUser().ifPresent(user -> {
            if (!UserContext.isGlobalScope(user)
                    && !user.warehouseIds().contains(sourceWarehouseId)
                    && !user.warehouseIds().contains(destinationWarehouseId)) {
                throw ApiException.forbidden("Bạn không có quyền xem lệnh điều chuyển giữa kho ID: "
                        + sourceWarehouseId + " và kho ID: " + destinationWarehouseId);
            }
        });
    }

    private void checkWarehouseWriteAccess(Long warehouseId) {
        UserContext.getCurrentUser().ifPresent(user -> {
            UserContext.requireWarehouseWriteAccess(user, warehouseId);
        });
    }
}

